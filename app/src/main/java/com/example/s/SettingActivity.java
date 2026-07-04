package com.example.s;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class SettingActivity extends AppCompatActivity {

    private EditText etRadius;
    private TextView tvCurrentLocation;
    private double selectedLat = 0.0;
    private double selectedLng = 0.0;
    private AppCompatButton btnSetLocation, btnSaveSettings;
    private FusedLocationProviderClient fusedLocationClient;
    private FirebaseFirestore db;
    private double lat = 0, lon = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);

        etRadius = findViewById(R.id.etRadius);
        tvCurrentLocation = findViewById(R.id.tvCurrentLocation);
        btnSetLocation = findViewById(R.id.btnSetLocation);
        btnSaveSettings = findViewById(R.id.btnSaveSettings);

        String classId = getIntent().getStringExtra("classId");
        db = FirebaseFirestore.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        btnSetLocation.setOnClickListener(v -> {

            // Replace with real GPS values
            selectedLat = 18.5204;
            selectedLng = 73.8567;

            Toast.makeText(this, "Location Captured", Toast.LENGTH_SHORT).show();
        });
        btnSaveSettings.setOnClickListener(v -> {

            String radiusStr = etRadius.getText().toString();

            if(radiusStr.isEmpty()){
                Toast.makeText(this,"Enter radius",Toast.LENGTH_SHORT).show();
                return;
            }
            if(selectedLat == 0.0 || selectedLng == 0.0){
                Toast.makeText(this,"Capture location first",Toast.LENGTH_SHORT).show();
                return;
            }
            double radius = Double.parseDouble(radiusStr);

            Map<String,Object> data = new HashMap<>();
            data.put("radius", radius);
            data.put("centerLat", selectedLat);
            data.put("centerLng", selectedLng);

            FirebaseFirestore.getInstance()
                    .collection("classes")
                    .document(classId)
                    .update(data)
                    .addOnSuccessListener(unused ->
                            Toast.makeText(this,"Settings Saved",Toast.LENGTH_SHORT).show());
        });
    }

    private void fetchCurrentCoordinates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                lat = location.getLatitude();
                lon = location.getLongitude();
                tvCurrentLocation.setText("Location Captured: " + lat + ", " + lon);
                Toast.makeText(this, "Classroom coordinates set!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Could not get current location. Make sure GPS is on.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void saveGeofenceSettings() {
        String radiusStr = etRadius.getText().toString().trim();
        if (radiusStr.isEmpty() || lat == 0) {
            Toast.makeText(this, "Capture location and set radius first", Toast.LENGTH_SHORT).show();
            return;
        }

        double radius;
        try {
            radius = Double.parseDouble(radiusStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid radius format", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> settings = new HashMap<>();
        settings.put("classroomLat", lat);
        settings.put("classroomLon", lon);
        settings.put("allowedRadius", radius);

        String teacherId = FirebaseAuth.getInstance().getUid();
        if (teacherId == null) return;

        db.collection("teachers").document(teacherId)
                .set(settings)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Classroom Geofence Saved! ✅", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}
