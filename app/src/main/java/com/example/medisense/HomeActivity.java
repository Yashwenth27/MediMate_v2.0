package com.example.medisense;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences; // Import this
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;

public class HomeActivity extends AppCompatActivity {

    private Button loginButton, signUpButton;
    private EditText username, password;
    private DatabaseReference dbRef;

    public static final String PREFS_NAME = "MediSensePrefs";
    public static final String KEY_USERNAME = "Username";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // --- ### NEW: AUTO-LOGIN CHECK ### ---
        // Check if the user is already logged in
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedUsername = sharedPreferences.getString(KEY_USERNAME, null);

        if (savedUsername != null) {
            // User is already logged in, go straight to Dash
            Toast.makeText(this, "Welcome back, " + savedUsername, Toast.LENGTH_SHORT).show();
            goToDashActivity(savedUsername);
            return; // Skip the rest of onCreate
        }
        // --- ############################# ---

        // If no user is saved, show the login screen
        setContentView(R.layout.activity_home);

        // Find all views
        loginButton = (Button) findViewById(R.id.splash_login);
        signUpButton = (Button) findViewById(R.id.splash_signup);
        username = (EditText) findViewById(R.id.splash_username);
        password = (EditText) findViewById(R.id.splash_password);

        // Get Firebase reference
        dbRef = FirebaseDatabase.getInstance().getReference("users");

        // Set login listener
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loginUser();
            }
        });

        // Set signup listener
        signUpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                registerUser();
            }
        });
    }

    /**
     * Handles user login
     */
    private void loginUser() {
        String inputUser = username.getText().toString().trim();
        String inputPass = password.getText().toString().trim();

        if (inputUser.isEmpty() || inputPass.isEmpty()) {
            Toast.makeText(HomeActivity.this, "Username and password required", Toast.LENGTH_SHORT).show();
            return;
        }

        dbRef.child(inputUser).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String dbPass = snapshot.child("password").getValue(String.class);
                    if (dbPass != null && dbPass.equals(inputPass)) {
                        Toast.makeText(HomeActivity.this, "Login successful", Toast.LENGTH_SHORT).show();
                        goToDashActivity(inputUser);
                    } else {
                        Toast.makeText(HomeActivity.this, "Invalid password", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(HomeActivity.this, "User not found", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(HomeActivity.this, "Database error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Handles new user registration
     */
    private void registerUser() {
        String inputUser = username.getText().toString().trim();
        String inputPass = password.getText().toString().trim();

        if (inputUser.isEmpty() || inputPass.isEmpty()) {
            Toast.makeText(HomeActivity.this, "Username and password cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        dbRef.child(inputUser).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Toast.makeText(HomeActivity.this, "Username already taken. Please login.", Toast.LENGTH_SHORT).show();
                } else {
                    HashMap<String, Object> userData = new HashMap<>();
                    userData.put("password", inputPass);
                    userData.put("name", inputUser);
                    userData.put("med_info", "Not Set");
                    userData.put("med_count", "0");
                    userData.put("med_sched", "00:00");

                    dbRef.child(inputUser).setValue(userData).addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            if (task.isSuccessful()) {
                                Toast.makeText(HomeActivity.this, "Registration successful. Logging in...", Toast.LENGTH_SHORT).show();
                                goToDashActivity(inputUser);
                            } else {
                                Toast.makeText(HomeActivity.this, "Registration failed. Try again.", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(HomeActivity.this, "Database error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Helper method to navigate to DashActivity and SAVE the session
     */
    private void goToDashActivity(String user) {
        // --- ### NEW: SAVE USER ON LOGIN ### ---
        // Save the username to SharedPreferences
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_USERNAME, user);
        editor.apply();
        // --- ############################# ---

        Intent i = new Intent(HomeActivity.this, DashActivity.class);
        i.putExtra("Username", user);
        startActivity(i);
        finish(); // Close this activity
    }
}