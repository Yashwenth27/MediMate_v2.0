package com.example.medisense;

import android.annotation.SuppressLint;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

public class BleService extends Service {
    private final static String TAG = "BleService";

    // --- Target Device Configuration ---
    private static final String TARGET_DEVICE_NAME = "MediTrack_Dispenser";
    private static final String SERVICE_UUID = "12345678-1234-1234-1234-1234567890ab";
    private static final String CHAR_SSID_UUID = "12345678-1234-1234-1234-1234567890ac"; // Was CHAR_AC
    private static final String CHAR_PASS_UUID = "12345678-1234-1234-1234-1234567890ad"; // Was CHAR_AD
    private static final String CHAR_PID_UUID = "12345678-1234-1234-1234-1234567890ae"; // Was CHAR_AE
    private static final long SCAN_TIMEOUT_MS = 10000; // 10 seconds

    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private Handler scanTimeoutHandler = new Handler(Looper.getMainLooper());
    private boolean deviceFound = false;

    // --- Write Queue for Sequential Writes ---
    private final Queue<Runnable> gattWriteQueue = new LinkedList<>();
    private boolean isWriting = false;

    // --- Binder ---
    private final IBinder binder = new LocalBinder();
    public class LocalBinder extends Binder {
        BleService getService() {
            return BleService.this;
        }
    }
    @Nullable @Override public IBinder onBind(Intent intent) { return binder; }

    // --- LiveData for UI Updates ---
    private final MutableLiveData<String> operationStatus = new MutableLiveData<>("Idle");
    private final MutableLiveData<Boolean> isReadyToProvision = new MutableLiveData<>(false);

    public LiveData<String> getOperationStatus() { return operationStatus; }
    public LiveData<Boolean> isReadyToProvision() { return isReadyToProvision; }


    @Override
    public boolean onUnbind(Intent intent) {
        close();
        return super.onUnbind(intent);
    }

    public boolean initialize() {
        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            Log.e(TAG, "Unable to initialize BluetoothManager.");
            return false;
        }
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        return true;
    }

    // --- Scanning (Now with auto-connect) ---
    @SuppressLint("MissingPermission")
    private final ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            String deviceName = device.getName();

            if (deviceName != null && deviceName.equals(TARGET_DEVICE_NAME)) {
                Log.i(TAG, "Found target device: " + TARGET_DEVICE_NAME);
                deviceFound = true;
                stopScan(); // Stop scanning, we found it
                connect(device.getAddress()); // Auto-connect
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.w(TAG, "BLE Scan Failed with error code: " + errorCode);
            operationStatus.postValue("Error: Scan Failed");
        }
    };

    @SuppressLint("MissingPermission")
    public void startScan() {
        if (bluetoothLeScanner == null || bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.w(TAG, "BLE Scanner not initialized or Bluetooth is off.");
            operationStatus.postValue("Error: Bluetooth not ready");
            return;
        }

        deviceFound = false;
        operationStatus.postValue("Scanning for " + TARGET_DEVICE_NAME + "...");

        // Stop scan after a timeout
        scanTimeoutHandler.postDelayed(this::stopScan, SCAN_TIMEOUT_MS);

        try {
            bluetoothLeScanner.startScan(leScanCallback);
        } catch (SecurityException e) {
            Log.e(TAG, "Bluetooth scanning permission not granted.", e);
        }
    }

    @SuppressLint("MissingPermission")
    public void stopScan() {
        if (bluetoothLeScanner == null) return;

        scanTimeoutHandler.removeCallbacksAndMessages(null); // Stop timeout handler
        try {
            bluetoothLeScanner.stopScan(leScanCallback);
        } catch (SecurityException e) {
            Log.e(TAG, "Bluetooth scanning permission not granted.", e);
        }

        // ### FIX 1: Check operationStatus to avoid overwriting "Connecting" ###
        if (!deviceFound && !operationStatus.getValue().contains("Connecting")) {
            operationStatus.postValue("Error: Device '" + TARGET_DEVICE_NAME + "' not found.");
        }
    }

    // --- Connection & GATT ---
    @SuppressLint("MissingPermission")
    public boolean connect(final String address) {
        if (bluetoothAdapter == null || address == null) return false;

        final BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
        if (device == null) return false;

        try {
            operationStatus.postValue("Connecting to device...");
            bluetoothGatt = device.connectGatt(this, false, gattCallback);
            return true;
        } catch (SecurityException e) {
            Log.e(TAG, "Bluetooth connect permission not granted.", e);
            return false;
        }
    }

    public void disconnect() {
        if (bluetoothGatt == null) return;
        try {
            bluetoothGatt.disconnect();
        } catch (SecurityException e) {
            Log.e(TAG, "Bluetooth connect permission not granted.", e);
        }
    }

    public void close() {
        if (bluetoothGatt == null) return;
        try {
            bluetoothGatt.close();
            bluetoothGatt = null;
            // ### FIX 2: Update LiveData on close ###
            isReadyToProvision.postValue(false);
            operationStatus.postValue("Disconnected");
            // #####################################
        } catch (SecurityException e) {
            Log.e(TAG, "Bluetooth connect permission not granted.", e);
        }
    }

    // --- GATT Callback ---
    @SuppressLint("MissingPermission")
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            try {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(TAG, "Connected to GATT server.");
                    operationStatus.postValue("Connected. Discovering services...");
                    isReadyToProvision.postValue(false);
                    gatt.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i(TAG, "Disconnected from GATT server.");
                    operationStatus.postValue("Disconnected");
                    isReadyToProvision.postValue(false);
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Bluetooth permission not granted.", e);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Services discovered!");
                operationStatus.postValue("Ready to Provision");
                isReadyToProvision.postValue(true);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
                operationStatus.postValue("Error: Service discovery failed");
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Write successful for: " + characteristic.getUuid());
            } else {
                Log.w(TAG, "Write failed for: " + characteristic.getUuid() + " status: " + status);
            }
            // This write is done, clear the flag and process the next
            isWriting = false;
            processGattWriteQueue();
        }
    };

    // --- Write Queue Logic ---
    private void processGattWriteQueue() {
        if (isWriting || gattWriteQueue.isEmpty()) {
            return;
        }
        isWriting = true;
        Runnable operation = gattWriteQueue.poll();
        if (operation != null) {
            operation.run(); // Execute the write
        }
    }

    @SuppressLint("MissingPermission")
    private void writeCharacteristicInternal(String serviceUuidStr, String charUuidStr, byte[] value) {
        if (bluetoothGatt == null) {
            Log.w(TAG, "BluetoothGatt not initialized.");
            isWriting = false; // Clear flag so queue isn't stuck
            return;
        }

        BluetoothGattService service = bluetoothGatt.getService(UUID.fromString(serviceUuidStr));
        if (service == null) {
            Log.w(TAG, "Service not found: " + serviceUuidStr);
            isWriting = false;
            return;
        }

        BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(charUuidStr));
        if (characteristic == null) {
            Log.w(TAG, "Characteristic not found: " + charUuidStr);
            isWriting = false;
            return;
        }

        Log.d(TAG, "Writing to " + charUuidStr);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bluetoothGatt.writeCharacteristic(characteristic, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            } else {
                characteristic.setValue(value);
                bluetoothGatt.writeCharacteristic(characteristic);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Bluetooth permission not granted.", e);
            isWriting = false;
        }
    }

    // --- Public Master Provision Function ---
    public void provisionDevice(String ssid, String password, String pid) {
        // Clear any old operations
        gattWriteQueue.clear();
        isWriting = false;

        // Add SSID write to queue
        gattWriteQueue.add(() -> {
            operationStatus.postValue("Writing SSID...");
            writeCharacteristicInternal(SERVICE_UUID, CHAR_SSID_UUID, ssid.getBytes(StandardCharsets.UTF_8));
        });

        // Add Password write to queue
        gattWriteQueue.add(() -> {
            operationStatus.postValue("Writing Password...");
            writeCharacteristicInternal(SERVICE_UUID, CHAR_PASS_UUID, password.getBytes(StandardCharsets.UTF_8));
        });

        // Add PID write to queue
        gattWriteQueue.add(() -> {
            operationStatus.postValue("Writing PID...");
            writeCharacteristicInternal(SERVICE_UUID, CHAR_PID_UUID, pid.getBytes(StandardCharsets.UTF_8));
        });

        // Add final "Done" status to queue
        gattWriteQueue.add(() -> {
            operationStatus.postValue("Provisioning Complete!");
            isWriting = false; // Mark as done
            // ### FIX 3: Disconnect after provisioning ###
            new Handler(Looper.getMainLooper()).postDelayed(this::disconnect, 500);
            // ##########################################
        });

        // Start processing the queue
        processGattWriteQueue();
    }
}