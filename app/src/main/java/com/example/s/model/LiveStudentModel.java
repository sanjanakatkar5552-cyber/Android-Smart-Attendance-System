package com.example.s.model;

public class LiveStudentModel {

    private String name;
    private String rollNo;
    private String status;
    private double distance;

    public LiveStudentModel(){}

    public LiveStudentModel(String name, String rollNo, double distance, String status){
        this.name = name;
        this.rollNo = rollNo;
        this.distance = distance;
        this.status = status;
    }

    public String getName() {
        return name;
    }

    public String getRollNo() {
        return rollNo;
    }

    public String getStatus() {
        return status;
    }

    public double getDistance() {
        return distance;
    }
}