package com.example.s.model;

public class StudentModel {

    public String name;
    public String email;
    public String status;
    public Boolean faceRegistered;

    public StudentModel(){}

    public StudentModel(String name,String email,String status,Boolean faceRegistered){

        this.name=name;
        this.email=email;
        this.status=status;
        this.faceRegistered=faceRegistered;

    }
}