package org.tgallagherm.torque.wrenchtimepro;

import java.util.Locale;
import java.util.List;

import org.prowl.torque.remote.ITorqueService;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.widget.TextView;

public class PluginActivity extends Activity {

    // Using a simple TextView to output the structured manufacturer details
    private TextView infoTextView;
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
        displayToUI("Hello from WrenchTimePro!");
//        gatherManufacturerData();
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        Intent intent = new Intent();
        // Torque app package name and the service action
        intent.setClassName("org.prowl.torque", "org.prowl.torque.remote.TorqueService");
        boolean ok = bindService(intent, connection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (torqueService != null) {
            unbindService(connection);
        }
    }

    /**
     * Initializes the UI components.
     */
    private void initializeViews() {
        infoTextView = findViewById(R.id.vehicle_info_text);
    }

    /**
     * Helper to update the UI TextView with provided text.
     * Ensures the update runs on the Main UI thread and appends a newline.
     */
    private void displayToUI(final String message) {
        runOnUiThread(() -> {
            if (infoTextView != null) {
                infoTextView.append("\n" + message);
            }
        });
    }

    /**
     * Gathers physical manufacturing details of the vehicle.
     */
    private void gatherManufacturerData() {
        if (torqueService == null) return;

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

            displayToUI(sb.toString());

        } catch (RemoteException e) {
            Log.e(TAG, "Failed to gather physical manufacturer data", e);
            displayToUI("Error retrieving vehicle specification data.");
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

        StringBuilder decoded = new StringBuilder();
        decoded.append("--- Manufacturer Codes ---\n")
                .append("Model Year Code: ").append(yearCode).append("\n")
                .append("WMI (Manufacturer ID): ").append(wmi).append("\n")
                .append("VDS (Trim/Body/Engine Codes): ").append(vds).append("\n");

        return decoded.toString();
    }

    /**
     * Fetches real-time data from the Torque app via the AIDL interface.
     */
    private void updateWithRealData() {
        if (torqueService == null) return;

        try {
            String[] profile = torqueService.getVehicleProfileInformation();
            if (profile != null && profile.length > 0) {
                String profileName = profile[0];
                infoTextView.append("\n\nConnected to Torque Profile: " + profileName);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to get vehicle profile information", e);
        }
    }

    /**
     * Presentation Layer: Transforms the data structures into clear, scannable text.
     * Modular: Adjust this function to change how information layout appears on screen.
     */
    private String formatManufacturerDetails(List<Manufacturer> manufacturers) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Vehicle Manufacturer Directory ===\n\n");
        
        for (Manufacturer maker : manufacturers) {
            sb.append("Brand: ").append(maker.name()).append("\n")
              .append("Headquarters: ").append(maker.headquarters()).append("\n")
              .append("Market Scope: ").append(maker.marketScope()).append("\n")
              .append("-----------------------------------\n");
        }
        
        return sb.toString();
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
                displayToUI("Distance tracking not available.");
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
        // Use java.util.Locale.US to ensure consistent formatting
        String formattedValue = String.format(Locale.US, "%.2f", value);
        displayToUI(label + ": " + formattedValue + " " + unit);
    }

    /**
     * Inner Data Model representing basic Manufacturer metrics.
     * Modular: Expand fields here (e.g., specific torque specs, fastener lists) to grow features.
     */
    public record Manufacturer(String name, String headquarters, String marketScope) {}

    /**
     * Handles the connection lifecycle with the Torque service.
     */
    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder service) {
            torqueService = ITorqueService.Stub.asInterface(service);

            // Explicitly trigger data gathering once the connection is established
            updateWithRealData();
            gatherManufacturerData();
            updateDistanceTracked();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            torqueService = null;
        }
    };
}