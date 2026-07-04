package com.example.s;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Patterns;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    EditText etEmail, etMobile, etPassword;
    EditText etName, etRollNo, etDepartment, etYear;

    AppCompatButton btnSignup;
    TextView tvLogin;
    RadioGroup rgRole;
    RadioButton rbTeacher, rbStudent;
    ProgressBar progressBar;

    FirebaseAuth auth;
    FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        etEmail      = findViewById(R.id.etEmail);
        etMobile     = findViewById(R.id.etMobile);
        etPassword   = findViewById(R.id.etPassword);
        etName       = findViewById(R.id.etName);
        etRollNo     = findViewById(R.id.etRollNo);
        etDepartment = findViewById(R.id.etDepartment);
        etYear       = findViewById(R.id.etYear);
        rbTeacher    = findViewById(R.id.rbTeacher);
        rbStudent    = findViewById(R.id.rbStudent);
        btnSignup    = findViewById(R.id.btnSignup);
        tvLogin      = findViewById(R.id.tvLogin);
        rgRole       = findViewById(R.id.rgRole);
        progressBar  = findViewById(R.id.progressBar);

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        setupLoginText();
        setupRoleVisibility();

        btnSignup.setOnClickListener(v -> registerUser());
    }

    private void setupRoleVisibility() {
        rgRole.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isStudent = (checkedId == R.id.rbStudent);
            etName.setVisibility(isStudent ? View.VISIBLE : View.GONE);
            etRollNo.setVisibility(isStudent ? View.VISIBLE : View.GONE);
            etDepartment.setVisibility(isStudent ? View.VISIBLE : View.GONE);
            etYear.setVisibility(isStudent ? View.VISIBLE : View.GONE);
        });
    }

    private void setupLoginText() {
        String text = "Already have an account? Login";
        SpannableString ss = new SpannableString(text);
        ClickableSpan clickableSpan = new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                startActivity(new Intent(RegisterActivity.this, Login_Activity2.class));
            }
            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                ds.setColor(getResources().getColor(R.color.colorPrimary));
                ds.setUnderlineText(false);
                ds.setFakeBoldText(true);
            }
        };
        int start = text.indexOf("Login");
        int end   = start + "Login".length();
        ss.setSpan(clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tvLogin.setText(ss);
        tvLogin.setMovementMethod(LinkMovementMethod.getInstance());
        tvLogin.setHighlightColor(Color.TRANSPARENT);
    }


    private void registerUser() {

        String email    = etEmail.getText().toString().trim();
        String mobile   = etMobile.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String name     = etName.getText().toString().trim();
        String roll     = etRollNo.getText().toString().trim();
        String dept     = etDepartment.getText().toString().trim();
        String year     = etYear.getText().toString().trim();

        String role = rbTeacher.isChecked() ? "teacher" : "student";

        if (email.isEmpty()) {
            etEmail.setError("Email required"); return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Enter valid email"); return;
        }
        if (mobile.isEmpty()) {
            etMobile.setError("Mobile number required"); return;
        }
        if (!mobile.matches("[0-9]{10}")) {
            etMobile.setError("Enter 10-digit mobile number"); return;
        }
        if (password.isEmpty()) {
            etPassword.setError("Password required"); return;
        }
        if (!password.matches("^(?=.*[A-Z])(?=.*[a-z])(?=.*[0-9])(?=.*[@#$%^&+=]).{8,}$")) {
            etPassword.setError("Password must contain 8+ chars, uppercase, lowercase, number & special char (@#$%^&+=)");
            return;
        }

        if (role.equals("student")) {
            if (name.isEmpty()) {
                etName.setError("Name required"); return;
            }
            if (!name.matches("[a-zA-Z ]+")) {
                etName.setError("Name must contain only letters"); return;
            }
            if (roll.isEmpty()) {
                etRollNo.setError("Roll number required"); return;
            }
            if (roll.length() < 1) {
                etRollNo.setError("Enter valid roll number"); return;
            }
            if (dept.isEmpty()) {
                etDepartment.setError("Department required"); return;
            }
            if (!dept.matches("[a-zA-Z ]+")) {
                etDepartment.setError("Department must contain only letters"); return;
            }
            if (!year.equalsIgnoreCase("FY") &&
                    !year.equalsIgnoreCase("SY") &&
                    !year.equalsIgnoreCase("TY")) {
                etYear.setError("Enter only FY, SY or TY"); return;
            }
        }

        progressBar.setVisibility(View.VISIBLE);
        btnSignup.setEnabled(false);

        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {

                    String uid = authResult.getUser().getUid();

                    // Build Firestore user document
                    Map<String, Object> userMap = new HashMap<>();
                    userMap.put("email",          email);
                    userMap.put("mobile",          mobile);
                    userMap.put("role",            role);
                    userMap.put("faceRegistered",  false);

                    // Add student-specific fields
                    if (role.equals("student")) {
                        userMap.put("name",       name);
                        userMap.put("rollNo",      roll);
                        userMap.put("department",  dept);
                        userMap.put("year",        year);
                    }

                    // Save to Firestore
                    db.collection("users")
                            .document(uid)
                            .set(userMap)
                            .addOnSuccessListener(unused -> {

                                progressBar.setVisibility(View.GONE);
                                Toast.makeText(this,
                                        role.substring(0,1).toUpperCase() + role.substring(1)
                                                + " Registered Successfully!",
                                        Toast.LENGTH_SHORT).show();

                                startActivity(new Intent(this, Login_Activity2.class));
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                progressBar.setVisibility(View.GONE);
                                btnSignup.setEnabled(true);
                                Toast.makeText(this,
                                        "Firestore Error: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    btnSignup.setEnabled(true);
                    Toast.makeText(this,
                            "Registration Error: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }
}




