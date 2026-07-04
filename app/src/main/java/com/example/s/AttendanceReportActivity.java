package com.example.s;

import android.app.DatePickerDialog;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.s.adapter.AttendanceRecordAdapter;
import com.example.s.model.AttendanceRecordModel;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.chip.Chip;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;


public class AttendanceReportActivity extends AppCompatActivity {

    private FirebaseFirestore db;


    private ListenerRegistration sessionsListener;
    private ListenerRegistration directClassListener;
    private final Map<String, ListenerRegistration> sessionRecordListeners = new ConcurrentHashMap<>();

    private final Map<String, List<AttendanceRecordModel>> sessionBucket = new ConcurrentHashMap<>();
    private final List<AttendanceRecordModel> directBucket = new ArrayList<>();

    private final List<AttendanceRecordModel> fullList     = new ArrayList<>();
    private final List<AttendanceRecordModel> filteredList = new ArrayList<>();

    private Spinner          spinnerSubject;
    private BarChart         barChart;
    private TextView         tvPresent, tvAbsent, tvAttendanceRate, tvRecordCount, tvSelectedDateLabel;
    private TextView         tvLowAttendanceAlert;
    private View             cardLowAttendanceAlert;
    private EditText         searchStudent;
    private Button           btnSelectDate, btnClearDate, btnExportPdf, btnExportExcel;
    private Chip             chipAll, chipToday, chipWeek, chipMonth, chipLowAttendance;

    private String selectedSubject = "All";
    private String selectedDate    = "";
    private String chipMode        = "ALL";
    private String searchQuery     = "";

    private String classId      = "";
    private String classSubject = "";

    private final List<String>      subjects       = new ArrayList<>();
    private       ArrayAdapter<String> subjectAdapter = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance_report);

        db      = FirebaseFirestore.getInstance();
        classId = getIntent().getStringExtra("classId");
        if (classId == null) classId = "";

        bindViews();
        setupSpinner();
        setupSearch();
        setupButtons();
        setupChips();
        setupChart();

        if (classId.isEmpty()) {
            promptSelectClass();
        } else {
            fetchClassSubjectThenListen();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        removeAllListeners();
    }

    private void removeAllListeners() {
        if (sessionsListener    != null) { sessionsListener.remove();    sessionsListener    = null; }
        if (directClassListener != null) { directClassListener.remove(); directClassListener = null; }
        for (ListenerRegistration r : sessionRecordListeners.values()) r.remove();
        sessionRecordListeners.clear();
    }

    private void promptSelectClass() {
        String teacherId = FirebaseAuth.getInstance().getUid();
        if (teacherId == null) { finish(); return; }

        db.collection("classes").whereEqualTo("teacherId", teacherId).get()
                .addOnSuccessListener(query -> {
                    if (query.isEmpty()) {
                        Toast.makeText(this, "No classes found", Toast.LENGTH_SHORT).show();
                        finish(); return;
                    }
                    List<String> names = new ArrayList<>();
                    List<String> ids   = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : query) {
                        String s = doc.getString("subject");
                        String b = doc.getString("batch");
                        names.add((s != null ? s : "?") + (b != null ? " (" + b + ")" : ""));
                        ids.add(doc.getId());
                    }
                    new android.app.AlertDialog.Builder(this)
                            .setTitle("Select Class")
                            .setItems(names.toArray(new String[0]), (d, i) -> {
                                classId = ids.get(i);
                                fetchClassSubjectThenListen();
                            })
                            .setNegativeButton("Cancel", (d, w) -> finish())
                            .setCancelable(false).show();
                })
                .addOnFailureListener(e -> finish());
    }

    private void bindViews() {
        barChart               = findViewById(R.id.barChart);
        btnSelectDate          = findViewById(R.id.btnSelectDate);
        btnClearDate           = findViewById(R.id.btnClearDate);
        btnExportPdf           = findViewById(R.id.btnExportPdf);
        btnExportExcel         = findViewById(R.id.btnExportExcel);
        tvPresent              = findViewById(R.id.tvPresentCount);
        tvAbsent               = findViewById(R.id.tvAbsentCount);
        tvAttendanceRate       = findViewById(R.id.tvAttendanceRate);
        tvRecordCount          = findViewById(R.id.tvRecordCount);
        tvSelectedDateLabel    = findViewById(R.id.tvSelectedDateLabel);
        tvLowAttendanceAlert   = findViewById(R.id.tvLowAttendanceAlert);
        cardLowAttendanceAlert = findViewById(R.id.cardLowAttendanceAlert);
        spinnerSubject         = findViewById(R.id.spinnerSubject);
        searchStudent          = findViewById(R.id.searchStudent);
        chipAll                = findViewById(R.id.chipAll);
        chipToday              = findViewById(R.id.chipToday);
        chipWeek               = findViewById(R.id.chipWeek);
        chipMonth              = findViewById(R.id.chipMonth);
        chipLowAttendance      = findViewById(R.id.chipLowAttendance);
    }

    private void setupSpinner() {
        subjects.add("All");
        subjectAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, subjects);
        spinnerSubject.setAdapter(subjectAdapter);
        spinnerSubject.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                selectedSubject = p.getItemAtPosition(pos).toString();
                applyFilters();
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
                searchQuery = s.toString().trim().toLowerCase(Locale.getDefault());
                applyFilters();
            }
        });
    }

    private void setupButtons() {
        btnSelectDate.setOnClickListener(v -> openDatePicker());
        btnClearDate.setOnClickListener(v -> {
            selectedDate = "";
            chipMode     = "ALL";
            btnSelectDate.setText("📅 Pick Date");
            highlightChip(chipAll);
            applyFilters();
        });
        btnExportPdf.setOnClickListener(v -> exportPDF());
        btnExportExcel.setOnClickListener(v -> exportCSV());
    }

    private void setupChips() {
        highlightChip(chipAll);
        chipAll.setOnClickListener(v -> {
            chipMode = "ALL"; selectedDate = "";
            btnSelectDate.setText("📅 Pick Date");
            highlightChip(chipAll); applyFilters();
        });
        chipToday.setOnClickListener(v -> {
            chipMode = "TODAY"; selectedDate = "";
            btnSelectDate.setText("📅 Pick Date");
            highlightChip(chipToday); applyFilters();
        });
        chipWeek.setOnClickListener(v -> {
            chipMode = "WEEK"; selectedDate = "";
            btnSelectDate.setText("📅 Pick Date");
            highlightChip(chipWeek); applyFilters();
        });
        chipMonth.setOnClickListener(v -> {
            chipMode = "MONTH"; selectedDate = "";
            btnSelectDate.setText("📅 Pick Date");
            highlightChip(chipMonth); applyFilters();
        });
        chipLowAttendance.setOnClickListener(v -> {
            chipMode = "LOW"; selectedDate = "";
            btnSelectDate.setText("📅 Pick Date");
            highlightChip(chipLowAttendance); applyFilters();
        });
    }

    private void highlightChip(Chip active) {
        int on  = Color.parseColor("#1A237E");
        int off = Color.parseColor("#FFFFFF");
        for (Chip c : new Chip[]{chipAll, chipToday, chipWeek, chipMonth}) {
            if (c == null) continue;
            c.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(c == active ? on : off));
            c.setTextColor(c == active ? Color.WHITE : Color.parseColor("#1A237E"));
        }
        if (chipLowAttendance != null) {
            boolean lowActive = (active == chipLowAttendance);
            chipLowAttendance.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(
                    lowActive ? Color.parseColor("#E65100") : Color.parseColor("#FFF3E0")));
            chipLowAttendance.setTextColor(lowActive ? Color.WHITE : Color.parseColor("#E65100"));
        }
    }

    private void setupChart() {
        barChart.setDrawGridBackground(false);
        barChart.getDescription().setEnabled(false);
        barChart.setDrawValueAboveBar(true);
        barChart.setTouchEnabled(false);
        barChart.getLegend().setEnabled(false);
        barChart.getAxisRight().setEnabled(false);
        barChart.setBackgroundColor(Color.TRANSPARENT);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(new String[]{"Present", "Absent"}));
        xAxis.setTextColor(Color.parseColor("#37474F"));

        YAxis yAxis = barChart.getAxisLeft();
        yAxis.setDrawGridLines(true);
        yAxis.setGridColor(Color.parseColor("#E8EAF6"));
        yAxis.setAxisMinimum(0f);
        yAxis.setTextColor(Color.parseColor("#37474F"));
    }

    private void fetchClassSubjectThenListen() {
        if (classId.isEmpty()) { startAllListeners(); return; }
        db.collection("classes").document(classId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String s = doc.getString("subject");
                        if (!empty(s)) classSubject = s;
                    }
                    startAllListeners();
                })
                .addOnFailureListener(e -> startAllListeners());
    }


    private void startAllListeners() {
        removeAllListeners();

        listenSessionChain();
    }


    private void listenDirectClassRecords() { /* no-op */ }


    private void listenSessionChain() {
        sessionsListener = db.collection("attendanceSessions")
                .whereEqualTo("classId", classId)
                .addSnapshotListener((sessions, error) -> {
                    if (error != null || sessions == null) return;

                    Set<String> activeIds = new LinkedHashSet<>();
                    for (QueryDocumentSnapshot s : sessions) {
                        String sid = s.getString("sessionId");
                        if (empty(sid)) sid = s.getId();
                        activeIds.add(sid);
                    }

                    List<String> toRemove = new ArrayList<>();
                    for (String sid : sessionRecordListeners.keySet())
                        if (!activeIds.contains(sid)) toRemove.add(sid);
                    for (String sid : toRemove) {
                        sessionRecordListeners.get(sid).remove();
                        sessionRecordListeners.remove(sid);
                        sessionBucket.remove(sid);
                    }
                    if (!toRemove.isEmpty()) rebuildAndFilter();

                    for (QueryDocumentSnapshot session : sessions) {
                        String sid = session.getString("sessionId");
                        if (empty(sid)) sid = session.getId();
                        if (sessionRecordListeners.containsKey(sid)) continue;

                        final String finalSid    = sid;
                        final String sessionDate = extractDateFromSession(session);

                        ListenerRegistration lr = db.collection("attendance")
                                .document(finalSid)
                                .collection("students")
                                .addSnapshotListener((records, err) -> {
                                    List<AttendanceRecordModel> batch = new ArrayList<>();
                                    if (records != null)
                                        for (QueryDocumentSnapshot r : records)
                                            batch.add(parseRecord(r, sessionDate));
                                    sessionBucket.put(finalSid, batch);
                                    rebuildAndFilter();
                                });
                        sessionRecordListeners.put(sid, lr);
                    }
                });
    }

    private AttendanceRecordModel parseRecord(QueryDocumentSnapshot doc, String fallbackDate) {
        // Name
        String name = doc.getString("name");
        if (empty(name)) name = doc.getString("studentName");
        if (empty(name)) name = doc.getString("email");
        if (empty(name)) name = "Unknown";

        // Roll number
        String roll = doc.getString("rollNo");
        if (empty(roll)) roll = doc.getString("rollNumber");
        if (empty(roll)) roll = doc.getString("roll");
        if (empty(roll)) {
            String sid = doc.getString("studentId");
            roll = empty(sid) ? "—" : (sid.length() > 6 ? "…" + sid.substring(sid.length() - 6) : sid);
        }

        // Subject
        String subject = doc.getString("subject");
        if (empty(subject)) subject = classSubject;


        SimpleDateFormat dateSdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        dateSdf.setTimeZone(java.util.TimeZone.getDefault());

        String date = doc.getString("date");
        if (empty(date)) {
            // Try to derive date from a timestamp on the record itself
            com.google.firebase.Timestamp recTs = doc.getTimestamp("markedAt");
            if (recTs == null) recTs = doc.getTimestamp("timestamp");
            if (recTs == null) recTs = doc.getTimestamp("createdAt");
            if (recTs != null)
                date = dateSdf.format(recTs.toDate());
        }
        if (empty(date)) date = fallbackDate;

        date = normalizeDate(date);

        String time = doc.getString("time");
        if (empty(time)) {
            com.google.firebase.Timestamp ts = doc.getTimestamp("timestamp");
            if (ts == null) ts = doc.getTimestamp("markedAt");
            if (ts != null) {
                SimpleDateFormat timeSdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
                timeSdf.setTimeZone(java.util.TimeZone.getDefault());
                time = timeSdf.format(ts.toDate());
            }
        }

        boolean present = false;
        Boolean boolPres = doc.getBoolean("present");
        if (boolPres != null) {
            present = boolPres;
        } else {
            String status = doc.getString("status");
            present = "present".equalsIgnoreCase(status);
        }

        // Location
        Double lat  = doc.getDouble("studentLat"); if (lat  == null) lat  = doc.getDouble("lat");
        Double lng  = doc.getDouble("studentLng"); if (lng  == null) lng  = doc.getDouble("lng");
        Double dist = doc.getDouble("distance");

        return new AttendanceRecordModel(name, roll, subject, present, date, time, lat, lng, dist);
    }

    private String extractDateFromSession(QueryDocumentSnapshot session) {
        String date = session.getString("date");
        if (!empty(date)) return normalizeDate(date);


        com.google.firebase.Timestamp ts = session.getTimestamp("startTime");
        if (ts == null) ts = session.getTimestamp("sessionStartTime");
        if (ts == null) ts = session.getTimestamp("createdAt");
        if (ts == null) ts = session.getTimestamp("timestamp");
        if (ts != null) {

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            sdf.setTimeZone(java.util.TimeZone.getDefault());
            return sdf.format(ts.toDate());
        }


        return "";
    }

    private String todayStr() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }


    private void rebuildAndFilter() {
        fullList.clear();
        Set<String> seen = new LinkedHashSet<>();

        for (Map.Entry<String, List<AttendanceRecordModel>> entry : sessionBucket.entrySet()) {
            String sessionId = entry.getKey();
            for (AttendanceRecordModel m : entry.getValue()) {
                m.setSessionId(sessionId);
                String key = sessionId + "|" + safe(m.getRollNo());
                if (seen.add(key)) fullList.add(m);
            }
        }
        for (AttendanceRecordModel m : directBucket) {
            m.setSessionId("direct|" + safe(m.getDate()));
            String key = safe(m.getRollNo()) + "|" + safe(m.getDate());
            if (seen.add(key)) fullList.add(m);
        }

        computeStudentPercents();

        Set<String> subjectSet = new LinkedHashSet<>();
        subjectSet.add("All");
        if (!classSubject.isEmpty()) subjectSet.add(classSubject);
        for (AttendanceRecordModel m : fullList)
            if (!empty(m.getSubject())) subjectSet.add(m.getSubject());
        refreshSubjectSpinner(new ArrayList<>(subjectSet));

        applyFilters();
    }


    private void computeStudentPercents() {
        Map<String, int[]> stats = new HashMap<>(); // rollNo → [present, total]
        for (AttendanceRecordModel m : fullList) {
            String key = safe(m.getRollNo());
            if (!stats.containsKey(key)) stats.put(key, new int[]{0, 0});
            stats.get(key)[1]++;
            if (m.isPresent()) stats.get(key)[0]++;
        }
        for (AttendanceRecordModel m : fullList) {
            String key = safe(m.getRollNo());
            int[] s = stats.get(key);
            if (s != null && s[1] > 0)
                m.setAttendancePercent((s[0] * 100f) / s[1]);
        }
    }

    private void refreshSubjectSpinner(List<String> newSubjects) {
        String prev = subjects.isEmpty() ? "All"
                : subjects.get(Math.max(spinnerSubject.getSelectedItemPosition(), 0));
        subjects.clear();
        subjects.addAll(newSubjects);
        subjectAdapter.notifyDataSetChanged();
        int idx = subjects.indexOf(prev);
        if (idx >= 0) spinnerSubject.setSelection(idx, false);
    }


    private void applyFilters() {
        filteredList.clear();

        Calendar today = Calendar.getInstance();

        Calendar weekStart = Calendar.getInstance();
        int dow = weekStart.get(Calendar.DAY_OF_WEEK); // 1=Sun … 7=Sat
        int daysBack = (dow == Calendar.SUNDAY) ? 6 : (dow - Calendar.MONDAY);
        weekStart.add(Calendar.DAY_OF_MONTH, -daysBack);
        weekStart.set(Calendar.HOUR_OF_DAY, 0);
        weekStart.set(Calendar.MINUTE, 0);
        weekStart.set(Calendar.SECOND, 0);
        weekStart.set(Calendar.MILLISECOND, 0);

        Calendar weekEnd = Calendar.getInstance();
        weekEnd.set(Calendar.HOUR_OF_DAY, 23);
        weekEnd.set(Calendar.MINUTE, 59);
        weekEnd.set(Calendar.SECOND, 59);
        weekEnd.set(Calendar.MILLISECOND, 999);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());


        java.util.Map<String, Float> rollOverallPct = new java.util.HashMap<>();
        for (AttendanceRecordModel m : fullList) {
            String roll = safe(m.getRollNo());

            if (!rollOverallPct.containsKey(roll) && m.getAttendancePercent() > 0f) {
                rollOverallPct.put(roll, m.getAttendancePercent());
            }
        }

        for (AttendanceRecordModel m : fullList) {

            String recDate = normalizeDate(safe(m.getDate()));

            // Subject filter
            if (!selectedSubject.equals("All") && !selectedSubject.equals(m.getSubject()))
                continue;

            // Search filter
            if (!searchQuery.isEmpty()) {
                String n = safe(m.getName()).toLowerCase(Locale.getDefault());
                String r = safe(m.getRollNo()).toLowerCase(Locale.getDefault());
                if (!n.contains(searchQuery) && !r.contains(searchQuery)) continue;
            }

            // Date / chip filter
            if (!selectedDate.isEmpty()) {
                // Exact date from DatePicker
                if (!selectedDate.equals(recDate)) continue;
            } else {
                switch (chipMode) {
                    case "TODAY":
                        if (!todayStr().equals(recDate)) continue;
                        break;
                    case "WEEK":
                        try {
                            Date d = sdf.parse(recDate);
                            if (d == null) continue;
                            if (d.before(weekStart.getTime()) || d.after(weekEnd.getTime())) continue;
                        } catch (Exception ignored) { continue; }
                        break;
                    case "MONTH":
                        try {
                            Date d = sdf.parse(recDate);
                            if (d == null) continue;
                            Calendar dc = Calendar.getInstance(); dc.setTime(d);
                            if (dc.get(Calendar.MONTH) != today.get(Calendar.MONTH)
                                    || dc.get(Calendar.YEAR) != today.get(Calendar.YEAR)) continue;
                        } catch (Exception ignored) { continue; }
                        break;
                    case "LOW":

                        float overallPct = rollOverallPct.getOrDefault(safe(m.getRollNo()), 0f);
                        if (overallPct >= 75f || overallPct <= 0f) continue;
                        break;
                }
            }

            filteredList.add(m);
        }

        java.util.Map<String, Boolean> studentBestStatus = new java.util.HashMap<>();
        for (AttendanceRecordModel m : filteredList) {
            String roll = safe(m.getRollNo());
            boolean cur = studentBestStatus.containsKey(roll) && studentBestStatus.get(roll);
            studentBestStatus.put(roll, cur || m.isPresent());
        }
        int uniquePresent = 0, uniqueAbsent = 0;
        for (boolean p : studentBestStatus.values()) { if (p) uniquePresent++; else uniqueAbsent++; }


        java.util.Set<String> lowStudentRolls = new java.util.HashSet<>();
        for (java.util.Map.Entry<String, Float> entry : rollOverallPct.entrySet()) {
            if (entry.getValue() > 0f && entry.getValue() < 75f) {
                lowStudentRolls.add(entry.getKey());
            }
        }
        if (!selectedSubject.equals("All")) {
            java.util.Set<String> rollsForSubject = new java.util.HashSet<>();
            for (AttendanceRecordModel m : fullList) {
                if (selectedSubject.equals(m.getSubject()))
                    rollsForSubject.add(safe(m.getRollNo()));
            }
            lowStudentRolls.retainAll(rollsForSubject);
        }
        int lowCount = lowStudentRolls.size();

        // Update summary cards
        tvPresent.setText(String.valueOf(uniquePresent));
        tvAbsent.setText(String.valueOf(uniqueAbsent));
        tvRecordCount.setText(studentBestStatus.size() + " students");
        int total = uniquePresent + uniqueAbsent;
        int rate  = total > 0 ? (int)((uniquePresent * 100.0) / total) : 0;
        tvAttendanceRate.setText(rate + "%");
        updateChart(uniquePresent, uniqueAbsent);

        // Header subtitle
        String dl = selectedDate.isEmpty() ? chipModeLabel() : selectedDate;
        String sl = selectedSubject.equals("All") ? "All Subjects" : selectedSubject;
        tvSelectedDateLabel.setText(dl + "  •  " + sl);

        // Low attendance banner — show on all chips EXCEPT the LOW chip itself
        if (lowCount > 0 && !chipMode.equals("LOW")) {
            tvLowAttendanceAlert.setText("⚠ " + lowCount + " student(s) below 75% attendance");
            cardLowAttendanceAlert.setVisibility(View.VISIBLE);
        } else {
            cardLowAttendanceAlert.setVisibility(View.GONE);
        }

        showPATable(filteredList);
    }

    private String chipModeLabel() {
        switch (chipMode) {
            case "TODAY":  return "Today";
            case "WEEK":   return "This Week";
            case "MONTH":  return "This Month";
            case "LOW":    return "Below 75%";
            default:       return "All Dates";
        }
    }

    private void updateChart(int present, int absent) {
        List<BarEntry> entries = new ArrayList<>();
        entries.add(new BarEntry(0, present));
        entries.add(new BarEntry(1, absent));
        BarDataSet set = new BarDataSet(entries, "Attendance");
        set.setColors(Color.parseColor("#43A047"), Color.parseColor("#E53935"));
        set.setValueTextSize(14f);
        set.setValueTextColor(Color.parseColor("#37474F"));
        BarData data = new BarData(set);
        data.setBarWidth(0.5f);
        barChart.setData(data);
        barChart.animateY(600);
        barChart.invalidate();
    }

    private void openDatePicker() {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, day) -> {
            month++;
            selectedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month, day);
            chipMode     = "CUSTOM";
            String displayDate = String.format(Locale.getDefault(), "%02d/%02d/%04d", day, month, year);
            btnSelectDate.setText("📅 " + displayDate);
            highlightChip(null);
            applyFilters();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }


    private void exportPDF() {
        if (filteredList.isEmpty()) {
            Toast.makeText(this, "No records to export", Toast.LENGTH_SHORT).show();
            return;
        }


        java.util.TreeMap<String, String> rollToName = new java.util.TreeMap<>();
        java.util.LinkedHashMap<String, java.util.LinkedHashSet<String>> dateSlots =
                new java.util.LinkedHashMap<>();
        java.util.Map<String, java.util.Map<String, String>> colRollStatus =
                new java.util.LinkedHashMap<>();

        for (AttendanceRecordModel m : filteredList) {
            String roll = safe(m.getRollNo());
            if (roll.isEmpty()) roll = "?";
            String date = normalizeDate(safe(m.getDate()));
            String slot = safe(m.getSessionId());
            if (slot.isEmpty()) slot = safe(m.getTime());
            rollToName.put(roll, safe(m.getName()));
            if (!dateSlots.containsKey(date)) dateSlots.put(date, new java.util.LinkedHashSet<>());
            dateSlots.get(date).add(slot);
        }

        java.util.List<String> colKeys = new java.util.ArrayList<>();
        java.util.Map<String, String> colLabel = new java.util.LinkedHashMap<>();
        for (String date : dateSlots.keySet()) {
            java.util.LinkedHashSet<String> slots = dateSlots.get(date);
            boolean multi = slots.size() > 1;
            int idx = 1;
            for (String slot : slots) {
                String key = date + "||" + slot;
                colKeys.add(key);
                colLabel.put(key, multi ? (formatShortDate(date) + " L" + idx) : formatShortDate(date));
                colRollStatus.put(key, new java.util.HashMap<>());
                idx++;
            }
        }

        for (AttendanceRecordModel m : filteredList) {
            String roll = safe(m.getRollNo()); if (roll.isEmpty()) roll = "?";
            String slot = safe(m.getSessionId()); if (slot.isEmpty()) slot = safe(m.getTime());
            String key  = normalizeDate(safe(m.getDate())) + "||" + slot;
            if (colRollStatus.containsKey(key)) {
                String ex = colRollStatus.get(key).get(roll);
                if (ex == null || "A".equals(ex))
                    colRollStatus.get(key).put(roll, m.isPresent() ? "P" : "A");
            }
        }

        Map<String, Map<String, String>> pivotStatus = new java.util.TreeMap<>();
        for (String roll : rollToName.keySet()) pivotStatus.put(roll, new java.util.LinkedHashMap<>());
        for (String key : colKeys) {
            Map<String, String> colMap = colRollStatus.get(key);
            for (String roll : rollToName.keySet()) {
                pivotStatus.get(roll).put(key, colMap.getOrDefault(roll, "A"));
            }
        }

        List<String> dates = colKeys; // now colKeys = ordered lecture columns


        int fixedW   = 70 + 150;                        // 220
        int dateColW = 55;
        int totalW   = Math.max(fixedW + dates.size() * dateColW + 40, 620);
        int rowH     = 24;
        int headerH  = 110;
        int totalH   = Math.max(headerH + (pivotStatus.size() + 1) * rowH + 40, 400);

        PdfDocument document = new PdfDocument();
        PdfDocument.PageInfo pageInfo =
                new PdfDocument.PageInfo.Builder(totalW, totalH, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        // ── Background ─────────────────────────────────────────────────────────
        Paint bgPaint = new Paint();
        bgPaint.setColor(Color.WHITE);
        canvas.drawRect(0, 0, totalW, totalH, bgPaint);

        // ── Title ──────────────────────────────────────────────────────────────
        Paint tp = new Paint();
        tp.setTextSize(16f);
        tp.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tp.setColor(Color.parseColor("#1A237E"));
        canvas.drawText(
                "Attendance Report — " + (classSubject.isEmpty() ? classId : classSubject),
                20, 28, tp);

        // ── Subtitle ───────────────────────────────────────────────────────────
        Paint sp = new Paint();
        sp.setTextSize(10f);
        sp.setColor(Color.parseColor("#546E7A"));
        canvas.drawText(
                "Filter: " + chipModeLabel() + "  |  Subject: " + selectedSubject
                        + "  |  Generated: " + todayStr(),
                20, 46, sp);

        // ── Summary ────────────────────────────────────────────────────────────
        int pCnt = 0, aCnt = 0;
        for (Map<String, String> col : colRollStatus.values())
            for (String v : col.values()) { if ("P".equals(v)) pCnt++; else aCnt++; }
        int rate = (pCnt + aCnt) > 0 ? (int) ((pCnt * 100.0) / (pCnt + aCnt)) : 0;
        canvas.drawText(
                "Present: " + pCnt + "  |  Absent: " + aCnt + "  |  Rate: " + rate + "%",
                20, 62, sp);

        // ── Table header row ───────────────────────────────────────────────────
        int tableTop = 76;
        Paint hBg = new Paint(); hBg.setColor(Color.parseColor("#1A237E"));
        canvas.drawRect(20, tableTop, totalW - 20, tableTop + rowH, hBg);

        Paint hTxt = new Paint();
        hTxt.setTextSize(9f);
        hTxt.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        hTxt.setColor(Color.WHITE);

        int xRoll = 24;
        int xName = xRoll + 70;
        int xDates = xName + 150;

        canvas.drawText("Roll No", xRoll, tableTop + 15, hTxt);
        canvas.drawText("Name",    xName, tableTop + 15, hTxt);

        for (int i = 0; i < dates.size(); i++) {
            String key = dates.get(i);
            String label = colLabel.containsKey(key) ? colLabel.get(key) : formatShortDate(key.split("\\|\\|")[0]);
            float cellCenter = xDates + i * dateColW + dateColW / 2f;
            float textW = hTxt.measureText(label);
            canvas.drawText(label, cellCenter - textW / 2f, tableTop + 15, hTxt);
        }

        // ── Data rows ──────────────────────────────────────────────────────────
        Paint cell  = new Paint(); cell.setTextSize(10f);  cell.setColor(Color.parseColor("#212121"));
        Paint pPaint = new Paint(cell); pPaint.setColor(Color.parseColor("#2E7D32")); // green P
        Paint aPaint = new Paint(cell); aPaint.setColor(Color.parseColor("#C62828")); // red A
        Paint bg1 = new Paint(); bg1.setColor(Color.WHITE);
        Paint bg2 = new Paint(); bg2.setColor(Color.parseColor("#F3F6FD"));
        Paint linePaint = new Paint(); linePaint.setColor(Color.parseColor("#E0E0E0")); linePaint.setStrokeWidth(0.5f);

        int y = tableTop + rowH;
        int rowIdx = 0;
        for (String roll : pivotStatus.keySet()) {
            String name = rollToName.getOrDefault(roll, "");
            Map<String, String> dateMap = pivotStatus.get(roll);

            canvas.drawRect(20, y, totalW - 20, y + rowH,
                    rowIdx % 2 == 0 ? bg1 : bg2);
            canvas.drawLine(20, y + rowH, totalW - 20, y + rowH, linePaint);

            canvas.drawText(roll,                      xRoll, y + 15, cell);
            canvas.drawText(trunc(name, 20),           xName, y + 15, cell);

            for (int i = 0; i < dates.size(); i++) {
                String val = dateMap.getOrDefault(dates.get(i), "—");
                Paint valPaint = "P".equals(val) ? pPaint : ("A".equals(val) ? aPaint : cell);
                float cellCenter = xDates + i * dateColW + dateColW / 2f;
                float textW = valPaint.measureText(val);
                canvas.drawText(val, cellCenter - textW / 2f, y + 15, valPaint);
            }

            y += rowH;
            rowIdx++;
        }

        // ── Border around table ────────────────────────────────────────────────
        Paint border = new Paint();
        border.setColor(Color.parseColor("#1A237E"));
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(1f);
        canvas.drawRect(20, tableTop, totalW - 20, y, border);

        document.finishPage(page);
        try {
            File downloads = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS);
            downloads.mkdirs();
            File file = new File(downloads, "AttendanceReport.pdf");
            document.writeTo(new java.io.FileOutputStream(file));
            document.close();
            Toast.makeText(this, "✅ PDF saved to Downloads!", Toast.LENGTH_LONG).show();
            openFileWithIntent(file, "application/pdf");
        } catch (Exception e) {
            Toast.makeText(this, "❌ PDF failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }


    private void exportCSV() {
        if (filteredList.isEmpty()) {
            Toast.makeText(this, "No records to export", Toast.LENGTH_SHORT).show();
            return;
        }
        try {

            java.util.TreeMap<String, String>              rollToName   = new java.util.TreeMap<>();
            java.util.LinkedHashMap<String, java.util.LinkedHashSet<String>> dateSlots =
                    new java.util.LinkedHashMap<>();
            java.util.Map<String, java.util.Map<String, String>> colRollStatus =
                    new java.util.LinkedHashMap<>();

            for (AttendanceRecordModel m : filteredList) {
                String date = normalizeDate(safe(m.getDate()));

                String slot = safe(m.getSessionId());
                if (slot.isEmpty()) slot = safe(m.getTime()); // fallback for legacy records
                String roll = safe(m.getRollNo());
                if (!dateSlots.containsKey(date)) dateSlots.put(date, new java.util.LinkedHashSet<>());
                dateSlots.get(date).add(slot);
                rollToName.put(roll, safe(m.getName()));
            }

            java.util.List<String> colKeys   = new java.util.ArrayList<>();
            java.util.Map<String, String> colLabel = new java.util.LinkedHashMap<>();
            for (String date : dateSlots.keySet()) {
                java.util.LinkedHashSet<String> slots = dateSlots.get(date);
                boolean multi = slots.size() > 1;
                int idx = 1;
                for (String slot : slots) {
                    String key = date + "||" + slot;
                    colKeys.add(key);
                    colLabel.put(key, multi ? (formatShortDate(date) + " L" + idx) : formatShortDate(date));
                    colRollStatus.put(key, new java.util.HashMap<>());
                    idx++;
                }
            }

            // Fill colRollStatus
            for (AttendanceRecordModel m : filteredList) {
                String slot = safe(m.getSessionId());
                if (slot.isEmpty()) slot = safe(m.getTime());
                String key  = normalizeDate(safe(m.getDate())) + "||" + slot;
                String roll = safe(m.getRollNo());
                if (colRollStatus.containsKey(key)) {
                    String ex = colRollStatus.get(key).get(roll);
                    if (ex == null || "A".equals(ex))
                        colRollStatus.get(key).put(roll, m.isPresent() ? "P" : "A");
                }
            }

            File downloads = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS);
            downloads.mkdirs();
            File file = new File(downloads, "AttendanceReport.csv");
            // Use UTF-8 with BOM so Excel opens correctly
            java.io.OutputStream os = new java.io.FileOutputStream(file);
            os.write(new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF}); // UTF-8 BOM
            java.io.PrintWriter w = new java.io.PrintWriter(
                    new java.io.OutputStreamWriter(os, java.nio.charset.StandardCharsets.UTF_8));

            // Header row
            StringBuilder header = new StringBuilder("Roll No,Name");
            for (String key : colKeys) header.append(",").append(csv(colLabel.get(key)));
            w.println(header);

            // Data rows — one per student, A if no record for that date
            for (String roll : rollToName.keySet()) {
                StringBuilder row = new StringBuilder();
                row.append(csv(roll)).append(",").append(csv(rollToName.get(roll)));
                for (String key : colKeys) {
                    String val = colRollStatus.get(key).getOrDefault(roll, "A");
                    row.append(",").append(val);
                }
                w.println(row);
            }
            w.flush(); w.close();
            Toast.makeText(this, "✅ CSV saved to Downloads!", Toast.LENGTH_LONG).show();
            openFileWithIntent(file, "text/csv");
        } catch (Exception e) {
            Toast.makeText(this, "❌ CSV failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openFileWithIntent(File file, String mimeType) {
        try {
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".provider",
                    file);
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeType);
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(android.content.Intent.createChooser(intent, "Open with..."));
        } catch (Exception e) {
            Toast.makeText(this, "📁 File saved in Downloads folder", Toast.LENGTH_LONG).show();
        }
    }
    private Map<String, Map<String, String>> generateTable(List<AttendanceRecordModel> list) {

        Map<String, Map<String, String>> table = new TreeMap<>();

        for (AttendanceRecordModel m : list) {

            String roll = m.getRollNo();
            String date = m.getDate();

            if (!table.containsKey(roll)) {
                table.put(roll, new TreeMap<>());
            }

            table.get(roll).put(date, m.isPresent() ? "P" : "A");
        }

        return table;
    }
    private List<String> getAllDates(List<AttendanceRecordModel> list) {

        Set<String> dates = new TreeSet<>();

        for (AttendanceRecordModel m : list) {
            dates.add(m.getDate());
        }

        return new ArrayList<>(dates);
    }
    private TextView createCell(String text) {

        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setPadding(20, 10, 20, 10);
        tv.setTextSize(12);

        return tv;
    }

    private void showPATable(List<AttendanceRecordModel> list) {

        LinearLayout tableLayout = findViewById(R.id.tableLayout);
        if (tableLayout == null) return;

        tableLayout.removeAllViews();

        if (list.isEmpty()) return;


        java.util.LinkedHashMap<String, java.util.LinkedHashSet<String>> dateSlots = new java.util.LinkedHashMap<>();
        java.util.Map<String, java.util.Map<String, String>> colRollStatus = new java.util.LinkedHashMap<>();


        for (AttendanceRecordModel m : list) {
            String date = normalizeDate(safe(m.getDate()));

            String slot = safe(m.getSessionId());
            if (slot.isEmpty()) slot = safe(m.getTime()); // fallback for legacy records
            if (!dateSlots.containsKey(date)) dateSlots.put(date, new java.util.LinkedHashSet<>());
            dateSlots.get(date).add(slot);
        }

        List<String> colKeys = new ArrayList<>(); // key = date + "||" + slot
        java.util.Map<String, String> colLabel = new java.util.LinkedHashMap<>(); // key → display label

        for (String date : dateSlots.keySet()) {
            java.util.LinkedHashSet<String> slots = dateSlots.get(date);
            String shortDate = formatShortDate(date);
            boolean multiSlot = slots.size() > 1;
            int idx = 1;
            for (String slot : slots) {
                String key = date + "||" + slot;
                colKeys.add(key);
                if (multiSlot) {
                    colLabel.put(key, shortDate + "\nL" + idx);
                } else {
                    colLabel.put(key, shortDate);
                }
                colRollStatus.put(key, new java.util.HashMap<>());
                idx++;
            }
        }

        for (AttendanceRecordModel m : list) {
            String date = normalizeDate(safe(m.getDate()));
            String slot = safe(m.getSessionId());
            if (slot.isEmpty()) slot = safe(m.getTime());
            String key = date + "||" + slot;
            String roll = safe(m.getRollNo());
            if (colRollStatus.containsKey(key)) {
                // Prefer P over A
                String existing = colRollStatus.get(key).get(roll);
                if (existing == null || "A".equals(existing)) {
                    colRollStatus.get(key).put(roll, m.isPresent() ? "P" : "A");
                }
            }
        }

        java.util.TreeMap<String, String> rollToName = new java.util.TreeMap<>();
        for (AttendanceRecordModel m : list) {
            String roll = safe(m.getRollNo());
            if (!rollToName.containsKey(roll)) {
                rollToName.put(roll, safe(m.getName()));
            }
        }

        tvRecordCount.setText(rollToName.size() + " students");

        // ── Step 4: Dimensions ────────────────────────────────────────────────
        int rollColDp  = 70;
        int nameColDp  = 140;
        int dateColDp  = 52;
        float density = getResources().getDisplayMetrics().density;
        int rollColPx  = (int)(rollColDp * density);
        int nameColPx  = (int)(nameColDp * density);
        int dateColPx  = (int)(dateColDp * density);
        int rowHpx     = (int)(40 * density);

        // ── Step 5: HEADER ROW ────────────────────────────────────────────────
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setBackgroundColor(android.graphics.Color.parseColor("#1A237E"));
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);

        header.addView(makePivotHeaderCell("Roll No", rollColPx, rowHpx));
        header.addView(makePivotHeaderCell("Name",    nameColPx, rowHpx));

        for (String key : colKeys) {
            header.addView(makePivotHeaderCell(colLabel.get(key), dateColPx, rowHpx));
        }
        tableLayout.addView(header);

        // ── Step 6: DATA ROWS ─────────────────────────────────────────────────
        int rowIdx = 0;
        for (String roll : rollToName.keySet()) {
            String name = rollToName.get(roll);

            // Apply search filter
            if (!searchQuery.isEmpty()) {
                String rollL = roll.toLowerCase(Locale.getDefault());
                String nameL = name.toLowerCase(Locale.getDefault());
                if (!rollL.contains(searchQuery) && !nameL.contains(searchQuery)) continue;
            }

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            int rowBg = rowIdx % 2 == 0
                    ? android.graphics.Color.WHITE
                    : android.graphics.Color.parseColor("#F3F6FD");
            row.setBackgroundColor(rowBg);

            row.addView(makePivotTextCell(roll, rollColPx, rowHpx, "#212121", false));
            row.addView(makePivotTextCell(name, nameColPx, rowHpx, "#212121", false));

            for (String key : colKeys) {
                String val = colRollStatus.get(key).getOrDefault(roll, "A");
                String color;
                if ("P".equals(val))  color = "#2E7D32";  // green
                else                  color = "#C62828";  // red for A (absent or no record)
                row.addView(makePivotTextCell(val, dateColPx, rowHpx, color, true));
            }

            tableLayout.addView(row);

            // Divider
            View div = new View(this);
            div.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1));
            div.setBackgroundColor(android.graphics.Color.parseColor("#E0E0E0"));
            tableLayout.addView(div);

            rowIdx++;
        }
    }

    private android.view.View makePivotHeaderCell(String text, int widthPx, int heightPx) {
        TextView tv = new TextView(this);
        android.widget.LinearLayout.LayoutParams lp =
                new android.widget.LinearLayout.LayoutParams(widthPx, heightPx);
        tv.setLayoutParams(lp);
        tv.setText(text);
        tv.setTextColor(android.graphics.Color.WHITE);
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setPadding(8, 4, 8, 4);
        return tv;
    }

    private android.view.View makePivotTextCell(String text, int widthPx, int heightPx,
                                                String hexColor, boolean bold) {
        TextView tv = new TextView(this);
        android.widget.LinearLayout.LayoutParams lp =
                new android.widget.LinearLayout.LayoutParams(widthPx, heightPx);
        tv.setLayoutParams(lp);
        tv.setText(text);
        tv.setTextColor(android.graphics.Color.parseColor(hexColor));
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
        if (bold) tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setPadding(6, 2, 6, 2);
        return tv;
    }

    private String formatShortDate(String date) {
        // "yyyy-MM-dd" → "dd-MMM"
        try {
            String[] p = date.split("-");
            if (p.length == 3) {
                String[] months = {"","Jan","Feb","Mar","Apr","May","Jun",
                        "Jul","Aug","Sep","Oct","Nov","Dec"};
                int mo = Integer.parseInt(p[1]);
                return p[2] + "-" + (mo >= 1 && mo <= 12 ? months[mo] : p[1]);
            }
        } catch (Exception ignored) {}
        return date;
    }


    private String normalizeDate(String date) {
        if (date == null || date.trim().isEmpty()) return "";
        date = date.trim();
        if (date.matches("\\d{4}-\\d{2}-\\d{2}")) return date;
        SimpleDateFormat out = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        out.setTimeZone(java.util.TimeZone.getDefault());
        // Try dd/MM/yyyy
        try { SimpleDateFormat f = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()); f.setLenient(false); return out.format(f.parse(date)); } catch (Exception ignored) {}
        // Try yyyy/MM/dd
        try { SimpleDateFormat f = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()); f.setLenient(false); return out.format(f.parse(date)); } catch (Exception ignored) {}
        // Try MM/dd/yyyy
        try { SimpleDateFormat f = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()); f.setLenient(false); return out.format(f.parse(date)); } catch (Exception ignored) {}
        // Try d-MMM-yyyy  e.g. 5-Apr-2026
        try { SimpleDateFormat f = new SimpleDateFormat("d-MMM-yyyy", Locale.US); f.setLenient(false); return out.format(f.parse(date)); } catch (Exception ignored) {}
        // Try dd-MM-yyyy
        try { SimpleDateFormat f = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()); f.setLenient(false); return out.format(f.parse(date)); } catch (Exception ignored) {}
        // Try d/M/yyyy (single-digit day/month)
        try { SimpleDateFormat f = new SimpleDateFormat("d/M/yyyy", Locale.getDefault()); f.setLenient(false); return out.format(f.parse(date)); } catch (Exception ignored) {}
        return date;
    }

    private boolean empty(String s)          { return s == null || s.trim().isEmpty(); }
    private String  safe(String s)           { return s != null ? s : ""; }
    private String  csv(String s)            { return "\"" + safe(s).replace("\"", "\"\"") + "\""; }
    private String  trunc(String s, int max) { return s.length() > max ? s.substring(0, max) + "…" : s; }
}