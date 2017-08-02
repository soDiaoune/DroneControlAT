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
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;

import com.example.sdiaoune.droneControl.DroneAction.Discoverer;
import com.example.sdiaoune.droneControl.DroneAction.ParrotDrone;
import com.example.sdiaoune.droneControl.DroneAction.ParrotFlyingDrone;
import com.example.sdiaoune.droneControl.DroneControl.AccelerometerData;
import com.example.sdiaoune.droneControl.DroneControl.ActionType;
import com.example.sdiaoune.droneControl.DroneControl.InteractionType;
import com.parrot.arsdk.ARSDK;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DEVICE_STATE_ENUM;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;
import com.parrot.arsdk.arsal.ARSALPrint;
import com.parrot.arsdk.arsal.ARSAL_PRINT_LEVEL_ENUM;

import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements Discoverer.DiscovererListener, ParrotDrone.ParrotDroneListener, SensorEventListener {
    private static final String TAG = "MobileMainActivity";

    private static final int ALPHA_ANIM_DURATION = 500;

    /** Code for permission request result handling. */
    private static final int REQUEST_CODE_PERMISSIONS_REQUEST = 1;
    private static final String VALUE_STR = "value";



    private final Object mAcceleroLock = new Object();
    private final Object mDroneLock = new Object();

    private Button mEmergencyBt;
    private Switch mAcceleroSwitch;

    private ParrotDrone mDrone;
    private SensorManager mSensorManager;

    private AccelerometerData mAccData;
    private int mCurrentAction;
    private Handler mHandler;
    private Runnable mAnimRunnable;
    private Runnable mSendAccRunnable;
    private Runnable mReconnectRunnable;
    private Discoverer mDiscoverer;

    static {
        ARSDK.loadSDKLibs();
        ARSALPrint.setMinimumLogLevel(ARSAL_PRINT_LEVEL_ENUM.ARSAL_PRINT_VERBOSE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mDiscoverer = new Discoverer(this);
        mDiscoverer.addListener(this);

        mHandler = new Handler(getMainLooper());
        mCurrentAction = ActionType.NONE;
        mAccData = new AccelerometerData(0, 0, 0);
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

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

        mDiscoverer.setup();
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




    private void sendSensorValues() {
        synchronized (mDroneLock) {
            synchronized (mAcceleroLock) {

                if (mDrone!= null && mAccData != null) {
                        mDrone.pilotWithAcceleroData(mAccData);
                    } else {
                        mDrone.stopPiloting();
                    }
                }
            }
        }
    

    @Override
    public void onServiceDiscovered(ARDiscoveryDeviceService deviceService) {

    }

    @Override
    public void onDiscoveryTimedOut() {

    }

    @Override
    public void onDroneConnectionChanged(ARCONTROLLER_DEVICE_STATE_ENUM state) {
        switch (state) {
            case ARCONTROLLER_DEVICE_STATE_RUNNING:
                mDiscoverer.stopDiscovering();

                break;
            case ARCONTROLLER_DEVICE_STATE_STOPPED:
                synchronized (mDroneLock) {
                    mDrone = null;
                }

                mHandler.postDelayed(mReconnectRunnable, 5000);
                break;
        }
    }

    @Override
    public void onDroneActionChanged(int action) {
        switch (action) {
            case ActionType.LAND:
                mEmergencyBt.setVisibility(View.VISIBLE);
                break;
            default:
                mEmergencyBt.setVisibility(View.GONE);
                break;
        }
    }

    @Override
    public void onDroneWifiBandChanged(int band) {

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
