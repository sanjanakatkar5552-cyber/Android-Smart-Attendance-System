package com.example.s;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

import com.example.s.model.StudentModel;
import com.example.s.adapter.StudentAdapter;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class ManageStudentsActivity extends AppCompatActivity {

    private TextView tvClassName;
    private RecyclerView rvStudents;
    private FloatingActionButton fabAddStudent;

    private FirebaseFirestore db;
    private String classId;

    private List<StudentModel> studentList;
    private StudentAdapter studentAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_students);

        studentList = new ArrayList<>();

        studentAdapter = new StudentAdapter(this, studentList, classId);
        rvStudents = findViewById(R.id.recyclerStudents);


        rvStudents.setLayoutManager(new LinearLayoutManager(this));

        rvStudents.setAdapter(studentAdapter);

        Button btnAddStudent = findViewById(R.id.btnAddStudent);
        db = FirebaseFirestore.getInstance();
        classId = getIntent().getStringExtra("classId");

        studentList = new ArrayList<>();
        studentAdapter = new StudentAdapter(this, studentList, classId);
        rvStudents.setLayoutManager(new LinearLayoutManager(this));
        rvStudents.setAdapter(studentAdapter);

        if (classId != null) {
            loadClassDetails();
            loadStudents();
        }


        btnAddStudent.setOnClickListener(v -> {

            Intent intent = new Intent(this, AddStudentActivity.class);
            intent.putExtra("classId", classId);
            startActivity(intent);

        });
    }

    private void loadClassDetails() {

        db.collection("classes")
                .document(classId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {

                    if (documentSnapshot.exists()) {

                        String subject =
                                documentSnapshot.getString("subject");



                        Boolean isActive =
                                documentSnapshot.getBoolean("isActive");


                    }
                });
    }

    private void loadStudents(){

        db.collection("classes")
                .document(classId)
                .collection("students")
                .addSnapshotListener((value,error)->{

                    if(value==null) return;

                    studentList.clear();

                    for(DocumentSnapshot doc:value.getDocuments()){

                        StudentModel s=
                                doc.toObject(StudentModel.class);

                        studentList.add(s);
                    }

                    studentAdapter.notifyDataSetChanged();
                });

    }

    @Override
    protected void onResume() {
        super.onResume();
        loadStudents();
    }
}