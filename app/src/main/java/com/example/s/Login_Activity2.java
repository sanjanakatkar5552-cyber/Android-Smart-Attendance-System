package com.example.s;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class Login_Activity2 extends AppCompatActivity {

    EditText etEmail, etPassword;
    Button btnLogin;
    TextView tvRegister;
    ProgressBar progressBar;

    FirebaseAuth auth;
    FirebaseFirestore db;

    boolean isPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login2);

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvRegister = findViewById(R.id.tvRegister);
        progressBar = findViewById(R.id.progressBar);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (auth.getCurrentUser() != null) {
            String uid = auth.getCurrentUser().getUid();

            db.collection("users")
                    .document(uid)
                    .get()
                    .addOnSuccessListener(doc -> {

                        if (!doc.exists()) return;

                        String role = doc.getString("role");

                        if ("teacher".equals(role)) {
                            startActivity(new Intent(this, TeacherDashboardActivity.class));
                        } else {
                            startActivity(new Intent(this, StudentDashboardActivity.class));
                        }

                        finish();
                    });
        }

        setupRegisterText();

        btnLogin.setOnClickListener(v -> loginUser());

        etPassword.setOnTouchListener((v, event) -> {

            if (event.getAction() == MotionEvent.ACTION_UP) {

                if (etPassword.getCompoundDrawables()[2] != null) {

                    int drawableWidth =
                            etPassword.getCompoundDrawables()[2].getBounds().width();

                    if (event.getRawX() >= (etPassword.getRight() - drawableWidth)) {

                        if (isPasswordVisible) {
                            // Hide password
                            etPassword.setInputType(
                                    InputType.TYPE_CLASS_TEXT |
                                            InputType.TYPE_TEXT_VARIATION_PASSWORD);

                            etPassword.setCompoundDrawablesWithIntrinsicBounds(
                                    0, 0, R.drawable.ic_eye_off, 0);

                            isPasswordVisible = false;

                        } else {
                            // Show password
                            etPassword.setInputType(
                                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);

                            etPassword.setCompoundDrawablesWithIntrinsicBounds(
                                    0, 0, R.drawable.ic_eye_on, 0);

                            isPasswordVisible = true;
                        }

                        etPassword.setSelection(etPassword.getText().length());
                        return true;
                    }
                }
            }
            return false;
        });
    }

    private void setupRegisterText() {
        String text = "Don't have an account? Register here";
        SpannableString ss = new SpannableString(text);

        tvRegister.setLinkTextColor(ContextCompat.getColor(this, R.color.colorPrimary));

        ClickableSpan clickableSpan = new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                startActivity(new Intent(Login_Activity2.this, RegisterActivity.class));
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                super.updateDrawState(ds);
                ds.setColor(ContextCompat.getColor(Login_Activity2.this, R.color.colorPrimary));
                ds.setUnderlineText(false);
                ds.setFakeBoldText(true);
            }
        };

        int start = text.indexOf("Register here");
        if (start != -1) {
            int end = start + "Register here".length();
            ss.setSpan(clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        tvRegister.setText(ss);
        tvRegister.setMovementMethod(LinkMovementMethod.getInstance());
        tvRegister.setHighlightColor(Color.TRANSPARENT);
    }

    private void loginUser() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty()) {
            etEmail.setError("Email required");
            return;
        }

        if (password.isEmpty()) {
            etPassword.setError("Password required");
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnLogin.setEnabled(false);

        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String uid = auth.getCurrentUser().getUid();

                    db.collection("users")
                            .document(uid)
                            .get()
                            .addOnSuccessListener(doc -> {
                                progressBar.setVisibility(View.GONE);
                                if (!doc.exists()) {
                                    btnLogin.setEnabled(true);
                                    Toast.makeText(this, "User role not found", Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                Toast.makeText(this, "Login Successful", Toast.LENGTH_SHORT).show();
                                String role = doc.getString("role");
                                if ("teacher".equals(role)) {
                                    startActivity(new Intent(this, TeacherDashboardActivity.class));
                                } else {
                                    startActivity(new Intent(this, StudentDashboardActivity.class));
                                }
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                progressBar.setVisibility(View.GONE);
                                btnLogin.setEnabled(true);
                                Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    btnLogin.setEnabled(true);
                    Toast.makeText(this, "Login Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
