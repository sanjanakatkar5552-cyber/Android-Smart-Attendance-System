package com.example.s.adapter;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.s.R;
import com.example.s.model.AttendanceRecordModel;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;


public class Studentrecordadapter extends RecyclerView.Adapter<Studentrecordadapter.ViewHolder> {

    private List<AttendanceRecordModel> list;

    private static final int ROW_EVEN = Color.parseColor("#FFFFFF");
    private static final int ROW_ODD  = Color.parseColor("#F3F6FD");
    private static final String FMT_SAVE    = "yyyy-MM-dd";
    private static final String FMT_DISPLAY = "dd/MM/yyyy";

    public Studentrecordadapter(List<AttendanceRecordModel> list) { this.list = list; }

    public void updateList(List<AttendanceRecordModel> newList) { this.list = newList; notifyDataSetChanged(); }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView subject, date, time, distance, status;
        public ViewHolder(@NonNull View v) {
            super(v);
            subject  = v.findViewById(R.id.txtSubject);
            date     = v.findViewById(R.id.txtDate);
            time     = v.findViewById(R.id.txtTime);
            distance = v.findViewById(R.id.txtDistance);
            status   = v.findViewById(R.id.txtStatus);
        }
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_student_record, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        AttendanceRecordModel m = list.get(position);
        h.itemView.setBackgroundColor(position % 2 == 0 ? ROW_EVEN : ROW_ODD);

        h.subject.setText(safe(m.getSubject()));
        h.date.setText(toDisplay(safe(m.getDate())));
        h.time.setText(empty(m.getTime()) ? "—" : m.getTime());

        // Distance
        Double dist = m.getDistance();
        h.distance.setText((dist != null && dist > 0) ? String.format(Locale.getDefault(), "%.1f m", dist) : "—");

        // Status badge
        GradientDrawable badge = new GradientDrawable(); badge.setCornerRadius(32f);
        if (m.isPresent()) {
            badge.setColor(Color.parseColor("#E8F5E9"));
            h.status.setTextColor(Color.parseColor("#2E7D32"));
            h.status.setText("Present");
        } else {
            badge.setColor(Color.parseColor("#FFEBEE"));
            h.status.setTextColor(Color.parseColor("#C62828"));
            h.status.setText("Absent");
        }
        h.status.setBackground(badge);
    }

    @Override public int getItemCount() { return list.size(); }

    private String toDisplay(String s) {
        try { Date d = new SimpleDateFormat(FMT_SAVE, Locale.getDefault()).parse(s); if (d != null) return new SimpleDateFormat(FMT_DISPLAY, Locale.getDefault()).format(d); } catch (Exception ignored) {}
        return s;
    }
    private boolean empty(String s) { return s == null || s.trim().isEmpty(); }
    private String  safe(String s)  { return s != null ? s : "—"; }
}