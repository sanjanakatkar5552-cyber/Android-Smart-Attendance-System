package com.example.s;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.s.adapter.ApproveStudentAdapter;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class ApproveStudentsActivity extends AppCompatActivity {

    RecyclerView recyclerView;
    FirebaseFirestore db;
    List<DocumentSnapshot> studentList;
    ApproveStudentAdapter adapter;
    String classId;
    TextView tvHeader;
    ListenerRegistration registration;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_approve_students);

        recyclerView = findViewById(R.id.recyclerStudents);
        tvHeader = findViewById(R.id.tvHeader);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        db = FirebaseFirestore.getInstance();
        studentList = new ArrayList<>();

        classId = getIntent().getStringExtra("classId");

        adapter = new ApproveStudentAdapter(this, studentList, classId);
        recyclerView.setAdapter(adapter);

        listenForStudentUpdates();
    }

    private void listenForStudentUpdates() {
        if (classId == null) return;
        
        registration = db.collection("classes")
                .document(classId)
                .collection("students")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.w("APPROVE", "Listen failed.", e);
                        return;
                    }

                    if (snapshots != null) {
                        for (DocumentChange dc : snapshots.getDocumentChanges()) {
                            if (dc.getType() == DocumentChange.Type.MODIFIED) {
                                Boolean faceRegistered = dc.getDocument().getBoolean("faceRegistered");
                                String studentName = dc.getDocument().getString("name");
                                
                                if (Boolean.TRUE.equals(faceRegistered)) {
                                    Toast.makeText(this, "Notification: " + studentName + " has registered their face!", Toast.LENGTH_LONG).show();
                                }
                            }
                        }

                        studentList.clear();

                        for (DocumentSnapshot doc : snapshots.getDocuments()) {

                            Boolean faceRegistered = doc.getBoolean("faceRegistered");
                            Boolean approved = doc.getBoolean("approved");

                            if (Boolean.TRUE.equals(faceRegistered) && !Boolean.TRUE.equals(approved)) {
                                studentList.add(doc);
                            }
                        }

                        adapter.notifyDataSetChanged();
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (registration != null) {
            registration.remove();
        }
    }
}
