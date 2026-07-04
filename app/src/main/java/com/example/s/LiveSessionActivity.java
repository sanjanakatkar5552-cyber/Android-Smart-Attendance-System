package com.example.s;

import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.s.adapter.LiveStudentAdapter;
import com.example.s.model.LiveStudentModel;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LiveSessionActivity extends AppCompatActivity {

    private String classId;
    private String activeSessionId;

    private FirebaseFirestore db;

    private TextView tvLiveCount, tvMismatch, tvSessionTimer;
    private RecyclerView recyclerView;

    private List<LiveStudentModel> studentList = new ArrayList<>();
    private LiveStudentAdapter adapter;

    private ListenerRegistration attendanceListener;

    private Handler timerHandler = new Handler();
    private long startMillis;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_session);

        db = FirebaseFirestore.getInstance();

        classId = getIntent().getStringExtra("classId");
        activeSessionId = getIntent().getStringExtra("sessionId");

        tvLiveCount = findViewById(R.id.tvLiveCount);
        tvMismatch = findViewById(R.id.tvMismatch);
        tvSessionTimer = findViewById(R.id.tvSessionTimer);
        recyclerView = findViewById(R.id.recyclerLiveStudents);

        adapter = new LiveStudentAdapter(this, studentList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        findViewById(R.id.btnStopSession)
                .setOnClickListener(v -> stopSession());

        loadSessionStartTime();
        startRealtimeListener();
    }

    private void loadSessionStartTime() {

        db.collection("classes")
                .document(classId)
                .get()
                .addOnSuccessListener(doc -> {

                    Timestamp startTime = doc.getTimestamp("sessionStartTime");

                    if (startTime != null) {
                        startMillis = startTime.toDate().getTime();
                        startTimer();
                    }
                });
    }

    private void startTimer() {

        timerHandler.postDelayed(new Runnable() {
            @Override
            public void run() {

                long elapsed = System.currentTimeMillis() - startMillis;

                int minutes = (int) (elapsed / 60000);
                int seconds = (int) (elapsed / 1000) % 60;

                tvSessionTimer.setText(
                        "Live • " + minutes + "m " + seconds + "s"
                );

                // auto stop after 2 hours
                if (elapsed >= 2 * 60 * 60 * 1000) {
                    stopSession();
                }

                timerHandler.postDelayed(this, 1000);
            }
        }, 0);
    }

    private void startRealtimeListener() {

        if (activeSessionId == null) return;

        attendanceListener = db.collection("attendance")
                .document(activeSessionId)
                .collection("students")
                .addSnapshotListener((query, error) -> {

                    if (query == null) return;


                    Map<String, LiveStudentModel> deduped = new LinkedHashMap<>();

                    int mismatchCount = 0;

                    for (DocumentSnapshot doc : query.getDocuments()) {

                        String name = doc.getString("name");
                        String rollNo = doc.getString("rollNo");
                        String status = doc.getString("status");
                        Double distance = doc.getDouble("distance");

                        if (name == null || name.isEmpty()) name = "Student";
                        if (rollNo == null || rollNo.isEmpty()) rollNo = doc.getId();

                        if ("mismatch".equalsIgnoreCase(status)) mismatchCount++;

                        LiveStudentModel model = new LiveStudentModel(
                                name,
                                rollNo,
                                distance != null ? distance : 0,
                                status != null ? status : "absent"
                        );

                        if (!deduped.containsKey(rollNo)
                                || "present".equalsIgnoreCase(status)) {
                            deduped.put(rollNo, model);
                        }
                    }

                    studentList.clear();
                    studentList.addAll(deduped.values());

                    int presentCount = 0;
                    for (LiveStudentModel s : studentList) {
                        if ("present".equalsIgnoreCase(s.getStatus())) {
                            presentCount++;
                        }
                    }

                    tvLiveCount.setText("Present: " + presentCount);
                    tvMismatch.setText("Mismatch: " + mismatchCount);

                    adapter.notifyDataSetChanged();
                });
    }

    private void stopSession() {

        db.collection("classes")
                .document(classId)
                .update(
                        "isActive", false,
                        "activeSessionId", null,
                        "sessionEndTime",
                        com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
                .addOnSuccessListener(unused -> {

                    Toast.makeText(this,
                            "Session Stopped",
                            Toast.LENGTH_SHORT).show();

                    finish();
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (attendanceListener != null) {
            attendanceListener.remove();
        }

        timerHandler.removeCallbacksAndMessages(null);
    }
}

