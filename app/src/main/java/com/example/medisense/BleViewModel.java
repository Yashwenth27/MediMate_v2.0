package com.example.medisense;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

public class BleViewModel extends AndroidViewModel {

    private static final String TAG = "BleViewModel";

    private BleService bleService;
    private final MutableLiveData<Boolean> isServiceBound = new MutableLiveData<>(false);

    // --- LiveData Mirrors ---
    private final MediatorLiveData<String> operationStatus = new MediatorLiveData<>();
    private final MediatorLiveData<Boolean> isReadyToProvision = new MediatorLiveData<>();

    // --- Public LiveData Getters ---
    public LiveData<String> getOperationStatus() { return operationStatus; }
    public LiveData<Boolean> isReadyToProvision() { return isReadyToProvision; }

    // --- ### NEW GETTER ADDED HERE ### ---
    /**
     * Public getter to observe if the service is bound.
     */
    public LiveData<Boolean> getIsServiceBound() { return isServiceBound; }
    // --- ############################### ---


    public BleViewModel(@NonNull Application application) {
        super(application);
        // Start and bind to the BleService
        Intent gattServiceIntent = new Intent(application, BleService.class);
        application.bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.d(TAG, "BleService connected");
            bleService = ((BleService.LocalBinder) service).getService();
            if (bleService.initialize()) {
                isServiceBound.postValue(true); // This fires the new observer
                observeServiceData();
            } else {
                Log.e(TAG, "Could not initialize BleService");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.w(TAG, "BleService disconnected");
            isServiceBound.postValue(false);
            bleService = null;
        }
    };

    /**
     * Link our ViewModel's LiveData to the Service's LiveData.
     */
    private void observeServiceData() {
        if (bleService == null) return;
        operationStatus.addSource(bleService.getOperationStatus(), operationStatus::postValue);
        isReadyToProvision.addSource(bleService.isReadyToProvision(), isReadyToProvision::postValue);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (isServiceBound.getValue() != null && isServiceBound.getValue()) {
            getApplication().unbindService(serviceConnection);
            isServiceBound.postValue(false);
            bleService = null;
        }
    }

    // --- Public Methods for UI to Call ---

    public void startScan() {
        if (bleService != null && isServiceBound.getValue() != null && isServiceBound.getValue()) {
            bleService.startScan();
        } else {
            // This was the problem: this was being called before service was bound
            Log.w(TAG, "Service not bound, cannot start scan");
        }
    }

    public void provisionDevice(String ssid, String password, String pid) {
        if (bleService != null && isServiceBound.getValue() != null && isServiceBound.getValue()) {
            bleService.provisionDevice(ssid, password, pid);
        } else {
            Log.w(TAG, "Service not bound, cannot provision");
        }
    }
}