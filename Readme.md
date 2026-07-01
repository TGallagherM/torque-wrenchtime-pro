# Torque WrenchTime Pro

A modern, Material 3 maintenance reminder plugin for the **Torque Pro (OBD2 & Car)** Android application.

## Overview

**Torque WrenchTime Pro** allows users to set custom maintenance intervals (e.g., Oil Change every 5,000 miles, Tire Rotation every 7,500 miles). The plugin binds to the Torque Pro background service to track the vehicle's actual mileage in real-time and triggers push notifications through the Torque interface when a service threshold is reached.

## Key Features

-   **Material 3 UI**: Full support for Dynamic Colors (wallpaper-based tinting) and modern M3 components.
-   **Structured Reminders**: Define reminders with a human-readable name and a specific mileage interval.
-   **Real-time Tracking**: Uses Torque's internal high-precision Trip Distance PID (`ff120c,0`) for accurate updates.
-   **Persistent Storage**: Powered by the **Room Persistence Library** to ensure reminders and tracking data survive app restarts and phone reboots.
-   **Localized Experience**: Automatically switches between **Miles** and **KM** based on Torque settings and formats numbers according to the phone's regional settings (handling decimal commas/points correctly).
-   **Intelligent Sync**: Uses `ListAdapter` and `DiffUtil` for smooth UI animations and efficient data updates.

## Prerequisites

1.  **Torque Pro (OBD2 & Car)** must be installed on the device.
2.  **Full Permissions**: You must grant "Full Permissions" to this plugin within Torque Pro settings (**Settings > Plugins > Allow full permissions**). This is required for the plugin to read specific vehicle PIDs.

## How It Works

### Distance Tracking
The plugin monitors the `ff120c,0` PID. This internal Torque identifier tracks distance stored in the vehicle profile. The logic includes:
-   **Fixed-Unit Calculation**: All internal math and database storage is performed in Kilometers to prevent errors if the user toggles between Miles and KM.
-   **Reset Detection**: If a user resets their "Trip" in Torque, the plugin detects the mileage drop and recalibrates the tracking point automatically.

### Notifications
When the difference between the *current mileage* and the *last notified mileage* exceeds the user-defined interval, the plugin broadcasts an `org.prowl.torque.MESSAGE` intent. Torque Pro receives this and displays/speaks the maintenance alert to the driver.

## Development Architecture

-   **Language**: Java 21
-   **SDK Target**: Android 15 (API 35) / Android 17+ compatible.
-   **Database**: Room (SQLite abstraction).
-   **Communication**: AIDL (`ITorqueService.aidl`) for Inter-Process Communication with Torque Pro.

## Usage

1.  Open Torque Pro and ensure it is connected to your vehicle's ECU.
2.  Launch **WrenchTimePro** from the Torque Plugin menu or your app drawer.
3.  Tap the **Floating Action Button (+)** to add a new reminder.
4.  Enter the maintenance name and the interval (Miles or KM depending on your Torque preference).
5.  **Long-press** any item in the list to **Edit** or **Delete** it.
6.  Drive! The plugin will handle the rest in the background.

## Troubleshooting

-   **Mileage showing "---"**: Ensure Torque is actively connected to the car. The plugin can only read mileage when the OBD2 adapter is communicating with the ECU.
-   **No PIDs listed**: Verify that "Allow full permissions" is enabled in Torque settings.

---
*Developed by Tgallagherm.*