package com.example.s.adapter;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.s.R;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class ApproveStudentAdapter extends RecyclerView.Adapter<ApproveStudentAdapter.ViewHolder> {

    Context context;
    List<DocumentSnapshot> list;
    String classId;
    FirebaseFirestore db;

    public ApproveStudentAdapter(Context context, List<DocumentSnapshot> list, String classId) {
        this.context = context;
        this.list = list;
        this.classId = classId;
        db = FirebaseFirestore.getInstance();
    }


    public static class ViewHolder extends RecyclerView.ViewHolder {

        TextView tvName;
        Button btnApprove;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            tvName = itemView.findViewById(R.id.tvName);
            btnApprove = itemView.findViewById(R.id.btnApprove);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context)
                .inflate(R.layout.item_student_approval, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        DocumentSnapshot doc = list.get(position);

        String name = doc.getString("name");
        holder.tvName.setText(name != null && !name.isEmpty() ? name : "No Name");

        Boolean approved = doc.getBoolean("approved");
        Boolean faceRegistered = doc.getBoolean("faceRegistered");

        boolean isApproved = approved != null && approved;
        boolean isFaceRegistered = faceRegistered != null && faceRegistered;

        if (isFaceRegistered && !isApproved) {
            holder.btnApprove.setVisibility(View.VISIBLE);
        } else {
            holder.btnApprove.setVisibility(View.GONE);
        }

        Log.d("CHECK", "face=" + isFaceRegistered + " approved=" + isApproved);

        holder.btnApprove.setOnClickListener(v -> {

            int currentPosition = holder.getAdapterPosition();
            if (currentPosition == RecyclerView.NO_POSITION) return;

            String rawEmail = doc.getString("email");

            if (rawEmail == null) {
                Toast.makeText(context, "Email missing", Toast.LENGTH_SHORT).show();
                return;
            }

            final String email = rawEmail.trim().toLowerCase(); // ✅ FIX

            holder.btnApprove.setEnabled(false);

            db.collection("classes")
                    .document(classId)
                    .collection("students")
                    .document(email)
                    .update("approved", true)
                    .addOnSuccessListener(unused -> {

                        db.collection("students_master")
                                .document(email)
                                .update("approved", true);

                        Toast.makeText(context, "Approved ✅", Toast.LENGTH_SHORT).show();

                        list.remove(currentPosition);
                        notifyItemRemoved(currentPosition);
                    })
                    .addOnFailureListener(e -> {
                        holder.btnApprove.setEnabled(true);
                        Toast.makeText(context, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });

        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }
}
