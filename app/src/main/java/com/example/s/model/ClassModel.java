package com.example.s.model;

public class ClassModel {

    private String id;
    private String teacherId;
    private String department;
    private String year;
    private String subject;
    private String batch;

    private boolean isActive;
    private int totalStudents;
    private int radius;

    public ClassModel() {
    }

    // ================= GETTERS =================

    public String getId() {
        return id;
    }

    public String getTeacherId() {
        return teacherId;
    }

    public String getDepartment() {
        return department;
    }

    public String getYear() {
        return year;
    }

    public String getSubject() {
        return subject;
    }

    public String getBatch() {
        return batch;
    }

    public boolean getIsActive() {
        return isActive;
    }

    public int getTotalStudents() {
        return totalStudents;
    }

    public int getRadius() {
        return radius;
    }

    // ================= SETTERS =================

    public void setId(String id) {
        this.id = id;
    }

    public void setTeacherId(String teacherId) {
        this.teacherId = teacherId;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public void setBatch(String batch) {
        this.batch = batch;
    }

    public void setIsActive(boolean active) {
        this.isActive = active;
    }

    public void setTotalStudents(int totalStudents) {
        this.totalStudents = totalStudents;
    }

    public void setRadius(int radius) {
        this.radius = radius;
    }
}