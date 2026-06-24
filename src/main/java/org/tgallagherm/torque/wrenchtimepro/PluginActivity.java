package org.tgallagherm.torque.wrenchtimepro;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.widget.Toast;
import org.prowl.torque.remote.ITorqueService;

/**
 * REVISION HISTORY
 * ---------------------------------------------------------------------------
 * Version | Date       | Author       | Description
 * 1.0.0   | 2026-06-24 | TGallagherM  | Base Torque service mapping connection.
 * ---------------------------------------------------------------------------
 */
public class PluginActivity extends Activity {

    private ITorqueService torqueService = null;
    private boolean isBound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // We will define this UI layout file in the next step
        setContentView(ResourceUtils.getLayoutId(this, "activity_main"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindToTorqueService();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unbindFromTorqueService();
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            torqueService = ITorqueService.Stub.asInterface(service);
            isBound = true;
            onTorqueConnected();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            torqueService = null;
            isBound = false;
        }
    };

    private void bindToTorqueService() {
        Intent intent = new Intent();
        intent.setClassName("org.prowl.torque", "org.prowl.torque.remote.TorqueService");
        try {
            if (!bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)) {
                Toast.makeText(this, "Torque Pro instance not detected.", Toast.LENGTH_SHORT).show();
            }
        } catch (SecurityException e) {
            Toast.makeText(this, "Binding security fault: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void unbindFromTorqueService() {
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
    }

    private void onTorqueConnected() {
        try {
            int torqueVersion = torqueService.getVersion();
            Toast.makeText(this, "WrenchTimePro linked to Torque v" + torqueVersion, Toast.LENGTH_SHORT).show();
            // This is where we will eventually read real-time PIDs (like Odometer) to match against your maintenance milestones
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
}