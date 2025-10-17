package com.example.womensafetyapp;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.view.View; // Needed for future UI visibility control
import android.widget.TextView; // Assuming you might use a TextView for the warning

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final String PREFS_NAME = "SafetyAppPrefs";
    private static final String KEY_CONTACTS = "EmergencyContacts";
    private static final String KEY_SERVICE_STATUS = "ServiceStatus";

    private EditText etContact1, etContact2, etContact3;
    private Button btnSaveContacts;
    private SwitchMaterial toggleService;
    private SharedPreferences sharedPreferences;

    // Assuming you have a TextView or similar View in your layout for the persistent warning message
    // private TextView tvWarningMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI components and SharedPreferences
        etContact1 = findViewById(R.id.et_contact1);
        etContact2 = findViewById(R.id.et_contact2);
        etContact3 = findViewById(R.id.et_contact3);
        btnSaveContacts = findViewById(R.id.btn_save_contacts);
        toggleService = findViewById(R.id.toggle_service);
        // Assuming a warning view ID if you use a dedicated banner
        // tvWarningMessage = findViewById(R.id.tv_warning_message);
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Load saved contacts and service status
        loadContacts();

        // Initial permission check (will request if missing)
        checkAndRequestPermissions();

        // Set up listeners
        btnSaveContacts.setOnClickListener(v -> saveContacts());

        toggleService.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // When activating, first check if permissions and contacts are ready
                if (checkPermissionsGranted() && checkContactsExist()) {
                    startSOSService();
                    saveServiceStatus(true);
                } else {
                    // Reset the toggle if prerequisites fail
                    buttonView.setChecked(false);
                    checkAndRequestPermissions(); // Re-request permissions
                    if (!checkContactsExist()) {
                        Toast.makeText(this, "Please save at least one emergency contact first.", Toast.LENGTH_LONG).show();
                    } else if (!checkPermissionsGranted()) {
                        // Show warning immediately if permissions are missing
                        showPermissionRequiredMessage();
                    }
                }
            } else {
                // When deactivating
                stopSOSService();
                saveServiceStatus(false);
            }
        });
    }

    /**
     * LifeCycle Fix: Running the check in onResume ensures the status is refreshed
     * whenever the user navigates back to the app from Settings after manually granting permission.
     */
    @Override
    protected void onResume() {
        super.onResume();

        // 1. Check permissions every time the app comes to the foreground
        if (checkPermissionsGranted()) {
            // Permissions are now granted, hide the persistent message
            hidePermissionRequiredMessage();
        } else {
            // Permissions are still missing, show the persistent message
            showPermissionRequiredMessage();
            // Also, disable the service toggle if permissions are missing
            if (toggleService.isChecked()) {
                toggleService.setChecked(false);
                saveServiceStatus(false);
            }
        }

        // 2. Update toggle state based on permissions and saved status
        updateServiceToggleState();
    }

    /**
     * Helper to load the service state from SharedPreferences and update toggle.
     * The service is assumed to be running if the SharedPreferences value is true
     * AND permissions are granted.
     */
    private void updateServiceToggleState() {
        boolean savedStatus = sharedPreferences.getBoolean(KEY_SERVICE_STATUS, false);
        boolean permissionsOk = checkPermissionsGranted();

        // Only allow the toggle to be ON if permissions are granted
        if (savedStatus && permissionsOk) {
            toggleService.setChecked(true);
        } else {
            // If permissions are missing, or saved status is false, force OFF
            toggleService.setChecked(false);
            // If permissions are missing, force service status to false in prefs
            if (!permissionsOk) {
                saveServiceStatus(false);
            }
        }
    }


    /**
     * Saves the service status to SharedPreferences.
     */
    private void saveServiceStatus(boolean isRunning) {
        sharedPreferences.edit().putBoolean(KEY_SERVICE_STATUS, isRunning).apply();
    }

    /**
     * Saves the contacts from EditText fields into SharedPreferences. (No change needed)
     */
    private void saveContacts() {
        Set<String> contactSet = new HashSet<>();
        String c1 = etContact1.getText().toString().trim();
        String c2 = etContact2.getText().toString().trim();
        String c3 = etContact3.getText().toString().trim();

        if (!c1.isEmpty()) contactSet.add(c1);
        if (!c2.isEmpty()) contactSet.add(c2);
        if (!c3.isEmpty()) contactSet.add(c3);

        if (contactSet.isEmpty()) {
            Toast.makeText(this, "Please enter at least one valid phone number.", Toast.LENGTH_SHORT).show();
            return;
        }

        sharedPreferences.edit().putStringSet(KEY_CONTACTS, contactSet).apply();
        Toast.makeText(this, contactSet.size() + " emergency contacts saved!", Toast.LENGTH_SHORT).show();
    }

    /**
     * Loads the saved contacts from SharedPreferences and populates the EditText fields. (No change needed)
     */
    private void loadContacts() {
        Set<String> contactSet = sharedPreferences.getStringSet(KEY_CONTACTS, new HashSet<>());
        List<String> contacts = new ArrayList<>(contactSet);

        // Clear fields first
        etContact1.setText("");
        etContact2.setText("");
        etContact3.setText("");

        // Populate fields up to 3 contacts
        if (contacts.size() > 0) etContact1.setText(contacts.get(0));
        if (contacts.size() > 1) etContact2.setText(contacts.get(1));
        if (contacts.size() > 2) etContact3.setText(contacts.get(2));
    }

    /**
     * Checks if at least one contact is saved. (No change needed)
     */
    private boolean checkContactsExist() {
        Set<String> contactSet = sharedPreferences.getStringSet(KEY_CONTACTS, new HashSet<>());
        return !contactSet.isEmpty();
    }

    /**
     * Starts the Foreground SOS Service. (No change needed)
     */
    private void startSOSService() {
        Intent serviceIntent = new Intent(this, SOSService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
        Toast.makeText(this, "Voice Monitoring Activated. Say 'SOS'!", Toast.LENGTH_LONG).show();
    }

    /**
     * Stops the Foreground SOS Service. (No change needed)
     */
    private void stopSOSService() {
        Intent serviceIntent = new Intent(this, SOSService.class);
        stopService(serviceIntent);
        Toast.makeText(this, "Voice Monitoring Deactivated.", Toast.LENGTH_LONG).show();
    }

    // --- Permission Handling ---

    /**
     * Defines all required permissions based on the Android version. (No change needed)
     */
    private String[] getRequiredPermissions() {
        // Core permissions needed across all modern versions
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.SEND_SMS);
        permissions.add(Manifest.permission.RECORD_AUDIO);
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);

        // Permissions specific to Android Q (10) and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        }

        // Permissions specific to Android T (13) and above for notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        return permissions.toArray(new String[0]);
    }

    /**
     * Checks if all necessary permissions are granted. (No change needed)
     */
    private boolean checkPermissionsGranted() {
        for (String permission : getRequiredPermissions()) {
            // NOTE: ACCESS_BACKGROUND_LOCATION requires a separate check path in Settings.
            // This method accurately checks the final granted state for all declared permissions.
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Helper function to visually hide the persistent permission message.
     * Replace the Toast with your actual UI logic (e.g., hiding a TextView).
     */
    private void hidePermissionRequiredMessage() {
        // Example: if (tvWarningMessage != null) tvWarningMessage.setVisibility(View.GONE);
        // Using a Toast temporarily, but ideally you hide the visible banner element.
        System.out.println("DEBUG: Permissions granted. Hiding warning message.");
    }

    /**
     * Helper function to visually show the persistent permission message.
     * Replace the Toast with your actual UI logic (e.g., showing a TextView).
     */
    private void showPermissionRequiredMessage() {
        // Example: if (tvWarningMessage != null) tvWarningMessage.setVisibility(View.VISIBLE);
        // Using a Toast to reflect the image's warning
        Toast.makeText(this, "All critical permissions (Location, SMS, Microphone) are required for the app to function.", Toast.LENGTH_LONG).show();
        System.out.println("DEBUG: Permissions missing. Showing warning message.");
    }


    /**
     * Requests missing permissions from the user. (No change needed, but needed for initial flow)
     */
    private void checkAndRequestPermissions() {
        List<String> missingPermissions = new ArrayList<>();
        for (String permission : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }

        if (!missingPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            // If all are granted initially, hide the message
            hidePermissionRequiredMessage();
        }
    }

    /**
     * Callback for permission request results.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (checkPermissionsGranted()) {
                Toast.makeText(this, "All permissions granted!", Toast.LENGTH_SHORT).show();
                hidePermissionRequiredMessage();
                // If the user just granted permissions and the toggle was ON, try starting service
                if (toggleService.isChecked()) {
                    startSOSService();
                }
            } else {
                // If permissions are still missing after the initial request
                showPermissionRequiredMessage();
                // Disable the service toggle if permissions are missing
                toggleService.setChecked(false);
                saveServiceStatus(false);
            }
        }
    }
}
