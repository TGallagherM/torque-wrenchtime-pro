package org.tgallagherm.torque.wrenchtimepro;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.prowl.torque.remote.ITorqueService;
import org.tgallagherm.torque.wrenchtimepro.data.AppDatabase;
import org.tgallagherm.torque.wrenchtimepro.data.Reminder;

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
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;

public class PluginActivity extends Activity {

    private boolean isBound = false;
    // Using a simple TextView to output the structured manufacturer details
    private TextView mileageTextView;
    private ITorqueService torqueService;
    private static final String TAG = "WrenchTimePro";
    private String currentUnit = "miles"; // Tracks the user's unit preference from Torque, Default to miles
    private ReminderAdapter adapter;
    private final List<Reminder> reminderList = new ArrayList<>();

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
        new Thread(() -> {
            List<Reminder> savedReminders = AppDatabase.getInstance(this).reminderDao().getAll();
            runOnUiThread(() -> {
                reminderList.addAll(savedReminders);
                // Modern ListAdapter approach: Submit the list to trigger DiffUtil
                adapter.submitList(new ArrayList<>(reminderList));
            });
        }).start();

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
        adapter = new ReminderAdapter();
        remindersRecycler.setLayoutManager(new LinearLayoutManager(this));
        remindersRecycler.setAdapter(adapter);

        // 2. Setup the FAB to add text
        findViewById(R.id.add_reminder_fab).setOnClickListener(v -> {
            showAddReminderBottomSheet(null, -1);
        });
    }

    /**
     * Displays a Material 3 Bottom Sheet to either create a new reminder
     * or edit an existing one.
     *
     * @param existing Optional reminder to edit. Pass null for a new reminder.
     * @param position The position in the list if editing, otherwise -1.
     */
    private void showAddReminderBottomSheet(Reminder existing, int position) {
        com.google.android.material.bottomsheet.BottomSheetDialog bottomSheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);

        @SuppressLint("InflateParams") View view = getLayoutInflater().inflate(R.layout.layout_add_reminder, null);
        bottomSheet.setContentView(view);

        com.google.android.material.textfield.TextInputEditText nameInput = view.findViewById(R.id.edit_name);
        com.google.android.material.textfield.TextInputEditText milesInput = view.findViewById(R.id.edit_miles);
        com.google.android.material.textfield.TextInputLayout milesLayout = view.findViewById(R.id.miles_input_layout);
        TextView title = view.findViewById(R.id.sheet_title);

        // Dynamic Hint: "At miles" or "At km" based on regional setting
        if (milesLayout != null) {
            milesLayout.setHint(getString(R.string.distance_hint_format, currentUnit));
        }

        // If editing, pre-fill the fields
        if (existing != null) {
            title.setText(R.string.edit_reminder);
            nameInput.setText(existing.name);
            milesInput.setText(existing.miles);
        }else {
            title.setText(R.string.new_reminder);
        }

        view.findViewById(R.id.save_button).setOnClickListener(v -> {
            String name = Objects.requireNonNull(nameInput.getText()).toString().trim();
            String miles = Objects.requireNonNull(milesInput.getText()).toString().trim();

            if (!name.isEmpty() && !miles.isEmpty()) {
                if (existing != null) {
                    existing.name = name;
                    existing.miles = miles;
                    new Thread(() -> {
                        // Save changes to database
                        AppDatabase.getInstance(this).reminderDao().update(existing);
                        runOnUiThread(() -> {
                            adapter.submitList(new ArrayList<>(reminderList));
                        });
                    }).start();
                } else {
                    addReminder(name, miles);
                }
                bottomSheet.dismiss();
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
     * Adds a new reminder item to the list and notifies the adapter to refresh the UI.
     * @param name The name of the reminder to add.
     * @param miles The miles of the reminder to add.
     */
    private void addReminder(String name, String miles) {
        Reminder newReminder = new Reminder(name, miles);
        new Thread(() -> {
            // 1. Save to database
            AppDatabase.getInstance(this).reminderDao().insert(newReminder);

            // 2. Update UI on main thread
            runOnUiThread(() -> {
                reminderList.add(newReminder); // Add to our local list
                adapter.submitList(new ArrayList<>(reminderList)); // Refresh UI
            });
        }).start();
    }

    /**
     * Modern ListAdapter for the RecyclerView.
     * Automatically handles item animations and background diffing for best performance.
     */
    private class ReminderAdapter extends ListAdapter<Reminder, ReminderAdapter.ViewHolder> {

        // DiffUtil handles the complex logic of identifying exactly what changed in the list
        private static final DiffUtil.ItemCallback<Reminder> DIFF_CALLBACK =
                new DiffUtil.ItemCallback<Reminder>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull Reminder oldItem, @NonNull Reminder newItem) {
                        // Return true if items represent the same entity (usually compare IDs)
                        return oldItem.id == newItem.id;
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull Reminder oldItem, @NonNull Reminder newItem) {
                        // Return true if the visual data is identical
                        return Objects.equals(oldItem.name, newItem.name) &&
                                Objects.equals(oldItem.miles, newItem.miles);
                    }
                };

        public ReminderAdapter() {
            super(DIFF_CALLBACK);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_reminder, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Reminder item = getItem(position); // Use getItem(position) provided by ListAdapter
            holder.name.setText(item.name);

            String localizedMiles;
            try {
                double m = Double.parseDouble(item.miles);
                localizedMiles = java.text.NumberFormat.getInstance(Locale.getDefault()).format(m);
            } catch (Exception e) {
                localizedMiles = item.miles;
            }

            holder.miles.setText(holder.itemView.getContext().getString(
                    R.string.mileage_display_format,
                    localizedMiles,
                    currentUnit));

            holder.itemView.setOnLongClickListener(v -> {
                showEditDeleteOptions(position);
                return true;
            });
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView name, miles;
            ViewHolder(View v) {
                super(v);
                name = v.findViewById(R.id.reminder_name);
                miles = v.findViewById(R.id.reminder_miles);
            }
        }
    }

    /**
     * Displays a Material 3 Alert Dialog offering options to modify or remove an entry.
     * When an item in the reminder list is long-pressed, this method provides
     * an "Edit" or "Delete" menu. Choosing "Edit" re-opens the input sheet
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
                        Reminder toDelete = reminderList.get(position);
                        new Thread(() -> {
                            // 1. Remove from database
                            AppDatabase.getInstance(this).reminderDao().delete(toDelete);

                            // 2. Update UI
                            runOnUiThread(() -> {
                                reminderList.remove(position);
                                adapter.submitList(new ArrayList<>(reminderList));
                            });
                        }).start();
                    }
                })
                .show();
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
                checkReminders(values[0]);
            } else {
                displayToUI("Distance tracking not available.", mileageTextView);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during distance tracking update", e);
        }
    }

    /**
     * Helper to format and display the distance with user preferred units.
     * Overwrites the previous value in the UI (replacing the placeholder).
     */
    private void displayDistance(double value) throws RemoteException {
        String oldUnit = currentUnit; // Store previous unit
        // 1. Get the unit from Torque ("km" or "miles")
        currentUnit = torqueService.getPreferredUnit("km");
        double displayValue = value;

        // If the user's preferred unit is miles, convert km to miles
        if (currentUnit != null && currentUnit.equalsIgnoreCase("miles")) {
            displayValue = value * 0.62137119;
        }

//        Locale.getDefault() ensures the phone's regional formatting is used
        String formattedValue = String.format(Locale.getDefault(), "%.2f", displayValue);
        String displayText = getString(R.string.mileage_display_format,
                formattedValue,
                (currentUnit != null ? currentUnit : ""));

        displayToUI(displayText, mileageTextView);

        // 4. Refresh the reminder list so labels ("miles" -> "km") update immediately
        if (!Objects.equals(oldUnit, currentUnit)) {
            runOnUiThread(() -> adapter.notifyItemRangeChanged(0, adapter.getItemCount()));
        }
    }

    /**
     * Checks all reminders against current mileage and sends a notification
     * through Torque if an interval threshold is reached.
     */
    private void checkReminders(double currentRawKm) {
        // 1. Thread Safety: Create a snapshot copy of the list to avoid crashes
        // if the user adds/edits a reminder during the check.
        final List<Reminder> snapshot = new ArrayList<>(reminderList);

        new Thread(() -> {
            for (Reminder reminder : snapshot) {
                try {
                    // 2. Accuracy: Convert user's typed interval to KM internally
                    double intervalVal = Double.parseDouble(reminder.miles);
                    double intervalInKm = (currentUnit != null && currentUnit.equalsIgnoreCase("miles"))
                            ? intervalVal / 0.62137119
                            : intervalVal;

                    // 3. Accuracy (Trip Reset): If mileage reset to 0, reset our tracking point
                    if (currentRawKm < reminder.lastNotifiedMileage) {
                        reminder.lastNotifiedMileage = currentRawKm;
                        AppDatabase.getInstance(this).reminderDao().update(reminder);
                    }
                    // 4. Initialization
                    else if (reminder.lastNotifiedMileage == -1) {
                        reminder.lastNotifiedMileage = currentRawKm;
                        AppDatabase.getInstance(this).reminderDao().update(reminder);
                    }
                    // 5. Trigger Notification (Calculated purely in KM)
                    else if (currentRawKm - reminder.lastNotifiedMileage >= intervalInKm) {
                        sendTorqueMessage(reminder.name);
                        reminder.lastNotifiedMileage = currentRawKm;
                        AppDatabase.getInstance(this).reminderDao().update(reminder);
                    }
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Invalid format: " + reminder.name);
                }
            }
        }).start();
    }

    /**
     * Sends a message broadcast that Torque Pro receives and displays
     * (or speaks aloud) to the user.
     */
    private void sendTorqueMessage(String reminderName) {
        Intent intent = new Intent("org.prowl.torque.MESSAGE");
        // This is the standard extra key for Torque messages
        intent.putExtra("message", "Maintenance Due: " + reminderName);
        sendBroadcast(intent);
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
