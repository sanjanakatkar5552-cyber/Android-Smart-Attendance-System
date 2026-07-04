package com.example.s;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class AddStudentActivity extends AppCompatActivity {

    private EditText etStudentEmail, etStudentName,etRollNo;
    private AppCompatButton btnAddStudent, btnBulkImport;
    private ProgressBar progressBar;

    private FirebaseFirestore db;

    private String classId;
    private String subject;

    private final ActivityResultLauncher<Intent> filePickerLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {

                        if (result.getResultCode() == Activity.RESULT_OK
                                && result.getData() != null) {

                            Uri uri = result.getData().getData();

                            if (uri != null) {
                                importStudentsFromCSV(uri);
                            }
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_student);

        db = FirebaseFirestore.getInstance();

        classId = getIntent().getStringExtra("classId");
        subject = getIntent().getStringExtra("subject");

        etStudentName = findViewById(R.id.etStudentName);
        etStudentEmail = findViewById(R.id.etStudentEmail);
        etRollNo = findViewById(R.id.etRollNo);

        btnAddStudent = findViewById(R.id.btnAddStudent);
        btnBulkImport = findViewById(R.id.btnBulkImport);

        progressBar = findViewById(R.id.progressBar);

        btnAddStudent.setOnClickListener(v -> addStudent());

        btnBulkImport.setOnClickListener(v -> openCSVPicker());
        Button btnDownloadSample = findViewById(R.id.btnDownloadSample);

        btnDownloadSample.setOnClickListener(v -> {

            String sample =
                    "name,email\n" +
                            "Rahul Sharma,rahul@gmail.com\n" +
                            "Amit Patil,amit@gmail.com\n" +
                            "Priya Joshi,priya@gmail.com";

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, sample);

            startActivity(Intent.createChooser(intent,"Share Sample CSV"));
        });
    }


    private void openCSVPicker() {

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("text/*");

        filePickerLauncher.launch(intent);
    }


    private void addStudent() {

        String name = etStudentName.getText().toString().trim();
        String email = etStudentEmail.getText().toString().trim().toLowerCase();        String rollNo = etRollNo.getText().toString().trim();
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email)) {

            Toast.makeText(this,
                    "Enter student name and email",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (classId == null) {

            Toast.makeText(this,
                    "Class not found",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        saveStudentToFirestore(name, email,rollNo, true);
    }


    private void saveStudentToFirestore(String name, String email, String rollNo, boolean single) {

        progressBar.setVisibility(View.VISIBLE);

        Map<String, Object> student = new HashMap<>();

        student.put("name", name);
        student.put("email", email);
        student.put("rollNo", rollNo);
        student.put("status", "pending");
        student.put("faceRegistered", false);
        student.put("approved", true); // teacher is adding → auto-approved

        db.collection("classes")
                .document(classId)
                .collection("students")
                .document(email)
                .set(student)

                .addOnSuccessListener(unused -> {
                    db.collection("classes")
                            .document(classId)
                            .update("totalStudents",
                                    FieldValue.increment(1));

                    progressBar.setVisibility(View.GONE);

                    if (single) {

                        Toast.makeText(this,
                                "Student added successfully",
                                Toast.LENGTH_SHORT).show();

                        sendInvitationEmail(email, name);

                        finish();
                    }
                })

                .addOnFailureListener(e -> {

                    progressBar.setVisibility(View.GONE);

                    Toast.makeText(this,
                            "Error: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }


    private void importStudentsFromCSV(Uri uri) {

        progressBar.setVisibility(View.VISIBLE);

        WriteBatch batch = db.batch();

        int count = 0;

        try {

            InputStream inputStream = getContentResolver().openInputStream(uri);

            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(inputStream));

            String line;

            while ((line = reader.readLine()) != null) {

                String[] data = line.split(",");

                if (data.length >= 2) {

                    String name = data[0].trim();
                    String email = data[1].trim().toLowerCase(); // ✅ ADD                    if(name.isEmpty() || email.isEmpty()) continue;

                    if(!email.contains("@")) continue;

                    Map<String, Object> student = new HashMap<>();

                    student.put("name", name);
                    student.put("email", email);
                    student.put("status", "pending");
                    student.put("faceRegistered", false);
                    student.put("approved", true); // teacher is adding → auto-approved
                    student.put("classId", classId);
                    student.put("subject", subject);

                    batch.set(
                            db.collection("classes")
                                    .document(classId)
                                    .collection("students")
                                    .document(email),
                            student);

                    count++;
                }
            }

            reader.close();

            if (count > 0) {

                int finalCount = count;

                batch.commit().addOnSuccessListener(unused -> {

                    progressBar.setVisibility(View.GONE);

                    Toast.makeText(this,
                            "Imported " + finalCount + " students",
                            Toast.LENGTH_LONG).show();

                    finish();
                });

            } else {

                progressBar.setVisibility(View.GONE);

                Toast.makeText(this,
                        "No valid CSV data",
                        Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {

            progressBar.setVisibility(View.GONE);

            Toast.makeText(this,
                    "CSV Error: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void sendInvitationEmail(String email, String name) {

        String mailSubject =
                "Invitation to join " + (subject != null ? subject : "Class");

        String mailBody =
                "Hello " + name + ",\n\n"
                        + "You have been added to "
                        + subject
                        + ".\nPlease register and mark attendance.";

        Intent intent = new Intent(Intent.ACTION_SENDTO);

        intent.setData(Uri.parse("mailto:"));

        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{email});
        intent.putExtra(Intent.EXTRA_SUBJECT, mailSubject);
        intent.putExtra(Intent.EXTRA_TEXT, mailBody);

        startActivity(Intent.createChooser(intent, "Send Email"));
    }
}

