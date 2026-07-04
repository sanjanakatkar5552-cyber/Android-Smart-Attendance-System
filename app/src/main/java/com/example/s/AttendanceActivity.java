package com.example.s;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.firestore.SetOptions;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.*;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class AttendanceActivity extends AppCompatActivity {

    private static final float FACE_MATCH_THRESHOLD   = 0.85f;
    private static final float MIN_FACE_SIZE_FRACTION = 0.15f;


    private static final float EYE_OPEN_PROB   = 0.65f;


    private static final float EYE_CLOSED_PROB = 0.45f;


    private static final int  BLINKS_REQUIRED = 2;


    private static final long BLINK_TIMEOUT_MS = 10000L;


    private static final long FRAME_INTERVAL_MS = 30L;

    private enum BlinkState { WAITING_OPEN, EYES_OPEN, EYES_CLOSED }
    private BlinkState blinkState   = BlinkState.WAITING_OPEN;
    private int        blinkCount   = 0;
    private boolean    blinkPassed  = false;
    private boolean    blinkRunning = false;


    private float minEyeValueSeen = 1.0f;

    private ExecutorService inferenceExecutor;
    private Handler         mainHandler;
    private Runnable        blinkScanRunnable;

    private PreviewView  previewView;
    private ProgressBar  progressBar;
    private TextView     tvLivenessHint;
    private Button       btnVerify;

    private FaceDetector            detector;
    private FaceNetModel            faceNetModel;
    private FirebaseFirestore       db;
    private FirebaseAuth            auth;
    private FusedLocationProviderClient fusedLocationClient;

    private String  classId, className, subject, sessionId;
    private float[] currentEmbedding;

    private static final int CAMERA_PERMISSION   = 101;
    private static final int LOCATION_PERMISSION = 102;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance);

        previewView    = findViewById(R.id.previewView);
        progressBar    = findViewById(R.id.progressBar);
        tvLivenessHint = findViewById(R.id.tvLivenessHint);
        btnVerify      = findViewById(R.id.btnVerify);

        db   = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        inferenceExecutor   = Executors.newSingleThreadExecutor();
        mainHandler         = new Handler(Looper.getMainLooper());
        classId             = getIntent().getStringExtra("classId");

        db.collection("classes").document(classId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        className = doc.getString("name");
                        subject   = doc.getString("subject");
                    }
                });

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setMinFaceSize(MIN_FACE_SIZE_FRACTION)
                .build();
        detector = FaceDetection.getClient(options);

        try {
            faceNetModel = new FaceNetModel(this, "facenet.tflite");
            warmUpFaceNet();
        } catch (Exception e) {
            Toast.makeText(this, "Face model load failed", Toast.LENGTH_SHORT).show();
        }

        checkCameraPermission();
        loadActiveSession();
        btnVerify.setEnabled(false);

        checkAccessAndStart();

        btnVerify.setOnClickListener(v -> startBlinkChallenge());    }

    private void warmUpFaceNet() {
        inferenceExecutor.execute(() -> {
            try {
                Bitmap dummy = Bitmap.createBitmap(160, 160, Bitmap.Config.RGB_565);
                faceNetModel.getFaceEmbedding(dummy);
                dummy.recycle();
            } catch (Exception ignored) {}
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopBlinkScan();
        if (inferenceExecutor != null) inferenceExecutor.shutdown();
    }

    private void loadActiveSession() {
        db.collection("classes").document(classId).get()
                .addOnSuccessListener(doc -> {
                    sessionId = doc.getString("activeSessionId");
                    if (sessionId == null) {
                        Toast.makeText(this, "Attendance session not started",
                                Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                CameraSelector selector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT).build();
                provider.unbindAll();
                provider.bindToLifecycle(this, selector, preview);
            } catch (Exception e) { e.printStackTrace(); }
        }, ContextCompat.getMainExecutor(this));
    }


    private void startBlinkChallenge() {
        if (blinkRunning) return;

        // Reset all state
        blinkState      = BlinkState.WAITING_OPEN;
        blinkCount      = 0;
        blinkPassed     = false;
        blinkRunning    = true;
        minEyeValueSeen = 1.0f;

        btnVerify.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        setHint("Look at the camera and blink " + BLINKS_REQUIRED + " times");

        // Timeout after 10 seconds
        mainHandler.postDelayed(() -> {
            if (!blinkPassed) {
                stopBlinkScan();
                progressBar.setVisibility(View.GONE);                btnVerify.setEnabled(true);
                setHint("Tap the button and blink naturally");
                Toast.makeText(this,
                        "Liveness check timed out.\n"
                                + "Tips: Face the camera directly,\n"
                                + "ensure good lighting, blink slowly.",
                        Toast.LENGTH_LONG).show();
            }
        }, BLINK_TIMEOUT_MS);

        scheduleNextBlinkFrame();
    }

    private void scheduleNextBlinkFrame() {
        blinkScanRunnable = () -> {
            if (!blinkRunning) return;
            analyseBlink();
        };
        mainHandler.postDelayed(blinkScanRunnable, FRAME_INTERVAL_MS);
    }

    private void stopBlinkScan() {
        blinkRunning = false;
        if (blinkScanRunnable != null) {
            mainHandler.removeCallbacks(blinkScanRunnable);
            blinkScanRunnable = null;
        }
    }


    private void analyseBlink() {
        Bitmap bitmap = previewView.getBitmap();
        if (bitmap == null) {
            scheduleNextBlinkFrame();
            return;
        }

        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, 320, 240, false);
        bitmap.recycle();

        detector.process(InputImage.fromBitmap(scaled, 0))
                .addOnSuccessListener(faces -> {
                    scaled.recycle();
                    if (!blinkRunning) return;

                    if (faces.isEmpty()) {
                        setHint("No face detected — look directly at the camera");
                        scheduleNextBlinkFrame();
                        return;
                    }
                    if (faces.size() > 1) {
                        setHint("Multiple faces — only you should be in frame");
                        scheduleNextBlinkFrame();
                        return;
                    }

                    Face face = faces.get(0);
                    Float leftEye  = face.getLeftEyeOpenProbability();
                    Float rightEye = face.getRightEyeOpenProbability();


                    if (leftEye == null || rightEye == null) {
                        setHint("Move to better lighting and look at the camera");
                        scheduleNextBlinkFrame();
                        return;
                    }


                    float eyeValue = Math.min(leftEye, rightEye);

                    Log.d("BLINK", "state=" + blinkState
                            + " L=" + String.format("%.2f", leftEye)
                            + " R=" + String.format("%.2f", rightEye)
                            + " min=" + String.format("%.2f", eyeValue)
                            + " blinks=" + blinkCount);

                    // Track minimum eye value seen (catches missed frames)
                    if (eyeValue < minEyeValueSeen) {
                        minEyeValueSeen = eyeValue;
                    }

                    switch (blinkState) {

                        case WAITING_OPEN:
                            // Wait until both eyes are clearly open
                            if (eyeValue > EYE_OPEN_PROB) {
                                blinkState = BlinkState.EYES_OPEN;
                                minEyeValueSeen = eyeValue; // reset tracker
                                setHint("Eyes detected! Now blink "
                                        + BLINKS_REQUIRED + " times slowly...");
                            } else {
                                // Show live feedback so user knows camera is working
                                setHint("Opening eyes... ("
                                        + Math.round(eyeValue * 100) + "%) "
                                        + " — open eyes wide and look at camera");
                            }
                            break;

                        case EYES_OPEN:
                            if (eyeValue < minEyeValueSeen) minEyeValueSeen = eyeValue;

                            if (eyeValue < EYE_CLOSED_PROB
                                    || minEyeValueSeen < EYE_CLOSED_PROB) {
                                blinkState = BlinkState.EYES_CLOSED;
                                Log.d("BLINK", "Eyes closed! eyeVal="
                                        + eyeValue + " minSeen=" + minEyeValueSeen);
                            } else {
                                // Show progress feedback
                                int remaining = BLINKS_REQUIRED - blinkCount;
                                setHint("Blink " + remaining + " more time"
                                        + (remaining > 1 ? "s" : "")
                                        + " — blink naturally and slowly");
                            }
                            break;

                        case EYES_CLOSED:
                            if (eyeValue > EYE_OPEN_PROB) {
                                blinkCount++;
                                minEyeValueSeen = eyeValue; // reset for next blink
                                Log.d("BLINK", "BLINK #" + blinkCount + " complete!");

                                if (blinkCount >= BLINKS_REQUIRED) {
                                    blinkPassed = true;
                                    stopBlinkScan();
                                    setHint("Liveness verified! Hold still...");
                                    mainHandler.postDelayed(this::captureAndVerify, 400);
                                } else {
                                    int remaining = BLINKS_REQUIRED - blinkCount;
                                    setHint("Blink " + blinkCount + " detected! "
                                            + remaining + " more...");
                                    blinkState = BlinkState.EYES_OPEN;
                                }
                            }
                            break;
                    }

                    if (blinkRunning) scheduleNextBlinkFrame();
                })
                .addOnFailureListener(e -> {
                    scaled.recycle();
                    if (blinkRunning) scheduleNextBlinkFrame();
                });
    }

    private void captureAndVerify() {
        Bitmap bitmap = previewView.getBitmap();
        if (bitmap == null) {
            progressBar.setVisibility(View.GONE);
            btnVerify.setEnabled(true);
            Toast.makeText(this, "Camera error. Try again.", Toast.LENGTH_SHORT).show();
            return;
        }

        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, 320, 240, false);
        bitmap.recycle();

        detector.process(InputImage.fromBitmap(scaled, 0))
                .addOnSuccessListener(faces -> {
                    if (faces.isEmpty()) {
                        scaled.recycle();
                        progressBar.setVisibility(View.GONE);
                        btnVerify.setEnabled(true);
                        Toast.makeText(this, "Face not found. Try again.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (faces.size() > 1) {
                        scaled.recycle();
                        progressBar.setVisibility(View.GONE);
                        btnVerify.setEnabled(true);
                        Toast.makeText(this, "Multiple faces. Only you in frame.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Face face   = faces.get(0);
                    Rect bounds = face.getBoundingBox();

                    float sizeFrac = (float) bounds.width() / scaled.getWidth();
                    if (sizeFrac < MIN_FACE_SIZE_FRACTION) {
                        scaled.recycle();
                        progressBar.setVisibility(View.GONE);
                        btnVerify.setEnabled(true);
                        Toast.makeText(this, "Hold phone closer to your face.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    int left   = Math.max(0, bounds.left);
                    int top    = Math.max(0, bounds.top);
                    int right  = Math.min(scaled.getWidth(),  bounds.right);
                    int bottom = Math.min(scaled.getHeight(), bounds.bottom);
                    int w = right - left, h = bottom - top;

                    if (w <= 0 || h <= 0) {
                        scaled.recycle();
                        progressBar.setVisibility(View.GONE);
                        btnVerify.setEnabled(true);
                        Toast.makeText(this, "Face detection error. Try again.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Bitmap cropped = Bitmap.createScaledBitmap(
                            Bitmap.createBitmap(scaled, left, top, w, h),
                            160, 160, false);
                    scaled.recycle();

                    setHint("Verifying your identity...");
                    inferenceExecutor.execute(() -> {
                        float[] embedding = faceNetModel.getFaceEmbedding(cropped);
                        cropped.recycle();
                        runOnUiThread(() -> {
                            currentEmbedding = embedding;
                            verifyFace();
                        });
                    });
                })
                .addOnFailureListener(e -> {
                    scaled.recycle();
                    progressBar.setVisibility(View.GONE);
                    btnVerify.setEnabled(true);
                    Toast.makeText(this, "Face detection failed. Try again.",
                            Toast.LENGTH_SHORT).show();
                });
    }


    private void verifyFace() {
        String uid = auth.getUid();
        if (uid == null) {
            progressBar.setVisibility(View.GONE);
            btnVerify.setEnabled(true);
            return;
        }

        db.collection("users").document(uid).get()
                .addOnSuccessListener(userDoc -> {
                    if (!userDoc.exists()) {
                        progressBar.setVisibility(View.GONE);
                        btnVerify.setEnabled(true);
                        Toast.makeText(this, "User account not found.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    List<Double> stored = (List<Double>) userDoc.get("faceEmbedding");
                    if (stored == null || stored.isEmpty()) {
                        progressBar.setVisibility(View.GONE);
                        btnVerify.setEnabled(true);
                        Toast.makeText(this,
                                "Face not registered. Please register your face first.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    float[] storedEmb = new float[stored.size()];
                    for (int i = 0; i < stored.size(); i++)
                        storedEmb[i] = stored.get(i).floatValue();

                    float distance = calculateDistance(storedEmb, currentEmbedding);

                    Log.d("FACE_MATCH", "distance=" + distance
                            + " threshold=" + FACE_MATCH_THRESHOLD
                            + " result=" + (distance <= FACE_MATCH_THRESHOLD
                            ? "MATCH" : "MISMATCH"));

                    if (distance <= FACE_MATCH_THRESHOLD) {

                        Log.d("FACE_MATCH", "Face matched! dist=" + distance);
                        setHint("Face verified. Checking location...");
                        getLocation();

                    } else if (distance <= 1.1f) {

                        progressBar.setVisibility(View.GONE);
                        btnVerify.setEnabled(true);
                        setHint("Tap button to try again");
                        Toast.makeText(this,
                                "Face does not match your registered face.\n"
                                        + "Try better lighting or look directly at the camera.",
                                Toast.LENGTH_LONG).show();

                    } else {

                        progressBar.setVisibility(View.GONE);
                        btnVerify.setEnabled(true);
                        setHint("Tap button to try again");
                        Toast.makeText(this,
                                "Unknown face detected.\n"
                                        + "This face is not registered in the system.\n"
                                        + "Only registered students can mark attendance.",
                                Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    btnVerify.setEnabled(true);
                    Toast.makeText(this, "Database error. Check internet connection.",
                            Toast.LENGTH_SHORT).show();
                });
    }


    private float calculateDistance(float[] a, float[] b) {
        float sum = 0;
        for (int i = 0; i < a.length; i++) {
            float d = a[i] - b[i];
            sum += d * d;
        }
        return (float) Math.sqrt(sum);
    }


    private void getLocation() {
        // ── Step 1: Check if permission is granted. If not, REQUEST it. ──────────
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Ask the user for permission. Result handled in onRequestPermissionsResult.
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_PERMISSION);
            return;
        }

        setHint("Getting your location...");

        // ── Step 2: Try getLastLocation first (fast, no battery cost) ────────────
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        // Got a cached location — use it directly
                        checkRadius(location);
                    } else {
                        // ── Step 3: No cached location (fresh device / GPS just enabled)
                        //    Request a single fresh fix using LocationCallback ──────────
                        requestFreshLocation();
                    }
                })
                .addOnFailureListener(e -> requestFreshLocation());
    }

    private void requestFreshLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        setHint("Waiting for GPS signal...");

        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 3000L)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(1000L)
                .setMaxUpdates(1)
                .build();

        LocationCallback callback = new LocationCallback() {
            @Override
            public void onLocationResult(@androidx.annotation.NonNull LocationResult result) {
                fusedLocationClient.removeLocationUpdates(this);
                Location loc = result.getLastLocation();
                if (loc != null) {
                    checkRadius(loc);
                } else {
                    progressBar.setVisibility(View.GONE);
                    btnVerify.setEnabled(true);
                    Toast.makeText(AttendanceActivity.this,
                            "Could not get location.\nMake sure GPS is ON and you are not indoors.",
                            Toast.LENGTH_LONG).show();
                }
            }
        };

        // Timeout: if no fix within 10 seconds, show helpful message
        mainHandler.postDelayed(() -> {
            fusedLocationClient.removeLocationUpdates(callback);
            if (progressBar.getVisibility() == View.VISIBLE) {
                progressBar.setVisibility(View.GONE);
                btnVerify.setEnabled(true);
                Toast.makeText(this,
                        "GPS timed out. Move to an open area\nand try again.",
                        Toast.LENGTH_LONG).show();
            }
        }, 10000L);

        fusedLocationClient.requestLocationUpdates(request, callback,
                Looper.getMainLooper());
    }

    private void checkRadius(Location location) {
        db.collection("attendanceSessions").document(sessionId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        progressBar.setVisibility(View.GONE);
                        btnVerify.setEnabled(true);
                        Toast.makeText(this, "Session not found.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    double centerLat = doc.getDouble("centerLat");
                    double centerLng = doc.getDouble("centerLng");
                    double radius    = doc.getDouble("radius");
                    float[] results  = new float[1];
                    Location.distanceBetween(location.getLatitude(), location.getLongitude(),
                            centerLat, centerLng, results);
                    Log.d("ATTENDANCE_DEBUG", "dist=" + results[0] + " radius=" + radius);
                    if (results[0] <= radius) {
                        checkStudentApproval(location, results[0]);
                    } else {
                        progressBar.setVisibility(View.GONE);
                        btnVerify.setEnabled(true);
                        Toast.makeText(this,
                                "You are outside the classroom.\nDistance: "
                                        + (int) results[0] + "m  (allowed: " + (int) radius + "m)",
                                Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    btnVerify.setEnabled(true);
                    Toast.makeText(this, "Session error.", Toast.LENGTH_SHORT).show();
                });
    }
    private void checkStudentApproval(Location location, float distance) {

        String email = auth.getCurrentUser()
                .getEmail()
                .trim()
                .toLowerCase();

        db.collection("classes")
                .document(classId)
                .collection("students")
                .document(email)
                .get(Source.SERVER)
                .addOnSuccessListener(doc -> {

                    if (!doc.exists()) {
                        progressBar.setVisibility(View.GONE);
                        btnVerify.setEnabled(true);
                        Toast.makeText(this,
                                "You are not in this class ❌",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }


                    checkDuplicateAttendance(location, distance);

                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    btnVerify.setEnabled(true);
                    Toast.makeText(this,
                            "Error: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void checkDuplicateAttendance(Location location, float distance) {
        db.collection("attendance").document(sessionId)
                .collection("students").document(auth.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        progressBar.setVisibility(View.GONE);
                        btnVerify.setEnabled(true);
                        Toast.makeText(this, "Attendance already marked for this session.",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        saveAttendance(location, distance);
                    }
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    btnVerify.setEnabled(true);
                    Toast.makeText(this, "Duplicate check failed.", Toast.LENGTH_SHORT).show();
                });
    }


    private void saveAttendance(Location location, float distance) {
        String studentId = auth.getUid();
        String userEmail = auth.getCurrentUser().getEmail().trim().toLowerCase();
        if (studentId == null || userEmail == null) {
            progressBar.setVisibility(View.GONE);
            btnVerify.setEnabled(true);
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show();
            return;
        }
        db.collection("classes").document(classId)
                .collection("students").document(userEmail).get()
                .addOnSuccessListener(studentDoc -> {
                    String studentName = "Student", rollNo = "", department = "";
                    if (studentDoc.exists()) {
                        String n = studentDoc.getString("name");
                        String d = studentDoc.getString("department");
                        Object r = studentDoc.get("rollNo");
                        if (n != null) studentName = n;
                        if (d != null) department  = d;
                        if (r != null) rollNo      = String.valueOf(r);
                    }
                    String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                    String time = new SimpleDateFormat("HH:mm:ss",   Locale.getDefault()).format(new Date());

                    Map<String, Object> data = new HashMap<>();
                    data.put("name", studentName);   data.put("email",      userEmail);
                    data.put("studentId", studentId); data.put("classId",   classId);
                    data.put("className", className); data.put("subject",   subject);
                    data.put("rollNo", rollNo);        data.put("department",department);
                    data.put("sessionId", sessionId); data.put("status",    "present");
                    data.put("present", true);         data.put("date",      date);
                    data.put("time", time);
                    data.put("lat",      location.getLatitude());
                    data.put("lng",      location.getLongitude());
                    data.put("distance", distance);
                    data.put("timestamp", FieldValue.serverTimestamp());

                    DocumentReference nestedRef = db.collection("attendance")
                            .document(sessionId).collection("students").document(studentId);
                    DocumentReference flatRef = db.collection("attendance_records")
                            .document(studentId + "_" + sessionId);

                    nestedRef.get().addOnSuccessListener(snapshot -> {
                        if (snapshot.exists()) {
                            progressBar.setVisibility(View.GONE);
                            btnVerify.setEnabled(true);
                            Toast.makeText(this, "Attendance already marked.",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        nestedRef.set(data).addOnSuccessListener(unused -> {
                            flatRef.set(data)
                                    .addOnSuccessListener(u2 ->
                                            Log.d("ATTENDANCE_FLOW", "Flat copy saved"))
                                    .addOnFailureListener(e ->
                                            Log.e("ATTENDANCE_ERROR", "Flat failed: " + e.getMessage()));
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(this, "Attendance Marked Successfully",
                                    Toast.LENGTH_SHORT).show();
                            String today = new SimpleDateFormat("yyyy-MM-dd",
                                    Locale.getDefault()).format(new Date());
                            DocumentReference summaryRef =
                                    db.collection("attendance_summary").document(today);
                            summaryRef.get().addOnSuccessListener(doc -> {
                                Map<String, Object> upd = new HashMap<>();
                                if (doc.exists()) {
                                    Long p = doc.getLong("present");
                                    upd.put("present", (p != null ? p : 0L) + 1);
                                } else {
                                    upd.put("present", 1);
                                    upd.put("absent", 0);
                                    upd.put("late", 0);
                                }
                                summaryRef.set(upd, SetOptions.merge());
                            });
                            finish();
                        }).addOnFailureListener(e -> {
                            progressBar.setVisibility(View.GONE);
                            btnVerify.setEnabled(true);
                            Toast.makeText(this, "Save failed: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        });
                    });
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    btnVerify.setEnabled(true);
                    Toast.makeText(this, "Failed to fetch student data.",
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void setHint(String msg) {
        if (tvLivenessHint != null) {
            tvLivenessHint.setText(msg);
            tvLivenessHint.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) startCamera();
        if (requestCode == LOCATION_PERMISSION && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) getLocation();
    }
    private void checkAccessAndStart() {

        String userEmail = auth.getCurrentUser()
                .getEmail()
                .trim()
                .toLowerCase();

        db.collection("classes")
                .document(classId)
                .collection("students")
                .document(userEmail)
                .get()
                .addOnSuccessListener(doc -> {

                    if (!doc.exists()) {

                        Toast.makeText(this,
                                "You are not part of this class ❌",
                                Toast.LENGTH_SHORT).show();

                        finish(); // 🔥 STOP FULL ACTIVITY
                        return;
                    }

                    // ✅ allowed → continue
                    btnVerify.setEnabled(true);

                })
                .addOnFailureListener(e -> {

                    Toast.makeText(this,
                            "Error: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();

                    finish();
                });
    }
}
/*package com.example.s;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.firestore.SetOptions;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.*;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class AttendanceActivity extends AppCompatActivity {

    private static final float FACE_MATCH_THRESHOLD   = 0.85f;
    private static final float MIN_FACE_SIZE_FRACTION = 0.15f;


    private static final float EYE_OPEN_PROB   = 0.65f;


    private static final float EYE_CLOSED_PROB = 0.45f;


    private static final int  BLINKS_REQUIRED = 2;


    private static final long BLINK_TIMEOUT_MS = 10000L;


    private static final long FRAME_INTERVAL_MS = 30L;

    private enum BlinkState { WAITING_OPEN, EYES_OPEN, EYES_CLOSED }
    private BlinkState blinkState   = BlinkState.WAITING_OPEN;
    private int        blinkCount   = 0;
    private boolean    blinkPassed  = false;
    private boolean    blinkRunning = false;


    private float minEyeValueSeen = 1.0f;

    private ExecutorService inferenceExecutor;
    private Handler         mainHandler;
    private Runnable        blinkScanRunnable;

    private PreviewView  previewView;
    private ProgressBar  progressBar;
    private TextView     tvLivenessHint;
    private Button       btnVerify;

    private FaceDetector            detector;
    private FaceNetModel            faceNetModel;
    private FirebaseFirestore       db;
    private FirebaseAuth            auth;
    private FusedLocationProviderClient fusedLocationClient;

    private String  classId, className, subject, sessionId;
    private float[] currentEmbedding;

    private static final int CAMERA_PERMISSION   = 101;
    private static final int LOCATION_PERMISSION = 102;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance);

        previewView    = findViewById(R.id.previewView);
        progressBar    = findViewById(R.id.progressBar);
        tvLivenessHint = findViewById(R.id.tvLivenessHint);
        btnVerify      = findViewById(R.id.btnVerify);

        db   = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        inferenceExecutor   = Executors.newSingleThreadExecutor();
        mainHandler         = new Handler(Looper.getMainLooper());
        classId             = getIntent().getStringExtra("classId");

        db.collection("classes").document(classId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        className = doc.getString("name");
                        subject   = doc.getString("subject");
                    }
                });

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setMinFaceSize(MIN_FACE_SIZE_FRACTION)
                .build();
        detector = FaceDetection.getClient(options);

        try {
            faceNetModel = new FaceNetModel(this, "facenet.tflite");
            warmUpFaceNet();
        } catch (Exception e) {
            Toast.makeText(this, "Face model load failed", Toast.LENGTH_SHORT).show();
        }

        checkCameraPermission();
        loadActiveSession();
        btnVerify.setEnabled(false);

        checkAccessAndStart();

        btnVerify.setOnClickListener(v -> startBlinkChallenge());    }

    private void warmUpFaceNet() {
        inferenceExecutor.execute(() -> {
            try {
                Bitmap dummy = Bitmap.createBitmap(160, 160, Bitmap.Config.RGB_565);
                faceNetModel.getFaceEmbedding(dummy);
                dummy.recycle();
            } catch (Exception ignored) {}
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopBlinkScan();
        if (inferenceExecutor != null) inferenceExecutor.shutdown();
    }

    private void loadActiveSession() {
        db.collection("classes").document(classId).get()
                .addOnSuccessListener(doc -> {
                    sessionId = doc.getString("activeSessionId");
                    if (sessionId == null) {
                        Toast.makeText(this, "Attendance session not started",
                                Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                CameraSelector selector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT).build();
                provider.unbindAll();
                provider.bindToLifecycle(this, selector, preview);
            } catch (Exception e) { e.printStackTrace(); }
        }, ContextCompat.getMainExecutor(this));
    }


    private void startBlinkChallenge() {
        if (blinkRunning) return;

        // Reset all state
        blinkState      = BlinkState.WAITING_OPEN;
        blinkCount      = 0;
        blinkPassed     = false;
        blinkRunning    = true;
        minEyeValueSeen = 1.0f;

        btnVerify.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        setHint("Look at the camera and blink " + BLINKS_REQUIRED + " times");

        // Timeout after 10 seconds
        mainHandler.postDelayed(() -> {
            if (!blinkPassed) {
                stopBlinkScan();
                progressBar.setVisibility(View.GONE);                btnVerify.setEnabled(true);
                setHint("Tap the button and blink naturally");
                Toast.makeText(this,
                        "Liveness check timed out.\n"
                                + "Tips: Face the camera directly,\n"
                                + "ensure good lighting, blink slowly.",
                        Toast.LENGTH_LONG).show();
            }
        }, BLINK_TIMEOUT_MS);

        scheduleNextBlinkFrame();
    }

    private void scheduleNextBlinkFrame() {
        blinkScanRunnable = () -> {
            if (!blinkRunning) return;
            analyseBlink();
        };
        mainHandler.postDelayed(blinkScanRunnable, FRAME_INTERVAL_MS);
    }

    private void stopBlinkScan() {
        blinkRunning = false;
        if (blinkScanRunnable != null) {
            mainHandler.removeCallbacks(blinkScanRunnable);
            blinkScanRunnable = null;
        }
    }


    private void analyseBlink() {
        Bitmap bitmap = previewView.getBitmap();
        if (bitmap == null) {
            scheduleNextBlinkFrame();
            return;
        }

        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, 320, 240, false);
        bitmap.recycle();

        detector.process(InputImage.fromBitmap(scaled, 0))
                .addOnSuccessListener(faces -> {
                    scaled.recycle();
                    if (!blinkRunning) return;

                    if (faces.isEmpty()) {
                        setHint("No face detected — look directly at the camera");
                        scheduleNextBlinkFrame();
                        return;
                    }
                    if (faces.size() > 1) {
                        setHint("Multiple faces — only you should be in frame");
                        scheduleNextBlinkFrame();
                        return;
                    }

                    Face face = faces.get(0);
                    Float leftEye  = face.getLeftEyeOpenProbability();
                    Float rightEye = face.getRightEyeOpenProbability();


                    if (leftEye == null || rightEye == null) {
                        setHint("Move to better lighting and look at the camera");
                        scheduleNextBlinkFrame();
                        return;
                    }


                    float eyeValue = Math.min(leftEye, rightEye);

                    Log.d("BLINK", "state=" + blinkState
                            + " L=" + String.format("%.2f", leftEye)
                            + " R=" + String.format("%.2f", rightEye)
                            + " min=" + String.format("%.2f", eyeValue)
                            + " blinks=" + blinkCount);

                    // Track minimum eye value seen (catches missed frames)
                    if (eyeValue < minEyeValueSeen) {
                        minEyeValueSeen = eyeValue;
                    }

                    switch (blinkState) {

                        case WAITING_OPEN:
                            // Wait until both eyes are clearly open
                            if (eyeValue > EYE_OPEN_PROB) {
                                blinkState = BlinkState.EYES_OPEN;
                                minEyeValueSeen = eyeValue; // reset tracker
                                setHint("Eyes detected! Now blink "
                                        + BLINKS_REQUIRED + " times slowly...");
                            } else {
                                // Show live feedback so user knows camera is working
                                setHint("Opening eyes... ("
                                        + Math.round(eyeValue * 100) + "%) "
                                        + " — open eyes wide and look at camera");
                            }
                            break;

                        case EYES_OPEN:
                            if (eyeValue < minEyeValueSeen) minEyeValueSeen = eyeValue;

                            if (eyeValue < EYE_CLOSED_PROB
                                    || minEyeValueSeen < EYE_CLOSED_PROB) {
                                blinkState = BlinkState.EYES_CLOSED;
                                Log.d("BLINK", "Eyes closed! eyeVal="
                                        + eyeValue + " minSeen=" + minEyeValueSeen);
                            } else {
                                // Show progress feedback
                                int remaining = BLINKS_REQUIRED - blinkCount;
                                setHint("Blink " + remaining + " more time"
                                        + (remaining > 1 ? "s" : "")
                                        + " — blink naturally and slowly");
                            }
                            break;

                        case EYES_CLOSED:
                            if (eyeValue > EYE_OPEN_PROB) {
                                blinkCount++;
                                minEyeValueSeen = eyeValue; // reset for next blink
                                Log.d("BLINK", "BLINK #" + blinkCount + " complete!");

                                if (blinkCount >= BLINKS_REQUIRED) {
                                    blinkPassed = true;
                                    stopBlinkScan();
                                    setHint("Liveness verified! Hold still...");
                                    mainHandler.postDelayed(this::captureAndVerify, 400);
                                } else {
                                    int remaining = BLINKS_REQUIRED - blinkCount;
                                    setHint("Blink " + blinkCount + " detected! "
                                            + remaining + " more...");
                                    blinkState = BlinkState.EYES_OPEN;
                                }
                            }
                            break;
                    }

                    if (blinkRunning) scheduleNextBlinkFrame();
                })
                .addOnFailureListener(e -> {
                    scaled.recycle();
                    if (blinkRunning) scheduleNextBlinkFrame();
                });
    }

    private void captureAndVerify() {
        Bitmap bitmap = previewView.getBitmap();
        if (bitmap == null) {
            progressBar.setVisibility(View.GONE);
            btnVerify.setEnabled(true);
            Toast.makeText(this, "Camera error. Try again.", Toast.LENGTH_SHORT).show();
            return;
        }

        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, 320, 240, false);
        bitmap.recycle();

        detector.process(InputImage.fromBitmap(scaled, 0))
                .addOnSuccessListener(faces -> {
                    if (faces.isEmpty()) {
                        scaled.recycle();
                        progressBar.setVisibility(View.GONE);
                        btnVerify.setEnabled(true);
                        Toast.makeText(this, "Face not found. Try again.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (faces.size() > 1) {
                        scaled.recycle();
                        progressBar.setVisibility(View.GONE);
                        btnVerify.setEnabled(true);
                        Toast.makeText(this, "Multiple faces. Only you in frame.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Face face   = faces.get(0);
                    Rect bounds = face.getBoundingBox();

                    float sizeFrac = (float) bounds.width() / scaled.getWidth();
                    if (sizeFrac < MIN_FACE_SIZE_FRACTION) {
                        scaled.recycle();
                        progressBar.setVisibility(View.GONE);
                        btnVerify.setEnabled(true);
                        Toast.makeText(this, "Hold phone closer to your face.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    int left   = Math.max(0, bounds.left);
                    int top    = Math.max(0, bounds.top);
                    int right  = Math.min(scaled.getWidth(),  bounds.right);
                    int bottom = Math.min(scaled.getHeight(), bounds.bottom);
                    int w = right - left, h = bottom - top;

                    if (w <= 0 || h <= 0) {
                        scaled.recycle();
                        progressBar.setVisibility(View.GONE);
                        btnVerify.setEnabled(true);
                        Toast.makeText(this, "Face detection error. Try again.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Bitmap cropped = Bitmap.createScaledBitmap(
                            Bitmap.createBitmap(scaled, left, top, w, h),
                            160, 160, false);
                    scaled.recycle();

                    setHint("Verifying your identity...");
                    inferenceExecutor.execute(() -> {
                        float[] embedding = faceNetModel.getFaceEmbedding(cropped);
                        cropped.recycle();
                        runOnUiThread(() -> {
                            currentEmbedding = embedding;
                            verifyFace();
                        });
                    });
                })
                .addOnFailureListener(e -> {
                    scaled.recycle();
                    progressBar.setVisibility(View.GONE);
                    btnVerify.setEnabled(true);
                    Toast.makeText(this, "Face detection failed. Try again.",
                            Toast.LENGTH_SHORT).show();
                });
    }


    private void verifyFace() {
        String uid = auth.getUid();
        if (uid == null) {
            progressBar.setVisibility(View.GONE);
            btnVerify.setEnabled(true);
            return;
        }

        db.collection("users").document(uid).get()
                .addOnSuccessListener(userDoc -> {
                    if (!userDoc.exists()) {
                        progressBar.setVisibility(View.GONE);
                        btnVerify.setEnabled(true);
                        Toast.makeText(this, "User account not found.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    List<Double> stored = (List<Double>) userDoc.get("faceEmbedding");
                    if (stored == null || stored.isEmpty()) {
                        progressBar.setVisibility(View.GONE);
                        btnVerify.setEnabled(true);
                        Toast.makeText(this,
                                "Face not registered. Please register your face first.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    float[] storedEmb = new float[stored.size()];
                    for (int i = 0; i < stored.size(); i++)
                        storedEmb[i] = stored.get(i).floatValue();

                    float distance = calculateDistance(storedEmb, currentEmbedding);

                    Log.d("FACE_MATCH", "distance=" + distance
                            + " threshold=" + FACE_MATCH_THRESHOLD
                            + " result=" + (distance <= FACE_MATCH_THRESHOLD
                            ? "MATCH" : "MISMATCH"));

                    if (distance <= FACE_MATCH_THRESHOLD) {

                        Log.d("FACE_MATCH", "Face matched! dist=" + distance);
                        setHint("Face verified. Checking location...");
                        getLocation();

                    } else if (distance <= 1.1f) {

                        progressBar.setVisibility(View.GONE);
                        btnVerify.setEnabled(true);
                        setHint("Tap button to try again");
                        Toast.makeText(this,
                                "Face does not match your registered face.\n"
                                        + "Try better lighting or look directly at the camera.",
                                Toast.LENGTH_LONG).show();

                    } else {

                        progressBar.setVisibility(View.GONE);
                        btnVerify.setEnabled(true);
                        setHint("Tap button to try again");
                        Toast.makeText(this,
                                "Unknown face detected.\n"
                                        + "This face is not registered in the system.\n"
                                        + "Only registered students can mark attendance.",
                                Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    btnVerify.setEnabled(true);
                    Toast.makeText(this, "Database error. Check internet connection.",
                            Toast.LENGTH_SHORT).show();
                });
    }


    private float calculateDistance(float[] a, float[] b) {
        float sum = 0;
        for (int i = 0; i < a.length; i++) {
            float d = a[i] - b[i];
            sum += d * d;
        }
        return (float) Math.sqrt(sum);
    }


    private void getLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            progressBar.setVisibility(View.GONE);
            btnVerify.setEnabled(true);
            Toast.makeText(this, "Location permission required.", Toast.LENGTH_SHORT).show();
            return;
        }
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location == null) {
                        progressBar.setVisibility(View.GONE);
                        btnVerify.setEnabled(true);
                        Toast.makeText(this,
                                "Cannot get location. Enable GPS and try again.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    checkRadius(location);
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    btnVerify.setEnabled(true);
                    Toast.makeText(this, "Location failed.", Toast.LENGTH_SHORT).show();
                });
    }

    private void checkRadius(Location location) {
        db.collection("attendanceSessions").document(sessionId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        progressBar.setVisibility(View.GONE);
                        btnVerify.setEnabled(true);
                        Toast.makeText(this, "Session not found.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    double centerLat = doc.getDouble("centerLat");
                    double centerLng = doc.getDouble("centerLng");
                    double radius    = doc.getDouble("radius");
                    float[] results  = new float[1];
                    Location.distanceBetween(location.getLatitude(), location.getLongitude(),
                            centerLat, centerLng, results);
                    Log.d("ATTENDANCE_DEBUG", "dist=" + results[0] + " radius=" + radius);
                    if (results[0] <= radius) {
                        checkStudentApproval(location, results[0]);
                    } else {
                        progressBar.setVisibility(View.GONE);
                        btnVerify.setEnabled(true);
                        Toast.makeText(this,
                                "You are outside the classroom.\nDistance: "
                                        + (int) results[0] + "m  (allowed: " + (int) radius + "m)",
                                Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    btnVerify.setEnabled(true);
                    Toast.makeText(this, "Session error.", Toast.LENGTH_SHORT).show();
                });
    }
    private void checkStudentApproval(Location location, float distance) {

        String email = auth.getCurrentUser()
                .getEmail()
                .trim()
                .toLowerCase();

        db.collection("classes")
                .document(classId)
                .collection("students")
                .document(email)
                .get(Source.SERVER)
                .addOnSuccessListener(doc -> {

                    if (!doc.exists()) {
                        progressBar.setVisibility(View.GONE);
                        btnVerify.setEnabled(true);
                        Toast.makeText(this,
                                "You are not in this class ❌",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }


                    checkDuplicateAttendance(location, distance);

                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    btnVerify.setEnabled(true);
                    Toast.makeText(this,
                            "Error: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void checkDuplicateAttendance(Location location, float distance) {
        db.collection("attendance").document(sessionId)
                .collection("students").document(auth.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        progressBar.setVisibility(View.GONE);
                        btnVerify.setEnabled(true);
                        Toast.makeText(this, "Attendance already marked for this session.",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        saveAttendance(location, distance);
                    }
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    btnVerify.setEnabled(true);
                    Toast.makeText(this, "Duplicate check failed.", Toast.LENGTH_SHORT).show();
                });
    }


    private void saveAttendance(Location location, float distance) {
        String studentId = auth.getUid();
        String userEmail = auth.getCurrentUser().getEmail().trim().toLowerCase();
        if (studentId == null || userEmail == null) {
            progressBar.setVisibility(View.GONE);
            btnVerify.setEnabled(true);
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show();
            return;
        }
        db.collection("classes").document(classId)
                .collection("students").document(userEmail).get()
                .addOnSuccessListener(studentDoc -> {
                    String studentName = "Student", rollNo = "", department = "";
                    if (studentDoc.exists()) {
                        String n = studentDoc.getString("name");
                        String d = studentDoc.getString("department");
                        Object r = studentDoc.get("rollNo");
                        if (n != null) studentName = n;
                        if (d != null) department  = d;
                        if (r != null) rollNo      = String.valueOf(r);
                    }
                    String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                    String time = new SimpleDateFormat("HH:mm:ss",   Locale.getDefault()).format(new Date());

                    Map<String, Object> data = new HashMap<>();
                    data.put("name", studentName);   data.put("email",      userEmail);
                    data.put("studentId", studentId); data.put("classId",   classId);
                    data.put("className", className); data.put("subject",   subject);
                    data.put("rollNo", rollNo);        data.put("department",department);
                    data.put("sessionId", sessionId); data.put("status",    "present");
                    data.put("present", true);         data.put("date",      date);
                    data.put("time", time);
                    data.put("lat",      location.getLatitude());
                    data.put("lng",      location.getLongitude());
                    data.put("distance", distance);
                    data.put("timestamp", FieldValue.serverTimestamp());

                    DocumentReference nestedRef = db.collection("attendance")
                            .document(sessionId).collection("students").document(studentId);
                    DocumentReference flatRef = db.collection("attendance_records")
                            .document(studentId + "_" + sessionId);

                    nestedRef.get().addOnSuccessListener(snapshot -> {
                        if (snapshot.exists()) {
                            progressBar.setVisibility(View.GONE);
                            btnVerify.setEnabled(true);
                            Toast.makeText(this, "Attendance already marked.",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        nestedRef.set(data).addOnSuccessListener(unused -> {
                            flatRef.set(data)
                                    .addOnSuccessListener(u2 ->
                                            Log.d("ATTENDANCE_FLOW", "Flat copy saved"))
                                    .addOnFailureListener(e ->
                                            Log.e("ATTENDANCE_ERROR", "Flat failed: " + e.getMessage()));
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(this, "Attendance Marked Successfully",
                                    Toast.LENGTH_SHORT).show();
                            String today = new SimpleDateFormat("yyyy-MM-dd",
                                    Locale.getDefault()).format(new Date());
                            DocumentReference summaryRef =
                                    db.collection("attendance_summary").document(today);
                            summaryRef.get().addOnSuccessListener(doc -> {
                                Map<String, Object> upd = new HashMap<>();
                                if (doc.exists()) {
                                    Long p = doc.getLong("present");
                                    upd.put("present", (p != null ? p : 0L) + 1);
                                } else {
                                    upd.put("present", 1);
                                    upd.put("absent", 0);
                                    upd.put("late", 0);
                                }
                                summaryRef.set(upd, SetOptions.merge());
                            });
                            finish();
                        }).addOnFailureListener(e -> {
                            progressBar.setVisibility(View.GONE);
                            btnVerify.setEnabled(true);
                            Toast.makeText(this, "Save failed: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        });
                    });
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    btnVerify.setEnabled(true);
                    Toast.makeText(this, "Failed to fetch student data.",
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void setHint(String msg) {
        if (tvLivenessHint != null) {
            tvLivenessHint.setText(msg);
            tvLivenessHint.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) startCamera();
        if (requestCode == LOCATION_PERMISSION && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) getLocation();
    }
    private void checkAccessAndStart() {

        String userEmail = auth.getCurrentUser()
                .getEmail()
                .trim()
                .toLowerCase();

        db.collection("classes")
                .document(classId)
                .collection("students")
                .document(userEmail)
                .get()
                .addOnSuccessListener(doc -> {

                    if (!doc.exists()) {

                        Toast.makeText(this,
                                "You are not part of this class ❌",
                                Toast.LENGTH_SHORT).show();

                        finish(); // 🔥 STOP FULL ACTIVITY
                        return;
                    }

                    // ✅ allowed → continue
                    btnVerify.setEnabled(true);

                })
                .addOnFailureListener(e -> {

                    Toast.makeText(this,
                            "Error: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();

                    finish();
                });
    }
}*/

































