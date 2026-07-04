package com.example.s.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import com.example.s.R;
import com.example.s.model.StudentModel;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;
import android.widget.Toast;

public class StudentAdapter extends RecyclerView.Adapter<StudentAdapter.ViewHolder> {

    private List<StudentModel> list;
    private Context context;
    private String classId;

    private FirebaseFirestore db = FirebaseFirestore.getInstance();

    public StudentAdapter(Context context, List<StudentModel> list, String classId){
        this.context = context;
        this.list = list;
        this.classId = classId;
    }

    public class ViewHolder extends RecyclerView.ViewHolder{

        TextView tvName, status, face;
        MaterialButton btnFace, btnRemove;

        public ViewHolder(View v){
            super(v);

            tvName = v.findViewById(R.id.tvName);
            status = v.findViewById(R.id.tvStatus);
            face = v.findViewById(R.id.tvFace);

            btnFace = v.findViewById(R.id.btnRegisterFace);
            btnRemove = v.findViewById(R.id.btnRemove);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent,int viewType){

        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_student,parent,false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder h,int position) {

        StudentModel s = list.get(position);

        h.tvName.setText(s.name);
        h.status.setText(s.status);

        // FACE STATUS
        String email = s.email != null ? s.email.trim().toLowerCase() : "";

        FirebaseFirestore.getInstance()
                .collection("students_master")
                .document(email)
                .get()
                .addOnSuccessListener(doc -> {

                    Boolean registered = doc.getBoolean("faceRegistered");

                    if (Boolean.TRUE.equals(registered)) {
                        h.face.setText("Face Registered ✓");
                    } else {
                        h.face.setText("Face Not Registered");
                    }
                })
                .addOnFailureListener(e -> {
                    h.face.setText("Error loading");
                });

        // STATUS COLOR
        if ("approved".equalsIgnoreCase(s.status)) {
            h.status.setTextColor(0xFF2E7D32); // green
        } else if ("pending".equalsIgnoreCase(s.status)) {
            h.status.setTextColor(0xFFF57C00); // orange
        } else {
            h.status.setTextColor(0xFF666666);
        }

        // FACE BUTTON VISIBILITY
        if ("approved".equalsIgnoreCase(s.status)) {
            h.btnFace.setVisibility(View.GONE);
        } else {
            if (Boolean.TRUE.equals(s.faceRegistered)) {
                h.btnFace.setVisibility(View.VISIBLE);
            } else {
                h.btnFace.setVisibility(View.GONE);
            }
        }

        // REGISTER FACE
        h.btnFace.setOnClickListener(v -> {

            db.collection("classes")
                    .document(classId)
                    .collection("students")
                    .document(s.email)
                    .update("status", "approved")
                    .addOnSuccessListener(unused ->
                            Toast.makeText(context, "Student Approved", Toast.LENGTH_SHORT).show());
        });



        h.btnRemove.setOnClickListener(v -> {

            db.collection("classes")
                    .document(classId)
                    .collection("students")
                    .document(s.email)
                    .delete()

                    .addOnSuccessListener(unused -> {

                        db.collection("classes")
                                .document(classId)
                                .update("totalStudents",
                                        FieldValue.increment(-1));

                        Toast.makeText(context,
                                "Student Removed",
                                Toast.LENGTH_SHORT).show();

                    });

        });
    }

    @Override
    public int getItemCount(){
        return list.size();
    }
}