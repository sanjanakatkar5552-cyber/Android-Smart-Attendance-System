package com.example.s;

import android.util.Patterns;

public class ValidationUtils {

    public static boolean isValidEmail(String email) {
        return email != null &&
                Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    public static boolean isValidMobile(String mobile) {
        return mobile != null &&
                mobile.matches("[6-9][0-9]{9}");
    }

    public static boolean isValidPassword(String password) {
        return password != null &&
                password.length() >= 6 &&
                password.matches(".*[0-9].*") &&
                password.matches(".*[!@#$%^&*()].*");
    }
}
