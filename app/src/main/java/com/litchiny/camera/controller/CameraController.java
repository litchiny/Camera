package com.litchiny.camera.controller;

import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.DngCreator;
import android.location.Location;
import android.media.Image;
import android.media.MediaRecorder;
import android.view.SurfaceHolder;
import java.util.List;

public abstract class CameraController {
    public static final long EXPOSURE_TIME_DEFAULT = 33333333;
    private static final String TAG = "CameraController";
    private final int cameraId;
    public int count_camera_parameters_exception;
    public int count_precapture_timeout;
    public volatile int test_af_state_null_focus;
    public volatile int test_capture_results;
    public volatile int test_fake_flash_focus;
    public volatile int test_fake_flash_photo;
    public volatile int test_fake_flash_precapture;
    public boolean test_wait_capture_result;

    public static class Area {
        final Rect rect;
        final int weight;

        public Area(Rect rect, int weight) {
            this.rect = rect;
            this.weight = weight;
        }
    }

    public interface AutoFocusCallback {
        void onAutoFocus(boolean z);
    }

    public static class CameraFeatures {
        public boolean can_disable_shutter_sound;
        public float exposure_step;
        public boolean is_exposure_lock_supported;
        public boolean is_video_stabilization_supported;
        public boolean is_zoom_supported;
        public int max_expo_bracketing_n_images;
        public int max_exposure;
        public long max_exposure_time;
        public int max_iso;
        public int max_num_focus_areas;
        public int max_temperature;
        public int max_zoom;
        public int min_exposure;
        public long min_exposure_time;
        public int min_iso;
        public int min_temperature;
        public float minimum_focus_distance;
        public List<Size> picture_sizes;
        public List<Size> preview_sizes;
        public List<String> supported_flash_values;
        public List<String> supported_focus_values;
        public boolean supports_expo_bracketing;
        public boolean supports_exposure_time;
        public boolean supports_face_detection;
        public boolean supports_iso_range;
        public boolean supports_raw;
        public boolean supports_white_balance_temperature;
        public List<Size> video_sizes;
        public List<Size> video_sizes_high_speed;
        public float view_angle_x;
        public float view_angle_y;
        public List<Integer> zoom_ratios;
    }

    public interface ContinuousFocusMoveCallback {
        void onContinuousFocusMove(boolean z);
    }

    public interface ErrorCallback {
        void onError();
    }

    public static class Face {
        public final Rect rect;
        public final int score;

        Face(int score, Rect rect) {
            this.score = score;
            this.rect = rect;
        }
    }

    public interface FaceDetectionListener {
        void onFaceDetection(CameraController.Face[] faceArr);
    }

    public interface PictureCallback {
        void onBurstPictureTaken(List<byte[]> list);

        void onCompleted();

        void onFrontScreenTurnOn();

        void onPictureTaken(byte[] bArr);

        void onRawPictureTaken(DngCreator dngCreator, Image image);

        void onStarted();
    }

    public static class Size {
        public final int height;
        public final int width;

        public Size(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public boolean equals(Object o) {
            if (!(o instanceof Size)) {
                return false;
            }
            Size that = (Size) o;
            if (this.width == that.width && this.height == that.height) {
                return true;
            }
            return false;
        }

        public int hashCode() {
            return (this.width * 31) + this.height;
        }
    }

    public static class SupportedValues {
        public final String selected_value;
        public final List<String> values;

        SupportedValues(List<String> values, String selected_value) {
            this.values = values;
            this.selected_value = selected_value;
        }
    }

    public abstract void autoFocus(AutoFocusCallback autoFocusCallback, boolean z);

    public abstract void cancelAutoFocus();

    public abstract void clearFocusAndMetering();

    public abstract void enableShutterSound(boolean z);

    public abstract boolean focusIsContinuous();

    public abstract boolean focusIsVideo();

    public abstract String getAPI();

    public abstract boolean getAutoExposureLock();

    public abstract CameraFeatures getCameraFeatures();

    public abstract int getCameraOrientation();

    public abstract String getColorEffect();

    public abstract long getDefaultExposureTime();

    public abstract int getDisplayOrientation();

    public abstract int getExposureCompensation();

    public abstract long getExposureTime();

    public abstract String getFlashValue();

    public abstract List<Area> getFocusAreas();

    public abstract float getFocusDistance();

    public abstract String getFocusValue();

    public abstract int getISO();

    public abstract String getISOKey();

    public abstract int getJpegQuality();

    public abstract List<Area> getMeteringAreas();

    public abstract String getParametersString();

    public abstract Size getPictureSize();

    public abstract Size getPreviewSize();

    public abstract String getSceneMode();

    public abstract List<int[]> getSupportedPreviewFpsRange();

    public abstract boolean getVideoStabilization();

    public abstract String getWhiteBalance();

    public abstract int getWhiteBalanceTemperature();

    public abstract int getZoom();

    public abstract void initVideoRecorderPostPrepare(MediaRecorder mediaRecorder) throws CameraControllerException;

    public abstract void initVideoRecorderPrePrepare(MediaRecorder mediaRecorder);

    public abstract boolean isFrontFacing();

    public abstract boolean isManualISO();

    public abstract void onError();

    public abstract void reconnect() throws CameraControllerException;

    public abstract void release();

    public abstract void removeLocationInfo();

    public abstract void setAutoExposureLock(boolean z);

    public abstract void setCaptureFollowAutofocusHint(boolean z);

    public abstract SupportedValues setColorEffect(String str);

    public abstract void setContinuousFocusMoveCallback(ContinuousFocusMoveCallback continuousFocusMoveCallback);

    public abstract void setDisplayOrientation(int i);

    public abstract void setExpoBracketing(boolean z);

    public abstract void setExpoBracketingNImages(int i);

    public abstract void setExpoBracketingStops(double d);

    public abstract boolean setExposureCompensation(int i);

    public abstract boolean setExposureTime(long j);

    public abstract void setFaceDetectionListener(FaceDetectionListener faceDetectionListener);

    public abstract void setFlashValue(String str);

    public abstract boolean setFocusAndMeteringArea(List<Area> list);

    public abstract boolean setFocusDistance(float f);

    public abstract void setFocusValue(String str);

    public abstract SupportedValues setISO(String str);

    public abstract boolean setISO(int i);

    public abstract void setJpegQuality(int i);

    public abstract void setLocationInfo(Location location);

    public abstract void setManualISO(boolean z, int i);

    public abstract void setOptimiseAEForDRO(boolean z);

    public abstract void setPictureSize(int i, int i2);

    public abstract void setPreviewDisplay(SurfaceHolder surfaceHolder) throws CameraControllerException;

    public abstract void setPreviewFpsRange(int i, int i2);

    public abstract void setPreviewSize(int i, int i2);

    public abstract void setPreviewTexture(SurfaceTexture surfaceTexture) throws CameraControllerException;

    public abstract void setRaw(boolean z);

    public abstract void setRecordingHint(boolean z);

    public abstract void setRotation(int i);

    public abstract SupportedValues setSceneMode(String str);

    public abstract void setUseExpoFastBurst(boolean z);

    public abstract void setVideoStabilization(boolean z);

    public abstract SupportedValues setWhiteBalance(String str);

    public abstract boolean setWhiteBalanceTemperature(int i);

    public abstract void setZoom(int i);

    public abstract boolean startFaceDetection();

    public abstract void startPreview() throws CameraControllerException;

    public abstract void stopPreview();

    public abstract boolean supportsAutoFocus();

    public abstract void takePicture(PictureCallback pictureCallback, ErrorCallback errorCallback);

    public abstract void unlock();

    CameraController(int cameraId) {
        this.cameraId = cameraId;
    }

    public int getCameraId() {
        return this.cameraId;
    }

    public void setUseCamera2FakeFlash(boolean use_fake_precapture) {
    }

    public boolean getUseCamera2FakeFlash() {
        return false;
    }

    public String getDefaultSceneMode() {
        return "auto";
    }

    public String getDefaultColorEffect() {
        return "none";
    }

    public String getDefaultWhiteBalance() {
        return "auto";
    }

    public String getDefaultISO() {
        return "auto";
    }

    public boolean captureResultIsAEScanning() {
        return false;
    }

    public boolean needsFlash() {
        return false;
    }

    public boolean captureResultHasWhiteBalanceTemperature() {
        return false;
    }

    public int captureResultWhiteBalanceTemperature() {
        return 0;
    }

    public boolean captureResultHasIso() {
        return false;
    }

    public int captureResultIso() {
        return 0;
    }

    public boolean captureResultHasExposureTime() {
        return false;
    }

    public long captureResultExposureTime() {
        return 0;
    }

    SupportedValues checkModeIsSupported(List<String> values, String value, String default_value) {
        if (values == null || values.size() <= 1) {
            return null;
        }
        if (!values.contains(value)) {
            if (values.contains(default_value)) {
                value = default_value;
            } else {
                value = (String) values.get(0);
            }
        }
        return new SupportedValues(values, value);
    }
}
