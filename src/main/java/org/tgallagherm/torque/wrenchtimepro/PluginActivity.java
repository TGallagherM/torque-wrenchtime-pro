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
        displayToUI("Hello from WrenchTimePro!",vehicleInfoTextView);
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
     * Gathers physical manufacturing details of the vehicle.
     */
    private void gatherManufacturerData() {
        if (torqueService == null) {
            displayToUI("Torque not ready to retrieve data",mileageTextView);
            return;
        }

        try {
            String[] profile = torqueService.getVehicleProfileInformation();
            String profileName = (profile != null && profile.length > 0) ? profile[0] : "Unknown";

            // Retrieve the VIN from the profile data
            String vin = torqueService.retrieveProfileData("org.prowl.torque.VIN");

            StringBuilder sb = new StringBuilder();
            sb.append("=== Physical Vehicle Specifications ===\n\n")
                    .append("Registered Profile: ").append(profileName).append("\n");

            if (vin != null && !vin.isEmpty()) {
                sb.append("VIN: ").append(vin).append("\n\n")
                        .append(parseVinData(vin)); // Extract manufacturer details from VIN
            } else {
                sb.append("VIN: Not detected (Ensure ECU is connected)\n");
            }

            appendToUI(sb.toString(),mileageTextView);

        } catch (RemoteException e) {
            Log.e(TAG, "Failed to gather physical manufacturer data", e);
            appendToUI("Error retrieving vehicle specification data.",mileageTextView);
        }
    }

    /**
     * Extracts and decodes manufacturer codes from the VIN string.
     * Focuses on World Manufacturer ID, Vehicle Descriptor, and Model Year.
     * VIN Character 4-8 are manufacturer specific, wil add switch cases based on manufacturer later
     */
    private String parseVinData(String vin) {
        if (vin == null || vin.length() < 17) {
            return "Invalid VIN length for parsing manufacturer codes.";
        }

        // 1-3: World Manufacturer Identifier (WMI)
        String wmi = vin.substring(0, 3);
        // 4-8: Vehicle Descriptor Section (VDS) - Often contains trim/engine/axle codes
        String vds = vin.substring(3, 8);
        // 10: Model Year Code
        char yearCode = vin.charAt(9);

        return "--- Manufacturer Codes ---\n" +
                "Model Year Code: " + yearCode + "\n" +
                "WMI (Manufacturer ID): " + wmi + "\n" +
                "VDS (Trim/Body/Engine Codes): " + vds + "\n";
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
                displayToUI("Connected to Torque",vehicleInfoTextView);
                for (String data: profile) {
                    appendToUI("Profile data[" + index + "]: " + data,vehicleInfoTextView);
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
                displayToUI("Distance tracking not available.",mileageTextView);
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
        appendToUI(label + ": " + formattedValue + " " + unit,mileageTextView);
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder service) {
            torqueService = ITorqueService.Stub.asInterface(service);

            new Thread(() -> {
            // Explicitly trigger data gathering once the connection is established
                getProfileData();     // Clears "Hello" and starts the report
                gatherManufacturerData(); // Appends manufacturer info
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
                    gatherManufacturerData(); // Appends manufacturer info
//                    updateDistanceTracked(); // Appends distance info
                }).start();
            } else if ("org.prowl.torque.PROFILE_CHANGED".equals(action)) {
                // Vehicle profile switched
                new Thread(() -> {
                    gatherManufacturerData(); // Appends manufacturer info
//                    updateDistanceTracked(); // Appends distance info
                }).start();
            }
        }
    };
}