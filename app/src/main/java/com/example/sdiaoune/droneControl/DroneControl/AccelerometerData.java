package com.example.sdiaoune.droneControl.DroneControl;

/**
 * Created by sdiaoune on 8/1/2017.
 */

public class AccelerometerData {
    private final float mAccData[];

    public AccelerometerData(float accX, float accY, float accZ) {
        mAccData = new float[3];
        setAccData(accX, accY, accZ);
    }

    public float getAccX()
    {
        return mAccData[0];
    }

    public float getAccY()
    {
        return mAccData[1];
    }

    public float getAccZ()
    {
        return mAccData[2];
    }

    public void setAccData(float accX, float accY, float accZ) {
        mAccData[0] = accX;
        mAccData[1] = accY;
        mAccData[2] = accZ;
    }

    @Override
    public String toString()
    {
        return "Accelero = [" + getAccX() + ", " + getAccY() + ", " + getAccZ() + "]";
    }
}
