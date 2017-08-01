package com.example.sdiaoune.droneControl.DroneAction;

import android.content.Context;
import android.support.annotation.NonNull;

import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;

/**
 * Created by sdiaoune on 8/1/2017.
 */

public abstract class ParrotFlyingDrone extends ParrotDrone {

    public ParrotFlyingDrone(@NonNull ARDiscoveryDeviceService deviceService, Context ctx) {
        super(deviceService, ctx);
    }

    public abstract void sendEmergency();

}