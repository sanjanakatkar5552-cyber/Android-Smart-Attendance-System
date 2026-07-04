package com.example.s;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class ReviewLeaveActivity extends AppCompatActivity {

    private RecyclerView rvLeaveRequests;
    private FirebaseFirestore db;
    private List<DocumentSnapshot> leaveList;
    private LeaveAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_control); // Reusing layout for consistency

        TextView tvHeader = findViewById(R.id.tvHeader);
        tvHeader.setText("Leave Request Approvals");

        rvLeaveRequests = findViewById(R.id.rvFaceControl);
        rvLeaveRequests.setLayoutManager(new LinearLayoutManager(this));

        db = FirebaseFirestore.getInstance();
        leaveList = new ArrayList<>();
        adapter = new LeaveAdapter();
        rvLeaveRequests.setAdapter(adapter);

        loadPendingLeaves();
    }

    private void loadPendingLeaves() {
        db.collection("leave_requests")
                .whereEqualTo("status", "pending")
                .addSnapshotListener((value, error) -> {
                    if (value != null) {
                        leaveList.clear();
                        leaveList.addAll(value.getDocuments());
                        adapter.notifyDataSetChanged();
                    }
                });
    }

    private class LeaveAdapter extends RecyclerView.Adapter<LeaveAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(ReviewLeaveActivity.this).inflate(R.layout.item_manage_student, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DocumentSnapshot doc = leaveList.get(position);
            holder.tvName.setText(doc.getString("email"));
            holder.tvDetails.setText("Reason: " + doc.getString("reason") + "\nDate: " + doc.getString("date"));
            holder.btnAction.setText("Review");

            holder.btnAction.setOnClickListener(v -> showDetailsDialog(doc));
        }

        @Override
        public int getItemCount() { return leaveList.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvDetails;
            androidx.appcompat.widget.AppCompatButton btnAction;
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvStudentName);
                tvDetails = itemView.findViewById(R.id.tvStudentEmail);
                btnAction = itemView.findViewById(R.id.btnOverride);
            }
        }
    }

    private void showDetailsDialog(DocumentSnapshot doc) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_leave_details, null);
        
        ImageView ivCert = view.findViewById(R.id.ivCertificate);
        TextView tvReason = view.findViewById(R.id.tvDialogReason);
        
        tvReason.setText(doc.getString("reason"));
        String url = doc.getString("docUrl");
        if (url != null) Glide.with(this).load(url).into(ivCert);

        builder.setView(view)
                .setPositiveButton("Approve", (dialog, which) -> updateStatus(doc.getId(), "approved"))
                .setNegativeButton("Reject", (dialog, which) -> updateStatus(doc.getId(), "rejected"))
                .show();
    }

    private void updateStatus(String id, String status) {
        db.collection("leave_requests").document(id).update("status", status)
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Status updated to " + status, Toast.LENGTH_SHORT).show());
    }
}
