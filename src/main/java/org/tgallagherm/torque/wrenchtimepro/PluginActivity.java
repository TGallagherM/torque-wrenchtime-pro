package org.tgallagherm.torque.wrenchtimepro;

import java.util.ArrayList;
import java.util.List;

import org.prowl.torque.remote.ITorqueService;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

public class PluginActivity extends Activity {

    // Using a simple TextView to output the structured manufacturer details
    private TextView infoTextView;
    private ITorqueService torqueService;
    private static final String TAG = "WrenchTimePro";

    private static final String DISTANCE_PID = "0131"; // PID for Distance travelled since codes cleared

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Ensure you have an activity_main.xml layout with a TextView id: vehicle_info_text
        setContentView(R.layout.activity_main);
        
        initializeViews();
        displayVehicleData();
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
     * Orchestrates gathering and rendering the data.
     */
    private void displayVehicleData() {
        List<Manufacturer> manufacturers = loadManufacturerSpecifications();
        String formattedOutput = formatManufacturerDetails(manufacturers);
        infoTextView.setText(formattedOutput);
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
     * Data Layer: Generates the list of vehicle manufacturers.
     * Modular: Add new manufacturers or models by appending items to this list.
     */
    private List<Manufacturer> loadManufacturerSpecifications() {
        List<Manufacturer> list = new ArrayList<>();
        
        // Base technical details derived from standard manufacturer references
        list.add(new Manufacturer("Toyota", "Japan", "Global"));
        list.add(new Manufacturer("Ford", "USA", "Global"));
        list.add(new Manufacturer("General Motors", "USA", "Global"));
        
        return list;
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
     * Torque's internal PIDs for "Distance (Trip)". Displays the value
     * in the user's preferred units (KM/Miles).
     * */
    private void updateDistanceTracked() { if (torqueService == null) return;
        try {
            // 1. Try the standard OBD PID first
            double[] values = torqueService.getPIDValuesAsDouble(new String[]{DISTANCE_PID});

            if (values != null && values.length > 0 && !Double.isNaN(values[0])) {
                displayDistance("Distance (OBD)", values[0]);
                return;
            }

            // 2. Fallback: Search for Torque's internal "Trip Distance" sensor
            String[] allPids = torqueService.listAllPIDs();
            String tripDistancePidId = null;

            if (allPids != null) {
                for (String pidId : allPids) {
                    // Torque IDs often contain the name. We look for the internal trip distance.
                    // Common internal IDs for trip distance often include "ff1204" or similar
                    String description = torqueService.getDescriptionForPid(Long.parseLong(pidId.split(",")[0], 16));
                    if (description != null && description.toLowerCase().contains("distance (trip)")) {
                        tripDistancePidId = pidId;
                        break;
                    }
                }
            }

            if (tripDistancePidId != null) {
                double[] tripValues = torqueService.getPIDValuesAsDouble(new String[]{tripDistancePidId});
                if (tripValues != null && tripValues.length > 0 && !Double.isNaN(tripValues[0])) {
                    displayDistance("Distance (Trip)", tripValues[0]);
                }
            } else {
                infoTextView.append("\n\nDistance tracking not available.");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error during distance tracking update", e);
        }
    }

    /**
     * Helper to format and display the distance with user preferred units.
     */
    private void displayDistance(String label, double value) throws RemoteException {
        // getPreferredUnit converts the SI unit label to the user's preference (e.g. Miles)
        String unit = torqueService.getPreferredUnit("km");
        infoTextView.append("\n\n" + label + ": " + String.format("%.2f", value) + " " + unit);
    }

    /**
     * Inner Data Model representing basic Manufacturer metrics.
     * Modular: Expand fields here (e.g., specific torque specs, fastener lists) to grow features.
     */
    public record Manufacturer(String name, String headquarters, String marketScope) {}

    /**
     * Bits of service code. You usually won't need to change this.
     */
    private ServiceConnection connection = new ServiceConnection() {
        public void onServiceConnected(ComponentName arg0, IBinder service) {
            torqueService = ITorqueService.Stub.asInterface(service);
//            updateWithRealData();
        };
        public void onServiceDisconnected(ComponentName name) {
            torqueService = null;
        };
    };
}