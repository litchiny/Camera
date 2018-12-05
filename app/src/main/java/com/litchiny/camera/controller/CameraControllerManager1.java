package com.litchiny.camera.controller;

import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;

public class CameraControllerManager1 extends CameraControllerManager {
    private static final String TAG = "CControllerManager1";

    public int getNumberOfCameras() {
        return Camera.getNumberOfCameras();
    }

    public boolean isFrontFacing(int cameraId) {
        try {
            CameraInfo camera_info = new CameraInfo();
            Camera.getCameraInfo(cameraId, camera_info);
            if (camera_info.facing == 1) {
                return true;
            }
            return false;
        } catch (RuntimeException e) {
            e.printStackTrace();
            return false;
        }
    }
}
