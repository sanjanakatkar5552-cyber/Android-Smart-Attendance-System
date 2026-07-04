package com.example.s;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;

import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;

import java.util.HashMap;
import java.util.Map;

public class ClassroomSettingActivity extends AppCompatActivity {

    private MapView mapView;
    private GeoPoint classroomLocation;
    private Marker marker;
    private Polygon radiusCircle;

    private FusedLocationProviderClient fusedLocationClient;

    private TextInputEditText etRadius;
    private TextView tvLatitude, tvLongitude;
    private SwitchMaterial switchFace, switchLocation, switchQR;

    private Button btnCapture, btnTestRadius;
    private MaterialButton btnSave;

    private FirebaseFirestore db;
    private String classId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_classroom_setting);

        Configuration.getInstance().setUserAgentValue(getPackageName());

        db = FirebaseFirestore.getInstance();
        classId = getIntent().getStringExtra("classId");

        mapView = findViewById(R.id.mapView);
        etRadius = findViewById(R.id.etRadius);
        tvLatitude = findViewById(R.id.tvLatitude);
        tvLongitude = findViewById(R.id.tvLongitude);
        switchFace = findViewById(R.id.switchFace);
        switchLocation = findViewById(R.id.switchLocation);
        switchQR = findViewById(R.id.switchQR);

        btnCapture = findViewById(R.id.btnCapture);
        btnTestRadius = findViewById(R.id.btnTestRadius);
        btnSave = findViewById(R.id.btnSave);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);

        MapController mapController = (MapController) mapView.getController();
        mapController.setZoom(18.0);

        GeoPoint startPoint = new GeoPoint(18.5204, 73.8567);
        mapController.setCenter(startPoint);

        MapEventsReceiver receiver = new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {

                classroomLocation = p;

                tvLatitude.setText("Latitude: " + p.getLatitude());
                tvLongitude.setText("Longitude: " + p.getLongitude());

                addMarker(p);
                updateRadiusCircle();

                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                return false;
            }
        };

        mapView.getOverlays().add(new MapEventsOverlay(receiver));


        btnCapture.setOnClickListener(v -> captureCurrentLocation());

        btnTestRadius.setOnClickListener(v -> testRadius());

        btnSave.setOnClickListener(v -> saveSettings());
    }

    private void addMarker(GeoPoint point) {

        if (marker != null) {
            mapView.getOverlays().remove(marker);
        }

        marker = new Marker(mapView);
        marker.setPosition(point);
        marker.setTitle("Classroom Location");

        mapView.getOverlays().add(marker);
        mapView.invalidate();
    }

    private void updateRadiusCircle() {

        if (classroomLocation == null) return;

        String radiusStr = etRadius.getText().toString();
        if (radiusStr.isEmpty()) return;

        int radius = Integer.parseInt(radiusStr);

        if (radiusCircle != null) {
            mapView.getOverlayManager().remove(radiusCircle);
        }

        radiusCircle = new Polygon(mapView);

        radiusCircle.setPoints(Polygon.pointsAsCircle(classroomLocation, radius));
        radiusCircle.getOutlinePaint().setColor(Color.BLUE);
        radiusCircle.getFillPaint().setColor(0x220000FF);

        mapView.getOverlayManager().add(radiusCircle);
        mapView.invalidate();
    }

    private void captureCurrentLocation() {

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {

            if (location != null) {

                double lat = location.getLatitude();
                double lon = location.getLongitude();

                classroomLocation = new GeoPoint(lat, lon);

                tvLatitude.setText("Latitude: " + lat);
                tvLongitude.setText("Longitude: " + lon);

                MapController mapController = (MapController) mapView.getController();
                mapController.setCenter(classroomLocation);
                mapController.setZoom(19.0);

                addMarker(classroomLocation);
                updateRadiusCircle();

            } else {

                Toast.makeText(this,
                        "Turn ON GPS to get location",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void testRadius() {

        if (classroomLocation == null || radiusCircle == null) {
            Toast.makeText(this, "Set location & radius first", Toast.LENGTH_SHORT).show();
            return;
        }

        GeoPoint current = (GeoPoint) mapView.getMapCenter();

        float[] results = new float[1];

        Location.distanceBetween(
                classroomLocation.getLatitude(),
                classroomLocation.getLongitude(),
                current.getLatitude(),
                current.getLongitude(),
                results
        );

        if (results[0] <= radiusCircle.getDistance()) {
            Toast.makeText(this, "Device inside radius", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Device outside radius", Toast.LENGTH_LONG).show();
        }
    }

    private void saveSettings() {

        if (classroomLocation == null) {
            Toast.makeText(this, "Select location first", Toast.LENGTH_SHORT).show();
            return;
        }

        String radiusStr = etRadius.getText().toString();

        if (radiusStr.isEmpty()) {
            Toast.makeText(this, "Enter radius", Toast.LENGTH_SHORT).show();
            return;
        }

        int radius = Integer.parseInt(radiusStr);

        Map<String, Object> settings = new HashMap<>();

        settings.put("centerLat", classroomLocation.getLatitude());
        settings.put("centerLng", classroomLocation.getLongitude());
        settings.put("radius", radius);
        settings.put("faceEnabled", switchFace.isChecked());
        settings.put("locationEnabled", switchLocation.isChecked());
        settings.put("qrEnabled", switchQR.isChecked());

        db.collection("classes")
                .document(classId)
                .update(settings)
                .addOnSuccessListener(unused ->
                        Toast.makeText(this, "Settings Saved", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }
}