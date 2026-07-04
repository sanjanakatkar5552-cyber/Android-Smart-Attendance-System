package com.example.s;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.s.adapter.LeaveRequestAdapter;
import com.example.s.model.LeaveModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LeaveRequestActivity extends AppCompatActivity {

    private EditText etLeaveReason, etLeaveDate;
    private ImageView ivDocPreview;
    private CardView cardAttachDoc;
    private AppCompatButton btnSubmitLeave;
    private ProgressBar progressBar;

    private Bitmap docBitmap;

    private FirebaseFirestore db;
    RecyclerView recyclerLeaveHistory;

    LeaveRequestAdapter adapter;

    List<LeaveModel> leaveList = new ArrayList<>();

    private String studentId;
    private String studentEmail;

    private String teacherId;
    private String classId;

    // CAMERA RESULT
    private final ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {

                        if (result.getResultCode() == RESULT_OK
                                && result.getData() != null
                                && result.getData().getExtras() != null) {

                            docBitmap = (Bitmap) result.getData()
                                    .getExtras().get("data");

                            ivDocPreview.setImageBitmap(docBitmap);
                            ivDocPreview.setAlpha(1f);
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leave_request);

        etLeaveReason = findViewById(R.id.etLeaveReason);
        etLeaveDate = findViewById(R.id.etLeaveDate);
        ivDocPreview = findViewById(R.id.ivDocPreview);
        cardAttachDoc = findViewById(R.id.cardAttachDoc);
        btnSubmitLeave = findViewById(R.id.btnSubmitLeave);
        progressBar = findViewById(R.id.progressBar);

        db = FirebaseFirestore.getInstance();

        studentId = FirebaseAuth.getInstance().getUid();

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            studentEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();
        }

        teacherId = getIntent().getStringExtra("teacherId");
        classId = getIntent().getStringExtra("classId");

        if (teacherId == null || classId == null) {
            Toast.makeText(this,
                    "Teacher/Class not found",
                    Toast.LENGTH_LONG).show();
            finish();
        }

        etLeaveDate.setOnClickListener(v -> showDatePicker());

        cardAttachDoc.setOnClickListener(v -> openCamera());

        btnSubmitLeave.setOnClickListener(v -> submitLeaveRequest());
        recyclerLeaveHistory = findViewById(R.id.recyclerLeaveHistory);

        adapter = new LeaveRequestAdapter(this, leaveList);

        recyclerLeaveHistory.setLayoutManager(new LinearLayoutManager(this));

        recyclerLeaveHistory.setAdapter(adapter);

        loadLeaveHistory();
    }
    private void loadLeaveHistory() {

        String studentId = FirebaseAuth.getInstance().getUid();

        FirebaseFirestore.getInstance()
                .collection("leave_requests")
                .whereEqualTo("studentId", studentId)
                .addSnapshotListener((query, error) -> {

                    if (query == null) return;

                    leaveList.clear();

                    for (DocumentSnapshot doc : query.getDocuments()) {

                        leaveList.add(new LeaveModel(

                                doc.getId(),
                                doc.getString("studentId"),
                                doc.getString("studentName"),
                                doc.getString("rollNo"),
                                doc.getString("classId"),
                                doc.getString("reason"),
                                doc.getString("date"),
                                doc.getString("status"),
                                doc.getString("certificateImage")

                        ));
                    }

                    adapter.notifyDataSetChanged();
                });
    }

    private void showDatePicker() {

        Calendar c = Calendar.getInstance();

        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, y, m, d) ->
                        etLeaveDate.setText(d + "/" + (m + 1) + "/" + y),
                year, month, day);

        dialog.show();
    }

    private void openCamera() {

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        cameraLauncher.launch(intent);
    }


    private void submitLeaveRequest() {

        String reason = etLeaveReason.getText().toString().trim();
        String date = etLeaveDate.getText().toString().trim();

        if(reason.isEmpty()){
            Toast.makeText(this,"Enter reason",Toast.LENGTH_SHORT).show();
            return;
        }

        if(date.isEmpty()){
            Toast.makeText(this,"Select date",Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        db.collection("users")
                .document(studentId)
                .get()
                .addOnSuccessListener(userDoc -> {

                    String studentName = userDoc.getString("name");
                    String rollNo = String.valueOf(userDoc.get("rollNo"));

                    String encodedImage = "";

                    if(docBitmap != null){

                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        docBitmap.compress(Bitmap.CompressFormat.JPEG,100,baos);

                        byte[] imageBytes = baos.toByteArray();

                        encodedImage = Base64.encodeToString(imageBytes,Base64.DEFAULT);
                    }

                    Map<String,Object> leave = new HashMap<>();

                    leave.put("studentId", studentId);
                    leave.put("studentEmail", studentEmail);
                    leave.put("studentName", studentName);
                    leave.put("rollNo", rollNo);
                    leave.put("teacherId", teacherId);
                    leave.put("classId", classId);
                    leave.put("reason", reason);
                    leave.put("date", date);
                    leave.put("status", "pending");
                    leave.put("certificateImage", encodedImage);

                    db.collection("leave_requests")
                            .add(leave)
                            .addOnSuccessListener(documentReference -> {

                                progressBar.setVisibility(View.GONE);

                                Toast.makeText(this,
                                        "Leave submitted successfully",
                                        Toast.LENGTH_LONG).show();

                                finish();
                            });

                });
    }
}