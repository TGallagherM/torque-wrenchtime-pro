package org.tgallagherm.torque.wrenchtimepro;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.prowl.torque.remote.ITorqueService;

import android.annotation.SuppressLint;
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
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;

public class PluginActivity extends Activity {

    private boolean isBound = false;
    // Using a simple TextView to output the structured manufacturer details
    private TextView mileageTextView;
    private ITorqueService torqueService;
    private static final String TAG = "WrenchTimePro";
    private ReminderAdapter adapter;
    private final List<String> reminderList = new ArrayList<>();

    /**
     * Called when the activity is first created. Initializes the UI, applies dynamic colors,
     * and sets up window insets for the FAB.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 1. Apply dynamic colors FIRST
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this);

        super.onCreate(savedInstanceState);

        // Ensure you have an activity_main.xml layout with a TextView id: vehicle_info_text
        setContentView(R.layout.activity_main);

        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setTitle(R.string.app_label);

        View addReminderFab = findViewById(R.id.add_reminder_fab);

        ViewCompat.setOnApplyWindowInsetsListener(addReminderFab, (v, windowInsets) -> {
            int navBarHeight = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;

            // Adjust the margin to be the default (e.g. 24dp) PLUS the navigation bar height
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            mlp.bottomMargin = navBarHeight + (int) (24 * getResources().getDisplayMetrics().density);
            v.setLayoutParams(mlp);

            return windowInsets;
        });

        initializeViews();
    }

    /**
     * Called when the activity is being resumed. Binds to the Torque service
     * and registers the broadcast receiver for vehicle updates.
     */
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

    /**
     * Called when the activity is losing focus. Unregisters the Torque broadcast receiver
     * and unbinds from the Torque service to prevent memory leaks.
     */
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
        mileageTextView = findViewById(R.id.mileage_info_text);
        RecyclerView remindersRecycler = findViewById(R.id.reminders_recycler);
        // Setup the list
        adapter = new ReminderAdapter(reminderList);
        remindersRecycler.setLayoutManager(new LinearLayoutManager(this));
        remindersRecycler.setAdapter(adapter);

        // 2. Setup the FAB to add text
        findViewById(R.id.add_reminder_fab).setOnClickListener(v -> {
            showAddReminderBottomSheet();
        });
    }

    /**
     * Displays a Material 3 Bottom Sheet containing an input field to
     * create a custom mileage reminder.
     */
    private void showAddReminderBottomSheet() {
        com.google.android.material.bottomsheet.BottomSheetDialog bottomSheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);

        @SuppressLint("InflateParams") View view = getLayoutInflater().inflate(R.layout.layout_add_reminder, null);
        bottomSheet.setContentView(view);

        com.google.android.material.textfield.TextInputEditText input =
                view.findViewById(R.id.reminder_input_edit_text);

        view.findViewById(R.id.save_reminder_button).setOnClickListener(v -> {
            String text = Objects.requireNonNull(input.getText()).toString().trim();
            if (!text.isEmpty()) {
                addReminder(text);
                bottomSheet.dismiss();
            } else {
                view.findViewById(R.id.reminder_input_layout).setActivated(true);
                // Optional: input.setError("Please enter a reminder");
            }
        });

        bottomSheet.show();
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
     * Adds a new reminder item to the list and notifies the adapter to refresh the UI.
     * @param text The text of the reminder to add.
     */
    private void addReminder(String text) {
        reminderList.add(text);
        adapter.notifyItemInserted(reminderList.size() - 1);
    }

    /**
     * Data model representing a single maintenance reminder.
     * Stores the name of the service (e.g., "Oil Change") and the specific
     * mileage at which it should be performed.
     */
    public static class Reminder {
        String name;
        String miles;

        public Reminder(String name, String miles) {
            this.name = name;
            this.miles = miles;
        }
    }

    /**
     * Adapter for the RecyclerView to manage and display the list of mileage reminders.
     */
    private static class ReminderAdapter extends RecyclerView.Adapter<ReminderAdapter.ViewHolder> {
        private final List<String> data;

        public ReminderAdapter(List<String> data) { this.data = data; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_reminder, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            holder.textView.setText(data.get(position));
        }

        @Override
        public int getItemCount() { return data.size(); }

        /**
         * ViewHolder class for the ReminderAdapter to hold the view for each list item.
         */
        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView textView;
            ViewHolder(View v) { super(v); textView = v.findViewById(R.id.reminder_text); }
        }
    }

    /**
     * Displays a Material 3 Alert Dialog offering options to modify or remove an entry.
     *
     * When an item in the reminder list is long-pressed, this method provides
     * a "Edit" or "Delete" menu. Choosing "Edit" re-opens the input sheet
     * with pre-filled data, while "Delete" removes the item with an animation.
     *
     * @param position The index of the item within the reminderList to be managed.
     */
    private void showEditDeleteOptions(int position) {
        String[] options = {"Edit", "Delete"};
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Manage Reminder")
                .setItems(options, (dialog, i) -> {
                    if (i == 0) {
                        // Edit logic: Re-open the input sheet pre-populated with existing data
                        showAddReminderBottomSheet(reminderList.get(position), position);
                    } else {
                        // Delete logic: Remove from data source and notify adapter for animation
                        reminderList.remove(position);
                        adapter.notifyItemRemoved(position);
                    }
                })
                .show();
    }

    /**
     * Fetches real-time data from the Torque app via the AIDL interface.
     */
    private void getProfileData() {
        if (torqueService == null) return;

        try {
            String[] profile = torqueService.getVehicleProfileInformation();
            int index = 0;
            if (profile != null ) {
//                displayToUI("Connected to Torque", vehicleInfoTextView);
                for (String data : profile) {
//                    appendToUI("Profile data[" + index + "]: " + data, vehicleInfoTextView);
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
     * Retrieves vehicle distance from the specific Torque PID (ff120c,0).
     * Replaces the placeholder with the current mileage value.
     */
    private void updateDistanceTracked() {
        if (torqueService == null) return;
        try {
            // Target the specific internal Torque PID for Trip Distance
            String targetPid = "ff120c,0";
            double[] values = torqueService.getPIDValuesAsDouble(new String[]{targetPid});

            if (values != null && values.length > 0 && !Double.isNaN(values[0])) {
                displayDistance(values[0]);
            } else {
                displayToUI("Distance tracking not available.", mileageTextView);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during distance tracking update", e);
        }
    }

    /**
     * Helper to list all available PIDs including those detected by Torque.
     */
    private void listAllPids() {
        if (torqueService == null) return;
        
        try {
            if (!torqueService.hasFullPermissions()) {
                Log.e(TAG, "Plugin does NOT have full permissions in Torque settings!");
                runOnUiThread(() -> addReminder("Error: Grant 'Full Permissions' in Torque Settings > Plugins"));
                return;
            }

            String[] pids = torqueService.listAllPIDsIncludingDetectedPIDs();
            if(pids == null) return;

            Log.d(TAG, "listAllPids called. Service null? " + (torqueService == null));
            Log.d(TAG, "PIDs found: " + pids.length);

            double[] values = torqueService.getPIDValuesAsDouble(pids);
            String[] info = torqueService.getPIDInformation(pids);
            List<String> fileOutput = new ArrayList<>();

            for (int i = 0; i < pids.length; i++) {
                String label = pids[i]; // Default to raw ID if name search fails

                // 3. Extract the "Long Name" from the CSV info string
                if (info != null && i < info.length && info[i] != null) {
                    String[] parts = info[i].split(",");
                    if (parts.length > 0 && !parts[0].trim().isEmpty()) {
                        label = parts[0];
                    }
                }

                // 4. Get the value
                double val = (values != null && i < values.length) ? values[i] : Double.NaN;
                String formattedValue = Double.isNaN(val) ? "N/A" : String.format(Locale.US, "%.2f", val);

                final String entry = "pid=" + pids[i] + " " + label + ": " + formattedValue;

                // 5. Update the list on the UI thread
                fileOutput.add(entry);
                runOnUiThread(() -> addReminder(entry));
            }

            savePidsToFile(fileOutput); // <-- Save to disk
        } catch (Exception e) {
            Log.e(TAG, "Error listing all PIDS", e);
        }
    }

    /**
     * Saves the current list of PIDs and their values to a text file
     * in the app's external files directory.
     * The file is saved at:
     * /Android/data/org.tgallagherm.torque.wrenchtimepro/files/torque_pids_log.txt
     *
     * @param data A list of formatted strings (e.g., "Engine RPM: 850.00") to write to disk.
     */
    private void savePidsToFile(List<String> data) {
        try {
            java.io.File file = new java.io.File(getExternalFilesDir(null), "torque_pids_log.txt");
            java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(file));
            for (String line : data) {
                writer.println(line);
            }
            writer.close();
            Log.d(TAG, "PID log saved to: " + file.getAbsolutePath());
        } catch (java.io.IOException e) {
            Log.e(TAG, "Failed to save PID log", e);
        }
    }

    /**
     * Helper to format and display the distance with user preferred units.
     * Overwrites the previous value in the UI (replacing the placeholder).
     */
    private void displayDistance(double value) throws RemoteException {
        String unit = torqueService.getPreferredUnit("km");
        double displayValue = value;
        // If the user's preferred unit is miles, convert km to miles
        if (unit != null && unit.equalsIgnoreCase("miles")) {
            displayValue = value * 0.62137119;
        }

        String formattedValue = String.format(Locale.US, "%.2f", displayValue);
        displayToUI(formattedValue + " " + (unit != null ? unit : ""), mileageTextView);
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder service) {
            torqueService = ITorqueService.Stub.asInterface(service);

            new Thread(() -> {
                try {
                    if (torqueService != null && torqueService.isConnectedToECU()) {
                        updateDistanceTracked(); // Updates distance info
                    }
                } catch (RemoteException e) { Log.e(TAG, "Error connecting to ECU", e); }
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
            Log.d(TAG, "Broadcast received: " + action); // Check Logcat for this!

            if ("org.prowl.torque.ACTION_VEHICLE_UPDATED".equals(action)) {
                // Just connected to adapter, refresh UI, ECU connected
                new Thread(() -> {
                    updateDistanceTracked(); // Updates distance info
                }).start();
            } else if ("org.prowl.torque.PROFILE_CHANGED".equals(action)) {
                // Vehicle profile switched
                new Thread(() -> {
                    updateDistanceTracked(); // Updates distance info
                }).start();
            }
        }
    };
}
