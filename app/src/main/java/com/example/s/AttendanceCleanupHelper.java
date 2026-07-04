package com.example.s;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AttendanceCleanupHelper {


    public static String getTodayFlat() {
        return new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date());
    }

    public static String getTodayIso() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    public static boolean isToday(String date) {
        if (date == null || date.isEmpty()) return false;
        return date.equals(getTodayFlat())
                || date.equals(getTodayIso())
                || date.equals(new SimpleDateFormat("yyyy-M-d", Locale.getDefault()).format(new Date()));
    }

    private static boolean isTodayTimestamp(com.google.firebase.Timestamp ts) {
        if (ts == null) return false;
        Calendar tsCal  = Calendar.getInstance();
        tsCal.setTime(ts.toDate());
        Calendar today  = Calendar.getInstance();
        return tsCal.get(Calendar.YEAR)        == today.get(Calendar.YEAR)
                && tsCal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
    }

    public static void showCleanupDialog(Context context) {
        new AlertDialog.Builder(context)
                .setTitle("🗑 Delete Old Attendance Records")
                .setMessage(
                        "Permanently delete ALL attendance records NOT from today.\n\n" +
                                "⚠ This cannot be undone.")
                .setPositiveButton("Yes, Delete", (d, w) -> deleteOldRecords(context, false))
                .setNegativeButton("Cancel", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private static void deleteOldRecords(Context context, boolean silent) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String teacherId = FirebaseAuth.getInstance().getUid();
        if (teacherId == null) return;

        ProgressDialog prog = null;
        if (!silent) {
            prog = new ProgressDialog(context);
            prog.setMessage("Finding old records…");
            prog.setCancelable(false);
            prog.show();
        }
        final ProgressDialog finalProg = prog;
        final List<DocumentReference> toDelete = new ArrayList<>();

        // ── Step 1: get teacher's classes ──────────────────────────────────
        db.collection("classes")
                .whereEqualTo("teacherId", teacherId)
                .get()
                .addOnSuccessListener(classes -> {
                    if (classes.isEmpty()) {
                        finish(toDelete, db, context, silent, finalProg);
                        return;
                    }

                    List<String> classIds = new ArrayList<>();
                    for (QueryDocumentSnapshot cls : classes) classIds.add(cls.getId());


                    int chunkSize = 10;
                    int totalChunks = (int) Math.ceil(classIds.size() / (double) chunkSize);
                    final int[] chunksRemaining = {totalChunks};

                    for (int i = 0; i < classIds.size(); i += chunkSize) {
                        List<String> chunk = classIds.subList(i, Math.min(i + chunkSize, classIds.size()));

                        db.collection("attendanceSessions")
                                .whereIn("classId", chunk)
                                .get()
                                .addOnSuccessListener(sessions -> {
                                    if (sessions.isEmpty()) {
                                        chunksRemaining[0]--;
                                        if (chunksRemaining[0] == 0)
                                            finish(toDelete, db, context, silent, finalProg);
                                        return;
                                    }

                                    final int[] sessionCount = {sessions.size()};

                                    for (QueryDocumentSnapshot session : sessions) {
                                        // Determine if this session is from today using startTime Timestamp
                                        com.google.firebase.Timestamp startTime =
                                                session.getTimestamp("startTime");
                                        boolean sessionIsToday = isTodayTimestamp(startTime);

                                        // Get the actual sessionId to look up attendance subcollection
                                        String sid = session.getString("sessionId");
                                        if (sid == null || sid.isEmpty()) sid = session.getId();
                                        final String finalSid = sid;
                                        final boolean isToday  = sessionIsToday;

                                        // ── Step 3: read students subcollection ──
                                        db.collection("attendance")
                                                .document(finalSid)
                                                .collection("students")
                                                .get()
                                                .addOnSuccessListener(students -> {
                                                    for (QueryDocumentSnapshot stu : students) {
                                                        // Student record date (may be dd/MM/yyyy or yyyy-MM-dd)
                                                        // Fall back to session's startTime date if missing
                                                        String d = stu.getString("date");
                                                        boolean stuIsToday;
                                                        if (d != null && !d.isEmpty()) {
                                                            stuIsToday = isToday(d);
                                                        } else {
                                                            // No date field — use session timestamp
                                                            stuIsToday = isToday;
                                                        }
                                                        if (!stuIsToday) {
                                                            toDelete.add(stu.getReference());
                                                        }
                                                    }

                                                    if (!isToday) {
                                                        // Delete the attendanceSessions doc
                                                        toDelete.add(session.getReference());
                                                        // Also delete the attendance parent doc
                                                        toDelete.add(
                                                                db.collection("attendance").document(finalSid));
                                                    }

                                                    sessionCount[0]--;
                                                    if (sessionCount[0] == 0) {
                                                        chunksRemaining[0]--;
                                                        if (chunksRemaining[0] == 0)
                                                            finish(toDelete, db, context, silent, finalProg);
                                                    }
                                                })
                                                .addOnFailureListener(e -> {
                                                    sessionCount[0]--;
                                                    if (sessionCount[0] == 0) {
                                                        chunksRemaining[0]--;
                                                        if (chunksRemaining[0] == 0)
                                                            finish(toDelete, db, context, silent, finalProg);
                                                    }
                                                });
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    chunksRemaining[0]--;
                                    if (chunksRemaining[0] == 0)
                                        finish(toDelete, db, context, silent, finalProg);
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    if (finalProg != null && finalProg.isShowing()) finalProg.dismiss();
                    if (!silent)
                        Toast.makeText(context, "❌ Failed: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                });
    }

    private static void finish(List<DocumentReference> refs, FirebaseFirestore db,
                               Context context, boolean silent, ProgressDialog prog) {
        if (prog != null && prog.isShowing()) prog.dismiss();

        if (refs.isEmpty()) {
            if (!silent)
                Toast.makeText(context, "✅ No old records found!", Toast.LENGTH_SHORT).show();
            return;
        }

        int total  = refs.size();
        int chunks = (int) Math.ceil(total / 500.0);

        for (int c = 0; c < chunks; c++) {
            WriteBatch batch = db.batch();
            int from = c * 500, to = Math.min(from + 500, total);
            for (int i = from; i < to; i++) batch.delete(refs.get(i));

            final boolean isLast = (c == chunks - 1);
            batch.commit()
                    .addOnSuccessListener(v -> {
                        if (isLast && !silent)
                            Toast.makeText(context,
                                    "🗑 " + total + " old record(s) deleted!",
                                    Toast.LENGTH_LONG).show();
                    })
                    .addOnFailureListener(e -> {
                        if (!silent)
                            Toast.makeText(context, "❌ " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                    });
        }
    }
}