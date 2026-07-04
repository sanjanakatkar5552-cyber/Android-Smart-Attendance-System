package com.example.s;

import android.content.Context;
import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class NotificationHelper {

    private static final String TAG = "NotificationHelper";
    private static final FirebaseFirestore db = FirebaseFirestore.getInstance();


    public static void notifyLowAttendance(String studentUid,
                                            int percentage, String subject) {
        if (percentage >= 75) return; // only notify if below threshold

        String title, body, type;

        if (percentage < 50) {
            type  = "very_low";
            title = "⚠ Critical: Very Low Attendance";
            body  = "Your " + subject + " attendance is " + percentage
                    + "%. You are at serious risk of being detained!";
        } else {
            type  = "low_attendance";
            title = "Attendance Alert";
            body  = "Your " + subject + " attendance is " + percentage
                    + "%. Attend more classes to stay above 75%.";
        }

        int classesNeeded = 0;
        if (percentage < 75) {

            classesNeeded = (int) Math.ceil((75.0 - percentage) / 2.5);
            body += " Attend at least " + classesNeeded + " more class(es).";
        }

        writeNotificationToFirestore(studentUid, type, title, body,
                String.valueOf(percentage));
    }


    public static void notifySessionStarted(String classId,
                                             String subject, String teacherName) {
        db.collection("classes").document(classId)
                .collection("students").get()
                .addOnSuccessListener(students -> {
                    for (QueryDocumentSnapshot student : students) {
                        // Get student UID from email
                        String email = student.getId();
                        findUidByEmail(email, uid -> {
                            if (uid != null) {
                                writeNotificationToFirestore(
                                        uid,
                                        "session_started",
                                        "Attendance Session Started",
                                        subject + " attendance is now open. Mark your attendance now!",
                                        null);
                            }
                        });
                    }
                });
    }


    public static void notifyTeacherDailySummary(String teacherUid,
                                                   int present, int absent,
                                                   int total, String date) {
        int rate = total > 0 ? (present * 100 / total) : 0;
        String title = "Daily Attendance Summary — " + date;
        String body  = "Present: " + present + "  Absent: " + absent
                + "  Total: " + total + "  Rate: " + rate + "%";
        if (absent > total / 2) {
            body += "\n⚠ High absenteeism today!";
        }
        writeNotificationToFirestore(teacherUid, "daily_summary", title, body, null);
    }


    public static void notifyLeaveUpdate(String studentUid,
                                          boolean approved, String date) {
        String title = approved ? "Leave Approved ✓" : "Leave Rejected";
        String body  = approved
                ? "Your leave request for " + date + " has been approved."
                : "Your leave request for " + date + " was not approved. Contact your teacher.";
        writeNotificationToFirestore(studentUid,
                "leave_update", title, body, null);
    }


    public static void notifyAttendanceCorrection(String studentUid,
                                                    boolean approved,
                                                    String subject, String date) {
        String title = approved ? "Attendance Corrected ✓" : "Correction Rejected";
        String body  = approved
                ? "Your attendance for " + subject + " on " + date + " has been corrected to Present."
                : "Your attendance correction request for " + subject + " on " + date + " was rejected.";
        writeNotificationToFirestore(studentUid,
                "correction_update", title, body, null);
    }


    public static void sendWeeklyAttendanceAlerts(String classId) {
        Log.d(TAG, "Sending weekly attendance alerts for class: " + classId);

        db.collection("attendance_records")
                .whereEqualTo("classId", classId)
                .get()
                .addOnSuccessListener(records -> {
                    // Group by studentId
                    java.util.Map<String, int[]> studentStats = new java.util.HashMap<>();
                    for (QueryDocumentSnapshot r : records) {
                        String sid = r.getString("studentId");
                        if (sid == null) continue;
                        if (!studentStats.containsKey(sid))
                            studentStats.put(sid, new int[]{0, 0}); // [present, total]
                        studentStats.get(sid)[1]++;
                        if (Boolean.TRUE.equals(r.getBoolean("present")))
                            studentStats.get(sid)[0]++;
                    }

                    // Notify students below 75%
                    for (java.util.Map.Entry<String, int[]> entry : studentStats.entrySet()) {
                        String sid   = entry.getKey();
                        int present  = entry.getValue()[0];
                        int total    = entry.getValue()[1];
                        int pct      = total > 0 ? (present * 100 / total) : 0;
                        if (pct < 75) {
                            notifyLowAttendance(sid, pct, "Overall");
                        }
                    }
                    Log.d(TAG, "Weekly alerts sent for " + studentStats.size() + " students");
                });
    }


    private static void writeNotificationToFirestore(
            String targetUid, String type,
            String title, String body, String percentage) {

        java.util.Map<String, Object> notif = new java.util.HashMap<>();
        notif.put("targetUid",  targetUid);
        notif.put("type",       type);
        notif.put("title",      title);
        notif.put("body",       body);
        notif.put("percentage", percentage != null ? percentage : "");
        notif.put("sent",       false);
        notif.put("createdAt",  com.google.firebase.firestore.FieldValue.serverTimestamp());

        db.collection("notifications")
                .add(notif)
                .addOnSuccessListener(ref ->
                        Log.d(TAG, "Notification queued: " + type + " for " + targetUid))
                .addOnFailureListener(e ->
                        Log.e(TAG, "Notification queue failed: " + e.getMessage()));
    }


    private static void findUidByEmail(String email, UidCallback callback) {
        db.collection("users")
                .whereEqualTo("email", email)
                .limit(1)
                .get()
                .addOnSuccessListener(docs -> {
                    if (!docs.isEmpty()) {
                        callback.onResult(docs.getDocuments().get(0).getId());
                    } else {
                        callback.onResult(null);
                    }
                })
                .addOnFailureListener(e -> callback.onResult(null));
    }

    interface UidCallback {
        void onResult(String uid);
    }
}
