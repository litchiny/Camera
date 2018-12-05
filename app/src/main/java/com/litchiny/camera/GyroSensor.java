package com.litchiny.camera;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import com.litchiny.camera.ui.PopupView;

public class GyroSensor implements SensorEventListener {
    private static final float NS2S = 1.0E-9f;
    private static final String TAG = "GyroSensor";
    private final float[] currentRotationMatrix = new float[9];
    private final float[] deltaRotationMatrix = new float[9];
    private final float[] deltaRotationVector = new float[4];
    private boolean hasTarget;
    private final float[] inVector = new float[3];
    private boolean is_recording;
    private final Sensor mSensor;
    private final SensorManager mSensorManager;
    private float targetAngle;
    private TargetCallback targetCallback;
    private final float[] targetVector = new float[3];
    private final float[] tempMatrix = new float[9];
    private final float[] tempVector = new float[3];
    private long timestamp;

    public interface TargetCallback {
        void onAchieved();
    }

    GyroSensor(Context context) {
        this.mSensorManager = (SensorManager) context.getSystemService("sensor");
        this.mSensor = this.mSensorManager.getDefaultSensor(4);
        setToIdentity();
    }

    private void setToIdentity() {
        for (int i = 0; i < 9; i++) {
            this.currentRotationMatrix[i] = 0.0f;
        }
        this.currentRotationMatrix[0] = PopupView.ALPHA_BUTTON_SELECTED;
        this.currentRotationMatrix[4] = PopupView.ALPHA_BUTTON_SELECTED;
        this.currentRotationMatrix[8] = PopupView.ALPHA_BUTTON_SELECTED;
    }

    private void setVector(float[] vector, float x, float y, float z) {
        vector[0] = x;
        vector[1] = y;
        vector[2] = z;
    }

    private float getMatrixComponent(float[] matrix, int row, int col) {
        return matrix[(row * 3) + col];
    }

    private void setMatrixComponent(float[] matrix, int row, int col, float value) {
        matrix[(row * 3) + col] = value;
    }

    private void transformVector(float[] result, float[] matrix, float[] vector) {
        for (int i = 0; i < 3; i++) {
            result[i] = 0.0f;
            for (int j = 0; j < 3; j++) {
                result[i] = result[i] + (getMatrixComponent(matrix, i, j) * vector[j]);
            }
        }
    }

    private void transformTransposeVector(float[] result, float[] matrix, float[] vector) {
        for (int i = 0; i < 3; i++) {
            result[i] = 0.0f;
            for (int j = 0; j < 3; j++) {
                result[i] = result[i] + (getMatrixComponent(matrix, j, i) * vector[j]);
            }
        }
    }

    void startRecording() {
        this.is_recording = true;
        this.timestamp = 0;
        setToIdentity();
        this.mSensorManager.registerListener(this, this.mSensor, 3);
    }

    void stopRecording() {
        if (this.is_recording) {
            this.is_recording = false;
            this.timestamp = 0;
            this.mSensorManager.unregisterListener(this);
        }
    }

    public boolean isRecording() {
        return this.is_recording;
    }

    void setTarget(float target_x, float target_y, float target_z, float targetAngle, TargetCallback targetCallback) {
        this.hasTarget = true;
        this.targetVector[0] = target_x;
        this.targetVector[1] = target_y;
        this.targetVector[2] = target_z;
        this.targetAngle = targetAngle;
        this.targetCallback = targetCallback;
    }

    void clearTarget() {
        this.hasTarget = false;
        this.targetCallback = null;
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    public void onSensorChanged(SensorEvent event) {
        if (this.timestamp != 0) {
            float dT = ((float) (event.timestamp - this.timestamp)) * NS2S;
            float axisX = event.values[0];
            float axisY = event.values[1];
            float axisZ = event.values[2];
            double omegaMagnitude = Math.sqrt((double) (((axisX * axisX) + (axisY * axisY)) + (axisZ * axisZ)));
            if (omegaMagnitude > 1.0E-5d) {
                axisX = (float) (((double) axisX) / omegaMagnitude);
                axisY = (float) (((double) axisY) / omegaMagnitude);
                axisZ = (float) (((double) axisZ) / omegaMagnitude);
            }
            double thetaOverTwo = (((double) dT) * omegaMagnitude) / 2.0d;
            float sinThetaOverTwo = (float) Math.sin(thetaOverTwo);
            float cosThetaOverTwo = (float) Math.cos(thetaOverTwo);
            this.deltaRotationVector[0] = sinThetaOverTwo * axisX;
            this.deltaRotationVector[1] = sinThetaOverTwo * axisY;
            this.deltaRotationVector[2] = sinThetaOverTwo * axisZ;
            this.deltaRotationVector[3] = cosThetaOverTwo;
            SensorManager.getRotationMatrixFromVector(this.deltaRotationMatrix, this.deltaRotationVector);
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    float value = 0.0f;
                    for (int k = 0; k < 3; k++) {
                        value += getMatrixComponent(this.currentRotationMatrix, i, k) * getMatrixComponent(this.deltaRotationMatrix, k, j);
                    }
                    setMatrixComponent(this.tempMatrix, i, j, value);
                }
            }
            System.arraycopy(this.tempMatrix, 0, this.currentRotationMatrix, 0, 9);
            if (this.hasTarget) {
                setVector(this.inVector, 0.0f, 0.0f, -1.0f);
                transformVector(this.tempVector, this.currentRotationMatrix, this.inVector);
                if (((float) Math.acos((double) (((this.tempVector[0] * this.targetVector[0]) + (this.tempVector[1] * this.targetVector[1])) + (this.tempVector[2] * this.targetVector[2])))) <= this.targetAngle) {
                    this.targetCallback.onAchieved();
                }
            }
        }
        this.timestamp = event.timestamp;
    }

    public void getRelativeInverseVector(float[] out, float[] in) {
        transformTransposeVector(out, this.currentRotationMatrix, in);
    }
}
