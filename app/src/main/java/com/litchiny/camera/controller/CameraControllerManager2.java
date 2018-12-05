package com.litchiny.camera.controller;

import android.annotation.TargetApi;
import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;

@TargetApi(21)
public class CameraControllerManager2 extends CameraControllerManager {
    private static final String TAG = "CControllerManager2";
    private final Context context;

    public CameraControllerManager2(Context context) {
        this.context = context;
    }

    public int getNumberOfCameras() {
        try {
            return ((CameraManager) this.context.getSystemService("camera")).getCameraIdList().length;
        } catch (Throwable e) {
            e.printStackTrace();
            return 0;
        }
    }

    public boolean isFrontFacing(int cameraId) {
        CameraManager manager = (CameraManager) this.context.getSystemService("camera");
        try {
            return ((Integer) manager.getCameraCharacteristics(manager.getCameraIdList()[cameraId]).get(CameraCharacteristics.LENS_FACING)).intValue() == 0;
        } catch (Throwable e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean isHardwareLevelSupported(CameraCharacteristics c, int requiredLevel) {
        boolean z = true;
        int deviceLevel = ((Integer) c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)).intValue();
        if (deviceLevel != 2) {
            if (requiredLevel > deviceLevel) {
                z = false;
            }
            return z;
        } else if (requiredLevel == deviceLevel) {
            return true;
        } else {
            return false;
        }
    }

    public boolean allowCamera2Support(int cameraId) {
        boolean z = false;
        CameraManager manager = (CameraManager) this.context.getSystemService("camera");
        try {
            z = isHardwareLevelSupported(manager.getCameraCharacteristics(manager.getCameraIdList()[cameraId]), 0);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return z;
    }
}
