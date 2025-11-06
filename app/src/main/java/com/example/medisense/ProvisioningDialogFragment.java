package com.example.medisense;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ProvisioningDialogFragment extends DialogFragment {

    private static final String TAG = "ProvisioningDialog";

    private BleViewModel bleViewModel;
    private Button provisionButton;
    private TextView statusText;
    private TextInputEditText editTextSsid;
    private TextInputEditText editTextPassword;
    private TextInputEditText editTextPid;

    private ActivityResultLauncher<String[]> requestPermissionsLauncher;
    private boolean arePermissionsGranted = false;
    private boolean isServiceBound = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Register for permission results
        requestPermissionsLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                this::handlePermissionResults
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the dialog layout
        return inflater.inflate(R.layout.dialog_provisioning, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Find views
        provisionButton = view.findViewById(R.id.provision_button);
        statusText = view.findViewById(R.id.provision_status_text);
        editTextSsid = view.findViewById(R.id.edit_text_ssid);
        editTextPassword = view.findViewById(R.id.edit_text_password);
        editTextPid = view.findViewById(R.id.edit_text_pid);

        // Get the Activity-scoped ViewModel
        bleViewModel = new ViewModelProvider(requireActivity()).get(BleViewModel.class);

        // Set click listener
        provisionButton.setOnClickListener(v -> provisionDevice());

        // Setup observers
        setupObservers();

        // Check permissions and start logic
        checkAndRequestBlePermissions();
    }

    private void setupObservers() {
        // Observe status updates
        bleViewModel.getOperationStatus().observe(getViewLifecycleOwner(), status -> {
            Log.d(TAG, "Status Update: " + status);
            statusText.setText("Status: " + status);
            // Show toast for every update
            Toast.makeText(getContext(), status, Toast.LENGTH_SHORT).show();

            if ("Provisioning Complete!".equals(status)) {
                // Close dialog on success
                dismiss();
            }
        });

        // Observe if the device is ready
        bleViewModel.isReadyToProvision().observe(getViewLifecycleOwner(), isReady -> {
            provisionButton.setEnabled(isReady);
        });

        // Observe if the service is bound
        bleViewModel.getIsServiceBound().observe(getViewLifecycleOwner(), isBound -> {
            isServiceBound = isBound;
            if (isBound && arePermissionsGranted) {
                startAppLogic();
            }
        });
    }

    private void provisionDevice() {
        String ssid = editTextSsid.getText().toString();
        String password = editTextPassword.getText().toString();
        String pid = editTextPid.getText().toString();

        if (ssid.isEmpty() || password.isEmpty() || pid.isEmpty()) {
            Toast.makeText(getContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        provisionButton.setEnabled(false);
        bleViewModel.provisionDevice(ssid, password, pid);
    }

    // --- PERMISSION HANDLING ---
    private void checkAndRequestBlePermissions() {
        List<String> permissionsToRequest = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        } else {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            statusText.setText("Status: Requesting permissions...");
            requestPermissionsLauncher.launch(permissionsToRequest.toArray(new String[0]));
        } else {
            arePermissionsGranted = true;
            if (isServiceBound) {
                startAppLogic();
            }
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
            if (isServiceBound) {
                startAppLogic();
            }
        } else {
            statusText.setText("Status: Permissions denied.");
            Toast.makeText(getContext(), "BLE permissions are required.", Toast.LENGTH_LONG).show();
            dismiss(); // Close dialog if permissions are denied
        }
    }

    private void startAppLogic() {
        Log.i(TAG, "App logic started: Permissions granted and service is bound.");
        bleViewModel.startScan();
    }
}