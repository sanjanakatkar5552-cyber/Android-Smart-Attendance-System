package com.example.s.model;

import java.util.Locale;

/**
 * AttendanceRecordModel — UPDATED
 *
 * CHANGES FROM ORIGINAL:
 * ══════════════════════════════════════════════════════════════
 * ADDED: email              — needed to match enrolled vs present
 * ADDED: time               — "HH:mm:ss" from Firestore timestamp
 * ADDED: attendancePercent  — computed per-student overall %
 * ADDED: Constructor with time parameter
 * ADDED: getTime(), getEmail(), setEmail()
 * ADDED: getAttendancePercent(), setAttendancePercent()
 * ADDED: getAttendancePercentLabel()
 * UPDATED: getLocationLabel() — shows distance in metres
 * ══════════════════════════════════════════════════════════════
 */
public class AttendanceRecordModel {

    private String  name;
    private String  rollNo;
    private String  subject;
    private boolean present;
    private String  date;
    private String  time;
    private Double  lat;
    private Double  lng;
    private Double  distance;

    // ── Extra fields added ─────────────────────────────────────────────────
    private String email            = "";
    private String sessionId        = "";  // which session this record belongs to
    private float  attendancePercent = 0f;

    // ── Constructor WITH time (used by AttendanceReportActivity) ──────────
    public AttendanceRecordModel(
            String name, String rollNo, String subject,
            boolean present, String date, String time,
            Double lat, Double lng, Double distance) {
        this.name     = name;
        this.rollNo   = rollNo;
        this.subject  = subject;
        this.present  = present;
        this.date     = date;
        this.time     = time;
        this.lat      = lat      != null ? lat      : 0.0;
        this.lng      = lng      != null ? lng      : 0.0;
        this.distance = distance != null ? distance : 0.0;
    }

    // ── Constructor WITHOUT time (backward compatibility) ─────────────────
    public AttendanceRecordModel(
            String name, String rollNo, String subject,
            boolean present, String date,
            Double lat, Double lng, Double distance) {
        this(name, rollNo, subject, present, date, null, lat, lng, distance);
    }

    // ── Getters ────────────────────────────────────────────────────────────
    public String  getName()     { return name    != null ? name    : ""; }
    public String  getRollNo()   { return rollNo  != null ? rollNo  : ""; }
    public String  getSubject()  { return subject != null ? subject : ""; }
    public boolean isPresent()   { return present; }
    public String  getDate()     { return date    != null ? date    : ""; }
    public String  getTime()     { return time    != null ? time    : ""; }
    public Double  getLat()      { return lat; }
    public Double  getLng()      { return lng; }
    public Double  getDistance() { return distance; }

    public String getEmail()            { return email != null ? email : ""; }
    public void   setEmail(String e)    { this.email = e; }

    public String getSessionId()              { return sessionId != null ? sessionId : ""; }
    public void   setSessionId(String sid)    { this.sessionId = sid; }

    public float  getAttendancePercent()          { return attendancePercent; }
    public void   setAttendancePercent(float pct) { this.attendancePercent = pct; }

    /** Returns "75%" if computed, or "—" if not. */
    public String getAttendancePercentLabel() {
        if (attendancePercent <= 0f) return "—";
        return String.format(Locale.getDefault(), "%.0f%%", attendancePercent);
    }

    /** Returns distance in metres, or lat/lng, or "N/A". */
    public String getLocationLabel() {
        if (lat == null || lng == null || (lat == 0.0 && lng == 0.0)) return "N/A";
        if (distance != null && distance > 0)
            return String.format(Locale.getDefault(), "%.0fm", distance);
        return String.format(Locale.getDefault(), "%.4f,%.4f", lat, lng);
    }
}
/*package com.example.s.model;

public class AttendanceRecordModel {

    private String name;
    private String rollNo;
    private String subject;
    private boolean present;
    private String date;
    private String time;
    private double lat;
    private double lng;
    private double distance;
    private float attendancePercent;
    private String sessionId;

    public AttendanceRecordModel(
            String name, String rollNo, String subject,
            boolean present, String date, String time,
            Double lat, Double lng, Double distance) {
        this.name      = name;
        this.rollNo    = rollNo;
        this.subject   = subject;
        this.present   = present;
        this.date      = date;
        this.time      = time != null ? time : "";
        this.lat       = lat      != null ? lat      : 0.0;
        this.lng       = lng      != null ? lng      : 0.0;
        this.distance  = distance != null ? distance : 0.0;
        this.attendancePercent = 0f;
        this.sessionId = "";
    }

    public AttendanceRecordModel(
            String name, String rollNo, String subject,
            boolean present, String date,
            Double lat, Double lng, Double distance) {
        this(name, rollNo, subject, present, date, null, lat, lng, distance);
    }

    public String  getName()              { return name; }
    public String  getRollNo()            { return rollNo; }
    public String  getSubject()           { return subject; }
    public boolean isPresent()            { return present; }
    public String  getDate()              { return date; }
    public String  getTime()              { return time; }
    public Double  getLat()               { return lat; }
    public Double  getLng()               { return lng; }
    public Double  getDistance()          { return distance; }
    public float   getAttendancePercent() { return attendancePercent; }
    public void    setAttendancePercent(float p) { this.attendancePercent = p; }
    public String  getSessionId()         { return sessionId != null ? sessionId : ""; }
    public void    setSessionId(String s) { this.sessionId = s != null ? s : ""; }

    public String getLocationLabel() {
        if (lat == 0.0 && lng == 0.0) return "N/A";
        return String.format("%.4f, %.4f", lat, lng);
    }

    public String getAttendancePercentLabel() {
        if (attendancePercent <= 0f) return "—";
        return String.format("%.0f%%", attendancePercent);
    }
}*/
