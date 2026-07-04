package com.example.s;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_TIME = 2000; // 2 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        ImageView imgSplash = findViewById(R.id.imgSplash);

        // Fade-in animation
        imgSplash.setAlpha(0f);
        imgSplash.animate()
                .alpha(1f)
                .setDuration(1200)
                .start();

        // Delay then move to Login
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, Login_Activity2.class);
            startActivity(intent);
            finish(); // remove splash from back stack
        }, SPLASH_TIME);
    }
}
