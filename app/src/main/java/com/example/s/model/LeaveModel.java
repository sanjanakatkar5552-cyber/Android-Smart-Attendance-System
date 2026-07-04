package com.example.s.model;

public class LeaveModel {

    public String id;
    public String studentId;
    public String studentName;
    public String rollNo;
    public String classId;
    public String reason;
    public String date;
    public String status;
    public String certificateImage;

    public LeaveModel(){}

    public LeaveModel(String id,
                      String studentId,
                      String studentName,
                      String rollNo,
                      String classId,
                      String reason,
                      String date,
                      String status,
                      String certificateImage) {

        this.id = id;
        this.studentId = studentId;
        this.studentName = studentName;
        this.rollNo = rollNo;
        this.classId = classId;
        this.reason = reason;
        this.date = date;
        this.status = status;
        this.certificateImage = certificateImage;
    }
}