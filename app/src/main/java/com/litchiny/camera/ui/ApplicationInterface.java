package com.litchiny.camera.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.hardware.camera2.DngCreator;
import android.media.Image;
import android.util.Pair;
import android.view.MotionEvent;

import java.util.Date;
import java.util.List;

public interface ApplicationInterface {

    void cameraClosed();

    void cameraInOperation(boolean z);

    void cameraSetup();

    void clearColorEffectPref();

    void clearExposureCompensationPref();

    void clearExposureTimePref();


    void clearSceneModePref();

    void clearWhiteBalancePref();

    double getCalibratedLevelAngle();

    int getCameraIdPref();

    Pair<Integer, Integer> getCameraResolutionPref();

    String getColorEffectPref();

    Context getContext();

    boolean getDoubleTapCapturePref();

    int getExpoBracketingNImagesPref();

    double getExpoBracketingStopsPref();

    int getExposureCompensationPref();

    long getExposureTimePref();

    boolean getFaceDetectionPref();

    String getFlashPref();

    float getFocusDistancePref();

    String getFocusPref(boolean z);

    boolean getForce4KPref();


    int getImageQualityPref();

    String getLockOrientationPref();

    boolean getOptimiseAEForDROPref();

    boolean getPausePreviewPref();

    String getPreviewRotationPref();

    String getPreviewSizePref();

    long getRepeatIntervalPref();

    String getRepeatPref();


    String getSceneModePref();

    boolean getShowToastsPref();

    boolean getShutterSoundPref();

    boolean getStartupFocusPref();

    long getTimerPref();

    boolean getTouchCapturePref();

    String getWhiteBalancePref();

    int getWhiteBalanceTemperaturePref();

    int getZoomPref();

    boolean isExpoBracketingPref();

    boolean isRawPref();

    boolean isTestAlwaysFocus();

    void layoutUI();

    void multitouchZoom(int i);

    boolean onBurstPictureTaken(List<byte[]> list, Date date);

    void onCameraError();

    void onCaptureStarted();

    void onContinuousFocusMove(boolean z);

    void onDrawPreview(Canvas canvas);

    void onFailedReconnectError();

    void onFailedStartPreview();

    void onPhotoError();

    void onPictureCompleted();

    boolean onPictureTaken(byte[] bArr, Date date);

    boolean onRawPictureTaken(DngCreator dngCreator, Image image, Date date);

    void requestCameraPermission();

    void requestStoragePermission();

    void setCameraIdPref(int i);

    void setCameraResolutionPref(int i, int i2);

    void setColorEffectPref(String str);

    void setExposureCompensationPref(int i);

    void setExposureTimePref(long j);

    void setFlashPref(String str);

    void setFocusDistancePref(float f);

    void setFocusPref(String str, boolean z);

    void setISOPref(String str);

    void setSceneModePref(String str);

    void setWhiteBalancePref(String str);

    void setWhiteBalanceTemperaturePref(int i);

    void setZoomPref(int i);

    void timerBeep(long j);

    void touchEvent(MotionEvent motionEvent);

    void turnFrontScreenFlashOn();

    boolean useCamera2();

    boolean useCamera2FakeFlash();

    boolean useCamera2FastBurst();
}
