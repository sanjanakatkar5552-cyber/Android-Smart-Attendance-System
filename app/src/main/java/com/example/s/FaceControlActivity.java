package com.example.s;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.List;

public class FaceControlActivity extends AppCompatActivity {

    private RecyclerView rvFaceControl;
    private FirebaseFirestore db;
    private String classId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_control);

        rvFaceControl = findViewById(R.id.rvFaceControl);
        db = FirebaseFirestore.getInstance();
        classId = getIntent().getStringExtra("classId");

        if (classId != null) {
            loadStudentsForFaceApproval();
        }
    }

    private void loadStudentsForFaceApproval() {
        rvFaceControl.setLayoutManager(new LinearLayoutManager(this));
        db.collection("classes").document(classId).collection("students")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {

                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}
