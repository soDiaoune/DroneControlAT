package com.example.sdiaoune.droneControl.DroneAction;

import android.content.Context;
import android.support.annotation.NonNull;

import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;
import com.parrot.arsdk.ardiscovery.ARDiscoveryService;

/**
 * Created by sdiaoune on 8/1/2017.
 */

public class ParrotDroneFactory {
    public static ParrotDrone createParrotDrone(@NonNull ARDiscoveryDeviceService deviceService, Context ctx) {
        ParrotDrone drone = null;
        switch (ARDiscoveryService.getProductFamily(ARDiscoveryService.getProductFromProductID(deviceService.getProductID()))) {
            case ARDISCOVERY_PRODUCT_FAMILY_ARDRONE:
                drone = new ParrotBebopDrone(deviceService, ctx);
                break;
        }

        return drone;
    }
}
