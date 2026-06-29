package org.tgallagherm.torque.wrenchtimepro;

import java.util.Locale;

import org.prowl.torque.remote.ITorqueService;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

public class PluginActivity extends Activity {

    private boolean isBound = false;
    // Using a simple TextView to output the structured manufacturer details
    private TextView vehicleInfoTextView;
    private TextView mileageTextView;
    private ITorqueService torqueService;
    private static final String TAG = "WrenchTimePro";
    private static final String DISTANCE_PID = "0131"; // PID for Distance traveled since codes cleared

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Ensure you have an activity_main.xml layout with a TextView id: vehicle_info_text
        setContentView(R.layout.activity_main);

        initializeViews();

        // Methods to run when the activity is created
        displayToUI("Hello from WrenchTimePro!", vehicleInfoTextView);
//        gatherManufacturerData();
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        Intent intent = new Intent();
        // Torque app package name and the service action
        intent.setClassName("org.prowl.torque", "org.prowl.torque.remote.TorqueService");
        isBound = bindService(intent, connection, 0);

        // Register the Torque broadcast receiver when the activity gains focus
        IntentFilter filter = new IntentFilter();
        filter.addAction("org.prowl.torque.ACTION_VEHICLE_UPDATED");
        filter.addAction("org.prowl.torque.PROFILE_CHANGED");
        // Using ContextCompat automatically handles the flag logic across different API levels
        ContextCompat.registerReceiver(
                this,
                torqueReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
        );
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Unregister immediately when the activity loses focus to prevent memory leaks
        unregisterReceiver(torqueReceiver);

        if (isBound) {
            unbindService(connection);
            isBound = false;
            torqueService = null;
        }
    }

    /**
     * Initializes the UI components.
     */
    private void initializeViews() {
        vehicleInfoTextView = findViewById(R.id.vehicle_info_text);
        mileageTextView = findViewById(R.id.mileage_info_text);
    }

    /**
     * Helper to update the UI TextView with provided text.
     * Ensures the update runs on the Main UI thread and appends a newline.
     */
    private void displayToUI(final String message, TextView view) {
        runOnUiThread(() -> {
            if (view != null) {
                view.setText(message);
            }
        });
    }

    /**
     * Helper to append text to the UI TextView.
     * Use this for modular data updates after the initial display.
     */
    private void appendToUI(final String message, TextView view) {
        runOnUiThread(() -> {
            if (view != null) {
                view.append("\n" + message);
            }
        });
    }

    /**
     * Fetches real-time data from the Torque app via the AIDL interface.
     */
    private void getProfileData() {
        if (torqueService == null) return;

        try {
            String[] profile = torqueService.getVehicleProfileInformation();
            int index = 0;
            if (profile != null && profile.length > 0) {
                displayToUI("Connected to Torque", vehicleInfoTextView);
                for (String data : profile) {
                    appendToUI("Profile data[" + index + "]: " + data, vehicleInfoTextView);
                    index++;
                }
//                String profileName = profile[0];
                // Use displayToUI to clear the "Hello" message and start fresh
//                displayToUI("Connected to Torque, Selected Profile: " + profileName,vehicleInfoTextView);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to get vehicle profile information", e);
        }
    }

    /**
     * Retrieves vehicle distance with fallback.
     * Tries standard OBD PID (0x0131) first; if unsupported, searches
     * Torque's internal PIDs for "Distance (Trip)".
     */
    private void updateDistanceTracked() {
        if (torqueService == null) return;
        try {
            double[] values = torqueService.getPIDValuesAsDouble(new String[]{DISTANCE_PID});

            if (values != null && values.length > 0 && !Double.isNaN(values[0])) {
                displayDistance("Distance (OBD)", values[0]);
                return;
            }

            String[] allPids = torqueService.listAllPIDs();
            String tripDistancePidId = null;

            if (allPids != null) {
                for (String pidId : allPids) {
                    // Use getPIDInformation to avoid deprecated getDescriptionForPid
                    String[] info = torqueService.getPIDInformation(new String[]{pidId});
                    if (info != null && info.length > 0) {
                        String description = info[0].split(",")[0];
                        if (description != null && description.toLowerCase().contains("distance (trip)")) {
                            tripDistancePidId = pidId;
                            break;
                        }
                    }
                }
            }

            if (tripDistancePidId != null) {
                double[] tripValues = torqueService.getPIDValuesAsDouble(new String[]{tripDistancePidId});
                if (tripValues != null && tripValues.length > 0 && !Double.isNaN(tripValues[0])) {
                    displayDistance("Distance (Trip)", tripValues[0]);
                }
            } else {
                displayToUI("Distance tracking not available.", mileageTextView);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error during distance tracking update", e);
        }
    }

    /**
     * Helper to format and display the distance with user preferred units.
     */
    private void displayDistance(String label, double value) throws RemoteException {
        String unit = torqueService.getPreferredUnit("km");
        double displayValue = value;
        // If the user's preferred unit is miles, convert km to miles
        if (unit != null && unit.equalsIgnoreCase("miles")) {
            displayValue = value * 0.62137119;
        }

        String formattedValue = String.format(Locale.US, "%.2f", displayValue);
        appendToUI(label + ": " + formattedValue + " " + unit, mileageTextView);
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder service) {
            torqueService = ITorqueService.Stub.asInterface(service);

            new Thread(() -> {
                // Explicitly trigger data gathering once the connection is established
                getProfileData();     // Clears "Hello" and starts the report
            }).start();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            torqueService = null;
        }
    };
    private final BroadcastReceiver torqueReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("org.prowl.torque.ACTION_VEHICLE_UPDATED".equals(action)) {
                // Just connected to adapter, refresh UI, ECU connected
                new Thread(() -> {
//                    updateDistanceTracked(); // Appends distance info
                }).start();
            } else if ("org.prowl.torque.PROFILE_CHANGED".equals(action)) {
                // Vehicle profile switched
                new Thread(() -> {
//                    updateDistanceTracked(); // Appends distance info
                }).start();
            }
        }
    };
}