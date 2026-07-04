package com.example.s.adapter;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.s.R;
import com.example.s.TeacherLeaveRequestsActivity;
import com.example.s.model.LeaveModel;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class LeaveRequestAdapter extends RecyclerView.Adapter<LeaveRequestAdapter.ViewHolder> {

    Context context;
    List<LeaveModel> list;

    FirebaseFirestore db = FirebaseFirestore.getInstance();

    public LeaveRequestAdapter(Context context, List<LeaveModel> list) {
        this.context = context;
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_leave_request, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        LeaveModel model = list.get(position);

        String name = model.studentName == null ? "Student" : model.studentName;
        String roll = model.rollNo == null ? "-" : model.rollNo;

        holder.tvStudent.setText(name + " (Roll No: " + roll + ")");
        holder.tvReason.setText(model.reason);
        holder.tvDate.setText(model.date);
        holder.tvStatus.setText(model.status);

        if(model.status.equals("approved"))
            holder.tvStatus.setTextColor(Color.parseColor("#4CAF50"));
        else if(model.status.equals("rejected"))
            holder.tvStatus.setTextColor(Color.parseColor("#F44336"));
        else
            holder.tvStatus.setTextColor(Color.parseColor("#FFA000"));

        if(context instanceof TeacherLeaveRequestsActivity){
            holder.btnApprove.setVisibility(View.VISIBLE);
            holder.btnReject.setVisibility(View.VISIBLE);
        }else{
            holder.btnApprove.setVisibility(View.GONE);
            holder.btnReject.setVisibility(View.GONE);
        }

        holder.btnApprove.setOnClickListener(v ->
                db.collection("leave_requests")
                        .document(model.id)
                        .update("status","approved"));

        holder.btnReject.setOnClickListener(v ->
                db.collection("leave_requests")
                        .document(model.id)
                        .update("status","rejected"));

        holder.itemView.setOnClickListener(v -> {

            AlertDialog.Builder builder = new AlertDialog.Builder(context);

            View dialogView = LayoutInflater.from(context)
                    .inflate(R.layout.dialog_leave_details,null);

            TextView tvReason = dialogView.findViewById(R.id.tvDialogReason);
            ImageView imgCertificate = dialogView.findViewById(R.id.ivCertificate);

            tvReason.setText(model.reason);

            if(model.certificateImage != null){

                byte[] decodedBytes =
                        Base64.decode(model.certificateImage,Base64.DEFAULT);

                Bitmap bitmap =
                        BitmapFactory.decodeByteArray(decodedBytes,0,decodedBytes.length);

                imgCertificate.setImageBitmap(bitmap);
            }

            builder.setView(dialogView);
            builder.setPositiveButton("Close",null);
            builder.show();
        });
        holder.itemView.setOnLongClickListener(v -> {

            new AlertDialog.Builder(context)
                    .setTitle("Leave Request")
                    .setItems(new String[]{"Delete", "Cancel"}, (dialog, which) -> {

                        if (which == 0) {

                            int pos = holder.getAdapterPosition();

                            if (pos == RecyclerView.NO_POSITION) return;



                            db.collection("leave_requests")
                                    .document(model.id)
                                    .delete()
                                    .addOnSuccessListener(unused -> {

                                        list.remove(pos);
                                        notifyItemRemoved(pos);

                                        Toast.makeText(context,
                                                "Request deleted",
                                                Toast.LENGTH_SHORT).show();

                                    })
                                    .addOnFailureListener(e -> {

                                        Toast.makeText(context,
                                                "Delete failed: " + e.getMessage(),
                                                Toast.LENGTH_LONG).show();
                                    });
                        }

                    })
                    .show();

            return true;
        });
    }


    @Override
    public int getItemCount() {
        return list.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder{

        TextView tvStudent,tvReason,tvDate,tvStatus;
        Button btnApprove,btnReject;

        public ViewHolder(@NonNull View itemView){

            super(itemView);

            tvStudent=itemView.findViewById(R.id.tvStudent);
            tvReason=itemView.findViewById(R.id.tvReason);
            tvDate=itemView.findViewById(R.id.tvDate);
            tvStatus=itemView.findViewById(R.id.tvStatus);

            btnApprove=itemView.findViewById(R.id.btnApprove);
            btnReject=itemView.findViewById(R.id.btnReject);
        }
    }
}