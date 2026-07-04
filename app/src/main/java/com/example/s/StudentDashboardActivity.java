package com.example.s;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

public class StudentDashboardActivity extends AppCompatActivity {

    private CardView cardMarkAttendance, cardFaceScan, cardReport,
            cardProfile, cardSessionStatus, cardLeaveRequest, cardLogout;

    private TextView tvSessionMsg, tvPercentage, tvGoalStatus;

    private ProgressBar pbGoal, progressBar;

    private FirebaseFirestore db;

    private String userEmail;
    private String classId;
    private String teacherId;

    private boolean isSessionActive = false;
    private boolean faceVerified = false;

    private String activeClassId;

    private ListenerRegistration sessionListener;
    // ── NEW: keep a reference so we can remove it on destroy ──
    private ListenerRegistration attendanceListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_dashboard);

        db = FirebaseFirestore.getInstance();

        userEmail = FirebaseAuth.getInstance()
                .getCurrentUser().getEmail()
                .trim().toLowerCase();

        initViews();
        cardLeaveRequest.setEnabled(false);
        cardLeaveRequest.setAlpha(0.5f);
        loadStudentClassAndTeacher();

        listenForActiveSessions();

        // ── FIX 1: calculate attendance from the correct collection ──
        calculateAttendanceGoal();

        checkInitialStatus();

        setupClickListeners();

        SmartAttendanceFCMService.refreshAndSaveToken();
        requestNotificationPermission();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        100);
            }
        }
    }

    private void initViews() {

        progressBar = findViewById(R.id.progressBar);

        cardMarkAttendance = findViewById(R.id.cardMarkAttendance);
        cardFaceScan       = findViewById(R.id.cardFaceScan);
        cardReport         = findViewById(R.id.cardReport);
        cardProfile        = findViewById(R.id.cardProfile);
        cardSessionStatus  = findViewById(R.id.cardSessionStatus);
        cardLeaveRequest   = findViewById(R.id.cardLeaveRequest);
        cardLogout         = findViewById(R.id.cardLogout);

        tvSessionMsg  = findViewById(R.id.tvSessionMsg);
        tvPercentage  = findViewById(R.id.tvPercentage);
        tvGoalStatus  = findViewById(R.id.tvGoalStatus);

        pbGoal = findViewById(R.id.pbGoal);
    }

    // ================= STUDENT CLASS =================

    private void loadStudentClassAndTeacher() {

        db.collection("classes")
                .get()
                .addOnSuccessListener(classes -> {

                    for (DocumentSnapshot classDoc : classes.getDocuments()) {

                        String cId = classDoc.getId();

                        db.collection("classes")
                                .document(cId)
                                .collection("students")
                                .document(userEmail)
                                .get()
                                .addOnSuccessListener(studentDoc -> {

                                    if (studentDoc.exists()) {

                                        classId   = cId;
                                        teacherId = classDoc.getString("teacherId");

                                        cardLeaveRequest.setEnabled(true);
                                        cardLeaveRequest.setAlpha(1f);

                                        // FIX: classId is now available — recalculate so
                                        // the progress bar uses the real total session count
                                        // instead of a temporary placeholder.
                                        calculateAttendanceGoal();
                                    }
                                });
                    }
                });
    }

    // ================= ACTIVE SESSION LISTENER =================

    private void listenForActiveSessions() {

        if (userEmail == null) return;

        sessionListener = db.collection("classes")
                .addSnapshotListener((classes, e) -> {

                    if (classes == null) return;

                    isSessionActive = false;
                    activeClassId   = null;
                    cardSessionStatus.setVisibility(View.GONE);

                    for (DocumentSnapshot classDoc : classes.getDocuments()) {

                        String  cId    = classDoc.getId();
                        Boolean active = classDoc.getBoolean("isActive");

                        if (!Boolean.TRUE.equals(active)) continue;

                        final DocumentSnapshot finalClassDoc = classDoc;

                        db.collection("classes")
                                .document(cId)
                                .collection("students")
                                .document(userEmail)
                                .get()
                                .addOnSuccessListener(studentDoc -> {

                                    if (!studentDoc.exists()) return;

                                    isSessionActive = true;
                                    activeClassId   = cId;

                                    String subject = finalClassDoc.getString("subject");

                                    cardSessionStatus.setVisibility(View.VISIBLE);
                                    tvSessionMsg.setText("Session Active : " + subject);

                                    Timestamp startTimestamp =
                                            finalClassDoc.getTimestamp("sessionStartTime");

                                    if (startTimestamp != null) {
                                        long startTime = startTimestamp.toDate().getTime();
                                        startSessionTimer(startTime);
                                    }
                                });
                    }
                });
    }

    // ================= SESSION TIMER =================

    private void startSessionTimer(long startMillis) {

        new CountDownTimer(Long.MAX_VALUE, 1000) {

            public void onTick(long millisUntilFinished) {

                long elapsed = System.currentTimeMillis() - startMillis;
                long minutes = elapsed / 60000;
                long seconds = (elapsed % 60000) / 1000;

                tvSessionMsg.setText(
                        "Session Active\nTime : "
                                + minutes + ":" + String.format("%02d", seconds));
            }

            public void onFinish() {}
        }.start();
    }


    private void calculateAttendanceGoal() {

        if (userEmail == null) return;

        final int targetPct = 75;

        if (attendanceListener != null) {
            attendanceListener.remove();
            attendanceListener = null;
        }


        attendanceListener = db.collection("attendance_records")
                .whereEqualTo("email", userEmail)
                .addSnapshotListener((attendedSnap, error) -> {

                    if (error != null || attendedSnap == null) return;

                    int attended = attendedSnap.size(); // count of classes student was PRESENT

                    String currentClassId = classId; // may be null on very first call

                    if (currentClassId == null) {

                        updateGoalUI(attended, Math.max(attended, 1), targetPct);
                        return;
                    }

                    db.collection("attendanceSessions")
                            .whereEqualTo("classId", currentClassId)
                            .get()
                            .addOnSuccessListener(sessionsSnap -> {

                                int totalSessions = sessionsSnap.size();


                                int total = Math.max(totalSessions, attended);
                                if (total == 0) total = 1;

                                updateGoalUI(attended, total, targetPct);
                            })
                            .addOnFailureListener(e -> {
                                // Network failure — fall back gracefully
                                int total = Math.max(attended, 1);
                                updateGoalUI(attended, total, targetPct);
                            });
                });
    }

    private void updateGoalUI(int attended, int total, int targetPct) {

        int percentage = (attended * 100) / total;

        tvPercentage.setText(percentage + "%");
        pbGoal.setMax(100);
        pbGoal.setProgress(Math.min(percentage, 100));

        if (percentage >= targetPct) {

            tvGoalStatus.setText("✅ Goal Reached! Keep it up.");
            tvGoalStatus.setTextColor(
                    getResources().getColor(android.R.color.holo_green_dark));

        } else {

            int needed = (int) Math.ceil((targetPct * total / 100.0) - attended);

            tvGoalStatus.setText("⚠️ Attend " + Math.max(needed, 0)
                    + " more classes to reach " + targetPct + "%");
            tvGoalStatus.setTextColor(Color.parseColor("#D32F2F")); // red
        }
    }

    // ================= FACE STATUS =================

    private void checkInitialStatus() {

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        db.collection("users")
                .document(uid)
                .addSnapshotListener((doc, error) -> {

                    if (doc == null || !doc.exists()) return;

                    Boolean faceReg = doc.getBoolean("faceRegistered");
                    String  status  = doc.getString("faceStatus");

                    if (Boolean.TRUE.equals(faceReg) && "APPROVED".equals(status)) {
                        faceVerified = true;
                    } else {
                        if (!faceVerified) showFaceScanAlert();
                    }
                });
    }

    private void showFaceScanAlert() {

        if (isFinishing()) return;

        new AlertDialog.Builder(this)
                .setTitle("Face Scan Required")
                .setMessage("Register your face to enable attendance.")
                .setPositiveButton("Scan Now",
                        (d, w) -> startActivity(
                                new Intent(this, FaceRegisterActivity.class)))
                .setNegativeButton("Later", null)
                .show();
    }

    // ================= BUTTON CLICKS =================

    private void setupClickListeners() {

        cardMarkAttendance.setOnClickListener(v -> {

            if (!isSessionActive || activeClassId == null) {
                showSimpleAlert("No Active Session",
                        "Wait for teacher to start session.");
                return;
            }

            Intent intent = new Intent(this, AttendanceActivity.class);
            intent.putExtra("classId", activeClassId);
            startActivity(intent);
        });

        cardFaceScan.setOnClickListener(v ->
                startActivity(new Intent(this, FaceRegisterActivity.class)));

        cardReport.setOnClickListener(v -> {
            Intent intent = new Intent(this, Studentattendancereportactivity.class);
            startActivity(intent);
        });

        cardProfile.setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));

        cardLeaveRequest.setOnClickListener(v -> {

            if (teacherId == null || classId == null) {
                Toast.makeText(this,
                        "Class still loading...",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(StudentDashboardActivity.this,
                    LeaveRequestActivity.class);
            intent.putExtra("teacherId", teacherId);
            intent.putExtra("classId",   classId);
            startActivity(intent);
        });

        cardLogout.setOnClickListener(v -> showLogoutDialog());
    }

    // ================= LOGOUT =================

    private void showLogoutDialog() {

        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("OK", (dialog, which) -> {

                    FirebaseAuth.getInstance().signOut();

                    Intent intent = new Intent(this, Login_Activity2.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showSimpleAlert(String title, String msg) {

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show();
    }

    private void listenLeaveStatus() {

        String uid = FirebaseAuth.getInstance().getUid();

        FirebaseFirestore.getInstance()
                .collection("leave_requests")
                .whereEqualTo("studentId", uid)
                .addSnapshotListener((query, error) -> {

                    if (query == null) return;

                    for (DocumentSnapshot doc : query.getDocuments()) {
                        String status = doc.getString("status");
                        Toast.makeText(this,
                                "Leave Status : " + status,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();

        calculateAttendanceGoal();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sessionListener    != null) sessionListener.remove();
        if (attendanceListener != null) attendanceListener.remove();
    }
}









