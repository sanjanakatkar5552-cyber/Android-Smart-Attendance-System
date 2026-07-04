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

import java.util.List;

public class AttendanceRecordAdapter extends RecyclerView.Adapter<AttendanceRecordAdapter.ViewHolder> {

    private List<AttendanceRecordModel> list;

    private static final int ROW_EVEN = Color.parseColor("#FFFFFF");
    private static final int ROW_ODD  = Color.parseColor("#F3F6FD");

    public AttendanceRecordAdapter(List<AttendanceRecordModel> list) {
        this.list = list;
    }

    public void updateList(List<AttendanceRecordModel> newList) {
        this.list = newList;
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, roll, subject, date, time, attendancePercent, status;

        public ViewHolder(@NonNull View v) {
            super(v);
            name              = v.findViewById(R.id.txtName);
            roll              = v.findViewById(R.id.txtRoll);
            subject           = v.findViewById(R.id.txtSubject);
            date              = v.findViewById(R.id.txtDate);
            time              = v.findViewById(R.id.txtTime);
            attendancePercent = v.findViewById(R.id.txtAttendancePercent);
            status            = v.findViewById(R.id.txtStatus);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_attendance_record, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        AttendanceRecordModel m = list.get(position);

        h.itemView.setBackgroundColor(position % 2 == 0 ? ROW_EVEN : ROW_ODD);

        h.name.setText(safe(m.getName()));
        h.roll.setText(safe(m.getRollNo()));
        h.subject.setText(safe(m.getSubject()));
        h.date.setText(safe(m.getDate()));
        h.time.setText(empty(m.getTime()) ? "—" : m.getTime());
        h.attendancePercent.setText(m.getAttendancePercentLabel());

        float pct = m.getAttendancePercent();
        h.attendancePercent.setTextColor(
                (pct > 0f && pct < 75f)
                        ? Color.parseColor("#C62828")
                        : Color.parseColor("#1565C0"));

        GradientDrawable badge = new GradientDrawable();
        badge.setCornerRadius(32f);
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

    @Override
    public int getItemCount() { return list.size(); }

    private String  safe(String s)  { return (s != null && !s.isEmpty()) ? s : "—"; }
    private boolean empty(String s) { return s == null || s.trim().isEmpty(); }
}