package com.example.s;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.*;

import java.io.IOException;
import java.util.*;

public class FaceRegisterActivity extends AppCompatActivity {

    private PreviewView previewView;
    private ImageView imgPreview;
    private AppCompatButton btnCapture, btnSave;
    private ProgressBar progressBar;

    private FirebaseFirestore firestore;
    private FirebaseAuth auth;

    private FaceDetector detector;
    private FaceNetModel faceNetModel;
    private float[] faceEmbedding;
    private boolean isFaceRegistered = false;
    private boolean updateRequest = false;
    FirebaseFirestore db = FirebaseFirestore.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_register);

        previewView = findViewById(R.id.previewView);
        imgPreview = findViewById(R.id.imgPreview);
        btnCapture = findViewById(R.id.btnCapture);
        btnSave = findViewById(R.id.btnSave);
        progressBar = findViewById(R.id.progressBar);

        firestore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        FaceDetectorOptions options =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                        .build();

        detector = FaceDetection.getClient(options);

        try {
            faceNetModel = new FaceNetModel(this, "facenet.tflite");
        } catch (IOException e) {
            Toast.makeText(this, "Model load failed", Toast.LENGTH_LONG).show();
        }

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, 10);
        }

        btnCapture.setOnClickListener(v -> takePhoto());

        btnSave.setOnClickListener(v -> saveToFirestore());
        checkFaceStatus();
    }


    private void checkFaceStatus() {

        String uid = auth.getUid();
        String email = auth.getCurrentUser().getEmail().trim().toLowerCase();

        // 🔥 CHECK FROM students_master (NEW)
        firestore.collection("students_master")
                .document(email)
                .get()
                .addOnSuccessListener(doc -> {

                    if (doc.exists()) {

                        Boolean registered = doc.getBoolean("faceRegistered");

                        if (Boolean.TRUE.equals(registered)) {

                            Toast.makeText(this,
                                    "Face already registered ❌",
                                    Toast.LENGTH_LONG).show();

                            finish();
                        }
                    }
                });

        // 🔥 KEEP OLD LOGIC ALSO (NO BREAK)
        firestore.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {

                    if (documentSnapshot.exists()) {

                        Boolean registered = documentSnapshot.getBoolean("faceRegistered");
                        Boolean request = documentSnapshot.getBoolean("updateRequest");

                        if (registered != null && registered) {
                            isFaceRegistered = true;

                            if (request != null && request) {
                                Toast.makeText(this,
                                        "Face update already pending approval",
                                        Toast.LENGTH_LONG).show();
                                finish();
                            }
                        }
                    }
                });
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider =
                        cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                CameraSelector cameraSelector =
                        new CameraSelector.Builder()
                                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                                .build();

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void takePhoto() {

        Bitmap bitmap = previewView.getBitmap();

        if (bitmap == null) {
            Toast.makeText(this, "Camera not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        InputImage image = InputImage.fromBitmap(bitmap, 0);

        detector.process(image)
                .addOnSuccessListener(faces -> {

                    progressBar.setVisibility(View.GONE);
                    btnSave.setEnabled(true);

                    if (faces.size() == 1) {

                        Face face = faces.get(0);

                        // 👁 Eye check
                        if (face.getLeftEyeOpenProbability() != null &&
                                face.getLeftEyeOpenProbability() < 0.5) {

                            Toast.makeText(this,
                                    "Keep eyes open",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // 🧠 Head straight check
                        if (face.getHeadEulerAngleY() > 15 ||
                                face.getHeadEulerAngleY() < -15) {

                            Toast.makeText(this,
                                    "Keep face straight",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        Rect bounds = face.getBoundingBox();

                        int x = Math.max(bounds.left, 0);
                        int y = Math.max(bounds.top, 0);
                        int width = Math.min(bounds.width(), bitmap.getWidth() - x);
                        int height = Math.min(bounds.height(), bitmap.getHeight() - y);

                        Bitmap cropped = Bitmap.createBitmap(bitmap, x, y, width, height);

                        faceEmbedding = faceNetModel.getFaceEmbedding(cropped);

                        imgPreview.setImageBitmap(bitmap);
                        imgPreview.setVisibility(View.VISIBLE);
                        previewView.setVisibility(View.GONE);
                        btnSave.setVisibility(View.VISIBLE);

                        Toast.makeText(this, "Face Captured", Toast.LENGTH_SHORT).show();

                    } else {
                        Toast.makeText(this,
                                "Ensure only one face",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    btnSave.setEnabled(true);
                    Toast.makeText(this,
                            "Face detection failed",
                            Toast.LENGTH_SHORT).show();
                });
    }


    private void saveToFirestore() {

        if (faceEmbedding == null) {
            Toast.makeText(this, "Capture face first", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSave.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);

        String uid = auth.getUid();
        String email = auth.getCurrentUser().getEmail().trim().toLowerCase();

        if (uid == null) return;

        List<Double> list = new ArrayList<>();
        for (float f : faceEmbedding) {
            list.add((double) f);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("faceEmbedding", list);
        data.put("faceRegistered", true);
        data.put("faceStatus", "APPROVED");
        data.put("updateRequest", false);

        firestore.collection("users")
                .document(uid)
                .set(data, SetOptions.merge());

        Map<String, Object> masterData = new HashMap<>();
        masterData.put("faceRegistered", true);
        updateStudentInAllClasses(email); // ✅ VERY IMPORTANT

        firestore.collection("students_master")
                .document(email)
                .set(masterData, SetOptions.merge());

        updateStudentCollection(email);

        progressBar.setVisibility(View.GONE);

        Toast.makeText(this,
                "Face Registered Successfully ✅",
                Toast.LENGTH_SHORT).show();

        setResult(RESULT_OK);
        finish();
    }
    private void updateStudentInAllClasses(String email) {

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("classes")
                .get()
                .addOnSuccessListener(querySnapshot -> {

                    for (DocumentSnapshot classDoc : querySnapshot.getDocuments()) {

                        db.collection("classes")
                                .document(classDoc.getId())
                                .collection("students")
                                .document(email)
                                .update(
                                        "faceRegistered", true,
                                        "approved", false
                                );
                    }
                });
    }
    private void updateStudentCollection(String userEmail) {

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("students_master")
                .document(userEmail)
                .update(
                        "faceRegistered", true,
                        "approved", false
                );

        db.collection("classes")
                .get()
                .addOnSuccessListener(querySnapshot -> {

                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {

                        String classId = doc.getId();

                        db.collection("classes")
                                .document(classId)
                                .collection("students")
                                .document(userEmail)
                                .update(
                                        "faceRegistered", true,
                                        "approved", false
                                );
                    }
                });
    }

}
