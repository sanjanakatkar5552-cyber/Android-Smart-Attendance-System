package com.example.s.adapter;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.s.ClassDetailsActivity;
import com.example.s.R;
import com.example.s.model.ClassModel;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import android.widget.TextView;

import java.util.List;

public class ClassAdapter extends RecyclerView.Adapter<ClassAdapter.ViewHolder> {

    private final List<ClassModel> classList;
    private final Context context;
    private final FirebaseFirestore db;

    public ClassAdapter(Context context, List<ClassModel> classList) {
        this.context = context;
        this.classList = classList;
        this.db = FirebaseFirestore.getInstance();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_class_card, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        ClassModel model = classList.get(position);
        if (model == null) return;

        String title = (model.getSubject() != null ? model.getSubject() : "Class")
                + " - "
                + (model.getYear() != null ? model.getYear() : "");

        holder.tvClassTitle.setText(title);

        holder.tvStudents.setText("Students: " + model.getTotalStudents());

        holder.tvRadius.setText("Radius: " + model.getRadius() + "m");

        if (model.getIsActive()) {
            holder.tvStatus.setText("● Active");
            holder.tvStatus.setTextColor(
                    ContextCompat.getColor(context, R.color.colorPrimary));
        } else {
            holder.tvStatus.setText("● Inactive");
            holder.tvStatus.setTextColor(
                    ContextCompat.getColor(context, android.R.color.darker_gray));
        }

        holder.btnOpenClass.setOnClickListener(v -> {
            Intent intent = new Intent(context, ClassDetailsActivity.class);
            intent.putExtra("classId", model.getId());
            context.startActivity(intent);
        });

        holder.itemView.setOnLongClickListener(v -> {
            showDeleteConfirmation(model, position);
            return true;
        });
    }


private void showDeleteConfirmation(ClassModel model, int position) {

    new AlertDialog.Builder(context)
            .setTitle("Delete Class")
            .setMessage("Are you sure you want to delete this class?")
            .setPositiveButton("Delete", (dialog, which) -> {

                FirebaseFirestore db = FirebaseFirestore.getInstance();

                db.collection("classes")
                        .document(model.getId())
                        .delete()
                        .addOnSuccessListener(aVoid -> {

                            classList.remove(position);
                            notifyItemRemoved(position);
                            notifyItemRangeChanged(position, classList.size());

                            Toast.makeText(context,
                                    "Class deleted successfully",
                                    Toast.LENGTH_SHORT).show();

                        })
                        .addOnFailureListener(e -> {

                            Toast.makeText(context,
                                    "Delete failed: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show();

                        });

            })
            .setNegativeButton("Cancel", null)
            .show();
}
    private void deleteClass(String classId){

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("classes")
                .document(classId)
                .collection("students")
                .get()
                .addOnSuccessListener(studentQuery -> {

                    WriteBatch batch = db.batch();

                    for(DocumentSnapshot doc : studentQuery){
                        batch.delete(doc.getReference());
                    }

                    batch.commit().addOnSuccessListener(a -> {

                        db.collection("classes")
                                .document(classId)
                                .delete()
                                .addOnSuccessListener(b ->
                                        Toast.makeText(context,
                                                "Class deleted successfully",
                                                Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(e ->
                                        Toast.makeText(context,
                                                "Delete failed",
                                                Toast.LENGTH_SHORT).show());

                    });

                });
    }
    @Override
    public int getItemCount() {
        return classList != null ? classList.size() : 0;
    }

    // ================= VIEW HOLDER =================

    static class ViewHolder extends RecyclerView.ViewHolder {

        TextView tvClassTitle, tvStudents, tvRadius, tvStatus;
        MaterialButton btnOpenClass;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            tvClassTitle = itemView.findViewById(R.id.tvClassTitle);
            tvStudents = itemView.findViewById(R.id.tvStudents);
            tvRadius = itemView.findViewById(R.id.tvRadius);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            btnOpenClass = itemView.findViewById(R.id.btnOpenClass);
        }
    }
}