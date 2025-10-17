🛡️ Kavach (कवच): Voice-Activated Personal Safety App

Kavach (meaning "Armor" or "Shield") is a high-priority, instant-alert Android application designed to provide immediate assistance during emergency situations. The application operates using an on-device "backend" architecture to eliminate network latency, ensuring the user can trigger help using a simple voice command even when the phone is locked or the app is in the background.

✨ Key Features

Voice Activation: Instantly triggers an alert when the user says the keywords "SOS" or "Help Me" (case-insensitive).

Foreground Service: Runs the core listening logic as a high-priority foreground service, guaranteeing continued operation even if the main application is closed or the device screen is off.

Location Tracking: Uses the Fused Location Provider to fetch the user's last known location quickly.

Emergency SMS Alert: Dispatches a branded SMS containing the user's exact Google Maps location link to a list of pre-saved emergency contacts.

State Integrity: Implements a robust permission checker to ensure the service is only active when all necessary permissions are granted.

🛠️ Technical Architecture & Backend Focus

The core strength of the Kavach app lies in its architecture, which prioritizes reliability and immediacy over cloud services.

Component

Responsibility

Technical Implementation

SOS Handler

Ensures the service is always available.

Implemented via a ForegroundService with FOREGROUND_SERVICE_TYPE_MICROPHONE.

Voice Trigger

Continuous, low-latency listening.

Uses the native Android SpeechRecognizer in a continuous loop, handling timeout and no-match errors by self-restarting.

Data Persistence

Configuration and contact storage.

SharedPreferences is used to store emergency contacts and the active service status, ensuring data survives reboots.

Reliability Gate

Critical permission validation.

A dedicated check in MainActivity.java's onResume() forces re-validation of Location, SMS, and Audio permissions to prevent false activation.

Alert Dispatch

Message and location delivery.

FusedLocationProviderClient for fast location; SmsManager for reliable text dispatch, prioritizing alert sending even if location fails.

🚀 Getting Started

Prerequisites

Android Studio (Latest Version)

A physical Android device or emulator with Google Play Services installed.

Setup and Build

Clone the Repository:

git clone https://github.com/SabreenaKhan/Kavach-safety-app.git


Open Project: Open the project folder in Android Studio.

Sync Gradle: Allow Gradle to sync and download dependencies.

Run: Select your device/emulator and click the Run button.

Permissions Required (Mandatory)

The app will prompt the user for the following critical permissions upon first run. These must be granted for the service to function:

android.permission.RECORD_AUDIO

android.permission.SEND_SMS

android.permission.ACCESS_FINE_LOCATION


🔗 Deployment

The latest debug build of the application is available for direct download and testing via the GitHub Releases page:

Download APK: https://github.com/SabreenaKhan/Kavach-safety-app/releases/tag/v1.0.0
