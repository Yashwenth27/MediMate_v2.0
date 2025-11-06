package com.example.medisense;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private ActivityResultLauncher<String[]> requestPermissionsLauncher;

    // --- ### MODIFIED LOGIC FLAGS ### ---
    private boolean arePermissionsGranted = false;
    private boolean isServiceBound = false; // New flag to track service binding
    // --- ############################ ---

    private BleViewModel bleViewModel;
    private Button provisionButton;
    private TextView statusText;
    private TextInputEditText editTextSsid;
    private TextInputEditText editTextPassword;
    private TextInputEditText editTextPid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // --- Find UI Elements ---
        provisionButton = findViewById(R.id.provision_button);
        statusText = findViewById(R.id.status_text);
        editTextSsid = findViewById(R.id.edit_text_ssid);
        editTextPassword = findViewById(R.id.edit_text_password);
        editTextPid = findViewById(R.id.edit_text_pid);

        // --- View Model Init ---
        bleViewModel = new ViewModelProvider(this).get(BleViewModel.class);

        // --- Permission Handling ---
        requestPermissionsLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                this::handlePermissionResults
        );
        checkAndRequestBlePermissions();

        // --- UI Click Listeners ---
        provisionButton.setOnClickListener(v -> provisionDevice());

        // --- Observe ViewModel LiveData ---
        setupObservers();
    }

    private void setupObservers() {
        // Observe status updates from the BleService
        bleViewModel.getOperationStatus().observe(this, status -> {
            Log.d(TAG, "Status Update: " + status);
            statusText.setText("Status: " + status);
        });

        // Observe if the device is ready (connected + services discovered)
        bleViewModel.isReadyToProvision().observe(this, isReady -> {
            provisionButton.setEnabled(isReady);
            if(isReady) {
                statusText.setText("Status: Ready to Provision");
            }
        });

        // --- ### NEW OBSERVER TO FIX RACE CONDITION ### ---
        // Observe if the service is bound
        bleViewModel.getIsServiceBound().observe(this, isBound -> {
            if (isBound) {
                Log.d(TAG, "Service is now bound.");
                isServiceBound = true;
                // Check if permissions were already granted
                if (arePermissionsGranted) {
                    startAppLogic();
                }
            } else {
                isServiceBound = false;
            }
        });
        // --- ######################################### ---
    }

    private void provisionDevice() {
        String ssid = editTextSsid.getText().toString();
        String password = editTextPassword.getText().toString();
        String pid = editTextPid.getText().toString();

        if (ssid.isEmpty() || password.isEmpty() || pid.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        provisionButton.setEnabled(false); // Disable button during provisioning
        bleViewModel.provisionDevice(ssid, password, pid);
    }

    // --- PERMISSION HANDLING ---

    private void checkAndRequestBlePermissions() {
        List<String> permissionsToRequest = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            Log.d(TAG, "Requesting permissions...");
            requestPermissionsLauncher.launch(permissionsToRequest.toArray(new String[0]));
        } else {
            Log.d(TAG, "All required BLE permissions are already granted.");
            arePermissionsGranted = true;
            // DO NOT start logic here. Wait for service to be bound.
            // The new observer will handle it.
        }
    }

    private void handlePermissionResults(Map<String, Boolean> results) {
        arePermissionsGranted = true;
        for (Boolean granted : results.values()) {
            if (!granted) {
                arePermissionsGranted = false;
                break;
            }
        }
        if (arePermissionsGranted) {
            Log.d(TAG, "All permissions granted by user.");
            // Check if service is *already* bound (in case permission was granted *after*)
            if (isServiceBound) {
                startAppLogic();
            }
            // If service is not bound yet, the new observer will call startAppLogic()
        } else {
            Log.w(TAG, "Not all permissions were granted.");
            statusText.setText("Status: Permissions denied. App cannot function.");
            Toast.makeText(this, "BLE permissions are required.", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * This is called ONLY when permissions are granted AND the service is bound.
     */
    private void startAppLogic() {
        Log.i(TAG, "App logic started: Permissions granted and service is bound.");
        // Auto-start the scan to find the device
        bleViewModel.startScan();
    }
}