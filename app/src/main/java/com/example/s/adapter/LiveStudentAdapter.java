package com.example.s.adapter;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.s.R;
import com.example.s.model.LiveStudentModel;

import java.util.List;

public class LiveStudentAdapter extends RecyclerView.Adapter<LiveStudentAdapter.ViewHolder> {

    private final Context context;
    private final List<LiveStudentModel> list;

    public LiveStudentAdapter(Context context, List<LiveStudentModel> list) {
        this.context = context;
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context)
                .inflate(R.layout.item_live_student, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        LiveStudentModel student = list.get(position);

        // Name
        holder.tvName.setText(student.getName());

        // Roll No
        holder.tvRollNo.setText("Roll No : " + student.getRollNo());

        // Distance formatted to 2 decimal places
        holder.tvDistance.setText(
                String.format("Distance : %.2f m", student.getDistance())
        );

        String status = student.getStatus();

        if ("present".equalsIgnoreCase(status)) {
            holder.tvStatus.setText("present");
            holder.tvStatus.setBackgroundColor(Color.parseColor("#388E3C")); // dark green
            holder.tvStatus.setTextColor(Color.WHITE);
        } else if ("mismatch".equalsIgnoreCase(status)) {
            holder.tvStatus.setText("mismatch");
            holder.tvStatus.setBackgroundColor(Color.parseColor("#F57C00")); // orange
            holder.tvStatus.setTextColor(Color.WHITE);
        } else {
            // absent (default)
            holder.tvStatus.setText("absent");
            holder.tvStatus.setBackgroundColor(Color.parseColor("#C62828")); // dark red
            holder.tvStatus.setTextColor(Color.WHITE);
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        TextView tvName, tvRollNo, tvDistance, tvStatus;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName     = itemView.findViewById(R.id.tvStudentName);
            tvRollNo   = itemView.findViewById(R.id.tvRollNo);
            tvDistance = itemView.findViewById(R.id.tvDistance);
            tvStatus   = itemView.findViewById(R.id.tvStatus);
        }
    }
}


