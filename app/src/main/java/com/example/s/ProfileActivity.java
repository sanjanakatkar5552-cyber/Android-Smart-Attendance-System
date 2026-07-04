package com.example.s;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class ProfileActivity extends AppCompatActivity {

    private static final int    GALLERY_REQUEST = 101;
    private static final String PREF_NAME       = "profile_prefs";

    private TextView  tvRole;
    private EditText  etEmail, etMobile, tvName,etDepartment;
    private AppCompatButton btnUpdateProfile;
    private ProgressBar progressBar;
    ImageView imgAvatar, btnEditPhoto;
    EditText  etYear, etRollNo;
    private LinearLayout layoutYearRollRow;

    private FirebaseFirestore db;
    private String uid;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        tvName            = findViewById(R.id.tvName);
        tvRole            = findViewById(R.id.tvRole);
        etEmail           = findViewById(R.id.etEmail);
        etMobile          = findViewById(R.id.etMobile);
        btnUpdateProfile  = findViewById(R.id.btnUpdateProfile);
        progressBar       = findViewById(R.id.progressBar);
        imgAvatar         = findViewById(R.id.imgAvatar);
        btnEditPhoto      = findViewById(R.id.btnEditPhoto);
        etYear            = findViewById(R.id.etYear);
        etRollNo          = findViewById(R.id.etRollNo);
        etDepartment      = findViewById(R.id.etDepartment);
        layoutYearRollRow = findViewById(R.id.layoutYearRollRow);

        prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        btnEditPhoto.setOnClickListener(v -> openGallery());
        imgAvatar.setOnClickListener(v -> openGallery());

        db  = FirebaseFirestore.getInstance();
        uid = FirebaseAuth.getInstance().getUid();

        showCachedAvatar();

        if (uid != null) loadUserProfile();

        AppCompatButton btnChangePassword = findViewById(R.id.btnChangePassword);
        btnChangePassword.setOnClickListener(v -> sendPasswordReset());
        btnUpdateProfile.setOnClickListener(v -> updateProfile());
    }


    private File getLocalAvatarFile() {
        // uid is always set before this is called
        return new File(getFilesDir(), "avatar_" + uid + ".jpg");
    }

    private void showCachedAvatar() {
        if (uid == null) return;
        File localFile = getLocalAvatarFile();
        if (localFile.exists()) {
            Glide.with(this)
                    .load(localFile)
                    .signature(new com.bumptech.glide.signature.ObjectKey(localFile.lastModified()))
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .circleCrop()
                    .placeholder(R.drawable.ic_profile)
                    .into(imgAvatar);
            return;
        }
        String cachedUrl = prefs.getString("avatar_url_" + uid, null);
        if (cachedUrl != null) {
            Glide.with(this).load(cachedUrl).circleCrop()
                    .placeholder(R.drawable.ic_profile).into(imgAvatar);
        }
    }


    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, GALLERY_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != GALLERY_REQUEST || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;


        Bitmap bitmap = decodeBitmapFromUri(data.getData());
        if (bitmap == null) {
            Toast.makeText(this, "Could not read selected image", Toast.LENGTH_SHORT).show();
            return;
        }


        File localFile = saveToLocalFile(bitmap);
        bitmap.recycle();

        if (localFile == null) {
            Toast.makeText(this, "Could not save image", Toast.LENGTH_SHORT).show();
            return;
        }

        Glide.with(this)
                .load(localFile)
                .signature(new com.bumptech.glide.signature.ObjectKey(localFile.lastModified()))
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .circleCrop()
                .into(imgAvatar);

        uploadFileToFirebase(localFile);
    }

    private Bitmap decodeBitmapFromUri(Uri uri) {
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) return null;
            Bitmap bmp = BitmapFactory.decodeStream(in);
            in.close();
            return bmp;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private File saveToLocalFile(Bitmap bitmap) {
        try {
            File file = getLocalAvatarFile();
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos);
            fos.flush();
            fos.close();
            return file;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void uploadFileToFirebase(File localFile) {
        progressBar.setVisibility(View.VISIBLE);

        byte[] imageBytes;
        try {
            Bitmap bmp = BitmapFactory.decodeFile(localFile.getAbsolutePath());
            if (bmp == null) throw new Exception("Could not decode saved file");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, baos);
            imageBytes = baos.toByteArray();
            bmp.recycle();
        } catch (Exception e) {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Image preparation failed: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        StorageReference ref = FirebaseStorage.getInstance()
                .getReference("profileImages/" + uid + ".jpg");

        ref.putBytes(imageBytes)                          // putBytes = no URI needed
                .continueWithTask(task -> {
                    if (!task.isSuccessful() && task.getException() != null)
                        throw task.getException();
                    return ref.getDownloadUrl();
                })
                .addOnSuccessListener(downloadUri -> {
                    String url = downloadUri.toString();
                    prefs.edit().putString("avatar_url_" + uid, url).apply();

                    db.collection("users").document(uid)
                            .update("profileImage", url)
                            .addOnSuccessListener(v -> {
                                progressBar.setVisibility(View.GONE);
                                Toast.makeText(this, "Photo updated ✅",
                                        Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e -> {
                                progressBar.setVisibility(View.GONE);
                                Toast.makeText(this,
                                        "Uploaded but DB sync failed: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    // Local file is already saved → still shows on restart
                    Toast.makeText(this,
                            "Cloud upload failed, photo kept locally",
                            Toast.LENGTH_LONG).show();
                });
    }


    private void sendPasswordReset() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        // Reload to ensure we have a fresh user state
        currentUser.reload().addOnCompleteListener(task -> {
            FirebaseUser fresh = FirebaseAuth.getInstance().getCurrentUser();
            if (fresh == null) return;

            String email = fresh.getEmail();
            if (email == null || email.isEmpty()) {
                Toast.makeText(this, "No email linked to this account",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            progressBar.setVisibility(View.VISIBLE);
            FirebaseAuth.getInstance()
                    .sendPasswordResetEmail(email)
                    .addOnSuccessListener(unused -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(this,
                                "Reset link sent to " + email
                                        + "\nCheck Spam folder if not in inbox",
                                Toast.LENGTH_LONG).show();
                    })
                    .addOnFailureListener(e -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(this,
                                "Failed: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    });
        });
    }

    // ─── Load profile from Firestore ──────────────────────────────────────────
    private void loadUserProfile() {
        progressBar.setVisibility(View.VISIBLE);
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    progressBar.setVisibility(View.GONE);
                    if (!doc.exists()) return;

                    String name     = doc.getString("name");
                    String email    = doc.getString("email");
                    String mobile   = doc.getString("mobile");
                    String role     = doc.getString("role");
                    String year     = doc.getString("year");
                    String rollNo   = doc.getString("rollNo");
                    String imageUrl = doc.getString("profileImage");

                    if ("teacher".equals(role)) {
                        if (layoutYearRollRow != null)
                            layoutYearRollRow.setVisibility(View.GONE);
                    } else {
                        if (layoutYearRollRow != null)
                            layoutYearRollRow.setVisibility(View.VISIBLE);
                        etYear.setText(year   != null ? year   : "");
                        etRollNo.setText(rollNo != null ? rollNo : "");
                    }


                    File localFile = getLocalAvatarFile();
                    if (!localFile.exists() && imageUrl != null && !imageUrl.isEmpty()) {
                        prefs.edit().putString("avatar_url_" + uid, imageUrl).apply();
                        Glide.with(this).load(imageUrl).circleCrop()
                                .placeholder(R.drawable.ic_profile)
                                .error(R.drawable.ic_profile)
                                .into(imgAvatar);
                    }

                    tvName.setText(name != null ? name : "User Name");
                    etEmail.setText(email);
                    etMobile.setText(mobile);
                    tvRole.setText(role != null ? role.toUpperCase() : "STUDENT");
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error loading profile: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    // ─── Update profile ───────────────────────────────────────────────────────
    private void updateProfile() {
        String mobile = etMobile.getText().toString().trim();
        if (mobile.isEmpty() || mobile.length() < 10) {
            etMobile.setError("Valid mobile required");
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        Map<String, Object> updates = new HashMap<>();
        updates.put("mobile", mobile);

        String role = tvRole.getText().toString().toLowerCase();
        if (!"teacher".equals(role)) {
            updates.put("year",   etYear.getText().toString().trim());
            updates.put("rollNo", etRollNo.getText().toString().trim());
        }

        db.collection("users").document(uid).update(updates)
                .addOnSuccessListener(v -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Profile Updated ✅", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Update failed: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }
}