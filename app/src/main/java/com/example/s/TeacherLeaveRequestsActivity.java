package com.example.s;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.s.adapter.LeaveRequestAdapter;
import com.example.s.model.LeaveModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class TeacherLeaveRequestsActivity extends AppCompatActivity {

    RecyclerView recyclerView;
    LeaveRequestAdapter adapter;

    List<LeaveModel> requestList = new ArrayList<>();

    FirebaseFirestore db;
    String teacherUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_leave_requests);

        recyclerView = findViewById(R.id.recyclerLeaves);

        db = FirebaseFirestore.getInstance();
        teacherUid = FirebaseAuth.getInstance().getUid();

        adapter = new LeaveRequestAdapter(this, requestList);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        loadLeaveRequests();
    }

    private void loadLeaveRequests() {

        db.collection("leave_requests")
                .whereEqualTo("teacherId", teacherUid)
                .addSnapshotListener((query, error) -> {

                    if (query == null) return;

                    requestList.clear();

                    for (DocumentSnapshot doc : query.getDocuments()) {

                        requestList.add(new LeaveModel(

                                doc.getId(),
                                doc.getString("studentId"),
                                doc.getString("studentName"),
                                doc.getString("rollNo"),
                                doc.getString("classId"),
                                doc.getString("reason"),
                                doc.getString("date"),
                                doc.getString("status"),
                                doc.getString("certificateImage")
                        ));
                    }

                    adapter.notifyDataSetChanged();
                });
    }
}