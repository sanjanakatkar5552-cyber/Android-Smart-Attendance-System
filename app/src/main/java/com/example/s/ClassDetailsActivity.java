package com.example.s;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.google.firebase.firestore.DocumentSnapshot;
import java.util.HashMap;
import java.util.Map;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.chip.Chip;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class ClassDetailsActivity extends AppCompatActivity {

    private String classId;
    private FirebaseFirestore db;

    private double centerLat = 0;
    private double centerLng = 0;
    private int radius = 30; // default radius

    private Button btnStart;

    private TextView tvClassName;
    private TextView tvStudentCount;
    private TextView tvRadius;
    private Chip chipSessionStatus;

    private Handler sessionHandler = new Handler();
    private Runnable sessionRunnable;

    private String activeSessionId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_class_details);

        db = FirebaseFirestore.getInstance();
        classId = getIntent().getStringExtra("classId");

        tvClassName = findViewById(R.id.tvClassName);
        tvStudentCount = findViewById(R.id.tvStudentCount);
        tvRadius = findViewById(R.id.tvRadius);
        chipSessionStatus = findViewById(R.id.chipSessionStatus);
        btnStart = findViewById(R.id.btnStart);

        loadClassData();
        loadApprovedStudentCount();

        // ATTENDANCE CARD
        findViewById(R.id.cardStartAttendance).setOnClickListener(v -> {

            if (activeSessionId == null) {

                Toast.makeText(this,
                        "Start session first",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            startActivity(new Intent(this,
                    LiveSessionActivity.class)
                    .putExtra("classId", classId)
                    .putExtra("sessionId", activeSessionId));
        });

        // STUDENTS
        findViewById(R.id.cardManageStudents).setOnClickListener(v -> {

            Intent intent = new Intent(this, ManageStudentsActivity.class);
            intent.putExtra("classId", classId);
            startActivity(intent);

        });

        // REPORTS
        findViewById(R.id.cardReports).setOnClickListener(v ->
                startActivity(new Intent(this,
                        AttendanceRecordsActivity.class)
                        .putExtra("classId", classId)));

        // SETTINGS
        findViewById(R.id.cardSettings).setOnClickListener(v ->
                startActivity(new Intent(this,
                        ClassroomSettingActivity.class)
                        .putExtra("classId", classId)));

        chipSessionStatus.setOnClickListener(v -> toggleSession());

        btnStart.setOnClickListener(v -> {

            if (activeSessionId == null) {
                startSession();
            } else {
                stopSession();
            }
        });
    }

    // ================= STUDENT COUNT =================

    private void loadApprovedStudentCount(){

        db.collection("classes")
                .document(classId)
                .collection("students")
                .whereEqualTo("status","approved")
                .addSnapshotListener((value,error)->{

                    if(value == null) return;

                    int count = value.size();

                    tvStudentCount.setText(String.valueOf(count));

                });
    }

    // ================= START SESSION =================

    private void startSession() {

        FusedLocationProviderClient fusedLocationClient =
                LocationServices.getFusedLocationProviderClient(this);

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {

            if(location != null){

                centerLat = location.getLatitude();
                centerLng = location.getLongitude();
            }

            activeSessionId = "session_" + System.currentTimeMillis();
            markAllStudentsAbsent(); // 🔥 MOST IMPORTANT

            Map<String, Object> sessionData = new HashMap<>();

            sessionData.put("sessionId", activeSessionId);
            sessionData.put("classId", classId);
            sessionData.put("teacherId", FirebaseAuth.getInstance().getUid());
            sessionData.put("centerLat", centerLat);
            sessionData.put("centerLng", centerLng);
            sessionData.put("radius", radius);
            sessionData.put("startTime", FieldValue.serverTimestamp());
            sessionData.put("isActive", true);

            // SAVE SESSION
            db.collection("attendanceSessions")
                    .document(activeSessionId)
                    .set(sessionData);

            // UPDATE CLASS
            db.collection("classes")
                    .document(classId)
                    .update(
                            "centerLat", centerLat,
                            "centerLng", centerLng,
                            "radius", radius,
                            "isActive", true,
                            "activeSessionId", activeSessionId,
                            "sessionStartTime", FieldValue.serverTimestamp()
                    );

            chipSessionStatus.setText("Live");
            btnStart.setText("STOP SESSION");

            long startMillis = System.currentTimeMillis();
            startDurationTimer(startMillis);

            Toast.makeText(this,
                    "Session Started",
                    Toast.LENGTH_SHORT).show();
        });
    }

    // ================= STOP SESSION =================

    private void stopSession() {

        db.collection("classes")
                .document(classId)
                .update(
                        "isActive", false,
                        "activeSessionId", null,
                        "sessionEndTime", FieldValue.serverTimestamp()
                );

        activeSessionId = null;

        chipSessionStatus.setText("Inactive");
        btnStart.setText("START SESSION");

        if(sessionRunnable != null)
            sessionHandler.removeCallbacks(sessionRunnable);

        Toast.makeText(this,"Session Stopped",Toast.LENGTH_SHORT).show();
    }

    // ================= TOGGLE =================

    private void toggleSession() {

        if (activeSessionId == null) {
            startSession();
        } else {
            stopSession();
        }
    }

    // ================= LOAD CLASS DATA =================

    private void loadClassData() {

        db.collection("classes")
                .document(classId)
                .addSnapshotListener((doc, error) -> {

                    if(doc == null || !doc.exists()) return;

                    String subject = doc.getString("subject");
                    String year = doc.getString("year");

                    Long rad = doc.getLong("radius");
                    Boolean active = doc.getBoolean("isActive");

                    Timestamp startTime =
                            doc.getTimestamp("sessionStartTime");

                    activeSessionId =
                            doc.getString("activeSessionId");

                    Double lat = doc.getDouble("centerLat");
                    Double lng = doc.getDouble("centerLng");

                    if (lat != null) centerLat = lat;
                    if (lng != null) centerLng = lng;
                    if (rad != null) radius = rad.intValue();

                    tvClassName.setText(subject + " - " + year);
                    tvRadius.setText(radius + "m");

                    if (active != null && active) {

                        chipSessionStatus.setText("Live");
                        btnStart.setText("STOP SESSION");

                        if (startTime != null) {

                            long startMillis =
                                    startTime.toDate().getTime();

                            startDurationTimer(startMillis);
                            checkAutoClose(startTime);
                        }

                    } else {

                        chipSessionStatus.setText("Inactive");
                        btnStart.setText("START SESSION");
                    }

                });
    }

    // ================= AUTO CLOSE =================

    private void checkAutoClose(Timestamp startTime) {

        long now = System.currentTimeMillis();
        long start = startTime.toDate().getTime();

        long diff = now - start;

        if (diff >= 2 * 60 * 60 * 1000) {
            stopSession();
        }
    }

    // ================= TIMER =================

    private void startDurationTimer(long startMillis) {

        if (sessionRunnable != null)
            sessionHandler.removeCallbacks(sessionRunnable);

        sessionRunnable = new Runnable() {
            @Override
            public void run() {

                long elapsed = System.currentTimeMillis() - startMillis;

                int minutes = (int) (elapsed / 60000);
                int seconds = (int) (elapsed / 1000) % 60;

                chipSessionStatus.setText(
                        "Live • " + minutes + "m " + seconds + "s");

                sessionHandler.postDelayed(this, 1000);
            }
        };

        sessionHandler.post(sessionRunnable);
    }
    private void markAllStudentsAbsent() {

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("classes")
                .document(classId)
                .collection("students")
                .get()
                .addOnSuccessListener(query -> {

                    for (DocumentSnapshot doc : query.getDocuments()) {

                        String email = doc.getString("email");
                        String name = doc.getString("name");
                        String rollNo = doc.getString("rollNo");

                        Map<String, Object> data = new HashMap<>();

                        data.put("name", name);
                        data.put("email", email);
                        data.put("rollNo", rollNo);
                        data.put("classId", classId);
                        data.put("sessionId", activeSessionId);

                        data.put("status", "absent");
                        data.put("present", false);

                        String date = new java.text.SimpleDateFormat(
                                "dd/MM/yyyy",
                                java.util.Locale.getDefault()
                        ).format(new java.util.Date());

                        data.put("date", date);

                        FirebaseFirestore.getInstance()
                                .collection("attendance")
                                .document(activeSessionId)
                                .collection("students")
                                .document(email)
                                .set(data);
                    }

                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (sessionRunnable != null)
            sessionHandler.removeCallbacks(sessionRunnable);
    }
}
