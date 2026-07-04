package com.example.s;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import com.google.firebase.FirebaseApp;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Firebase Check 👇
        FirebaseApp.initializeApp(this);
        if (FirebaseApp.getApps(this).isEmpty()) {
            Log.e("FirebaseStatus", "❌ Firebase NOT initialized");
        } else {
            Log.d("FirebaseStatus", "🔥 Firebase is REAY!");
        }

        Button btnLogin = findViewById(R.id.btnGoLogin);
        Button btnRegister = findViewById(R.id.btnGoRegister);

        btnLogin.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, Login_Activity2.class));
        });

        btnRegister.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, RegisterActivity.class));
        });
    }
}
