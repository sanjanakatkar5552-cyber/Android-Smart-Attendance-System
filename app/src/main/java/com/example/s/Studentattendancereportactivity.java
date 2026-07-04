package com.example.s;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.s.adapter.Studentrecordadapter;
import com.example.s.model.AttendanceRecordModel;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.chip.Chip;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public class Studentattendancereportactivity extends AppCompatActivity {

    private static final String TAG         = "StudentReport";
    private static final String FMT_SAVE    = "yyyy-MM-dd";
    private static final String FMT_DISPLAY = "dd/MM/yyyy";

    private FirebaseFirestore db;
    private String studentUID;   // FirebaseAuth.getUid() = Firestore doc ID in /students/
    private String userEmail;
    private String studentClassId = "";
    private String studentSubject = "";


    private ListenerRegistration sessionsListener;
    private final Map<String, ListenerRegistration> perSessionListeners = new ConcurrentHashMap<>();

    private final Map<String, AttendanceRecordModel> recordsBySession = new ConcurrentHashMap<>();

    private final List<AttendanceRecordModel> fullList     = new ArrayList<>();
    private final List<AttendanceRecordModel> filteredList = new ArrayList<>();

    // ── UI ────────────────────────────────────────────────────────────────────
    private RecyclerView         recyclerView;
    private Studentrecordadapter adapter;
    private Spinner              spinnerSubject;
    private BarChart             barChart;
    private LineChart            lineChart;
    private TextView             tvOverallPercent, tvOverallLabel,
            tvPresentCount, tvAbsentCount, tvTotalCount,
            tvRecordCount, tvSelectedDateLabel,
            tvAlertBanner, tvSubjectBreakdown;
    private View                 cardAlertBanner, overallRing;
    private EditText             searchStudent;
    private Button               btnSelectDate, btnClearDate;
    private Chip                 chipAll, chipToday, chipWeek, chipMonth;

    // ── Filter state ──────────────────────────────────────────────────────────
    private String selectedSubject = "All";
    private String selectedDate    = "";
    private String chipMode        = "ALL";
    private String searchQuery     = "";

    private final List<String>         subjects       = new ArrayList<>();
    private       ArrayAdapter<String> subjectAdapter = null;

    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_studentattendancereportactivity);

        db         = FirebaseFirestore.getInstance();
        studentUID = FirebaseAuth.getInstance().getUid();
        if (FirebaseAuth.getInstance().getCurrentUser() != null)
            userEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();

        if (studentUID == null) { finish(); return; }
        Log.d(TAG, "UID=" + studentUID + " email=" + userEmail);

        bindViews();
        setupRecyclerView();
        setupSpinner();
        setupSearch();
        setupButtons();
        setupChips();
        setupBarChart();
        setupLineChart();

        tvAlertBanner.setText("Loading your attendance...");
        cardAlertBanner.setVisibility(View.VISIBLE);

        findClassIdThenListen();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sessionsListener != null) sessionsListener.remove();
        for (ListenerRegistration r : perSessionListeners.values()) r.remove();
        perSessionListeners.clear();
    }


    private void findClassIdThenListen() {
        if (empty(userEmail)) {
            showError("Cannot load records: user email not found.");
            return;
        }

        db.collection("classes").get()
                .addOnSuccessListener(classes -> {
                    if (classes.isEmpty()) { showError("No classes found."); return; }

                    final int[] remaining = {classes.size()};

                    for (DocumentSnapshot classDoc : classes.getDocuments()) {
                        String cId = classDoc.getId();

                        classDoc.getReference()
                                .collection("students")
                                .document(userEmail)
                                .get()
                                .addOnSuccessListener(studentDoc -> {
                                    if (studentDoc.exists() && studentClassId.isEmpty()) {
                                        studentClassId = cId;
                                        String sub = classDoc.getString("subject");
                                        if (!empty(sub)) studentSubject = sub;
                                        Log.d(TAG, "classId=" + cId + " subject=" + studentSubject);
                                        startSessionsListener(); // ← only called once
                                    }
                                    remaining[0]--;
                                    if (remaining[0] <= 0 && studentClassId.isEmpty())
                                        showError("You are not enrolled in any class yet.");
                                })
                                .addOnFailureListener(e -> {
                                    remaining[0]--;
                                    if (remaining[0] <= 0 && studentClassId.isEmpty())
                                        showError("Could not load class data: " + e.getMessage());
                                });
                    }
                })
                .addOnFailureListener(e -> showError("Network error: " + e.getMessage()));
    }


    private void startSessionsListener() {
        Log.d(TAG, "Querying attendanceSessions WHERE classId=" + studentClassId);

        sessionsListener = db.collection("attendanceSessions")
                .whereEqualTo("classId", studentClassId)
                .addSnapshotListener((sessions, err) -> {
                    if (err != null) {
                        Log.e(TAG, "Sessions error: " + err.getMessage());
                        showError("Could not load sessions: " + err.getMessage());
                        return;
                    }
                    if (sessions == null || sessions.isEmpty()) {
                        Log.d(TAG, "No sessions for classId=" + studentClassId);
                        showError("No attendance sessions found for your class yet.");
                        return;
                    }

                    Log.d(TAG, sessions.size() + " sessions found");

                    // Collect current session IDs
                    Set<String> activeIds = new LinkedHashSet<>();
                    for (QueryDocumentSnapshot s : sessions) {
                        String sid = s.getString("sessionId");
                        if (empty(sid)) sid = s.getId();
                        activeIds.add(sid);
                    }

                    // Remove listeners for sessions that no longer exist
                    List<String> toRemove = new ArrayList<>();
                    for (String sid : perSessionListeners.keySet())
                        if (!activeIds.contains(sid)) toRemove.add(sid);
                    for (String sid : toRemove) {
                        perSessionListeners.get(sid).remove();
                        perSessionListeners.remove(sid);
                        recordsBySession.remove(sid);
                    }
                    if (!toRemove.isEmpty()) rebuildAndFilter();

                    // Add listener for each session
                    for (QueryDocumentSnapshot session : sessions) {
                        String sid = session.getString("sessionId");
                        if (empty(sid)) sid = session.getId();
                        if (perSessionListeners.containsKey(sid)) continue;

                        final String finalSid  = sid;
                        final String sessionDate = extractDateFromSession(session);

                        startStudentDocListener(finalSid, sessionDate);
                    }
                });
    }


    private void startStudentDocListener(String sessionId, String sessionDate) {
        Log.d(TAG, "Listening attendance/" + sessionId + "/students/" + studentUID);

        ListenerRegistration lr = db.collection("attendance")
                .document(sessionId)
                .collection("students")
                .document(studentUID) // doc ID = student's Firebase UID
                .addSnapshotListener((doc, err) -> {
                    if (err != null) {
                        Log.e(TAG, "Student doc error [" + sessionId + "]: " + err.getMessage());
                        return;
                    }
                    if (doc != null && doc.exists()) {
                        Log.d(TAG, "✅ Present record found: session=" + sessionId);
                        recordsBySession.put(sessionId, parseRecord(doc, sessionDate));
                    } else {

                        Log.d(TAG, "❌ No record → marking ABSENT for session=" + sessionId);
                        AttendanceRecordModel absentRecord = new AttendanceRecordModel(
                                "Me",           // name
                                "—",            // rollNo
                                empty(studentSubject) ? "Class" : studentSubject, // subject
                                false,          // present = false (ABSENT)
                                sessionDate,    // FIX Bug4b: keep sessionDate (may be ""), never todayKey()
                                "",             // time — unknown since absent
                                null,           // lat
                                null,           // lng
                                null            // distance
                        );
                        recordsBySession.put(sessionId, absentRecord);
                    }
                    rebuildAndFilter();
                });

        perSessionListeners.put(sessionId, lr);
    }


    private String extractDateFromSession(QueryDocumentSnapshot session) {
        String date = session.getString("date");
        if (!empty(date)) return date;
        // Fall back to startTime timestamp
        com.google.firebase.Timestamp ts = session.getTimestamp("startTime");
        if (ts != null)
            return new SimpleDateFormat(FMT_SAVE, Locale.getDefault()).format(ts.toDate());

        return "";
    }

    private AttendanceRecordModel parseRecord(DocumentSnapshot doc, String sessionDate) {
        String name = doc.getString("name");
        if (empty(name)) name = doc.getString("email");
        if (empty(name)) name = "Me";

        String roll = doc.getString("rollNo");
        if (empty(roll)) roll = "—";

        String subject = doc.getString("subject");
        if (empty(subject)) subject = doc.getString("className");
        if (empty(subject)) subject = studentSubject;
        if (empty(subject)) subject = "Class";


        String date = doc.getString("date");
        if (empty(date)) date = sessionDate;

        // Time
        String time = doc.getString("time");
        if (empty(time)) {
            com.google.firebase.Timestamp ts = doc.getTimestamp("timestamp");
            if (ts != null)
                time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(ts.toDate());
        }

        // Present flag
        boolean present;
        Boolean bp = doc.getBoolean("present");
        if (bp != null) present = bp;
        else present = "present".equalsIgnoreCase(doc.getString("status"));

        Double lat  = doc.getDouble("lat");
        Double lng  = doc.getDouble("lng");
        Double dist = doc.getDouble("distance");

        return new AttendanceRecordModel(name, roll, subject, present, date, time, lat, lng, dist);
    }

    private void rebuildAndFilter() {
        fullList.clear();
        fullList.addAll(recordsBySession.values());

        // Sort newest first
        fullList.sort((a, b) -> safe(b.getDate()).compareTo(safe(a.getDate())));

        Log.d(TAG, "Total records: " + fullList.size());

        rebuildSpinner();
        updateOverallStats();
        applyFilters();
    }

    private void rebuildSpinner() {
        String prev = subjects.isEmpty() ? "All"
                : subjects.get(Math.max(spinnerSubject.getSelectedItemPosition(), 0));
        Set<String> set = new LinkedHashSet<>();
        set.add("All");
        if (!studentSubject.isEmpty()) set.add(studentSubject);
        for (AttendanceRecordModel m : fullList)
            if (!empty(m.getSubject()) && !"Class".equals(m.getSubject()))
                set.add(m.getSubject());
        subjects.clear(); subjects.addAll(set); subjectAdapter.notifyDataSetChanged();
        int idx = subjects.indexOf(prev);
        if (idx >= 0) spinnerSubject.setSelection(idx, false);
    }

    // ── Overall stats (always from unfiltered fullList) ───────────────────────
    private void updateOverallStats() {
        int total = fullList.size(), presentCount = 0;
        for (AttendanceRecordModel m : fullList) if (m.isPresent()) presentCount++;
        int pct = total > 0 ? (int)(presentCount * 100.0 / total) : 0;

        tvOverallPercent.setText(pct + "%");
        tvOverallLabel.setText(total == 0 ? "No records yet"
                : pct >= 75 ? "Good Standing ✔" : "Below Target ⚠");

        if (overallRing != null)
            overallRing.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    total == 0 ? Color.parseColor("#9E9E9E")
                            : pct >= 75 ? Color.parseColor("#43A047")
                            : Color.parseColor("#E53935")));

        if (total == 0) {
            tvAlertBanner.setText("No attendance records found yet.\nMark your attendance to see data here.");
        } else if (pct < 75) {
            int needed = 0;
            while (needed < 500 && ((presentCount + needed) * 100 / (total + needed)) < 75) needed++;
            tvAlertBanner.setText("⚠ Your attendance is " + pct + "% ("
                    + presentCount + "/" + total + "). Attend "
                    + needed + " more class(es) to reach 75%.");
        } else {
            int canMiss = 0;
            while (canMiss < 500 && (presentCount * 100 / (total + canMiss + 1)) >= 75) canMiss++;
            tvAlertBanner.setText("✔ " + pct + "% (" + presentCount + "/" + total
                    + "). You can miss up to " + canMiss + " more class(es) and stay above 75%.");
        }
        cardAlertBanner.setVisibility(View.VISIBLE);

        buildSubjectBreakdown();
        buildTrendChart();
    }

    private void buildSubjectBreakdown() {
        if (tvSubjectBreakdown == null) return;
        if (fullList.isEmpty()) { tvSubjectBreakdown.setText("No records yet."); return; }
        Map<String, int[]> map = new HashMap<>();
        for (AttendanceRecordModel m : fullList) {
            String s = safe(m.getSubject());
            if (!map.containsKey(s)) map.put(s, new int[]{0, 0});
            map.get(s)[1]++;
            if (m.isPresent()) map.get(s)[0]++;
        }
        List<String> keys = new ArrayList<>(map.keySet()); Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (String sub : keys) {
            int[] s = map.get(sub);
            int p = s[1] > 0 ? s[0] * 100 / s[1] : 0;
            sb.append(p >= 75 ? "✔ " : "⚠ ")
                    .append(sub).append(":  ").append(s[0]).append("/").append(s[1])
                    .append("  (").append(p).append("%)\n");
        }
        tvSubjectBreakdown.setText(sb.toString().trim());
    }

    private void buildTrendChart() {
        Map<String, int[]> weekMap = new HashMap<>();
        SimpleDateFormat sdf = new SimpleDateFormat(FMT_SAVE, Locale.getDefault());
        for (AttendanceRecordModel m : fullList) {
            try {
                Date d = sdf.parse(safe(m.getDate())); if (d == null) continue;
                Calendar cal = Calendar.getInstance(); cal.setTime(d);
                String wk = cal.get(Calendar.YEAR) + "-W"
                        + String.format(Locale.getDefault(), "%02d", cal.get(Calendar.WEEK_OF_YEAR));
                if (!weekMap.containsKey(wk)) weekMap.put(wk, new int[]{0, 0});
                weekMap.get(wk)[1]++;
                if (m.isPresent()) weekMap.get(wk)[0]++;
            } catch (Exception ignored) {}
        }
        List<String> weeks = new ArrayList<>(weekMap.keySet()); Collections.sort(weeks);
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < weeks.size(); i++) {
            int[] s = weekMap.get(weeks.get(i));
            entries.add(new Entry(i, s[1] > 0 ? s[0] * 100f / s[1] : 0f));
        }
        if (entries.isEmpty()) { lineChart.clear(); lineChart.invalidate(); return; }
        LineDataSet set = new LineDataSet(entries, "Weekly %");
        set.setColor(Color.parseColor("#1A237E")); set.setCircleColor(Color.parseColor("#1A237E"));
        set.setLineWidth(2.5f); set.setCircleRadius(4f); set.setValueTextSize(9f);
        set.setFillAlpha(60); set.setFillColor(Color.parseColor("#3949AB")); set.setDrawFilled(true);
        set.setMode(LineDataSet.Mode.CUBIC_BEZIER); set.setValueTextColor(Color.parseColor("#37474F"));
        lineChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(weeks.toArray(new String[0])));
        lineChart.setData(new LineData(set)); lineChart.animateX(700); lineChart.invalidate();
    }

    private void applyFilters() {
        filteredList.clear();
        int present = 0, absent = 0;

        Calendar today = Calendar.getInstance();
        Calendar weekStart = Calendar.getInstance();
        weekStart.set(Calendar.DAY_OF_WEEK, weekStart.getFirstDayOfWeek());
        weekStart.set(Calendar.HOUR_OF_DAY, 0); weekStart.set(Calendar.MINUTE, 0); weekStart.set(Calendar.SECOND, 0);
        SimpleDateFormat sdf = new SimpleDateFormat(FMT_SAVE, Locale.getDefault());

        for (AttendanceRecordModel m : fullList) {
            if (!selectedSubject.equals("All") && !selectedSubject.equals(m.getSubject())) continue;

            if (!searchQuery.isEmpty()) {
                String sub = safe(m.getSubject()).toLowerCase(Locale.getDefault());
                String dt  = toDisplay(safe(m.getDate())).toLowerCase(Locale.getDefault());
                if (!sub.contains(searchQuery) && !dt.contains(searchQuery)) continue;
            }

            String recDate = safe(m.getDate());
            if (!selectedDate.isEmpty()) {
                if (!selectedDate.equals(recDate)) continue;
            } else {
                switch (chipMode) {
                    case "TODAY": if (!todayKey().equals(recDate)) continue; break;
                    case "WEEK":
                        try { Date d = sdf.parse(recDate); if (d == null || d.before(weekStart.getTime())) continue; } catch (Exception e) { continue; } break;
                    case "MONTH":
                        try { Date d = sdf.parse(recDate); if (d == null) continue;
                            Calendar dc = Calendar.getInstance(); dc.setTime(d);
                            if (dc.get(Calendar.MONTH) != today.get(Calendar.MONTH)
                                    || dc.get(Calendar.YEAR) != today.get(Calendar.YEAR)) continue;
                        } catch (Exception e) { continue; } break;
                }
            }

            filteredList.add(m);
            if (m.isPresent()) present++; else absent++;
        }

        tvPresentCount.setText(String.valueOf(present));
        tvAbsentCount.setText(String.valueOf(absent));
        tvTotalCount.setText(String.valueOf(present + absent));
        tvRecordCount.setText(filteredList.size() + " records");

        String dl = selectedDate.isEmpty() ? chipModeLabel() : toDisplay(selectedDate);
        tvSelectedDateLabel.setText(dl + "  •  "
                + (selectedSubject.equals("All") ? "All Subjects" : selectedSubject));

        List<BarEntry> entries = new ArrayList<>();
        entries.add(new BarEntry(0, present)); entries.add(new BarEntry(1, absent));
        BarDataSet set = new BarDataSet(entries, "");
        set.setColors(Color.parseColor("#43A047"), Color.parseColor("#E53935"));
        set.setValueTextSize(13f); set.setValueTextColor(Color.parseColor("#37474F"));
        BarData data = new BarData(set); data.setBarWidth(0.5f);
        barChart.setData(data); barChart.animateY(400); barChart.invalidate();

        adapter.updateList(new ArrayList<>(filteredList));
    }

    // ── UI setup ──────────────────────────────────────────────────────────────
    private void bindViews() {
        recyclerView        = findViewById(R.id.recyclerAttendance);
        spinnerSubject      = findViewById(R.id.spinnerSubject);
        barChart            = findViewById(R.id.barChart);
        lineChart           = findViewById(R.id.lineChart);
        btnSelectDate       = findViewById(R.id.btnSelectDate);
        btnClearDate        = findViewById(R.id.btnClearDate);
        searchStudent       = findViewById(R.id.searchStudent);
        tvOverallPercent    = findViewById(R.id.tvOverallPercent);
        tvOverallLabel      = findViewById(R.id.tvOverallLabel);
        tvPresentCount      = findViewById(R.id.tvPresentCount);
        tvAbsentCount       = findViewById(R.id.tvAbsentCount);
        tvTotalCount        = findViewById(R.id.tvTotalCount);
        tvRecordCount       = findViewById(R.id.tvRecordCount);
        tvSelectedDateLabel = findViewById(R.id.tvSelectedDateLabel);
        tvAlertBanner       = findViewById(R.id.tvAlertBanner);
        cardAlertBanner     = findViewById(R.id.cardAlertBanner);
        tvSubjectBreakdown  = findViewById(R.id.tvSubjectBreakdown);
        chipAll             = findViewById(R.id.chipAll);
        chipToday           = findViewById(R.id.chipToday);
        chipWeek            = findViewById(R.id.chipWeek);
        chipMonth           = findViewById(R.id.chipMonth);
        overallRing         = findViewById(R.id.overallRing);
    }

    private void setupRecyclerView() {
        adapter = new Studentrecordadapter(filteredList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        recyclerView.setNestedScrollingEnabled(false);
    }

    private void setupSpinner() {
        subjects.add("All");
        subjectAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, subjects);
        spinnerSubject.setAdapter(subjectAdapter);
        spinnerSubject.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                selectedSubject = p.getItemAtPosition(pos).toString(); applyFilters();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    private void setupSearch() {
        if (searchStudent == null) return;
        searchStudent.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                searchQuery = s.toString().trim().toLowerCase(Locale.getDefault()); applyFilters();
            }
        });
    }

    private void setupButtons() {
        btnSelectDate.setOnClickListener(v -> openDatePicker());
        btnClearDate.setOnClickListener(v -> {
            selectedDate = ""; chipMode = "ALL";
            btnSelectDate.setText("📅 Pick Date");
            highlightChip(chipAll); applyFilters();
        });
    }

    private void setupChips() {
        highlightChip(chipAll);
        chipAll.setOnClickListener(v ->   setChip("ALL",   chipAll));
        chipToday.setOnClickListener(v -> setChip("TODAY", chipToday));
        chipWeek.setOnClickListener(v ->  setChip("WEEK",  chipWeek));
        chipMonth.setOnClickListener(v -> setChip("MONTH", chipMonth));
    }

    private void setChip(String mode, Chip chip) {
        chipMode = mode; selectedDate = "";
        btnSelectDate.setText("📅 Pick Date");
        highlightChip(chip); applyFilters();
    }

    private void highlightChip(Chip active) {
        for (Chip c : new Chip[]{chipAll, chipToday, chipWeek, chipMonth}) {
            if (c == null) continue;
            boolean on = (c == active);
            c.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(
                    on ? Color.parseColor("#1A237E") : Color.WHITE));
            c.setTextColor(on ? Color.WHITE : Color.parseColor("#1A237E"));
        }
    }

    private void setupBarChart() {
        barChart.setDrawGridBackground(false); barChart.getDescription().setEnabled(false);
        barChart.setDrawValueAboveBar(true); barChart.setTouchEnabled(false);
        barChart.getLegend().setEnabled(false); barChart.getAxisRight().setEnabled(false);
        barChart.setBackgroundColor(Color.TRANSPARENT);
        XAxis x = barChart.getXAxis(); x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setDrawGridLines(false); x.setGranularity(1f);
        x.setValueFormatter(new IndexAxisValueFormatter(new String[]{"Present", "Absent"}));
        x.setTextColor(Color.parseColor("#37474F"));
        YAxis y = barChart.getAxisLeft(); y.setAxisMinimum(0f);
        y.setGridColor(Color.parseColor("#E8EAF6")); y.setTextColor(Color.parseColor("#37474F"));
    }

    private void setupLineChart() {
        lineChart.setDrawGridBackground(false); lineChart.getDescription().setEnabled(false);
        lineChart.setTouchEnabled(true); lineChart.setDragEnabled(true); lineChart.setScaleEnabled(false);
        lineChart.getLegend().setEnabled(false); lineChart.getAxisRight().setEnabled(false);
        lineChart.setBackgroundColor(Color.TRANSPARENT);
        lineChart.setNoDataText("No trend data yet.");
        lineChart.setNoDataTextColor(Color.parseColor("#FFA000"));
        XAxis x = lineChart.getXAxis(); x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setDrawGridLines(false); x.setTextColor(Color.parseColor("#37474F"));
        YAxis y = lineChart.getAxisLeft(); y.setAxisMinimum(0f); y.setAxisMaximum(100f);
        y.setGridColor(Color.parseColor("#E8EAF6")); y.setTextColor(Color.parseColor("#37474F"));
    }

    private void openDatePicker() {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, day) -> {
            selectedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, day);
            chipMode = "CUSTOM"; btnSelectDate.setText("📅 " + toDisplay(selectedDate));
            highlightChip(null); applyFilters();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showError(String msg) {
        Log.e(TAG, msg);
        tvAlertBanner.setText(msg);
        cardAlertBanner.setVisibility(View.VISIBLE);
        tvOverallPercent.setText("0%");
        tvOverallLabel.setText("No records yet");
        if (tvSubjectBreakdown != null) tvSubjectBreakdown.setText("No data yet.");
    }

    private String chipModeLabel() {
        switch (chipMode) { case "TODAY": return "Today"; case "WEEK": return "This Week";
            case "MONTH": return "This Month"; default: return "All Dates"; }
    }

    private String todayKey() { return new SimpleDateFormat(FMT_SAVE, Locale.getDefault()).format(new Date()); }
    private String toDisplay(String s) {
        try { Date d = new SimpleDateFormat(FMT_SAVE, Locale.getDefault()).parse(s);
            if (d != null) return new SimpleDateFormat(FMT_DISPLAY, Locale.getDefault()).format(d);
        } catch (Exception ignored) {} return s;
    }
    private boolean empty(String s) { return s == null || s.trim().isEmpty(); }
    private String  safe(String s)  { return s != null ? s : ""; }
}