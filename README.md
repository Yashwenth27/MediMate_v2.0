# MediMate (MediSense Project)

![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android)
![Language](https://img.shields.io/badge/Language-Java-ED8B00?style=for-the-badge&logo=java)
![Database](https://img.shields.io/badge/Database-Firebase_RTDB-FFCA28?style=for-the-badge&logo=firebase)
![Technology](https://img.shields.io/badge/Technology-Bluetooth_LE-0082FC?style=for-the-badge&logo=bluetooth)

This is the official Android application for MediMate. The app provides a user dashboard for managing medicine schedules and includes a hardware provisioning system for setting up `MediTrack_Dispenser` devices via Bluetooth Low Energy (BLE).

---

## 🚀 Features

* **User Authentication:** Custom Login & Sign Up system (non-Firebase-Auth).
* **Auto-Login:** Remembers the user's session using `SharedPreferences`.
* **Firebase Dashboard:** `DashActivity` fetches and displays the user's name, medicine info, pill count, and schedule in real-time.
* **Hardware Provisioning:** A modular dialog (`ProvisioningDialogFragment`) allows for provisioning the Wi-Fi credentials (SSID, Password) and a Patient ID (PID) to the dispenser.
* **BLE Auto-Connect:** Automatically scans for and connects to devices named `MediTrack_Dispenser`.
* **Robust BLE Writes:** Uses a sequential write queue to reliably send provisioning data.
* **Live Status Indicator:** `DashActivity` shows a green "Online" or red "Offline" dot based on the BLE connection status to the dispenser.
* **Supporting Features:** Sign Out, Swipe-to-Refresh, and pop-ups for Re-Scheduling and Updating Stock.

---

## 🏗️ Technology Stack

* **Language:** Java
* **Architecture:** MVVM-S (Model-View-ViewModel-Service)
    * **UI:** Activities / Fragments (e.g., `DashActivity`, `ProvisioningDialogFragment`)
    * **State:** `AndroidViewModel` and `LiveData` to hold state and survive configuration changes.
    * **Service:** A bound Android `Service` (`BleService`) to manage the BLE connection's lifecycle.
* **Database:** Firebase Realtime Database (for user data and schedules).
* **Connectivity:** Bluetooth Low Energy (BLE) for device provisioning.
* **Session:** `SharedPreferences` for auto-login.

---

## 🏛️ Architecture

The app is built on a clean, modular architecture to separate concerns.

1.  **UI Layer (`DashActivity`, `ProvisioningDialogFragment`)**
    * Responsible only for *displaying* data and *forwarding* user events.
    * Observes `LiveData` from the `BleViewModel`.
    * Knows nothing about BLE or Firebase.

2.  **ViewModel Layer (`BleViewModel`)**
    * Acts as a bridge between the UI and the `BleService`.
    * Binds to the `BleService` and exposes its `LiveData` (like `operationStatus`, `isReadyToProvision`) to the UI.
    * Survives UI destruction (like screen rotation), holding the app's state.

3.  **Service Layer (`BleService`)**
    * This is the core engine for all BLE operations.
    * It's a bound Android `Service`, so it can run even if the UI is in the background.
    * Manages scanning (with auto-connect), connection state, service discovery, and the sequential **write queue** to ensure provisioning data is sent reliably.

---

## 🛠️ How to Set Up (For New Developers)

To run this project, you must set up Firebase and have a compatible BLE device.

### 1. Firebase Setup

This project uses Firebase for user data, but **not** for Firebase Authentication.

1.  Go to the [Firebase Console](https://console.firebase.google.com/) and create a new project.
2.  Add an Android App to the project with the package name: `com.example.medisense`
3.  Download the `google-services.json` file and place it in the **`app/`** directory of this project.
4.  Go to **Realtime Database** > **Data**. Manually add the `users` node. Your data structure should look like this:

    ```json
    {
      "users": {
        "yash": {
          "med_count": "10",
          "med_info": "Paracetamol",
          "med_sched": "14:30",
          "name": "Yashwenth S",
          "password": "pass123"
        },
        "abijith": {
          "med_count": "20",
          "med_info": "Aspirin",
          "med_sched": "09:00",
          "name": "Abijith S V",
          "password": "password"
        }
      }
    }
    ```

5.  Go to **Realtime Database** > **Rules**. **This is critical.** Replace the default rules with the following to allow your app to read/write:

    ```json
    {
      "rules": {
        "users": {
          ".read": "true",
          ".write": "true"
        },
        "commands": {
          ".read": "true",
          ".write": "true"
        }
      }
    }
    ```

6.  Click **Publish**.

### 2. BLE Device Setup

The app is hard-coded to work with a specific BLE device.

* **Device Name:** The BLE device **must** advertise the name `MediTrack_Dispenser`.
* **GATT Profile:** The device must host the following Service and Characteristics.

    * **Service UUID:** `12345678-1234-1234-1234-1234567890ab`
    * **Characteristics (with WRITE property):**
        * SSID: `12345678-1234-1234-1234-1234567890ac`
        * Password: `12345678-1234-1V234-1234-1234567890ad`
        * Patient ID: `12345678-1234-1234-1234-1234567890ae`

---

## 📁 Key File Breakdown

* **`HomeActivity.java`**: Handles user Login, Sign Up, and Auto-Login.
* **`DashActivity.java`**: The main user dashboard. Listens to Firebase for data changes and observes the `BleViewModel` for "Online/Offline" status.
* **`ProvisioningDialogFragment.java`**: The modular pop-up for BLE provisioning. Contains all permission requests and UI logic for the provisioning flow.
* **`BleViewModel.java`**: The central `ViewModel` that connects all UI components (`DashActivity` and `ProvisioningDialogFragment`) to the `BleService`.
* **`BleService.java`**: **The core of the BLE system.** Manages scanning, connecting, and the sequential write queue.
* **`AndroidManifest.xml`**: Declares all required permissions (Bluetooth, Location) and the `BleService`.
