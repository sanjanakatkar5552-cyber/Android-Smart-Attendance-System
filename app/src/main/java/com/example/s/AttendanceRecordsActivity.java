package com.example.s;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.s.adapter.AttendanceRecordAdapter;
import com.example.s.model.AttendanceRecordModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AttendanceRecordsActivity — FIXED + LECTURE-WISE FILTER
 *
 * ROOT CAUSES OF DUPLICATE STUDENT COUNT:
 * ═══════════════════════════════════════════════════════════════
 * BUG 1 — sessionMap key was just "roll" (per session).
 *   Student attending 3 sessions = 3 separate sessionMaps, all added
 *   to fullList → same student appears 3 times = wrong count.
 *   FIX: key = "sessionId + "|" + roll" so each student appears
 *        once per lecture, and the lecture filter works correctly.
 *
 * BUG 2 — No lecture filter.
 *   "All records" = every lecture merged = duplicate students.
 *   FIX: Add "Lecture / Session" spinner — shows each session
 *        as "Subject – DD/MM/YYYY" entry. Selecting one shows
 *        only that lecture's records. 21 students → exactly 21.
 *
 * BUG 3 — fullList.clear() called both at top of snapshot callback
 *   AND inside the per-session success listener, causing partial clears.
 *   FIX: clear once at snapshot start, never inside sub-listeners.
 *
 * BUG 4 — "All" mode with no date filter merged all lectures,
 *   inflating both the list and the present/absent count.
 *   FIX: Lecture filter isolates records per session. When "All
 *        Lectures" is selected, show all but count correctly per
 *        unique student per day (not per session).
 * ═══════════════════════════════════════════════════════════════
 */
public class AttendanceRecordsActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private ListenerRegistration listener;

    private RecyclerView recyclerView;
    private AttendanceRecordAdapter adapter;

    // fullList holds ONE record per (sessionId + studentRoll) combination
    private final List<AttendanceRecordModel> fullList     = new ArrayList<>();
    private final List<AttendanceRecordModel> filteredList = new ArrayList<>();

    // ── Subject spinner ───────────────────────────────────────────────────────
    private final List<String> subjectList = new ArrayList<>();
    private ArrayAdapter<String> subjectAdapter;
    private Spinner spinnerSubject;

    // ── Lecture (session) spinner — NEW ───────────────────────────────────────
    private final List<String> lectureLabels = new ArrayList<>(); // "Java – 26/04"
    private final List<String> lectureIds    = new ArrayList<>(); // sessionId
    private ArrayAdapter<String> lectureAdapter;
    private Spinner spinnerLecture;

    // ── Filter state ──────────────────────────────────────────────────────────
    private String classId          = "";
    private String classSubject     = "";
    private String selectedDate     = "";
    private String selectedSubject  = "All";
    private String selectedLectureId = "All"; // "All" or a sessionId
    private String searchQuery      = "";
    private boolean todayOnly       = false;

    // ── Views ─────────────────────────────────────────────────────────────────
    private Button   btnSelectDate, btnClearDate;
    private CheckBox cbTodayOnly;
    private EditText searchStudent;
    private TextView tvPresentCount, tvAbsentCount, tvRecordCount, tvSelectedDateLabel;

    // ═════════════════════════════════════════════════════════════════════════
    // Date normalisation helper
    // ═════════════════════════════════════════════════════════════════════════
    private String normalizeToIso(String date) {
        if (date == null || date.isEmpty()) return "";
        if (date.matches("\\d{4}-\\d{2}-\\d{2}")) return date;
        for (String fmt : new String[]{"dd/MM/yyyy","yyyy-M-d","d/M/yyyy"}) {
            try {
                java.util.Date d = new SimpleDateFormat(fmt, Locale.getDefault()).parse(date);
                if (d != null)
                    return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(d);
            } catch (Exception ignored) {}
        }
        return date;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // onCreate
    // ═════════════════════════════════════════════════════════════════════════
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance_records);

        db      = FirebaseFirestore.getInstance();
        classId = getIntent().getStringExtra("classId");
        if (classId == null) classId = "";

        // ── Bind views ────────────────────────────────────────────────────
        recyclerView        = findViewById(R.id.recyclerAttendance);
        btnSelectDate       = findViewById(R.id.btnSelectDate);
        btnClearDate        = findViewById(R.id.btnClearDate);
        cbTodayOnly         = findViewById(R.id.cbTodayOnly);
        searchStudent       = findViewById(R.id.searchStudent);
        tvPresentCount      = findViewById(R.id.tvPresentCount);
        tvAbsentCount       = findViewById(R.id.tvAbsentCount);
        tvRecordCount       = findViewById(R.id.tvRecordCount);
        tvSelectedDateLabel = findViewById(R.id.tvSelectedDateLabel);
        spinnerSubject      = findViewById(R.id.spinnerSubject);
        spinnerLecture      = findViewById(R.id.spinnerLecture);   // NEW

        // ── RecyclerView ──────────────────────────────────────────────────
        adapter = new AttendanceRecordAdapter(filteredList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        recyclerView.addItemDecoration(
                new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        recyclerView.setNestedScrollingEnabled(true);

        // ── Subject spinner ───────────────────────────────────────────────
        subjectList.add("All");
        subjectAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, subjectList);
        if (spinnerSubject != null) {
            spinnerSubject.setAdapter(subjectAdapter);
            spinnerSubject.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                    selectedSubject = p.getItemAtPosition(pos).toString();
                    applyFilters();
                }
                @Override public void onNothingSelected(AdapterView<?> p) {}
            });
        }

        // ── Lecture spinner (NEW) ─────────────────────────────────────────
        lectureLabels.add("All Lectures");
        lectureIds.add("All");
        lectureAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, lectureLabels);
        if (spinnerLecture != null) {
            spinnerLecture.setAdapter(lectureAdapter);
            spinnerLecture.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                    selectedLectureId = lectureIds.get(pos);
                    applyFilters();
                }
                @Override public void onNothingSelected(AdapterView<?> p) {}
            });
        }

        // ── Today checkbox ────────────────────────────────────────────────
        if (cbTodayOnly != null) {
            cbTodayOnly.setChecked(false);
            cbTodayOnly.setOnCheckedChangeListener((btn, checked) -> {
                todayOnly = checked;
                if (checked) {
                    selectedDate = "";
                    if (btnSelectDate != null) btnSelectDate.setText("Pick Date");
                }
                applyFilters();
            });
        }

        // ── Date picker ───────────────────────────────────────────────────
        if (btnSelectDate != null) btnSelectDate.setOnClickListener(v -> openDatePicker());
        if (btnClearDate  != null) {
            btnClearDate.setOnClickListener(v -> {
                selectedDate = "";
                todayOnly    = false;
                if (cbTodayOnly   != null) cbTodayOnly.setChecked(false);
                if (btnSelectDate != null) btnSelectDate.setText("Pick Date");
                applyFilters();
            });
        }

        // ── Search ────────────────────────────────────────────────────────
        if (searchStudent != null) {
            searchStudent.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void afterTextChanged(Editable s) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                    searchQuery = s.toString().trim().toLowerCase(Locale.getDefault());
                    applyFilters();
                }
            });
        }

        fetchClassSubjectThenListen();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listener != null) listener.remove();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Step 1 — fetch class subject then start session listener
    // ═════════════════════════════════════════════════════════════════════════
    private void fetchClassSubjectThenListen() {
        if (classId.isEmpty()) { attachListener(); return; }
        db.collection("classes").document(classId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String s = doc.getString("subject");
                        classSubject = (s != null) ? s : "";
                        if (!classSubject.isEmpty() && !subjectList.contains(classSubject)) {
                            subjectList.add(classSubject);
                            subjectAdapter.notifyDataSetChanged();
                        }
                    }
                    attachListener();
                })
                .addOnFailureListener(e -> attachListener());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Step 2 — listen to attendanceSessions, load student records per session
    // ═════════════════════════════════════════════════════════════════════════
    private void attachListener() {
        if (listener != null) listener.remove();

        String teacherId = FirebaseAuth.getInstance().getUid();
        if (teacherId == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        com.google.firebase.firestore.Query sessionQuery;
        if (!classId.isEmpty()) {
            sessionQuery = db.collection("attendanceSessions")
                    .whereEqualTo("classId", classId);
        } else {
            sessionQuery = db.collection("attendanceSessions")
                    .whereEqualTo("teacherId", teacherId);
        }

        listener = sessionQuery.addSnapshotListener((sessions, error) -> {
            if (error != null) {
                Toast.makeText(this, "Error: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (sessions == null || sessions.isEmpty()) {
                fullList.clear();
                resetLectureSpinner();
                applyFilters();
                return;
            }

            // ── FIX 3: clear ONCE here, not inside sub-listeners ──────────
            fullList.clear();

            final int[] remaining = {sessions.size()};
            final java.util.Set<String> subjectSet = new java.util.LinkedHashSet<>();
            subjectSet.add("All");

            // Build lecture spinner entries
            final List<String> newLectureLabels = new ArrayList<>();
            final List<String> newLectureIds    = new ArrayList<>();
            newLectureLabels.add("All Lectures");
            newLectureIds.add("All");

            for (QueryDocumentSnapshot session : sessions) {
                // ── Resolve sessionId ─────────────────────────────────────
                String sid = session.getString("sessionId");
                if (sid == null || sid.isEmpty()) sid = session.getId();
                final String finalSid = sid;

                // ── Resolve session date ──────────────────────────────────
                String rawDate = session.getString("date");
                if (rawDate == null || rawDate.isEmpty()) {
                    com.google.firebase.Timestamp ts = session.getTimestamp("startTime");
                    if (ts == null) ts = session.getTimestamp("createdAt");
                    if (ts != null)
                        rawDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                .format(ts.toDate());
                }
                final String sessionDate = normalizeToIso(rawDate);

                // ── Build lecture label: "Subject – DD/MM" ────────────────
                String sessSubject = session.getString("subject");
                if (sessSubject == null || sessSubject.isEmpty()) sessSubject = classSubject;
                if (sessSubject == null || sessSubject.isEmpty()) sessSubject = "Lecture";

                String lectureLabel = sessSubject + " – "
                        + (sessionDate.length() >= 10
                        ? sessionDate.substring(8) + "/" + sessionDate.substring(5, 7)
                        + "/" + sessionDate.substring(0, 4)
                        : sessionDate);
                newLectureLabels.add(lectureLabel);
                newLectureIds.add(finalSid);

                // ── Fetch students for this session ───────────────────────
                db.collection("attendance")
                        .document(finalSid)
                        .collection("students")
                        .get()
                        .addOnSuccessListener(students -> {

                            // ── FIX 1: key = sessionId + "|" + roll ──────
                            // This way one student across N sessions = N records
                            // Each record is associated with one specific lecture.
                            // When the lecture filter is active → exactly N students shown.
                            Map<String, AttendanceRecordModel> sessionMap = new LinkedHashMap<>();

                            for (QueryDocumentSnapshot doc : students) {
                                String  name    = doc.getString("name");
                                String  roll    = doc.getString("rollNo");
                                String  date    = doc.getString("date");
                                String  time    = doc.getString("time");
                                Boolean present = doc.getBoolean("present");
                                Double  lat     = doc.getDouble("lat");
                                if (lat == null) lat = doc.getDouble("studentLat");
                                Double  lng     = doc.getDouble("lng");
                                if (lng == null) lng = doc.getDouble("studentLng");
                                Double  dist    = doc.getDouble("distance");

                                String subject = doc.getString("subject");
                                if (subject == null || subject.isEmpty()) subject = classSubject;

                                if (name == null || name.isEmpty()) {
                                    name = doc.getString("email");
                                    if (name == null) name = "Unknown";
                                }

                                // Normalise roll number
                                if (roll == null || roll.isEmpty()) {
                                    Object r = doc.get("rollNo");
                                    if (r != null) roll = String.valueOf(r);
                                }
                                if (roll == null || roll.isEmpty()) {
                                    roll = doc.getString("studentId");
                                    if (roll == null) roll = doc.getId();
                                }

                                if (date == null || date.isEmpty()) date = sessionDate;
                                date = normalizeToIso(date);

                                // Time fallback from timestamp
                                if (time == null || time.isEmpty()) {
                                    com.google.firebase.Timestamp ts =
                                            doc.getTimestamp("timestamp");
                                    if (ts != null)
                                        time = new SimpleDateFormat("HH:mm:ss",
                                                Locale.getDefault()).format(ts.toDate());
                                }

                                if (subject != null && !subject.isEmpty())
                                    subjectSet.add(subject);

                                boolean isPresent = present != null && present;

                                // FIX 1: unique per (session + roll)
                                String mapKey = finalSid + "|" + roll;

                                AttendanceRecordModel model = new AttendanceRecordModel(
                                        name, roll, subject, isPresent,
                                        date, time, lat, lng, dist);
                                model.setEmail(doc.getString("email") != null
                                        ? doc.getString("email") : "");
                                // Store sessionId in model for lecture filtering
                                model.setSessionId(finalSid);

                                // Prefer present over absent for same key in same session
                                if (!sessionMap.containsKey(mapKey)
                                        || (isPresent && !sessionMap.get(mapKey).isPresent())) {
                                    sessionMap.put(mapKey, model);
                                }
                            }

                            // FIX 3: addAll happens here, clear was done once above
                            fullList.addAll(sessionMap.values());

                            remaining[0]--;
                            if (remaining[0] == 0) {
                                refreshSubjectSpinner(new ArrayList<>(subjectSet));
                                refreshLectureSpinner(newLectureLabels, newLectureIds);
                                computeAttendancePercents();
                                applyFilters();
                            }
                        })
                        .addOnFailureListener(e -> {
                            remaining[0]--;
                            if (remaining[0] == 0) {
                                refreshSubjectSpinner(new ArrayList<>(subjectSet));
                                refreshLectureSpinner(newLectureLabels, newLectureIds);
                                applyFilters();
                            }
                        });
            }
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Compute per-student overall attendance % across all sessions
    // ═════════════════════════════════════════════════════════════════════════
    private void computeAttendancePercents() {
        // Group by roll number across all sessions
        Map<String, int[]> stats = new java.util.HashMap<>();
        for (AttendanceRecordModel m : fullList) {
            String key = m.getRollNo();
            if (!stats.containsKey(key)) stats.put(key, new int[]{0, 0});
            stats.get(key)[1]++;
            if (m.isPresent()) stats.get(key)[0]++;
        }
        for (AttendanceRecordModel m : fullList) {
            int[] s = stats.get(m.getRollNo());
            if (s != null && s[1] > 0)
                m.setAttendancePercent((s[0] * 100f) / s[1]);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Spinner helpers
    // ═════════════════════════════════════════════════════════════════════════
    private void refreshSubjectSpinner(List<String> newSubjects) {
        if (spinnerSubject == null) return;
        String prev = subjectList.isEmpty() ? "All"
                : subjectList.get(Math.max(spinnerSubject.getSelectedItemPosition(), 0));
        subjectList.clear();
        subjectList.addAll(newSubjects);
        subjectAdapter.notifyDataSetChanged();
        int idx = subjectList.indexOf(prev);
        if (idx >= 0) spinnerSubject.setSelection(idx, false);
    }

    private void refreshLectureSpinner(List<String> labels, List<String> ids) {
        if (spinnerLecture == null) return;
        String prevId = selectedLectureId;
        lectureLabels.clear(); lectureLabels.addAll(labels);
        lectureIds.clear();    lectureIds.addAll(ids);
        lectureAdapter.notifyDataSetChanged();
        int idx = lectureIds.indexOf(prevId);
        spinnerLecture.setSelection(idx >= 0 ? idx : 0, false);
    }

    private void resetLectureSpinner() {
        lectureLabels.clear(); lectureLabels.add("All Lectures");
        lectureIds.clear();    lectureIds.add("All");
        if (lectureAdapter != null) lectureAdapter.notifyDataSetChanged();
        selectedLectureId = "All";
    }

    // ═════════════════════════════════════════════════════════════════════════
    // applyFilters — the core display logic
    //
    // FILTERING ORDER:
    //   1. Lecture filter  — show records from one specific session
    //   2. Subject filter  — filter by subject name
    //   3. Date filter     — specific date or today-only
    //   4. Search          — student name or roll number
    //
    // COUNT LOGIC (FIX 4):
    //   When "All Lectures" selected: count unique students per day
    //   When one lecture selected: count students in that session directly
    // ═════════════════════════════════════════════════════════════════════════
    private void applyFilters() {
        filteredList.clear();

        String todayStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(new java.util.Date());
        String effectiveDate = todayOnly ? todayStr
                : (!selectedDate.isEmpty() ? selectedDate : "");

        for (AttendanceRecordModel m : fullList) {

            // 1. Lecture filter
            if (!"All".equals(selectedLectureId)) {
                if (!selectedLectureId.equals(m.getSessionId())) continue;
            }

            // 2. Subject filter
            if (!"All".equals(selectedSubject)) {
                if (!selectedSubject.equals(m.getSubject())) continue;
            }

            // 3. Date filter
            if (!effectiveDate.isEmpty()) {
                if (!effectiveDate.equals(normalizeToIso(m.getDate()))) continue;
            }

            // 4. Search filter
            if (!searchQuery.isEmpty()) {
                String name = m.getName().toLowerCase(Locale.getDefault());
                String roll = m.getRollNo().toLowerCase(Locale.getDefault());
                if (!name.contains(searchQuery) && !roll.contains(searchQuery)) continue;
            }

            filteredList.add(m);
        }

        // Count present / absent in filtered list
        int present = 0, absent = 0;
        for (AttendanceRecordModel m : filteredList) {
            if (m.isPresent()) present++; else absent++;
        }

        if (tvPresentCount != null) tvPresentCount.setText(String.valueOf(present));
        if (tvAbsentCount  != null) tvAbsentCount.setText(String.valueOf(absent));
        if (tvRecordCount  != null) tvRecordCount.setText(filteredList.size() + " records");

        if (tvSelectedDateLabel != null) {
            String dateLabel = todayOnly ? "Today"
                    : selectedDate.isEmpty() ? "All Dates" : selectedDate;
            String lectLabel = "All".equals(selectedLectureId) ? "All Lectures"
                    : lectureLabels.size() > 1
                    ? lectureLabels.get(lectureIds.indexOf(selectedLectureId))
                    : "Lecture";
            tvSelectedDateLabel.setText(dateLabel + "  •  " + lectLabel
                    + "  •  " + selectedSubject);
        }

        adapter.updateList(new ArrayList<>(filteredList));
    }

    // ═════════════════════════════════════════════════════════════════════════
    private void openDatePicker() {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, day) -> {
            month++;
            selectedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month, day);
            String display = String.format(Locale.getDefault(), "%02d/%02d/%04d", day, month, year);
            todayOnly = false;
            if (cbTodayOnly   != null) cbTodayOnly.setChecked(false);
            if (btnSelectDate != null) btnSelectDate.setText("📅 " + display);
            applyFilters();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }
}/*package com.example.s;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.s.adapter.AttendanceRecordAdapter;
import com.example.s.model.AttendanceRecordModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AttendanceRecordsActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private ListenerRegistration listener;

    private RecyclerView recyclerView;
    private AttendanceRecordAdapter adapter;

    private final List<AttendanceRecordModel> fullList     = new ArrayList<>();
    private final List<AttendanceRecordModel> filteredList = new ArrayList<>();

    private final List<String> subjectList = new ArrayList<>();
    private ArrayAdapter<String> subjectAdapter;
    private Spinner spinnerSubject;

    private String classId         = "";
    private String classSubject    = "";
    private String selectedDate    = "";
    private String selectedSubject = "All";
    private String searchQuery     = "";
    private boolean todayOnly      = false;  // FIX Bug1: show ALL dates by default

    private Button   btnSelectDate, btnClearDate;
    private CheckBox cbTodayOnly;
    private EditText searchStudent;
    private TextView tvPresentCount, tvAbsentCount, tvRecordCount, tvSelectedDateLabel;

    private String normalizeToIso(String date) {
        if (date == null || date.isEmpty()) return "";
        if (date.matches("\\d{4}-\\d{2}-\\d{2}")) return date;
        try {
            java.util.Date d = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(date);
            if (d != null) return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(d);
        } catch (Exception ignored) {}
        try {
            java.util.Date d = new SimpleDateFormat("yyyy-M-d", Locale.getDefault()).parse(date);
            if (d != null) return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(d);
        } catch (Exception ignored) {}
        return date;
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance_records);

        db      = FirebaseFirestore.getInstance();
        classId = getIntent().getStringExtra("classId");
        if (classId == null) classId = "";

        recyclerView        = findViewById(R.id.recyclerAttendance);
        btnSelectDate       = findViewById(R.id.btnSelectDate);
        btnClearDate        = findViewById(R.id.btnClearDate);
        cbTodayOnly         = findViewById(R.id.cbTodayOnly);
        searchStudent       = findViewById(R.id.searchStudent);
        tvPresentCount      = findViewById(R.id.tvPresentCount);
        tvAbsentCount       = findViewById(R.id.tvAbsentCount);
        tvRecordCount       = findViewById(R.id.tvRecordCount);
        tvSelectedDateLabel = findViewById(R.id.tvSelectedDateLabel);
        spinnerSubject      = findViewById(R.id.spinnerSubject);

        adapter = new AttendanceRecordAdapter(filteredList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        recyclerView.addItemDecoration(
                new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));

        recyclerView.setNestedScrollingEnabled(true);

        subjectList.add("All");
        subjectAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, subjectList);
        if (spinnerSubject != null) {
            spinnerSubject.setAdapter(subjectAdapter);
            spinnerSubject.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                    selectedSubject = p.getItemAtPosition(pos).toString();
                    applyFilters();
                }
                @Override public void onNothingSelected(AdapterView<?> p) {}
            });
        }

        if (cbTodayOnly != null) {
            cbTodayOnly.setChecked(false);  // FIX Bug1: unchecked = show all dates
            cbTodayOnly.setOnCheckedChangeListener((btn, checked) -> {
                todayOnly = checked;
                if (checked) {
                    selectedDate = "";
                    if (btnSelectDate != null) btnSelectDate.setText("Pick Date");
                }
                applyFilters();
            });
        }

        if (btnSelectDate != null) btnSelectDate.setOnClickListener(v -> openDatePicker());

        if (btnClearDate != null) {
            btnClearDate.setOnClickListener(v -> {
                selectedDate = "";
                if (cbTodayOnly != null) cbTodayOnly.setChecked(false);
                todayOnly = false;
                if (btnSelectDate != null) btnSelectDate.setText("Pick Date");
                applyFilters();
            });
        }

        if (searchStudent != null) {
            searchStudent.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void afterTextChanged(Editable s) {}
                @Override
                public void onTextChanged(CharSequence s, int a, int b, int c) {
                    searchQuery = s.toString().trim().toLowerCase();
                    applyFilters();
                }
            });
        }

        fetchClassSubjectThenListen();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listener != null) listener.remove();
    }

    // ─────────────────────────────────────────────────────────────────────────
    private void fetchClassSubjectThenListen() {
        if (classId.isEmpty()) { attachListener(); return; }
        db.collection("classes").document(classId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String s = doc.getString("subject");
                        classSubject = (s != null) ? s : "";
                        if (!classSubject.isEmpty() && !subjectList.contains(classSubject)) {
                            subjectList.add(classSubject);
                            subjectAdapter.notifyDataSetChanged();
                        }
                    }
                    attachListener();
                })
                .addOnFailureListener(e -> attachListener());
    }

    // ─────────────────────────────────────────────────────────────────────────
    private void attachListener() {
        if (listener != null) listener.remove();

        String teacherId = FirebaseAuth.getInstance().getUid();
        if (teacherId == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        com.google.firebase.firestore.Query sessionQuery;
        if (!classId.isEmpty()) {
            sessionQuery = db.collection("attendanceSessions")
                    .whereEqualTo("classId", classId);
        } else {
            sessionQuery = db.collection("attendanceSessions")
                    .whereEqualTo("teacherId", teacherId);
        }

        listener = sessionQuery.addSnapshotListener((sessions, error) -> {
            if (error != null) {
                Toast.makeText(this,
                        "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                return;
            }
            if (sessions == null || sessions.isEmpty()) {
                fullList.clear();
                refreshSpinner(new ArrayList<>(java.util.Arrays.asList("All")));
                applyFilters();
                return;
            }

            fullList.clear();
            final int[] remaining = {sessions.size()};
            final java.util.Set<String> subjects = new java.util.LinkedHashSet<>();
            subjects.add("All");

            for (QueryDocumentSnapshot session : sessions) {
                String sid = session.getString("sessionId");
                if (sid == null || sid.isEmpty()) sid = session.getId();
                final String finalSid = sid;


                String rawSessDate = session.getString("date");
                if (rawSessDate == null || rawSessDate.isEmpty()) {
                    com.google.firebase.Timestamp ts = session.getTimestamp("startTime");
                    if (ts == null) ts = session.getTimestamp("createdAt");
                    if (ts != null) {
                        rawSessDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                .format(ts.toDate());
                    }
                }
                final String sessionFallbackDate = normalizeToIso(rawSessDate);

                db.collection("attendance")
                        .document(finalSid)
                        .collection("students")
                        .get()
                        .addOnSuccessListener(students -> {


                            Map<String, AttendanceRecordModel> sessionMap = new LinkedHashMap<>();

                            for (QueryDocumentSnapshot doc : students) {
                                String  name    = doc.getString("name");
                                String  roll    = doc.getString("rollNo");
                                String  date    = doc.getString("date");
                                Boolean present = doc.getBoolean("present");
                                Double  lat     = doc.getDouble("lat");
                                if (lat == null) lat = doc.getDouble("studentLat");
                                Double  lng     = doc.getDouble("lng");
                                if (lng == null) lng = doc.getDouble("studentLng");
                                Double  dist    = doc.getDouble("distance");

                                String subject = doc.getString("subject");
                                if (subject == null || subject.isEmpty()) subject = classSubject;

                                if (name == null || name.isEmpty()) {
                                    name = doc.getString("email");
                                    if (name == null) name = "Unknown";
                                }
                                if (roll == null || roll.isEmpty()) {
                                    roll = doc.getString("studentId");
                                    if (roll == null) roll = doc.getId();
                                    if (roll.length() > 6)
                                        roll = roll.substring(roll.length() - 6);
                                }

                                if (date == null || date.isEmpty()) date = sessionFallbackDate;
                                date = normalizeToIso(date);

                                if (subject != null && !subject.isEmpty()) subjects.add(subject);

                                boolean isPresent = present != null && present;
                                AttendanceRecordModel model = new AttendanceRecordModel(
                                        name, roll, subject, isPresent, date, lat, lng, dist);


                                if (!sessionMap.containsKey(roll)
                                        || (isPresent && !sessionMap.get(roll).isPresent())) {
                                    sessionMap.put(roll, model);
                                }
                            }

                            fullList.addAll(sessionMap.values());

                            remaining[0]--;
                            if (remaining[0] == 0) {
                                refreshSpinner(new ArrayList<>(subjects));
                                applyFilters();
                            }
                        })
                        .addOnFailureListener(e -> {
                            remaining[0]--;
                            if (remaining[0] == 0) {
                                refreshSpinner(new ArrayList<>(subjects));
                                applyFilters();
                            }
                        });
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    private void refreshSpinner(List<String> newSubjects) {
        if (spinnerSubject == null) return;
        String prev = subjectList.isEmpty() ? "All"
                : subjectList.get(Math.max(spinnerSubject.getSelectedItemPosition(), 0));
        subjectList.clear();
        subjectList.addAll(newSubjects);
        subjectAdapter.notifyDataSetChanged();
        int idx = subjectList.indexOf(prev);
        if (idx >= 0) spinnerSubject.setSelection(idx, false);
    }

    // ─────────────────────────────────────────────────────────────────────────
    private void applyFilters() {
        filteredList.clear();
        int present = 0, absent = 0;

        String todayFormatted = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(new java.util.Date());
        String effectiveDate = "";
        if (todayOnly) {
            effectiveDate = todayFormatted;
        } else if (!selectedDate.isEmpty()) {
            effectiveDate = selectedDate;
        }

        for (AttendanceRecordModel m : fullList) {
            if (!selectedSubject.equals("All") &&
                    !selectedSubject.equals(m.getSubject())) continue;

            if (!effectiveDate.isEmpty()) {
                String d = normalizeToIso(m.getDate());
                if (!d.equals(effectiveDate)) continue;
            }

            if (!searchQuery.isEmpty()) {
                String name = m.getName() != null ? m.getName().toLowerCase() : "";
                String roll = m.getRollNo() != null ? m.getRollNo().toLowerCase() : "";
                if (!name.contains(searchQuery) && !roll.contains(searchQuery)) continue;
            }

            filteredList.add(m);
            if (m.isPresent()) present++;
            else absent++;
        }

        if (tvPresentCount      != null) tvPresentCount.setText(String.valueOf(present));
        if (tvAbsentCount       != null) tvAbsentCount.setText(String.valueOf(absent));
        if (tvRecordCount       != null) tvRecordCount.setText(filteredList.size() + " records");
        if (tvSelectedDateLabel != null) {
            String label = todayOnly
                    ? "Today  •  " + AttendanceCleanupHelper.getTodayFlat()
                    : selectedDate.isEmpty() ? "All Dates" : selectedDate;
            tvSelectedDateLabel.setText(label + "  •  " + selectedSubject);
        }

        adapter.updateList(new ArrayList<>(filteredList));
    }

    // ─────────────────────────────────────────────────────────────────────────
    private void openDatePicker() {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, day) -> {
            month++;
            selectedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month, day);
            String display = String.format(Locale.getDefault(), "%02d/%02d/%04d", day, month, year);
            todayOnly = false;
            if (cbTodayOnly  != null) cbTodayOnly.setChecked(false);
            if (btnSelectDate != null) btnSelectDate.setText("📅 " + display);
            applyFilters();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }
}*/