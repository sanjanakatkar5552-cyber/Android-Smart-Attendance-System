package com.example.s;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FaceScanActivity extends AppCompatActivity {

    private PreviewView previewView;
    private AppCompatButton btnScan;
    private ProgressBar progressBar;
    private ExecutorService cameraExecutor;
    private FaceDetector detector;
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private String userEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_scan);

        previewView = findViewById(R.id.previewView);
        btnScan = findViewById(R.id.btnScan);
        progressBar = findViewById(R.id.progressBar);

        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        userEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();

        cameraExecutor = Executors.newSingleThreadExecutor();

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build();
        detector = FaceDetection.getClient(options);

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 10);
        }

        btnScan.setOnClickListener(v -> captureAndUploadFace());
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                        .build();

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview);

            } catch (ExecutionException | InterruptedException e) {
                Log.e("CAMERA", "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void captureAndUploadFace() {
        progressBar.setVisibility(View.VISIBLE);
        btnScan.setEnabled(false);

        Bitmap bitmap = previewView.getBitmap();
        if (bitmap == null) {
            Toast.makeText(this, "Failed to capture image", Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.GONE);
            btnScan.setEnabled(true);
            return;
        }

        InputImage image = InputImage.fromBitmap(bitmap, 0);
        detector.process(image)
                .addOnSuccessListener(faces -> {
                    if (faces.size() == 1) {
                        uploadToFirebase(bitmap);
                    } else {
                        Toast.makeText(this, "Please ensure one face is clearly visible", Toast.LENGTH_SHORT).show();
                        progressBar.setVisibility(View.GONE);
                        btnScan.setEnabled(true);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Face detection failed", Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                    btnScan.setEnabled(true);
                });
    }

    private void uploadToFirebase(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
        byte[] data = baos.toByteArray();

        StorageReference faceRef = storage.getReference().child("face_data/" + userEmail + ".jpg");
        faceRef.putBytes(data).addOnSuccessListener(taskSnapshot -> {
            updateFirestore();
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.GONE);
            btnScan.setEnabled(true);
        });
    }

    private void updateFirestore() {
        db.collectionGroup("students")
                .whereEqualTo("email", userEmail)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        doc.getReference().update("faceRegistered", true);
                    }
                    db.collection("users")
                            .whereEqualTo("email", userEmail)
                            .get()
                            .addOnSuccessListener(userDocs -> {
                                for (QueryDocumentSnapshot userDoc : userDocs) {
                                    userDoc.getReference().update("faceRegistered", true);
                                }
                                progressBar.setVisibility(View.GONE);
                                Toast.makeText(this, "Face Registered Successfully!", Toast.LENGTH_LONG).show();
                                finish();
                            });
                });
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}
