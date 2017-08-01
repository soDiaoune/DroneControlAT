package com.example.sdiaoune.droneControl.DroneAction;

import android.content.Context;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;

import com.example.sdiaoune.droneControl.DroneControl.AccelerometerData;
import com.example.sdiaoune.droneControl.DroneControl.ActionType;
import com.example.sdiaoune.droneControl.DroneControl.JoystickData;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DEVICE_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DICTIONARY_KEY_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_ERROR_ENUM;
import com.parrot.arsdk.arcontroller.ARControllerArgumentDictionary;
import com.parrot.arsdk.arcontroller.ARControllerDictionary;
import com.parrot.arsdk.arcontroller.ARControllerException;
import com.parrot.arsdk.arcontroller.ARDeviceController;
import com.parrot.arsdk.arcontroller.ARDeviceControllerListener;
import com.parrot.arsdk.arcontroller.ARFeatureCommon;
import com.parrot.arsdk.ardiscovery.ARDISCOVERY_PRODUCT_ENUM;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDevice;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceBLEService;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceNetService;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;
import com.parrot.arsdk.ardiscovery.ARDiscoveryException;
import com.parrot.arsdk.ardiscovery.ARDiscoveryService;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by sdiaoune on 8/1/2017.
 */

public abstract class ParrotDrone implements ARDeviceControllerListener {
    private static final String TAG = "ParrotDrone";

    public static final int WIFI_BAND_5GHZ = 0;
    public static final int WIFI_BAND_2_4GHZ = 1;

    public interface ParrotDroneListener {
        /**
         * Called when the connection to the drone changes
         *
         * @param state the state of the drone
         */
        void onDroneConnectionChanged(ARCONTROLLER_DEVICE_STATE_ENUM state);

        /**
         * Called when the action linked to the drone changes
         *
         * @param action the action type (@see ActionType)
         */
        void onDroneActionChanged(int action);

        /**
         * Called when the drone wifi band changes
         *
         * @param band the action type (@see ActionType)
         */
        void onDroneWifiBandChanged(int band);
    }

    private final List<ParrotDroneListener> mListeners;

    protected ARDeviceController mDeviceController;

    protected final Handler mHandler;

    protected int mCurrentAction;
    protected String mName;

    private final Context mContext;

    public ParrotDrone(@NonNull ARDiscoveryDeviceService deviceService, Context ctx) {

        mListeners = new ArrayList<>();
        mHandler = new Handler(ctx.getMainLooper());
        mContext = ctx;

        ARDiscoveryDevice discoveryDevice = createDiscoveryDevice(deviceService);
        ARDeviceController deviceController = null;
        if (discoveryDevice != null) {
            deviceController = createDeviceController(discoveryDevice);
        }

        if (deviceController != null) {
            deviceController.start();
        }
    }

    public void addListener(ParrotDroneListener listener) {
        mListeners.add(listener);
    }

    public void removeListener(ParrotDroneListener listener) {
        mListeners.remove(listener);
    }

    public int getCurrentAction() {
        return mCurrentAction;
    }

    public String getName() {
        return mName;
    }

    /**
     * Make the drone move according to the given data
     *
     * @param accelerometerData the data taken from the accelerometer
     */
    public abstract void pilotWithAcceleroData(AccelerometerData accelerometerData);

    /**
     * Make the drone move according to the given data
     *
     * @param joystickData the data taken from the joystick
     */
    public abstract void pilotWithJoystickData(JoystickData joystickData);

    /**
     * Make the drone stop moving
     */
    public abstract void stopPiloting();

    /**
     * Make the drone do its current action
     */
    public abstract void sendAction();

    protected void setCurrentAction(int action) {
        mCurrentAction = action;
        notifyActionChanged(action);
    }

    private ARDiscoveryDevice createDiscoveryDevice(@NonNull ARDiscoveryDeviceService service) {
        ARDiscoveryDevice device = null;
        try {
            device = new ARDiscoveryDevice();

            ARDISCOVERY_PRODUCT_ENUM productType = ARDiscoveryService.getProductFromProductID(service.getProductID());

            if (service.getDevice() instanceof ARDiscoveryDeviceNetService) {
                ARDiscoveryDeviceNetService netDeviceService = (ARDiscoveryDeviceNetService)service.getDevice();
                device.initWifi(productType, netDeviceService.getName(), netDeviceService.getIp(), netDeviceService.getPort());
            } else if (service.getDevice() instanceof ARDiscoveryDeviceBLEService) {
                ARDiscoveryDeviceBLEService bleDeviceService = (ARDiscoveryDeviceBLEService) service.getDevice();
                device.initBLE(productType, mContext.getApplicationContext(), bleDeviceService.getBluetoothDevice());
            }

        } catch (ARDiscoveryException e) {
            e.printStackTrace();
            Log.e(TAG, "Error: " + e.getError());
        }

        return device;
    }

    private ARDeviceController createDeviceController(@NonNull ARDiscoveryDevice discoveryDevice) {
        ARDeviceController deviceController = null;
        try {
            deviceController = new ARDeviceController(discoveryDevice);

            deviceController.addListener(this);
        } catch (ARControllerException e) {
            e.printStackTrace();
        }

        return deviceController;
    }

    //region ARDeviceControllerListener
    @Override
    public void onStateChanged(ARDeviceController deviceController, final ARCONTROLLER_DEVICE_STATE_ENUM newState, ARCONTROLLER_ERROR_ENUM error) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                notifyConnectionChanged(newState);
            }
        });

        if (newState == ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_STOPPED) {
            mDeviceController = null;

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    setCurrentAction(ActionType.NONE);
                }
            });
        } else if (newState == ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING) {
            mDeviceController = deviceController;
        }
    }

    @Override
    public void onExtensionStateChanged(ARDeviceController deviceController, ARCONTROLLER_DEVICE_STATE_ENUM newState, ARDISCOVERY_PRODUCT_ENUM product, String name, ARCONTROLLER_ERROR_ENUM error) {
    }

    @Override
    public void onCommandReceived(ARDeviceController deviceController, ARCONTROLLER_DICTIONARY_KEY_ENUM commandKey, ARControllerDictionary elementDictionary) {

        if ((commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_COMMON_SETTINGSSTATE_PRODUCTNAMECHANGED) && (elementDictionary != null)){
            ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
            if (args != null) {
                mName = (String)args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_SETTINGSSTATE_PRODUCTNAMECHANGED_NAME);
            }
        }
    }
    //endregion ARDeviceControllerListener

    protected void notifyConnectionChanged(ARCONTROLLER_DEVICE_STATE_ENUM state) {
        List<ParrotDroneListener> listenersCpy = new ArrayList<>(mListeners);
        for (ParrotDroneListener listener : listenersCpy) {
            listener.onDroneConnectionChanged(state);
        }
    }

    protected void notifyActionChanged(int action) {
        List<ParrotDroneListener> listenersCpy = new ArrayList<>(mListeners);
        for (ParrotDroneListener listener : listenersCpy) {
            listener.onDroneActionChanged(action);
        }
    }

    protected void notifyWifiBandChanged(int band) {
        List<ParrotDroneListener> listenersCpy = new ArrayList<>(mListeners);
        for (ParrotDroneListener listener : listenersCpy) {
            listener.onDroneWifiBandChanged(band);
        }
    }
}
