package com.example.s;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.s.adapter.ClassAdapter;
import com.example.s.model.ClassModel;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import android.app.AlertDialog;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.HashMap;
import java.util.Map;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TeacherDashboardActivity extends AppCompatActivity {

    // ── Views ─────────────────────────────────────────────────────────────────
    TextView tvSessionStatus, tvSessionTimer, tvTodayPercent, tvTotalStudents;
    TextView tvLeaveBadge, tvLeaveRequestsAlert, tvDashboardAlert;
    Button btnCalendar, btnCleanRecords;
    ImageView imgProfileMenu;
    BarChart attendanceChart;
    RadioGroup chartSelector;
    RecyclerView recyclerClasses;

    // ── Data ──────────────────────────────────────────────────────────────────
    FirebaseFirestore db;
    FirebaseAuth auth;
    private ClassAdapter adapter;
    private List<ClassModel> classList;
    private Handler timerHandler = new Handler();
    private long startTime = 0L;
    private String sessionId;

    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_dashboard);

        db   = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        sessionId = getIntent().getStringExtra("sessionId");

        // ── Bind views ────────────────────────────────────────────────────
        tvSessionStatus    = findViewById(R.id.tvSessionStatus);
        tvSessionTimer     = findViewById(R.id.tvSessionTimer);
        tvTodayPercent     = findViewById(R.id.tvTodayPercent);
        tvTotalStudents    = findViewById(R.id.tvTotalStudents);
        tvLeaveBadge       = findViewById(R.id.tvLeaveBadge);
        tvLeaveRequestsAlert = findViewById(R.id.tvLeaveRequestsAlert);
        tvDashboardAlert   = findViewById(R.id.tvDashboardAlert);
        btnCalendar        = findViewById(R.id.btnCalendar);
        btnCleanRecords    = findViewById(R.id.btnCleanRecords);
        imgProfileMenu     = findViewById(R.id.imgProfileMenu);
        recyclerClasses    = findViewById(R.id.recyclerClasses);
        attendanceChart    = findViewById(R.id.attendanceChart);
        chartSelector      = findViewById(R.id.chartSelector);

        // ── RecyclerView ──────────────────────────────────────────────────
        classList = new ArrayList<>();
        adapter   = new ClassAdapter(this, classList);
        recyclerClasses.setLayoutManager(new LinearLayoutManager(this));
        recyclerClasses.setAdapter(adapter);

        // ── Cleanup ───────────────────────────────────────────────────────
        btnCleanRecords.setOnClickListener(v ->
                AttendanceCleanupHelper.showCleanupDialog(this));

        // ── Chart radio selector ──────────────────────────────────────────
        setupChart();
        chartSelector.setOnCheckedChangeListener((group, id) -> {
            if      (id == R.id.rbDaily)   loadDailyAttendance();
            else if (id == R.id.rbWeekly)  loadWeeklyAttendance();
            else if (id == R.id.rbMonthly) loadMonthlyAttendance();
        });


        btnCalendar.setOnClickListener(v -> openReportWithClassSelection());

        // ── Create new class ──────────────────────────────────────────────
        Button btnCreateNewClass = findViewById(R.id.btnCreateNewClass);
        btnCreateNewClass.setOnClickListener(v ->
                startActivity(new Intent(this, CreateClassActivity.class)));

        // ── Profile / logout popup ────────────────────────────────────────
        imgProfileMenu.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(this, imgProfileMenu);
            menu.getMenu().add("Profile");
            menu.getMenu().add("Logout");
            menu.setOnMenuItemClickListener(item -> {
                if ("Profile".equals(item.getTitle())) {
                    startActivity(new Intent(this, ProfileActivity.class));
                } else if ("Logout".equals(item.getTitle())) {
                    FirebaseAuth.getInstance().signOut();
                    Intent intent = new Intent(this, Login_Activity2.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                }
                return true;
            });
            menu.show();
        });


        View btnSessionCard = findViewById(R.id.cardStartAttendance);
        if (btnSessionCard != null) {
            btnSessionCard.setOnClickListener(v -> showClassSessionDialog());
        }

        loadDashboardData();
        listenLiveSession();
        loadPendingFaceRequests();
        loadLeaveRequests();
        loadLeaveBadge();
        loadDailyAttendance();   // default chart view
        startAutoChartUpdates();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadMyClasses();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timerHandler.removeCallbacksAndMessages(null);
    }


    private void openReportWithClassSelection() {
        String teacherId = auth.getUid();
        if (teacherId == null) return;

        db.collection("classes")
                .whereEqualTo("teacherId", teacherId)
                .get()
                .addOnSuccessListener(query -> {
                    if (isFinishing() || isDestroyed()) return;

                    if (query.isEmpty()) {
                        Toast.makeText(this, "No classes found", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    List<String> names = new ArrayList<>();
                    List<String> ids   = new ArrayList<>();
                    for (DocumentSnapshot doc : query) {
                        String subj = doc.getString("subject");
                        String yr   = doc.getString("year");
                        names.add((subj != null ? subj : "?") + " – " + (yr != null ? yr : ""));
                        ids.add(doc.getId());
                    }
                    new android.app.AlertDialog.Builder(this)
                            .setTitle("Select Class for Report")
                            .setItems(names.toArray(new String[0]), (d, which) -> {
                                Intent intent = new Intent(this, AttendanceReportActivity.class);
                                intent.putExtra("classId", ids.get(which));
                                startActivity(intent);
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CHART SETUP
    // ══════════════════════════════════════════════════════════════════════════
    private void setupChart() {
        attendanceChart.getDescription().setEnabled(false);
        attendanceChart.setDrawGridBackground(false);
        attendanceChart.getAxisRight().setEnabled(false);
        attendanceChart.getAxisLeft().setAxisMinimum(0);
        attendanceChart.animateY(1200);

        XAxis xAxis = attendanceChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setDrawGridLines(false);
    }


    private java.util.Date parseAnyDate(String date) {
        if (date == null || date.isEmpty()) return null;
        for (String fmt : new String[]{"yyyy-MM-dd", "dd/MM/yyyy", "yyyy-M-d", "d/M/yyyy"}) {
            try {
                java.util.Date d = new SimpleDateFormat(fmt, Locale.getDefault()).parse(date);
                if (d != null) return d;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private boolean isCalendarToday(Calendar cal) {
        Calendar today = Calendar.getInstance();
        return cal.get(Calendar.YEAR)         == today.get(Calendar.YEAR)
                && cal.get(Calendar.DAY_OF_YEAR)  == today.get(Calendar.DAY_OF_YEAR);
    }

    private boolean isCalendarThisWeek(Calendar cal) {
        Calendar today = Calendar.getInstance();
        return cal.get(Calendar.YEAR)         == today.get(Calendar.YEAR)
                && cal.get(Calendar.WEEK_OF_YEAR) == today.get(Calendar.WEEK_OF_YEAR);
    }

    private boolean isCalendarThisMonth(Calendar cal) {
        Calendar today = Calendar.getInstance();
        return cal.get(Calendar.YEAR)  == today.get(Calendar.YEAR)
                && cal.get(Calendar.MONTH) == today.get(Calendar.MONTH);
    }


    private void loadDailyAttendance() {
        String teacherId = auth.getUid();
        if (teacherId == null) return;

        db.collection("classes")
                .whereEqualTo("teacherId", teacherId)
                .get()
                .addOnSuccessListener(classes -> {
                    if (classes.isEmpty()) {
                        updateDailyChart(0); tvTodayPercent.setText("0%"); return;
                    }
                    List<String> classIds = new ArrayList<>();
                    for (DocumentSnapshot doc : classes) classIds.add(doc.getId());

                    // Query attendanceSessions — has classId + startTime Timestamp
                    db.collection("attendanceSessions")
                            .whereIn("classId", classIds.subList(0, Math.min(10, classIds.size())))
                            .get()
                            .addOnSuccessListener(sessions -> {
                                if (sessions.isEmpty()) {
                                    updateDailyChart(0); tvTodayPercent.setText("0%");
                                    hideDashboardAlert(); return;
                                }

                                // Filter to sessions that started today
                                List<QueryDocumentSnapshot> todaySessions = new ArrayList<>();
                                for (QueryDocumentSnapshot s : sessions) {
                                    com.google.firebase.Timestamp ts = s.getTimestamp("startTime");
                                    if (ts != null) {
                                        Calendar cal = Calendar.getInstance();
                                        cal.setTime(ts.toDate());
                                        if (isCalendarToday(cal)) todaySessions.add(s);
                                    }
                                }

                                if (todaySessions.isEmpty()) {
                                    updateDailyChart(0); tvTodayPercent.setText("0%");
                                    hideDashboardAlert(); return;
                                }

                                final int[] present  = {0};
                                final int[] total    = {0};
                                final int[] remaining = {todaySessions.size()};

                                for (QueryDocumentSnapshot session : todaySessions) {
                                    String sid = session.getString("sessionId");
                                    if (sid == null || sid.isEmpty()) sid = session.getId();
                                    final String finalSid = sid;

                                    db.collection("attendance").document(finalSid)
                                            .collection("students").get()
                                            .addOnSuccessListener(students -> {
                                                for (QueryDocumentSnapshot stu : students) {
                                                    total[0]++;
                                                    Boolean p = stu.getBoolean("present");
                                                    if (p != null && p) present[0]++;
                                                }
                                                remaining[0]--;
                                                if (remaining[0] == 0) {
                                                    float pct = total[0] == 0 ? 0
                                                            : (present[0] * 100f / total[0]);
                                                    updateDailyChart(pct);
                                                    tvTodayPercent.setText((int) pct + "%");
                                                    if (pct < 75 && total[0] > 0)
                                                        showDashboardAlert("⚠ Attendance below 75%");
                                                    else hideDashboardAlert();
                                                }
                                            })
                                            .addOnFailureListener(e -> {
                                                remaining[0]--;
                                                if (remaining[0] == 0) updateDailyChart(0);
                                            });
                                }
                            })
                            .addOnFailureListener(e -> updateDailyChart(0));
                });
    }
    private void updateDailyChart(float percent) {
        ArrayList<BarEntry> entries = new ArrayList<>();
        entries.add(new BarEntry(0, percent));

        BarDataSet set = new BarDataSet(entries, "Today Attendance");
        set.setColor(0xFF4CAF50);
        set.setValueTextSize(14f);

        XAxis xAxis = attendanceChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(new String[]{"Present"}));

        BarData data = new BarData(set);
        attendanceChart.setData(data);
        attendanceChart.animateY(800);
        attendanceChart.invalidate();
    }


    private void loadWeeklyAttendance() {
        String teacherId = auth.getUid();
        if (teacherId == null) return;

        final int[] days = new int[7]; // 0=Sun … 6=Sat

        db.collection("classes")
                .whereEqualTo("teacherId", teacherId)
                .get()
                .addOnSuccessListener(classes -> {
                    if (classes.isEmpty()) { updateWeeklyChart(days); return; }

                    List<String> classIds = new ArrayList<>();
                    for (DocumentSnapshot doc : classes) classIds.add(doc.getId());

                    db.collection("attendanceSessions")
                            .whereIn("classId", classIds.subList(0, Math.min(10, classIds.size())))
                            .get()
                            .addOnSuccessListener(sessions -> {
                                if (sessions.isEmpty()) { updateWeeklyChart(days); return; }

                                // Filter to sessions this week
                                List<QueryDocumentSnapshot> weekSessions = new ArrayList<>();
                                for (QueryDocumentSnapshot s : sessions) {
                                    com.google.firebase.Timestamp ts = s.getTimestamp("startTime");
                                    if (ts != null) {
                                        Calendar cal = Calendar.getInstance();
                                        cal.setTime(ts.toDate());
                                        if (isCalendarThisWeek(cal)) weekSessions.add(s);
                                    }
                                }
                                if (weekSessions.isEmpty()) { updateWeeklyChart(days); return; }

                                final int[] remaining = {weekSessions.size()};
                                for (QueryDocumentSnapshot session : weekSessions) {
                                    com.google.firebase.Timestamp ts = session.getTimestamp("startTime");
                                    final Calendar sessionCal = Calendar.getInstance();
                                    if (ts != null) sessionCal.setTime(ts.toDate());

                                    String sid = session.getString("sessionId");
                                    if (sid == null || sid.isEmpty()) sid = session.getId();
                                    final String finalSid = sid;

                                    db.collection("attendance").document(finalSid)
                                            .collection("students").get()
                                            .addOnSuccessListener(students -> {
                                                for (QueryDocumentSnapshot stu : students) {
                                                    Boolean present = stu.getBoolean("present");
                                                    if (present == null || !present) continue;
                                                    // Use session startTime day-of-week
                                                    int dow = sessionCal.get(Calendar.DAY_OF_WEEK) - 1;
                                                    days[dow]++;
                                                }
                                                remaining[0]--;
                                                if (remaining[0] == 0) updateWeeklyChart(days);
                                            })
                                            .addOnFailureListener(e -> {
                                                remaining[0]--;
                                                if (remaining[0] == 0) updateWeeklyChart(days);
                                            });
                                }
                            })
                            .addOnFailureListener(e -> updateWeeklyChart(days));
                });
    }
    private void updateWeeklyChart(int[] days) {
        ArrayList<BarEntry> entries = new ArrayList<>();
        for (int i = 0; i < 7; i++) entries.add(new BarEntry(i, days[i]));

        BarDataSet set = new BarDataSet(entries, "Weekly Attendance");
        set.setColors(0xFF4CAF50, 0xFF2196F3, 0xFFFF9800,
                0xFF9C27B0, 0xFFF44336, 0xFF009688, 0xFF3F51B5);
        set.setValueTextSize(12f);

        attendanceChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(
                new String[]{"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"}));

        BarData data = new BarData(set);
        attendanceChart.setData(data);
        attendanceChart.animateY(800);
        attendanceChart.invalidate();
    }


    private void loadMonthlyAttendance() {
        String teacherId = auth.getUid();
        if (teacherId == null) return;

        final int[] weeks = new int[4];

        db.collection("classes")
                .whereEqualTo("teacherId", teacherId)
                .get()
                .addOnSuccessListener(classes -> {
                    if (classes.isEmpty()) { updateMonthlyChart(weeks); return; }

                    List<String> classIds = new ArrayList<>();
                    for (DocumentSnapshot doc : classes) classIds.add(doc.getId());

                    db.collection("attendanceSessions")
                            .whereIn("classId", classIds.subList(0, Math.min(10, classIds.size())))
                            .get()
                            .addOnSuccessListener(sessions -> {
                                if (sessions.isEmpty()) { updateMonthlyChart(weeks); return; }

                                // Filter to sessions this month
                                List<QueryDocumentSnapshot> monthSessions = new ArrayList<>();
                                for (QueryDocumentSnapshot s : sessions) {
                                    com.google.firebase.Timestamp ts = s.getTimestamp("startTime");
                                    if (ts != null) {
                                        Calendar cal = Calendar.getInstance();
                                        cal.setTime(ts.toDate());
                                        if (isCalendarThisMonth(cal)) monthSessions.add(s);
                                    }
                                }
                                if (monthSessions.isEmpty()) { updateMonthlyChart(weeks); return; }

                                final int[] remaining = {monthSessions.size()};
                                for (QueryDocumentSnapshot session : monthSessions) {
                                    com.google.firebase.Timestamp ts = session.getTimestamp("startTime");
                                    final Calendar sessionCal = Calendar.getInstance();
                                    if (ts != null) sessionCal.setTime(ts.toDate());

                                    String sid = session.getString("sessionId");
                                    if (sid == null || sid.isEmpty()) sid = session.getId();
                                    final String finalSid = sid;

                                    db.collection("attendance").document(finalSid)
                                            .collection("students").get()
                                            .addOnSuccessListener(students -> {
                                                for (QueryDocumentSnapshot stu : students) {
                                                    Boolean present = stu.getBoolean("present");
                                                    if (present == null || !present) continue;
                                                    int wk = sessionCal.get(Calendar.WEEK_OF_MONTH);
                                                    weeks[Math.min(wk - 1, 3)]++;
                                                }
                                                remaining[0]--;
                                                if (remaining[0] == 0) updateMonthlyChart(weeks);
                                            })
                                            .addOnFailureListener(e -> {
                                                remaining[0]--;
                                                if (remaining[0] == 0) updateMonthlyChart(weeks);
                                            });
                                }
                            })
                            .addOnFailureListener(e -> updateMonthlyChart(weeks));
                });
    }
    private void updateMonthlyChart(int[] weeks) {
        ArrayList<BarEntry> entries = new ArrayList<>();
        for (int i = 0; i < 4; i++) entries.add(new BarEntry(i, weeks[i]));

        BarDataSet set = new BarDataSet(entries, "Monthly Attendance");
        set.setColor(0xFF1E5AA8);
        set.setValueTextSize(12f);

        attendanceChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(
                new String[]{"Week 1", "Week 2", "Week 3", "Week 4"}));

        BarData data = new BarData(set);
        attendanceChart.setData(data);
        attendanceChart.animateY(1200);
        attendanceChart.invalidate();
    }


    private void startAutoChartUpdates() {
        Handler chartHandler = new Handler();
        Runnable chartRunnable = new Runnable() {
            @Override
            public void run() {
                int id = chartSelector.getCheckedRadioButtonId();
                if      (id == R.id.rbDaily)   loadDailyAttendance();
                else if (id == R.id.rbWeekly)  loadWeeklyAttendance();
                else if (id == R.id.rbMonthly) loadMonthlyAttendance();
                chartHandler.postDelayed(this, 30_000);
            }
        };
        chartHandler.post(chartRunnable);
    }


    private void loadDashboardData() {
        String teacherId = auth.getUid();
        if (teacherId == null) return;

        // Total students across all classes
        tvTotalStudents.setText("0");
        db.collection("classes")
                .whereEqualTo("teacherId", teacherId)
                .get()
                .addOnSuccessListener(classQuery -> {
                    tvTotalStudents.setText("0");
                    for (DocumentSnapshot doc : classQuery) {
                        db.collection("classes").document(doc.getId())
                                .collection("students").get()
                                .addOnSuccessListener(studentQuery -> {
                                    int cur = 0;
                                    try { cur = Integer.parseInt(
                                            tvTotalStudents.getText().toString()); }
                                    catch (Exception ignored) {}
                                    tvTotalStudents.setText(String.valueOf(cur + studentQuery.size()));
                                });
                    }
                });

        // Face mismatch alert
        db.collection("face_logs")
                .whereEqualTo("teacherId", teacherId)
                .whereEqualTo("status", "mismatch")
                .addSnapshotListener((snapshot, error) -> {
                    if (snapshot == null || snapshot.isEmpty()) return;
                    showDashboardAlert("🧠 " + snapshot.size() + " Face Mismatch Attempts");
                });
    }


    private void listenLiveSession() {
        String teacherId = auth.getUid();
        if (teacherId == null) return;

        db.collection("classes")
                .whereEqualTo("teacherId", teacherId)
                .whereEqualTo("isActive", true)
                .addSnapshotListener((snapshot, error) -> {
                    if (snapshot == null || snapshot.isEmpty()) {
                        tvSessionStatus.setText("No Live Session");
                        tvSessionTimer.setText("00:00:00");
                        timerHandler.removeCallbacksAndMessages(null);
                        return;
                    }
                    DocumentSnapshot doc = snapshot.getDocuments().get(0);
                    String subject = doc.getString("subject");
                    String year    = doc.getString("year");
                    tvSessionStatus.setText("LIVE SESSION ● " + subject + " - " + year);
                    showDashboardAlert("🟢 Live session currently running");
                    startSessionTimer();
                });
    }


    private void loadLeaveRequests() {
        String teacherId = auth.getUid();
        if (teacherId == null) return;

        db.collection("leave_requests")
                .whereEqualTo("teacherId", teacherId)
                .whereEqualTo("status", "pending")
                .addSnapshotListener((query, error) -> {
                    if (query == null) return;
                    int count = query.size();
                    tvLeaveRequestsAlert.setText("📩 " + count + " Leave Requests Pending");
                    tvLeaveRequestsAlert.setOnClickListener(v ->
                            startActivity(new Intent(this,
                                    TeacherLeaveRequestsActivity.class)));
                });
    }

    private void loadLeaveBadge() {
        String teacherId = auth.getUid();
        if (teacherId == null) return;

        db.collection("leave_requests")
                .whereEqualTo("teacherId", teacherId)
                .whereEqualTo("status", "pending")
                .addSnapshotListener((query, error) -> {
                    if (query == null) return;
                    tvLeaveBadge.setText(String.valueOf(query.size()));
                });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PENDING FACE REQUESTS
    // ══════════════════════════════════════════════════════════════════════════
    private void loadPendingFaceRequests() {
        String teacherId = auth.getUid();
        if (teacherId == null) return;

        db.collection("users")
                .whereEqualTo("teacherId", teacherId)
                .whereEqualTo("updateRequest", true)
                .get()
                .addOnSuccessListener(query -> {
                    int count = query.size();
                    if (count > 0) {
                        showDashboardAlert("🔔 " + count + " Face Update Requests Pending");
                    }
                });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MY CLASSES LIST
    // ══════════════════════════════════════════════════════════════════════════
    private void loadMyClasses() {
        String teacherId = FirebaseAuth.getInstance().getUid();
        if (teacherId == null) return;

        db.collection("classes")
                .whereEqualTo("teacherId", teacherId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    classList.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        ClassModel model = doc.toObject(ClassModel.class);
                        if (model != null) {
                            model.setId(doc.getId());
                            classList.add(model);
                        }
                    }
                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load classes",
                                Toast.LENGTH_SHORT).show());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SESSION TIMER
    // ══════════════════════════════════════════════════════════════════════════
    private void startSessionTimer() {
        startTime = SystemClock.uptimeMillis();
        timerHandler.removeCallbacksAndMessages(null);
        timerHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                long ms      = SystemClock.uptimeMillis() - startTime;
                int seconds  = (int) (ms / 1000);
                int minutes  = seconds / 60;
                int hours    = minutes / 60;
                seconds %= 60;
                minutes %= 60;
                tvSessionTimer.setText(
                        String.format(Locale.getDefault(),
                                "%02d:%02d:%02d", hours, minutes, seconds));
                timerHandler.postDelayed(this, 1000);
            }
        }, 0);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ALERT HELPERS
    // ══════════════════════════════════════════════════════════════════════════
    private void showDashboardAlert(String message) {
        if (tvDashboardAlert == null) return;
        tvDashboardAlert.setText(message);
        tvDashboardAlert.setVisibility(View.VISIBLE);
    }

    private void hideDashboardAlert() {
        if (tvDashboardAlert == null) return;
        tvDashboardAlert.setVisibility(View.GONE);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SESSION START / STOP
    // ══════════════════════════════════════════════════════════════════════════

    private void showClassSessionDialog() {
        if (classList.isEmpty()) {
            Toast.makeText(this, "No classes available", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] displayList = new String[classList.size()];
        for (int i = 0; i < classList.size(); i++) {
            ClassModel model = classList.get(i);
            String status = model.getIsActive() ? "[ACTIVE]" : "[INACTIVE]";
            displayList[i] = status + " " + model.getSubject();
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("Start / Stop Attendance Session")
                .setItems(displayList, (dialog, which) -> {
                    ClassModel selected = classList.get(which);
                    toggleSession(selected.getId(), !selected.getIsActive());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }


    private void toggleSession(String classId, boolean start) {

        if (start) {
            String newSessionId =
                    db.collection("attendanceSessions").document().getId();

            Map<String, Object> classUpdate = new HashMap<>();
            classUpdate.put("isActive",         true);
            classUpdate.put("activeSessionId",  newSessionId);
            classUpdate.put("sessionStartTime", FieldValue.serverTimestamp());

            db.collection("classes").document(classId)
                    .update(classUpdate)
                    .addOnSuccessListener(aVoid ->
                            db.collection("classes").document(classId).get()
                                    .addOnSuccessListener(classDoc -> {

                                        Double lat    = classDoc.getDouble("centerLat");
                                        Double lng    = classDoc.getDouble("centerLng");
                                        Double radius = classDoc.getDouble("radius");

                                        String sessionDateStr = new java.text.SimpleDateFormat(
                                                "yyyy-MM-dd", java.util.Locale.getDefault())
                                                .format(new java.util.Date());

                                        Map<String, Object> sessionData = new HashMap<>();
                                        sessionData.put("classId",   classId);
                                        sessionData.put("sessionId", newSessionId);  // FIX: store sessionId as field
                                        sessionData.put("date",      sessionDateStr); // FIX: store date as string field
                                        sessionData.put("centerLat", lat    != null ? lat    : 0.0);
                                        sessionData.put("centerLng", lng    != null ? lng    : 0.0);
                                        sessionData.put("radius",    radius != null ? radius : 100.0);
                                        sessionData.put("startTime", FieldValue.serverTimestamp());

                                        db.collection("attendanceSessions")
                                                .document(newSessionId)
                                                .set(sessionData)
                                                .addOnSuccessListener(unused -> {
                                                    Toast.makeText(this,
                                                            "Attendance Session Started ✅",
                                                            Toast.LENGTH_SHORT).show();
                                                    loadMyClasses();
                                                })
                                                .addOnFailureListener(e ->
                                                        Toast.makeText(this,
                                                                "Session doc error: " + e.getMessage(),
                                                                Toast.LENGTH_SHORT).show());
                                    }))
                    .addOnFailureListener(e ->
                            Toast.makeText(this,
                                    "Failed to start session: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show());
        } else {
            // ── STOP SESSION ─────────────────────────────────────────────────
            // 1. Get the current activeSessionId from the class doc
            db.collection("classes").document(classId).get()
                    .addOnSuccessListener(classDoc -> {
                        if (isFinishing() || isDestroyed()) return;

                        String activeSessionId = classDoc.getString("activeSessionId");
                        String date = new java.text.SimpleDateFormat("yyyy-MM-dd",
                                java.util.Locale.getDefault()).format(new java.util.Date());
                        String subject = classDoc.getString("subject");
                        if (subject == null) subject = "";
                        final String finalSubject = subject;

                        // 2. Stop the class session
                        Map<String, Object> stopUpdate = new HashMap<>();
                        stopUpdate.put("isActive",        false);
                        stopUpdate.put("activeSessionId", null);
                        stopUpdate.put("sessionEndTime",  FieldValue.serverTimestamp());

                        db.collection("classes").document(classId)
                                .update(stopUpdate)
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(this,
                                            "Attendance Session Stopped",
                                            Toast.LENGTH_SHORT).show();
                                    loadMyClasses();
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(this,
                                                "Failed to stop session: " + e.getMessage(),
                                                Toast.LENGTH_SHORT).show());

                        // 3. Mark absent for enrolled students who didn't mark attendance
                        if (activeSessionId == null || activeSessionId.isEmpty()) return;
                        final String finalSessionId = activeSessionId;

                        // Fetch all enrolled students in this class
                        db.collection("classes").document(classId)
                                .collection("students")
                                .get()
                                .addOnSuccessListener(enrolledSnap -> {
                                    if (enrolledSnap.isEmpty()) return;

                                    // Fetch who already marked attendance in this session
                                    db.collection("attendance")
                                            .document(finalSessionId)
                                            .collection("students")
                                            .get()
                                            .addOnSuccessListener(attendedSnap -> {
                                                // Build set of studentIds who attended
                                                java.util.Set<String> attendedIds = new java.util.HashSet<>();
                                                for (com.google.firebase.firestore.DocumentSnapshot a : attendedSnap.getDocuments()) {
                                                    attendedIds.add(a.getId());
                                                }

                                                // Write absent record for each enrolled student who didn't attend
                                                for (com.google.firebase.firestore.DocumentSnapshot enrolled : enrolledSnap.getDocuments()) {
                                                    String studentId = enrolled.getId();
                                                    // studentId here is email (used as doc key in students subcollection)
                                                    // Also check by uid stored in the doc
                                                    String uid = enrolled.getString("uid");
                                                    if (uid == null) uid = studentId;

                                                    boolean alreadyAttended = attendedIds.contains(studentId)
                                                            || attendedIds.contains(uid);
                                                    if (alreadyAttended) continue;

                                                    String name = enrolled.getString("name");
                                                    if (name == null) name = "Unknown";
                                                    String rollNo = "";
                                                    Object r = enrolled.get("rollNo");
                                                    if (r != null) rollNo = String.valueOf(r);

                                                    Map<String, Object> absentData = new HashMap<>();
                                                    absentData.put("name",      name);
                                                    absentData.put("email",     studentId);
                                                    absentData.put("studentId", uid);
                                                    absentData.put("classId",   classId);
                                                    absentData.put("subject",   finalSubject);
                                                    absentData.put("sessionId", finalSessionId);
                                                    absentData.put("rollNo",    rollNo);
                                                    absentData.put("status",    "absent");
                                                    absentData.put("present",   false);
                                                    absentData.put("date",      date);
                                                    absentData.put("time",      "");
                                                    absentData.put("distance",  0.0);
                                                    absentData.put("timestamp", FieldValue.serverTimestamp());


                                                    String docId = (uid != null && !uid.equals(studentId))
                                                            ? uid : studentId;
                                                    db.collection("attendance")
                                                            .document(finalSessionId)
                                                            .collection("students")
                                                            .document(docId)
                                                            .set(absentData);
                                                }
                                            });
                                });
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this,
                                    "Failed to stop session: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show());
        }
    }
}

