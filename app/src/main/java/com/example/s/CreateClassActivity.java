package com.example.s;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class CreateClassActivity extends AppCompatActivity {

    private EditText etDepartment, etYear, etSubject, etBatch;
    private Button btnCreateClass;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private FusedLocationProviderClient fusedLocationClient;

    private static final int LOCATION_PERMISSION_REQUEST = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_class);

        etDepartment   = findViewById(R.id.etDepartment);
        etYear         = findViewById(R.id.etYear);
        etSubject      = findViewById(R.id.etSubject);
        etBatch        = findViewById(R.id.etBatch);
        btnCreateClass = findViewById(R.id.btnCreateClass);

        db                   = FirebaseFirestore.getInstance();
        auth                 = FirebaseAuth.getInstance();
        fusedLocationClient  = LocationServices.getFusedLocationProviderClient(this);

        btnCreateClass.setOnClickListener(v -> validateAndCreateClass());
    }

    // ================= VALIDATE =================

    private void validateAndCreateClass() {

        String department = etDepartment.getText().toString().trim();
        String year       = etYear.getText().toString().trim();
        String subject    = etSubject.getText().toString().trim();
        String batch      = etBatch.getText().toString().trim();

        if (TextUtils.isEmpty(department)) {
            etDepartment.setError("Department required");
            etDepartment.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(year)) {
            etYear.setError("Year required");
            etYear.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(subject)) {
            etSubject.setError("Subject required");
            etSubject.requestFocus();
            return;
        }
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        String teacherId = auth.getCurrentUser().getUid();

        db.collection("classes")
                .whereEqualTo("teacherId", teacherId)
                .whereEqualTo("department", department)
                .whereEqualTo("year", year)
                .whereEqualTo("subject", subject)
                .whereEqualTo("batch", batch)
                .get()
                .addOnSuccessListener(query -> {
                    if (!query.isEmpty()) {
                        Toast.makeText(this,
                                "Class already exists ⚠",
                                Toast.LENGTH_LONG).show();
                    } else {
                        saveClassToFirestore(teacherId, department, year, subject, batch);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Error checking duplicate: " + e.getMessage(),
                                Toast.LENGTH_LONG).show());
    }


    private void saveClassToFirestore(String teacherId,
                                      String department,
                                      String year,
                                      String subject,
                                      String batch) {

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);

            Toast.makeText(this,
                    "Location permission needed. Grant it and try again.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {


            double lat    = (location != null) ? location.getLatitude()  : 0.0;
            double lng    = (location != null) ? location.getLongitude() : 0.0;
            double radius = 30.0; // metres — adjust per institution policy

            Map<String, Object> classData = new HashMap<>();
            classData.put("teacherId",  teacherId);
            classData.put("department", department);
            classData.put("year",       year);
            classData.put("subject",    subject);
            classData.put("batch",      batch);
            classData.put("createdAt",  FieldValue.serverTimestamp());

            classData.put("isActive",        false);
            classData.put("activeSessionId", null);
            classData.put("sessionStartTime", null);

            classData.put("totalStudents",   0);
            classData.put("registeredFaces", 0);

            classData.put("centerLat", lat);
            classData.put("centerLng", lng);
            classData.put("radius",    radius);

            classData.put("faceEnabled",     true);
            classData.put("locationEnabled", true);
            classData.put("qrEnabled",       false);

            db.collection("classes")
                    .add(classData)
                    .addOnSuccessListener(documentReference -> {

                        String classId = documentReference.getId();

                        Toast.makeText(this,
                                "Class Created ✅",
                                Toast.LENGTH_SHORT).show();

                        fetchAndAddStudents(classId, department, year);

                        Intent intent = new Intent(this, ClassDetailsActivity.class);
                        intent.putExtra("classId", classId);
                        startActivity(intent);
                        finish();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this,
                                    "Error creating class: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show());
        });
    }

    // ================= POPULATE STUDENTS =================

    private void fetchAndAddStudents(String classId, String department, String year) {

        db.collection("students_master")
                .whereEqualTo("department", department.trim())
                .whereEqualTo("year", year.trim())
                .get()
                .addOnSuccessListener(query -> {

                    if (query.isEmpty()) {
                        Log.d("CreateClass", "No students found in master for "
                                + department + "/" + year);
                        return;
                    }

                    int count = 0;

                    for (DocumentSnapshot doc : query.getDocuments()) {

                        String email          = doc.getId();
                        String name           = doc.getString("name");
                        String rollNo         = doc.getString("rollNo");
                        Boolean faceRegistered = doc.getBoolean("faceRegistered");

                        if (faceRegistered == null) faceRegistered = false;
                        if (rollNo == null)         rollNo         = "";

                        Map<String, Object> studentData = new HashMap<>();
                        studentData.put("email",         email);
                        studentData.put("name",          name);
                        studentData.put("rollNo",        rollNo);
                        studentData.put("faceRegistered", faceRegistered);
                        studentData.put("approved",      true); // teacher is adding → auto-approved
                        studentData.put("classId",       classId);
                        studentData.put("department",    department);
                        studentData.put("year",          year);

                        db.collection("classes")
                                .document(classId)
                                .collection("students")
                                .document(email)
                                .set(studentData);

                        count++;
                    }

                    db.collection("classes")
                            .document(classId)
                            .update("totalStudents", count);

                    Toast.makeText(this,
                            count + " Students Added ✅",
                            Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Error fetching students ❌",
                                Toast.LENGTH_SHORT).show());
    }

    // ================= PERMISSION RESULT =================

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    // ================= CLEAR INPUTS =================

    private void clearFields() {
        etDepartment.setText("");
        etYear.setText("");
        etSubject.setText("");
        etBatch.setText("");
    }
}







