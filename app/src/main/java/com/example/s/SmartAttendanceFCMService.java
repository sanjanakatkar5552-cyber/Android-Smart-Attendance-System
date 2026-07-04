package com.example.s;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.HashMap;
import java.util.Map;


public class SmartAttendanceFCMService extends FirebaseMessagingService {

    private static final String TAG = "FCMService";

    // Notification channel IDs
    public static final String CHANNEL_ATTENDANCE = "attendance_alerts";
    public static final String CHANNEL_SESSION    = "session_alerts";
    public static final String CHANNEL_GENERAL    = "general_alerts";

    // ── Called when a new FCM token is generated ──────────────────────────────
    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "New FCM token: " + token);
        saveTokenToFirestore(token);
    }

    // ── Called when notification is received while app is in foreground ───────
    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "Message from: " + remoteMessage.getFrom());

        Map<String, String> data = remoteMessage.getData();
        String type    = data.get("type");
        String title   = data.get("title");
        String body    = data.get("body");
        String percent = data.get("percentage");

        if (title == null) title = "Smart Attendance";
        if (body  == null) body  = "You have a new notification";

        // Also check notification payload (for background messages)
        if (remoteMessage.getNotification() != null) {
            if (title.equals("Smart Attendance"))
                title = remoteMessage.getNotification().getTitle();
            body  = remoteMessage.getNotification().getBody();
        }

        // Decide channel and intent based on type
        String channelId = CHANNEL_GENERAL;
        Intent intent    = new Intent(this, StudentDashboardActivity.class);

        if (type != null) {
            switch (type) {
                case "low_attendance":
                case "very_low":
                    channelId = CHANNEL_ATTENDANCE;
                    intent    = new Intent(this, StudentDashboardActivity.class);
                    break;
                case "session_started":
                    channelId = CHANNEL_SESSION;
                    intent    = new Intent(this, StudentDashboardActivity.class);
                    intent.putExtra("openAttendance", true);
                    break;
                case "daily_summary":
                    channelId = CHANNEL_GENERAL;
                    // For teacher — open teacher dashboard
                    intent = new Intent(this, TeacherDashboardActivity.class);
                    break;
            }
        }

        showNotification(title, body, channelId, intent, type);
    }

    // ── Build and show notification ───────────────────────────────────────────
    private void showNotification(String title, String body,
                                   String channelId, Intent intent, String type) {

        createNotificationChannels();

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, (int) System.currentTimeMillis(), intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        // Choose icon tint based on type
        int color = 0xFF7C3AED; // purple default
        if (type != null) {
            switch (type) {
                case "very_low":     color = 0xFFEF4444; break; // red
                case "low_attendance": color = 0xFFF59E0B; break; // amber
                case "session_started": color = 0xFF22C55E; break; // green
            }
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_notification) // add this drawable
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setSound(soundUri)
                .setColor(color)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            int notifId = (int) System.currentTimeMillis();
            manager.notify(notifId, builder.build());
        }
    }

    // ── Create notification channels (required for Android 8+) ───────────────
    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return;

            // Attendance alerts — high importance, makes sound
            NotificationChannel attCh = new NotificationChannel(
                    CHANNEL_ATTENDANCE,
                    "Attendance Alerts",
                    NotificationManager.IMPORTANCE_HIGH);
            attCh.setDescription("Alerts for low attendance percentage");
            attCh.enableVibration(true);
            manager.createNotificationChannel(attCh);

            // Session alerts — high importance
            NotificationChannel sessCh = new NotificationChannel(
                    CHANNEL_SESSION,
                    "Session Alerts",
                    NotificationManager.IMPORTANCE_HIGH);
            sessCh.setDescription("Alerts when teacher starts attendance session");
            manager.createNotificationChannel(sessCh);

            // General — default importance
            NotificationChannel genCh = new NotificationChannel(
                    CHANNEL_GENERAL,
                    "General Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT);
            genCh.setDescription("General app notifications");
            manager.createNotificationChannel(genCh);
        }
    }

    public static void saveTokenToFirestore(String token) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        Map<String, Object> update = new HashMap<>();
        update.put("fcmToken", token);

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .update(update)
                .addOnSuccessListener(v -> Log.d(TAG, "FCM token saved"))
                .addOnFailureListener(e -> Log.e(TAG, "Token save failed: " + e.getMessage()));
    }

    public static void refreshAndSaveToken() {
        com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> {
                    Log.d(TAG, "Token refreshed: " + token);
                    saveTokenToFirestore(token);
                })
                .addOnFailureListener(e ->
                        Log.e(TAG, "Token refresh failed: " + e.getMessage()));
    }
}
