package com.example.womensafetyapp;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class SOSService extends Service {

    private static final String TAG = "SOSService";
    private static final String CHANNEL_ID = "SOSServiceChannel";
    private static final int NOTIFICATION_ID = 101;
    private static final String PREFS_NAME = "SafetyAppPrefs";
    private static final String KEY_CONTACTS = "EmergencyContacts";
    private static final String TRIGGER_PHRASE = "sos"; // The trigger word (case-insensitive check)
    private static final String TRIGGER_PHRASE_ALT = "help me";

    // --- New Constant for App Name ---
    private static final String APP_NAME = "Kavach app";

    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private FusedLocationProviderClient fusedLocationClient;
    private final Handler restartHandler = new Handler();
    private Runnable restartRecognizerRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service Created");
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        initSpeechRecognizer();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 1. Create Notification Channel (for Android O+)
        createNotificationChannel();

        // 2. Create the persistent Notification
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        // --- Branding Fix 1: Update Notification Title ---
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(APP_NAME + " Active")
                .setContentText("Listening for the trigger word 'SOS' or 'Help me'...")
                .setSmallIcon(R.drawable.ic_launcher_foreground) // Use your app's icon
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();

        // 3. Start as Foreground Service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // For Android 14 (API 34) and higher, you must specify foreground service types.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // CORRECTED LINE: Use ServiceCompat constants for the types
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
                        ServiceCompat.STOP_FOREGROUND_REMOVE | ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        // 4. Start the voice recognition loop
        startListening();

        return START_STICKY; // Service should be restarted if killed by the OS
    }


    /**
     * Initializes the SpeechRecognizer and sets up the listener.
     */
    private void initSpeechRecognizer() {
        // If the device does not support SpeechRecognizer, log an error and don't proceed
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "Speech recognition is NOT available on this device.");
            Toast.makeText(this, "Voice recognition is unavailable.", Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new VoiceRecognitionListener());

        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());

        // Setup the continuous restart logic
        restartRecognizerRunnable = this::startListening;
    }

    /**
     * Starts the SpeechRecognizer listening process.
     */
    private void startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            try {
                // Cancel any previous session and start a new one
                speechRecognizer.cancel();
                speechRecognizer.startListening(recognizerIntent);
                Log.d(TAG, "SpeechRecognizer started.");
            } catch (Exception e) {
                Log.e(TAG, "Error starting SpeechRecognizer: " + e.getMessage());
                // Immediately attempt to restart if a setup error occurs
                restartHandler.postDelayed(restartRecognizerRunnable, 500);
            }
        } else {
            Log.e(TAG, "RECORD_AUDIO permission missing. Cannot start listening.");
        }
    }

    /**
     * Stops and cleans up the SpeechRecognizer.
     */
    private void stopListening() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
            Log.d(TAG, "SpeechRecognizer destroyed.");
        }
        restartHandler.removeCallbacks(restartRecognizerRunnable);
    }

    /**
     * Inner class implementing RecognitionListener for voice events.
     */
    private class VoiceRecognitionListener implements RecognitionListener {
        @Override
        public void onReadyForSpeech(Bundle params) { Log.d(TAG, "onReadyForSpeech"); }

        @Override
        public void onBeginningOfSpeech() { Log.d(TAG, "onBeginningOfSpeech"); }

        @Override
        public void onRmsChanged(float rmsdB) {}

        @Override
        public void onBufferReceived(byte[] buffer) {}

        @Override
        public void onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech - Restarting listener soon...");
            // Restart listening after a short delay
            restartHandler.postDelayed(restartRecognizerRunnable, 100);
        }

        @Override
        public void onError(int error) {
            String errorMessage = getErrorText(error);
            Log.e(TAG, "Recognition Error: " + errorMessage + " (Code: " + error + "). Restarting...");

            // In case of an error, restart the listening mechanism
            // ERROR_NO_MATCH (7) or ERROR_SPEECH_TIMEOUT (6) are common
            restartHandler.postDelayed(restartRecognizerRunnable, 100);
        }

        @Override
        public void onResults(Bundle results) {
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) {
                String recognizedText = matches.get(0).toLowerCase();
                Log.i(TAG, "Recognized: " + recognizedText);

                if (recognizedText.contains(TRIGGER_PHRASE) || recognizedText.contains(TRIGGER_PHRASE_ALT)) {
                    Log.d(TAG, "--- SOS TRIGGER DETECTED! ---");
                    Toast.makeText(getApplicationContext(), "Emergency Detected! Sending alerts...", Toast.LENGTH_LONG).show();
                    sendEmergencyAlert();
                }
            }
            // Ensure listening continues even after results are received
            restartHandler.postDelayed(restartRecognizerRunnable, 100);
        }

        @Override
        public void onPartialResults(Bundle partialResults) {}

        @Override
        public void onEvent(int eventType, Bundle params) {}

        // Helper to translate error codes to human-readable text
        private String getErrorText(int errorCode) {
            switch (errorCode) {
                case SpeechRecognizer.ERROR_AUDIO: return "Audio recording error";
                case SpeechRecognizer.ERROR_CLIENT: return "Client side error";
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "Insufficient permissions";
                case SpeechRecognizer.ERROR_NETWORK: return "Network error";
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "Network timeout";
                case SpeechRecognizer.ERROR_NO_MATCH: return "No recognition result matched";
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "Recognition service busy";
                case SpeechRecognizer.ERROR_SERVER: return "Server error";
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "No speech input";
                default: return "Unknown recognition error";
            }
        }
    }

    /**
     * Initiates the location retrieval and SMS sending process.
     */
    private void sendEmergencyAlert() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permission not granted. Cannot get location.");
            sendSms(null); // Send SMS without location if permission is missing
            return;
        }

        // Use the Fused Location Provider Client to get the last known location
        fusedLocationClient.getLastLocation().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                Location location = task.getResult();
                Log.d(TAG, "Location found: " + location.getLatitude() + ", " + location.getLongitude());
                sendSms(location);
            } else {
                Log.e(TAG, "Failed to get location or location is null.");
                sendSms(null);
            }
        });
    }

    /**
     * Retrieves contacts and sends the emergency message to all of them.
     */
    private void sendSms(Location location) {
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Set<String> contactSet = sharedPreferences.getStringSet(KEY_CONTACTS, new HashSet<>());

        if (contactSet.isEmpty()) {
            Log.e(TAG, "No emergency contacts saved. Alert aborted.");
            Toast.makeText(this, "No contacts saved. Please check settings.", Toast.LENGTH_LONG).show();
            return;
        }

        String locationLink = "Location Unavailable.";
        if (location != null) {
            // Google Maps URL format: https://www.google.com/maps/search/?api=1&query=<lat>,<lon>
            locationLink = "My live location: https://maps.google.com/?q=" + location.getLatitude() + "," + location.getLongitude();
        }

        // --- Branding Fix 2: Update SMS Message ---
        String emergencyMessage = "Emergency! I need help immediately. This message was triggered by the " + APP_NAME + ". " + locationLink;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "SMS permission not granted. Cannot send messages.");
            Toast.makeText(this, "SMS Permission missing. Message not sent.", Toast.LENGTH_LONG).show();
            return;
        }

        SmsManager smsManager = SmsManager.getDefault();

        for (String number : contactSet) {
            try {
                // SMS messages longer than 160 characters are automatically split.
                smsManager.sendTextMessage(number, null, emergencyMessage, null, null);
                Log.d(TAG, "SMS sent to: " + number);
                Toast.makeText(this, "Alert sent to " + number + "!", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "SMS failed to send to " + number + ": " + e.getMessage());
                Toast.makeText(this, "Alert to " + number + " failed.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Creates the notification channel for Android O (API 26) and above.
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    // --- Branding Fix 3: Update Notification Channel Name ---
                    APP_NAME + " Service",
                    NotificationManager.IMPORTANCE_HIGH
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // This is a started service, not a bound service
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service Destroyed");
        stopListening();
        // Reset the service status in SharedPreferences when destroyed normally
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        sharedPreferences.edit().putBoolean("ServiceStatus", false).apply();
    }
}
