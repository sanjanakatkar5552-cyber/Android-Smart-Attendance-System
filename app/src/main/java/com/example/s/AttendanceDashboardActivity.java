package com.example.s;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.*;

public class AttendanceDashboardActivity extends AppCompatActivity {

    CircularProgressIndicator attendanceProgress;

    TextView tvAttendanceScore;
    TextView tvPresent;
    TextView tvAbsent;
    TextView tvStreak;
    TextView tvSubjectBreakdown;
    TextView tvGoalMessage;
    TextView tvSelectedDate;

    PieChart pieChart;
    LineChart weeklyChart;

    LinearLayout subjectContainer;

    Button btnSelectDate;

    FirebaseFirestore db;

    int present = 0;
    int absent = 0;
    int late = 0;

    String selectedDate = null;

    Map<String,String> classNames = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance_dashboard);

        db = FirebaseFirestore.getInstance();

        attendanceProgress = findViewById(R.id.attendanceProgress);

        tvAttendanceScore = findViewById(R.id.tvAttendanceScore);
        tvPresent = findViewById(R.id.tvPresent);
        tvAbsent = findViewById(R.id.tvAbsent);
        tvStreak = findViewById(R.id.tvStreak);
        tvSubjectBreakdown = findViewById(R.id.tvSubjectBreakdown);
        tvGoalMessage = findViewById(R.id.tvGoalMessage);
        tvSelectedDate = findViewById(R.id.tvSelectedDate);

        pieChart = findViewById(R.id.pieChart);
        weeklyChart = findViewById(R.id.weeklyChart);

        subjectContainer = findViewById(R.id.subjectContainer);

        btnSelectDate = findViewById(R.id.btnSelectDate);

        loadClasses();

        loadAttendanceRealtime();

        btnSelectDate.setOnClickListener(v -> openCalendar());
    }



    private void loadClasses(){

        db.collection("classes")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {

                    for(DocumentSnapshot doc : queryDocumentSnapshots){

                        String id = doc.getId();
                        String name = doc.getString("name");

                        classNames.put(id,name);
                    }

                });
    }



    private void loadAttendanceRealtime(){

        db.collection("attendance")
                .addSnapshotListener((value,error)->{

                    if(value == null) return;

                    present = 0;
                    absent = 0;
                    late = 0;

                    Map<String,Integer> subjectData = new HashMap<>();

                    List<Entry> weeklyEntries = new ArrayList<>();

                    int index = 0;

                    for(DocumentSnapshot doc : value.getDocuments()){

                        String status = doc.getString("status");
                        String date = doc.getString("date");
                        String classId = doc.getString("classId");

                        if(selectedDate != null && !selectedDate.equals(date))
                            continue;

                        if("present".equals(status)) present++;
                        else if("late".equals(status)) late++;
                        else absent++;

                        String subject = classNames.get(classId);

                        if(subject == null) subject = "Subject";

                        int count = subjectData.getOrDefault(subject,0);

                        subjectData.put(subject,count+1);

                        weeklyEntries.add(new Entry(index++,count+1));
                    }

                    updateScore();

                    showPieChart();

                    showWeeklyGraph(weeklyEntries);

                    showSubjectProgress(subjectData);

                });

    }



    private void updateScore(){

        int total = present + absent + late;

        if(total == 0) return;

        int score = (present * 100) / total;

        attendanceProgress.setProgress(score);

        tvAttendanceScore.setText(score + "%");

        tvPresent.setText("Present : " + present);

        tvAbsent.setText("Absent : " + absent);

        tvStreak.setText("🔥 Streak : " + present + " days");

        if(score < 75){

            int need = (int)Math.ceil((0.75 * total) - present);

            tvGoalMessage.setText("⚠ You need "+need+" more classes to reach safe attendance");

        }
        else{

            tvGoalMessage.setText("✅ Your attendance is safe");

        }

    }



    private void showPieChart(){

        List<PieEntry> entries = new ArrayList<>();

        entries.add(new PieEntry(present,"Present"));
        entries.add(new PieEntry(late,"Late"));
        entries.add(new PieEntry(absent,"Absent"));

        PieDataSet set = new PieDataSet(entries,"Attendance");

        set.setColors(
                Color.parseColor("#4CAF50"),
                Color.parseColor("#FFC107"),
                Color.parseColor("#F44336")
        );

        set.setValueTextColor(Color.WHITE);

        PieData data = new PieData(set);

        pieChart.setData(data);

        pieChart.setCenterText("Attendance");

        pieChart.setCenterTextColor(Color.WHITE);

        pieChart.setEntryLabelColor(Color.WHITE);

        pieChart.getLegend().setTextColor(Color.WHITE);

        pieChart.invalidate();

    }


    private void showWeeklyGraph(List<Entry> entries){

        LineDataSet set = new LineDataSet(entries,"Weekly Attendance");

        set.setColor(Color.BLUE);

        set.setValueTextColor(Color.WHITE);

        LineData data = new LineData(set);

        weeklyChart.setData(data);

        weeklyChart.getXAxis().setTextColor(Color.WHITE);

        weeklyChart.getAxisLeft().setTextColor(Color.WHITE);

        weeklyChart.getLegend().setTextColor(Color.WHITE);

        weeklyChart.getAxisRight().setEnabled(false);

        weeklyChart.invalidate();

    }


    private void showSubjectProgress(Map<String,Integer> data){

        subjectContainer.removeAllViews();

        for(String subject : data.keySet()){

            int count = data.get(subject);

            TextView tv = new TextView(this);

            tv.setText(subject + "  →  " + count + " classes");

            tv.setTextSize(16);

            tv.setTextColor(Color.WHITE);

            ProgressBar progress = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);

            progress.setMax(10);

            progress.setProgress(count);

            subjectContainer.addView(tv);

            subjectContainer.addView(progress);

        }

    }


    private void openCalendar(){

        Calendar cal = Calendar.getInstance();

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view,year,month,day)->{

                    Calendar c = Calendar.getInstance();

                    c.set(year,month,day);

                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd",Locale.getDefault());

                    selectedDate = sdf.format(c.getTime());

                    tvSelectedDate.setText("Selected : " + selectedDate);

                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        );

        dialog.show();

    }

}