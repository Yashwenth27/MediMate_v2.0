package com.example.medisense;

import android.content.Intent;
import android.content.SharedPreferences; // Import this
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Random;

public class DashActivity extends AppCompatActivity {

    // --- (Your existing variables) ---
    private TextView nameTag, medicineName, pillsCount, scheduledTime, quoteText;
    private SwipeRefreshLayout swipeRefreshLayout;
    Button reScheduleBtn, updateStockBtn;
    private DatabaseReference userRef;
    private ValueEventListener realtimeListener;
    private String username;

    // --- (Your existing quotes array) ---
    private final String[] healthQuotes = {
            "An apple a day keeps the doctor away.", "Take care of your body. It’s the only place you have to live.",
            "Health is wealth, keep it safe.", "A fit body, a calm mind, a house full of love.",
            "Your body deserves the best care.", "Medicine cures diseases, but only health can cure you.",
            "Prevention is better than cure.", "Eat to live, not live to eat.",
            "Every pill counts, don’t skip.", "Wellness is the first wealth.",
            "Stay hydrated, stay healthy.", "Good sleep is the best medicine.",
            "A healthy outside starts from the inside.", "Don’t forget your meds, your body remembers.",
            "Healthy habits build a strong future.", "Fitness is not about being better than someone else, it’s about being better than you.",
            "Let food be thy medicine and medicine be thy food.", "Consistency in health creates miracles.",
            "You can’t pour from an empty cup — take care of yourself.", "Small healthy choices every day lead to big results."
    };

    // --- BLE-RELATED VARIABLES ---
    private BleViewModel bleViewModel;
    private FloatingActionButton settingsFab;
    private TextView statusIndicator;

    // --- ### NEW SIGN OUT BUTTON ### ---
    private Button signOutButton;
    // --- ######################### ---

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dash);

        // Bind views
        nameTag = findViewById(R.id.name_tag);
        medicineName = findViewById(R.id.medicine_name);
        pillsCount = findViewById(R.id.pills_count);
        scheduledTime = findViewById(R.id.scheduled_time);
        quoteText = findViewById(R.id.quote_text);
        swipeRefreshLayout = findViewById(R.id.swipeRefresh);
        reScheduleBtn = findViewById(R.id.re_schedule);
        updateStockBtn = findViewById(R.id.update);
        settingsFab = findViewById(R.id.settings_fab);
        statusIndicator = findViewById(R.id.status_indicator);

        // --- INITIALIZE BLE VIEWMODEL ---
        bleViewModel = new ViewModelProvider(this).get(BleViewModel.class);

        // Get username from intent
        Intent intent = getIntent();
        username = intent.getStringExtra("Username");

        // Show random quote
        Random random = new Random();
        int index = random.nextInt(healthQuotes.length);
        quoteText.setText(healthQuotes[index]);

        // Firebase reference for this user
        userRef = FirebaseDatabase.getInstance().getReference("users").child(username);

        // --- SETUP ALL ONCLICK LISTENERS ---
        swipeRefreshLayout.setOnRefreshListener(this::fetchUserData);

        reScheduleBtn.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString("username", username);
            RescheduleDialogFragment fragment = new RescheduleDialogFragment();
            fragment.setArguments(bundle);
            fragment.show(getSupportFragmentManager(), "reschedule");
        });

        updateStockBtn.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString("username", username);
            UpdateStockDialogFragment fragment = new UpdateStockDialogFragment();
            fragment.setArguments(bundle);
            fragment.show(getSupportFragmentManager(), "updateStock");
        });

        settingsFab.setOnClickListener(v -> {
            new ProvisioningDialogFragment().show(getSupportFragmentManager(), "provisioning");
        });
        // --- ############################# ---

        // Start real-time sync
        attachRealtimeListener();

        // Fetch once at start
        fetchUserData();

        // SETUP BLE OBSERVERS
        setupBleObservers();
    }

    // --- ### NEW SIGN OUT METHOD ### ---
    private void signOut() {
        // 1. Clear SharedPreferences
        SharedPreferences sharedPreferences = getSharedPreferences(HomeActivity.PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(HomeActivity.KEY_USERNAME); // Remove the saved username
        editor.apply();

        // 2. Go back to HomeActivity
        Intent intent = new Intent(DashActivity.this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK); // Clear back stack
        startActivity(intent);

        // 3. Finish DashActivity
        finish();
    }
    // --- ########################### ---

    private void setupBleObservers() {
        bleViewModel.isReadyToProvision().observe(this, isReady -> {
            if (isReady) {
                statusIndicator.setText("Online");
                statusIndicator.setCompoundDrawablesWithIntrinsicBounds(
                        ContextCompat.getDrawable(this, R.drawable.dot_green),
                        null, null, null);
            } else {
                statusIndicator.setText("Offline");
                statusIndicator.setCompoundDrawablesWithIntrinsicBounds(
                        ContextCompat.getDrawable(this, R.drawable.dot_red),
                        null, null, null);
            }
        });
    }

    private void fetchUserData() {
        swipeRefreshLayout.setRefreshing(true);
        userRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                DataSnapshot snapshot = task.getResult();
                String name = snapshot.child("name").getValue(String.class);
                String medInfo = snapshot.child("med_info").getValue(String.class);
                String medCount = snapshot.child("med_count").getValue(String.class);
                String medSched = snapshot.child("med_sched").getValue(String.class);
                if (name != null) nameTag.setText(name);
                if (medInfo != null) medicineName.setText(medInfo);
                if (medCount != null) pillsCount.setText(medCount);
                if (medSched != null) scheduledTime.setText(medSched);
            }
            swipeRefreshLayout.setRefreshing(false);
        });
    }

    private void attachRealtimeListener() {
        realtimeListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String name = snapshot.child("name").getValue(String.class);
                    String medInfo = snapshot.child("med_info").getValue(String.class);
                    String medCount = snapshot.child("med_count").getValue(String.class);
                    String medSched = snapshot.child("med_sched").getValue(String.class);
                    if (name != null) nameTag.setText(name);
                    if (medInfo != null) medicineName.setText(medInfo);
                    if (medCount != null) pillsCount.setText(medCount);
                    if (medSched != null) scheduledTime.setText(medSched);
                }
            }
            @Override
            public void onCancelled(DatabaseError error) {}
        };
        userRef.addValueEventListener(realtimeListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (userRef != null && realtimeListener != null) {
            userRef.removeEventListener(realtimeListener);
        }
    }
}