package com.example.sdiaoune.droneControl;

import android.content.pm.PackageManager;
import android.Manifest;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.example.sdiaoune.droneControl.DroneAction.Discoverer;
import com.example.sdiaoune.droneControl.DroneAction.ParrotDrone;
import com.example.sdiaoune.droneControl.DroneAction.ParrotDroneFactory;
import com.example.sdiaoune.droneControl.DroneAction.ParrotFlyingDrone;
import com.example.sdiaoune.droneControl.DroneControl.AccelerometerData;
import com.example.sdiaoune.droneControl.DroneControl.ActionType;
import com.parrot.arsdk.ARSDK;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DEVICE_STATE_ENUM;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;
import com.parrot.arsdk.arsal.ARSALPrint;
import com.parrot.arsdk.arsal.ARSAL_PRINT_LEVEL_ENUM;

import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements Discoverer.DiscovererListener, ParrotDrone.ParrotDroneListener, SensorEventListener {
    private static final String TAG = "MobileMainActivity";


    /** Code for permission request result handling. */
    private static final int REQUEST_CODE_PERMISSIONS_REQUEST = 1;



    private final Object mAcceleroLock = new Object();
    private final Object mDroneLock = new Object();

    private Button mEmergencyBt;
    private Switch mAcceleroSwitch;
    private TextView mConnectionTextView;
    private TextView mWifiTextView;
    private TextView mPilotingTextView;

    private ParrotDrone mDrone;
    private SensorManager mSensorManager;

    private AccelerometerData mAccData;
    private Handler mHandler;
    private Runnable mReconnectRunnable;
    private Discoverer mDiscoverer;

    static {
        ARSDK.loadSDKLibs();
        ARSALPrint.setMinimumLogLevel(ARSAL_PRINT_LEVEL_ENUM.ARSAL_PRINT_VERBOSE);
    }
    private boolean mUseWatchAccelero;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mDiscoverer = new Discoverer(this);
        mDiscoverer.addListener(this);

        mHandler = new Handler(getMainLooper());
        mAccData = new AccelerometerData(0, 0, 0);
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        mConnectionTextView = (TextView) findViewById(R.id.connection_text_view);
        mWifiTextView = (TextView) findViewById(R.id.wifi_text_view);
        mPilotingTextView = (TextView) findViewById(R.id.piloting_text_view);
        mAcceleroSwitch = (Switch) findViewById(R.id.acceleroSwitch);
        mAcceleroSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                onAcceleroSwitchCheckChanged();
            }
        });
        mEmergencyBt = (Button) findViewById(R.id.emergencyBt);
        mEmergencyBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onEmergencyClicked();
            }
        });

        mReconnectRunnable = new Runnable() {
            @Override
            public void run() {
                if (mDiscoverer != null) {
                    mDiscoverer.startDiscovering();
                    mConnectionTextView.setText(R.string.discovering);
                }
            }
        };
        Set<String> permissionsToRequest = new HashSet<>();
        String permission = Manifest.permission.ACCESS_COARSE_LOCATION;

        if (ContextCompat.checkSelfPermission(this, permission) !=
                PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                Toast.makeText(this, "Please allow permission " + permission, Toast.LENGTH_LONG).show();
                finish();
                return;
            } else {
                permissionsToRequest.add(permission);
            }
        }
        if (permissionsToRequest.size() > 0) {
            ActivityCompat.requestPermissions(this,
                    permissionsToRequest.toArray(new String[permissionsToRequest.size()]),
                    REQUEST_CODE_PERMISSIONS_REQUEST);
        }
    }

    private void onAcceleroSwitchCheckChanged() {
        mUseWatchAccelero = mAcceleroSwitch.isChecked();
        Log.i(TAG, "Accelero is checked = " + mUseWatchAccelero);
        if (!mUseWatchAccelero && mDrone != null) {
            mDrone.stopPiloting();
        }

        updatePilotingText();
    }

    private void updatePilotingText() {
        mPilotingTextView.setText((mUseWatchAccelero) ? R.string.with_piloting : R.string.no_piloting);
    }
    //region Button Listeners
    private void onEmergencyClicked() {
        if (mDrone != null && (mDrone instanceof ParrotFlyingDrone)) {
            ((ParrotFlyingDrone) mDrone).sendEmergency();
        }
    }



    @Override
    protected void onPause() {
        super.onPause();
        mDiscoverer.cleanup();

    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_UI);

        mDiscoverer.setup();
        mConnectionTextView.setText(R.string.discovering);

    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        boolean denied = false;
        if (permissions.length == 0) {
            // canceled, finish
            denied = true;
        } else {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                    denied = true;
                }
            }
        }

        if (denied) {
            Toast.makeText(this, "At least one permission is missing.", Toast.LENGTH_LONG).show();
            finish();
        }
    }





    @Override
    public void onServiceDiscovered(ARDiscoveryDeviceService deviceService) {
        mConnectionTextView.setVisibility(View.VISIBLE);
        if (mDrone == null) {
            mConnectionTextView.setText(String.format(getString(R.string.connecting_to_device), deviceService.getName()));
            mDiscoverer.stopDiscovering();
            synchronized (mDroneLock) {
                mDrone = ParrotDroneFactory.createParrotDrone(deviceService, this);
                mDrone.addListener(this);
                synchronized (mAcceleroLock) {

                    if ( mAccData != null && mUseWatchAccelero) {
                        mDrone.pilotWithAcceleroData(mAccData);
                    } else {
                        mDrone.stopPiloting();
                    }
                }
            }
        }
    }

    @Override
    public void onDiscoveryTimedOut() {

    }

    @Override
    public void onDroneConnectionChanged(ARCONTROLLER_DEVICE_STATE_ENUM state) {
        switch (state) {
            case ARCONTROLLER_DEVICE_STATE_RUNNING:
                mDiscoverer.stopDiscovering();
                mConnectionTextView.setText(String.format(getString(R.string.device_connected), mDrone.getName()));
                mWifiTextView.setVisibility(View.VISIBLE);
                mPilotingTextView.setVisibility(View.VISIBLE);

                break;
            case ARCONTROLLER_DEVICE_STATE_STOPPED:
                synchronized (mDroneLock) {
                    mDrone = null;
                }
                mConnectionTextView.setText(R.string.device_disconnected);
                mWifiTextView.setVisibility(View.GONE);
                mPilotingTextView.setVisibility(View.GONE);
                mHandler.postDelayed(mReconnectRunnable, 5000);
                break;
        }
    }

    @Override
    public void onDroneActionChanged(int action) {

    }

    @Override
    public void onDroneWifiBandChanged(int band) {
        switch (band) {
            case ParrotDrone.WIFI_BAND_2_4GHZ:
                mWifiTextView.setText(R.string.wifi_band_2ghz);
                break;
            case ParrotDrone.WIFI_BAND_5GHZ:
                mWifiTextView.setText(null);
                break;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                synchronized (mAcceleroLock)
                {
                    mAccData.setAccData(
                            event.values[0],
                            event.values[1],
                            event.values[2]);
                }
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
