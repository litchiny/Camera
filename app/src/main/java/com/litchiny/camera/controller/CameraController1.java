package com.litchiny.camera.controller;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusMoveCallback;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.ShutterCallback;
import android.location.Location;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class CameraController1 extends CameraController {
    private static final String TAG = "CameraController1";
    private static final int max_expo_bracketing_n_images = 3;
    private List<Integer> burst_exposures;
    private Camera camera;
    private final ErrorCallback camera_error_cb;
    private final CameraInfo camera_info = new CameraInfo();
    private int current_zoom_value;
    private int display_orientation;
    private int expo_bracketing_n_images = 3;
    private double expo_bracketing_stops = 2.0d;
    private boolean frontscreen_flash;
    private String iso_key;
    private int n_burst;
    private final List<byte[]> pending_burst_images = new ArrayList();
    private int picture_height;
    private int picture_width;
    private boolean want_expo_bracketing;

    private class CameraErrorCallback implements Camera.ErrorCallback {
        private CameraErrorCallback() {
        }

        public void onError(int error, Camera cam) {
            Log.e(CameraController1.TAG, "camera onError: " + error);
            if (error == 100) {
                Log.e(CameraController1.TAG, "    CAMERA_ERROR_SERVER_DIED");
                CameraController1.this.onError();
            } else if (error == 1) {
                Log.e(CameraController1.TAG, "    CAMERA_ERROR_UNKNOWN ");
            }
        }
    }

    private static class TakePictureShutterCallback implements ShutterCallback {
        private TakePictureShutterCallback() {
        }

        public void onShutter() {
        }
    }

    public CameraController1(int cameraId, ErrorCallback camera_error_cb) throws CameraControllerException {
        super(cameraId);
        this.camera_error_cb = camera_error_cb;
        try {
            this.camera = Camera.open(cameraId);
            if (this.camera == null) {
                throw new CameraControllerException();
            }
            try {
                Camera.getCameraInfo(cameraId, this.camera_info);
                this.camera.setErrorCallback(new CameraErrorCallback());
            } catch (RuntimeException e) {
                e.printStackTrace();
                release();
                throw new CameraControllerException();
            }
        } catch (RuntimeException e2) {
            e2.printStackTrace();
            throw new CameraControllerException();
        }
    }

    public void onError() {
        Log.e(TAG, "onError");
        if (this.camera != null) {
            this.camera.release();
            this.camera = null;
        }
        if (this.camera_error_cb != null) {
            this.camera_error_cb.onError();
        }
    }

    public void release() {
        this.camera.release();
        this.camera = null;
    }

    private Parameters getParameters() {
        return this.camera.getParameters();
    }

    private void setCameraParameters(Parameters parameters) {
        try {
            this.camera.setParameters(parameters);
        } catch (RuntimeException e) {
            e.printStackTrace();
            this.count_camera_parameters_exception++;
        }
    }

    private List<String> convertFlashModesToValues(List<String> supported_flash_modes) {
        List<String> output_modes = new ArrayList();
        if (supported_flash_modes != null) {
            if (supported_flash_modes.contains("off")) {
                output_modes.add("flash_off");
            }
            if (supported_flash_modes.contains("auto")) {
                output_modes.add("flash_auto");
            }
            if (supported_flash_modes.contains("on")) {
                output_modes.add("flash_on");
            }
            if (supported_flash_modes.contains("torch")) {
                output_modes.add("flash_torch");
            }
            if (supported_flash_modes.contains("red-eye")) {
                output_modes.add("flash_red_eye");
            }
        }
        if (output_modes.size() <= 1) {
            if (isFrontFacing()) {
                output_modes.clear();
                output_modes.add("flash_off");
                output_modes.add("flash_frontscreen_on");
            } else {
                output_modes.clear();
            }
        }
        return output_modes;
    }

    private List<String> convertFocusModesToValues(List<String> supported_focus_modes) {
        List<String> output_modes = new ArrayList();
        if (supported_focus_modes != null) {
            if (supported_focus_modes.contains("auto")) {
                output_modes.add("focus_mode_auto");
            }
            if (supported_focus_modes.contains("infinity")) {
                output_modes.add("focus_mode_infinity");
            }
            if (supported_focus_modes.contains("macro")) {
                output_modes.add("focus_mode_macro");
            }
            if (supported_focus_modes.contains("auto")) {
                output_modes.add("focus_mode_locked");
            }
            if (supported_focus_modes.contains("fixed")) {
                output_modes.add("focus_mode_fixed");
            }
            if (supported_focus_modes.contains("edof")) {
                output_modes.add("focus_mode_edof");
            }
            if (supported_focus_modes.contains("continuous-picture")) {
                output_modes.add("focus_mode_continuous_picture");
            }
            if (supported_focus_modes.contains("continuous-video")) {
                output_modes.add("focus_mode_continuous_video");
            }
        }
        return output_modes;
    }

    public String getAPI() {
        return "Camera";
    }

    public CameraFeatures getCameraFeatures() {
        Parameters parameters = getParameters();
        CameraFeatures camera_features = new CameraFeatures();
        camera_features.is_zoom_supported = parameters.isZoomSupported();
        if (camera_features.is_zoom_supported) {
            camera_features.max_zoom = parameters.getMaxZoom();
            try {
                camera_features.zoom_ratios = parameters.getZoomRatios();
            } catch (NumberFormatException e) {
                e.printStackTrace();
                camera_features.is_zoom_supported = false;
                camera_features.max_zoom = 0;
                camera_features.zoom_ratios = null;
            }
        }
        camera_features.supports_face_detection = parameters.getMaxNumDetectedFaces() > 0;
        List<android.hardware.Camera.Size> camera_picture_sizes = parameters.getSupportedPictureSizes();
        camera_features.picture_sizes = new ArrayList();
        for (android.hardware.Camera.Size camera_size : camera_picture_sizes) {
            camera_features.picture_sizes.add(new Size(camera_size.width, camera_size.height));
        }
        camera_features.supported_flash_values = convertFlashModesToValues(parameters.getSupportedFlashModes());
        camera_features.supported_focus_values = convertFocusModesToValues(parameters.getSupportedFocusModes());
        camera_features.max_num_focus_areas = parameters.getMaxNumFocusAreas();
        camera_features.is_exposure_lock_supported = parameters.isAutoExposureLockSupported();
        camera_features.is_video_stabilization_supported = parameters.isVideoStabilizationSupported();
        camera_features.min_exposure = parameters.getMinExposureCompensation();
        camera_features.max_exposure = parameters.getMaxExposureCompensation();
        camera_features.exposure_step = getExposureCompensationStep();
        boolean z = (camera_features.min_exposure == 0 || camera_features.max_exposure == 0) ? false : true;
        camera_features.supports_expo_bracketing = z;
        camera_features.max_expo_bracketing_n_images = 3;
        List<android.hardware.Camera.Size> camera_video_sizes = parameters.getSupportedVideoSizes();
        if (camera_video_sizes == null) {
            camera_video_sizes = parameters.getSupportedPreviewSizes();
        }
        camera_features.video_sizes = new ArrayList();
        for (android.hardware.Camera.Size camera_size2 : camera_video_sizes) {
            camera_features.video_sizes.add(new Size(camera_size2.width, camera_size2.height));
        }
        List<android.hardware.Camera.Size> camera_preview_sizes = parameters.getSupportedPreviewSizes();
        camera_features.preview_sizes = new ArrayList();
        for (android.hardware.Camera.Size camera_size22 : camera_preview_sizes) {
            camera_features.preview_sizes.add(new Size(camera_size22.width, camera_size22.height));
        }
        if (VERSION.SDK_INT >= 17) {
            camera_features.can_disable_shutter_sound = this.camera_info.canDisableShutterSound;
        } else {
            camera_features.can_disable_shutter_sound = false;
        }
        try {
            camera_features.view_angle_x = parameters.getHorizontalViewAngle();
            camera_features.view_angle_y = parameters.getVerticalViewAngle();
        } catch (Exception e2) {
            e2.printStackTrace();
            Log.e(TAG, "exception reading horizontal or vertical view angles");
            camera_features.view_angle_x = 55.0f;
            camera_features.view_angle_y = 43.0f;
        }
        if (camera_features.view_angle_x > 150.0f || camera_features.view_angle_y > 150.0f) {
            Log.e(TAG, "camera API reporting stupid view angles, set to sensible defaults");
            camera_features.view_angle_x = 55.0f;
            camera_features.view_angle_y = 43.0f;
        }
        return camera_features;
    }

    public long getDefaultExposureTime() {
        return 0;
    }

    public SupportedValues setSceneMode(String value) {
        String default_value = getDefaultSceneMode();
        Parameters parameters = getParameters();
        SupportedValues supported_values = checkModeIsSupported(parameters.getSupportedSceneModes(), value, default_value);
        if (supported_values != null) {
            String scene_mode = parameters.getSceneMode();
            if (!(scene_mode == null || scene_mode.equals(supported_values.selected_value))) {
                parameters.setSceneMode(supported_values.selected_value);
                setCameraParameters(parameters);
            }
        }
        return supported_values;
    }

    public String getSceneMode() {
        return getParameters().getSceneMode();
    }

    public SupportedValues setColorEffect(String value) {
        String default_value = getDefaultColorEffect();
        Parameters parameters = getParameters();
        SupportedValues supported_values = checkModeIsSupported(parameters.getSupportedColorEffects(), value, default_value);
        if (supported_values != null) {
            String color_effect = parameters.getColorEffect();
            if (color_effect == null || !color_effect.equals(supported_values.selected_value)) {
                parameters.setColorEffect(supported_values.selected_value);
                setCameraParameters(parameters);
            }
        }
        return supported_values;
    }

    public String getColorEffect() {
        return getParameters().getColorEffect();
    }

    public SupportedValues setWhiteBalance(String value) {
        String default_value = getDefaultWhiteBalance();
        Parameters parameters = getParameters();
        List<String> values = parameters.getSupportedWhiteBalance();
        if (values != null) {
            while (values.contains("manual")) {
                values.remove("manual");
            }
        }
        SupportedValues supported_values = checkModeIsSupported(values, value, default_value);
        if (supported_values != null) {
            String white_balance = parameters.getWhiteBalance();
            if (!(white_balance == null || white_balance.equals(supported_values.selected_value))) {
                parameters.setWhiteBalance(supported_values.selected_value);
                setCameraParameters(parameters);
            }
        }
        return supported_values;
    }

    public String getWhiteBalance() {
        return getParameters().getWhiteBalance();
    }

    public boolean setWhiteBalanceTemperature(int temperature) {
        return false;
    }

    public int getWhiteBalanceTemperature() {
        return 0;
    }

    public SupportedValues setISO(String value) {
        SupportedValues supported_values = null;
        Parameters parameters = getParameters();
        String iso_values = parameters.get("iso-values");
        if (iso_values == null) {
            iso_values = parameters.get("iso-mode-values");
            if (iso_values == null) {
                iso_values = parameters.get("iso-speed-values");
                if (iso_values == null) {
                    iso_values = parameters.get("nv-picture-iso-values");
                }
            }
        }
        List<String> list = null;
        if (iso_values != null && iso_values.length() > 0) {
            String[] isos_array = iso_values.split(",");
            if (isos_array.length > 0) {
                HashSet<String> hashSet = new HashSet();
                list = new ArrayList();
                for (String iso : isos_array) {
                    if (!hashSet.contains(iso)) {
                        list.add(iso);
                        hashSet.add(iso);
                    }
                }
            }
        }
        this.iso_key = "iso";
        if (parameters.get(this.iso_key) == null) {
            this.iso_key = "iso-speed";
            if (parameters.get(this.iso_key) == null) {
                this.iso_key = "nv-picture-iso";
                if (parameters.get(this.iso_key) == null) {
                    if (Build.MODEL.contains("Z00")) {
                        this.iso_key = "iso";
                    } else {
                        this.iso_key = null;
                    }
                }
            }
        }
        if (this.iso_key != null) {
            if (list == null) {
                list = new ArrayList();
                list.add("auto");
                list.add("50");
                list.add("100");
                list.add("200");
                list.add("400");
                list.add("800");
                list.add("1600");
            }
            supported_values = checkModeIsSupported(list, value, getDefaultISO());
            if (supported_values != null) {
                parameters.set(this.iso_key, supported_values.selected_value);
                setCameraParameters(parameters);
            }
        }
        return supported_values;
    }

    public String getISOKey() {
        return this.iso_key;
    }

    public void setManualISO(boolean manual_iso, int iso) {
    }

    public boolean isManualISO() {
        return false;
    }

    public boolean setISO(int iso) {
        return false;
    }

    public int getISO() {
        return 0;
    }

    public long getExposureTime() {
        return 0;
    }

    public boolean setExposureTime(long exposure_time) {
        return false;
    }

    public Size getPictureSize() {
        return new Size(this.picture_width, this.picture_height);
    }

    public void setPictureSize(int width, int height) {
        Parameters parameters = getParameters();
        this.picture_width = width;
        this.picture_height = height;
        parameters.setPictureSize(width, height);
        setCameraParameters(parameters);
    }

    public Size getPreviewSize() {
        android.hardware.Camera.Size camera_size = getParameters().getPreviewSize();
        return new Size(camera_size.width, camera_size.height);
    }

    public void setPreviewSize(int width, int height) {
        Parameters parameters = getParameters();
        parameters.setPreviewSize(width, height);
        setCameraParameters(parameters);
    }

    public void setExpoBracketing(boolean want_expo_bracketing) {
        if (this.camera != null && this.want_expo_bracketing != want_expo_bracketing) {
            this.want_expo_bracketing = want_expo_bracketing;
        }
    }

    public void setExpoBracketingNImages(int n_images) {
        if (n_images <= 1 || n_images % 2 == 0) {
            throw new RuntimeException();
        }
        if (n_images > 3) {
            n_images = 3;
        }
        this.expo_bracketing_n_images = n_images;
    }

    public void setExpoBracketingStops(double stops) {
        if (stops <= 0.0d) {
            throw new RuntimeException();
        }
        this.expo_bracketing_stops = stops;
    }

    public void setUseExpoFastBurst(boolean use_expo_fast_burst) {
    }

    public void setOptimiseAEForDRO(boolean optimise_ae_for_dro) {
    }

    public void setRaw(boolean want_raw) {
    }

    public void setVideoStabilization(boolean enabled) {
        Parameters parameters = getParameters();
        parameters.setVideoStabilization(enabled);
        setCameraParameters(parameters);
    }

    public boolean getVideoStabilization() {
        return getParameters().getVideoStabilization();
    }

    public int getJpegQuality() {
        return getParameters().getJpegQuality();
    }

    public void setJpegQuality(int quality) {
        Parameters parameters = getParameters();
        parameters.setJpegQuality(quality);
        setCameraParameters(parameters);
    }

    public int getZoom() {
        return this.current_zoom_value;
    }

    public void setZoom(int value) {
        Parameters parameters = getParameters();
        this.current_zoom_value = value;
        parameters.setZoom(value);
        setCameraParameters(parameters);
    }

    public int getExposureCompensation() {
        return getParameters().getExposureCompensation();
    }

    private float getExposureCompensationStep() {
        try {
            return getParameters().getExposureCompensationStep();
        } catch (Exception e) {
            e.printStackTrace();
            return 0.33333334f;
        }
    }

    public boolean setExposureCompensation(int new_exposure) {
        Parameters parameters = getParameters();
        if (new_exposure == parameters.getExposureCompensation()) {
            return false;
        }
        parameters.setExposureCompensation(new_exposure);
        setCameraParameters(parameters);
        return true;
    }

    public void setPreviewFpsRange(int min, int max) {
        try {
            Parameters parameters = getParameters();
            parameters.setPreviewFpsRange(min, max);
            setCameraParameters(parameters);
        } catch (RuntimeException e) {
            Log.e(TAG, "setPreviewFpsRange failed to get parameters");
            e.printStackTrace();
        }
    }

    public List<int[]> getSupportedPreviewFpsRange() {
        try {
            return getParameters().getSupportedPreviewFpsRange();
        } catch (StringIndexOutOfBoundsException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void setFocusValue(String focus_value) {
        Parameters parameters = getParameters();
        int obj = -1;
        switch (focus_value.hashCode()) {
            case -2084726721:
                if (focus_value.equals("focus_mode_locked")) {
                    obj = 1;
                    break;
                }
                break;
            case -1897460700:
                if (focus_value.equals("focus_mode_auto")) {
                    obj = 8;
                    break;
                }
                break;
            case -1897358037:
                if (focus_value.equals("focus_mode_edof")) {
                    obj = 5;
                    break;
                }
                break;
            case -711944829:
                if (focus_value.equals("focus_mode_continuous_picture")) {
                    obj = 6;
                    break;
                }
                break;
            case 402565696:
                if (focus_value.equals("focus_mode_continuous_video")) {
                    obj = 7;
                    break;
                }
                break;
            case 590698013:
                if (focus_value.equals("focus_mode_infinity")) {
                    obj = 2;
                    break;
                }
                break;
            case 1312524191:
                if (focus_value.equals("focus_mode_fixed")) {
                    obj = 4;
                    break;
                }
                break;
            case 1318730743:
                if (focus_value.equals("focus_mode_macro")) {
                    obj = 3;
                    break;
                }
                break;
        }
        switch (obj) {
            case 8:
            case 1:
                parameters.setFocusMode("auto");
                break;
            case 2:
                parameters.setFocusMode("infinity");
                break;
            case 3:
                parameters.setFocusMode("macro");
                break;
            case 4:
                parameters.setFocusMode("fixed");
                break;
            case 5:
                parameters.setFocusMode("edof");
                break;
            case 6:
                parameters.setFocusMode("continuous-picture");
                break;
            case 7:
                parameters.setFocusMode("continuous-video");
                break;
        }
        setCameraParameters(parameters);
    }

    private String convertFocusModeToValue(String focus_mode) {
        String focus_value = "";
        if (focus_mode == null) {
            return focus_value;
        }
        if (focus_mode.equals("auto")) {
            return "focus_mode_auto";
        }
        if (focus_mode.equals("infinity")) {
            return "focus_mode_infinity";
        }
        if (focus_mode.equals("macro")) {
            return "focus_mode_macro";
        }
        if (focus_mode.equals("fixed")) {
            return "focus_mode_fixed";
        }
        if (focus_mode.equals("edof")) {
            return "focus_mode_edof";
        }
        if (focus_mode.equals("continuous-picture")) {
            return "focus_mode_continuous_picture";
        }
        if (focus_mode.equals("continuous-video")) {
            return "focus_mode_continuous_video";
        }
        return focus_value;
    }

    public String getFocusValue() {
        return convertFocusModeToValue(getParameters().getFocusMode());
    }

    public float getFocusDistance() {
        return 0.0f;
    }

    public boolean setFocusDistance(float focus_distance) {
        return false;
    }

    private String convertFlashValueToMode(String flash_value) {
        String flash_mode = "";
        int obj = -1;
        switch (flash_value.hashCode()) {
            case -1195303778:
                if (flash_value.equals("flash_auto")) {
                    obj = 1;
                    break;
                }
                break;
            case -1146923872:
                if (flash_value.equals("flash_off")) {
                    obj = 6;
                    break;
                }
                break;
            case -10523976:
                if (flash_value.equals("flash_frontscreen_on")) {
                    obj = 5;
                    break;
                }
                break;
            case 1617654509:
                if (flash_value.equals("flash_torch")) {
                    obj = 3;
                    break;
                }
                break;
            case 1625570446:
                if (flash_value.equals("flash_on")) {
                    obj = 2;
                    break;
                }
                break;
            case 2008442932:
                if (flash_value.equals("flash_red_eye")) {
                    obj = 4;
                    break;
                }
                break;
        }
        switch (obj) {
            case 6:
                return "off";
            case 1:
                return "auto";
            case 2:
                return "on";
            case 3:
                return "torch";
            case 4:
                return "red-eye";
            case 5:
                return "off";
            default:
                return flash_mode;
        }
    }

    public void setFlashValue(String flash_value) {
        Parameters parameters = getParameters();
        this.frontscreen_flash = false;
        if (flash_value.equals("flash_frontscreen_on")) {
            this.frontscreen_flash = true;
        } else if (parameters.getFlashMode() != null) {
            final String flash_mode = convertFlashValueToMode(flash_value);
            if (flash_mode.length() > 0 && !flash_mode.equals(parameters.getFlashMode())) {
                if (!parameters.getFlashMode().equals("torch") || flash_mode.equals("off")) {
                    parameters.setFlashMode(flash_mode);
                    setCameraParameters(parameters);
                    return;
                }
                parameters.setFlashMode("off");
                setCameraParameters(parameters);
                new Handler().postDelayed(new Runnable() {
                    public void run() {
                        if (CameraController1.this.camera != null) {
                            Parameters parameters = CameraController1.this.getParameters();
                            parameters.setFlashMode(flash_mode);
                            CameraController1.this.setCameraParameters(parameters);
                        }
                    }
                }, 100);
            }
        }
    }

    private String convertFlashModeToValue(String flash_mode) {
        String flash_value = "";
        if (flash_mode == null) {
            return flash_value;
        }
        if (flash_mode.equals("off")) {
            return "flash_off";
        }
        if (flash_mode.equals("auto")) {
            return "flash_auto";
        }
        if (flash_mode.equals("on")) {
            return "flash_on";
        }
        if (flash_mode.equals("torch")) {
            return "flash_torch";
        }
        if (flash_mode.equals("red-eye")) {
            return "flash_red_eye";
        }
        return flash_value;
    }

    public String getFlashValue() {
        return convertFlashModeToValue(getParameters().getFlashMode());
    }

    public void setRecordingHint(boolean hint) {
        Parameters parameters = getParameters();
        String focus_mode = parameters.getFocusMode();
        if (focus_mode != null && !focus_mode.equals("continuous-video")) {
            parameters.setRecordingHint(hint);
            setCameraParameters(parameters);
        }
    }

    public void setAutoExposureLock(boolean enabled) {
        Parameters parameters = getParameters();
        parameters.setAutoExposureLock(enabled);
        setCameraParameters(parameters);
    }

    public boolean getAutoExposureLock() {
        Parameters parameters = getParameters();
        if (parameters.isAutoExposureLockSupported()) {
            return parameters.getAutoExposureLock();
        }
        return false;
    }

    public void setRotation(int rotation) {
        Parameters parameters = getParameters();
        parameters.setRotation(rotation);
        setCameraParameters(parameters);
    }

    public void setLocationInfo(Location location) {
        Parameters parameters = getParameters();
        parameters.removeGpsData();
        parameters.setGpsTimestamp(System.currentTimeMillis() / 1000);
        parameters.setGpsLatitude(location.getLatitude());
        parameters.setGpsLongitude(location.getLongitude());
        parameters.setGpsProcessingMethod(location.getProvider());
        if (location.hasAltitude()) {
            parameters.setGpsAltitude(location.getAltitude());
        } else {
            parameters.setGpsAltitude(0.0d);
        }
        if (location.getTime() != 0) {
            parameters.setGpsTimestamp(location.getTime() / 1000);
        }
        setCameraParameters(parameters);
    }

    public void removeLocationInfo() {
        Parameters parameters = getParameters();
        parameters.removeGpsData();
        setCameraParameters(parameters);
    }

    public void enableShutterSound(boolean enabled) {
        if (VERSION.SDK_INT >= 17) {
            this.camera.enableShutterSound(enabled);
        }
    }

    public boolean setFocusAndMeteringArea(List<Area> areas) {
        List<Camera.Area> camera_areas = new ArrayList();
        for (Area area : areas) {
            camera_areas.add(new Camera.Area(area.rect, area.weight));
        }
        Parameters parameters = getParameters();
        String focus_mode = parameters.getFocusMode();
        if (parameters.getMaxNumFocusAreas() == 0 || focus_mode == null || !(focus_mode.equals("auto") || focus_mode.equals("macro") || focus_mode.equals("continuous-picture") || focus_mode.equals("continuous-video"))) {
            if (parameters.getMaxNumMeteringAreas() != 0) {
                parameters.setMeteringAreas(camera_areas);
                setCameraParameters(parameters);
            }
            return false;
        }
        parameters.setFocusAreas(camera_areas);
        if (parameters.getMaxNumMeteringAreas() != 0) {
            parameters.setMeteringAreas(camera_areas);
        }
        setCameraParameters(parameters);
        return true;
    }

    public void clearFocusAndMetering() {
        Parameters parameters = getParameters();
        boolean update_parameters = false;
        if (parameters.getMaxNumFocusAreas() > 0) {
            parameters.setFocusAreas(null);
            update_parameters = true;
        }
        if (parameters.getMaxNumMeteringAreas() > 0) {
            parameters.setMeteringAreas(null);
            update_parameters = true;
        }
        if (update_parameters) {
            setCameraParameters(parameters);
        }
    }

    public List<Area> getFocusAreas() {
        List<Camera.Area> camera_areas = getParameters().getFocusAreas();
        if (camera_areas == null) {
            return null;
        }
        List<Area> areas = new ArrayList();
        for (Camera.Area camera_area : camera_areas) {
            areas.add(new Area(camera_area.rect, camera_area.weight));
        }
        return areas;
    }

    public List<Area> getMeteringAreas() {
        List<Camera.Area> camera_areas = getParameters().getMeteringAreas();
        if (camera_areas == null) {
            return null;
        }
        List<Area> areas = new ArrayList();
        for (Camera.Area camera_area : camera_areas) {
            areas.add(new Area(camera_area.rect, camera_area.weight));
        }
        return areas;
    }

    public boolean supportsAutoFocus() {
        String focus_mode = getParameters().getFocusMode();
        if (focus_mode == null || (!focus_mode.equals("auto") && !focus_mode.equals("macro"))) {
            return false;
        }
        return true;
    }

    public boolean focusIsContinuous() {
        String focus_mode = getParameters().getFocusMode();
        if (focus_mode == null || (!focus_mode.equals("continuous-picture") && !focus_mode.equals("continuous-video"))) {
            return false;
        }
        return true;
    }

    public boolean focusIsVideo() {
        String current_focus_mode = getParameters().getFocusMode();
        return current_focus_mode != null && current_focus_mode.equals("continuous-video");
    }

    public void reconnect() throws CameraControllerException {
        try {
            this.camera.reconnect();
        } catch (IOException e) {
            e.printStackTrace();
            throw new CameraControllerException();
        }
    }

    public void setPreviewDisplay(SurfaceHolder holder) throws CameraControllerException {
        try {
            this.camera.setPreviewDisplay(holder);
        } catch (IOException e) {
            e.printStackTrace();
            throw new CameraControllerException();
        }
    }

    public void setPreviewTexture(SurfaceTexture texture) throws CameraControllerException {
        try {
            this.camera.setPreviewTexture(texture);
        } catch (IOException e) {
            e.printStackTrace();
            throw new CameraControllerException();
        }
    }

    public void startPreview() throws CameraControllerException {
        try {
            this.camera.startPreview();
        } catch (RuntimeException e) {
            e.printStackTrace();
            throw new CameraControllerException();
        }
    }

    public void stopPreview() {
        this.camera.stopPreview();
    }

    public boolean startFaceDetection() {
        try {
            this.camera.startFaceDetection();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public void setFaceDetectionListener(final FaceDetectionListener listener) {
        this.camera.setFaceDetectionListener(new Camera.FaceDetectionListener() {
            @Override
            public void onFaceDetection(Camera.Face[] camera_faces, Camera camera) {
                Face[] faces = new Face[camera_faces.length];
                for (int i = 0; i < camera_faces.length; i++) {
                    faces[i] = new Face(camera_faces[i].score, camera_faces[i].rect);
                }
                listener.onFaceDetection(faces);
            }
        });
    }

    public void autoFocus(final AutoFocusCallback cb, boolean capture_follows_autofocus_hint) {
        try {
            this.camera.autoFocus(new Camera.AutoFocusCallback() {
                boolean done_autofocus = false;

                public void onAutoFocus(boolean success, Camera camera) {
                    if (!this.done_autofocus) {
                        this.done_autofocus = true;
                        cb.onAutoFocus(success);
                    }
                }
            });
        } catch (RuntimeException e) {
            e.printStackTrace();
            cb.onAutoFocus(false);
        }
    }

    public void setCaptureFollowAutofocusHint(boolean capture_follows_autofocus_hint) {
    }

    public void cancelAutoFocus() {
        try {
            this.camera.cancelAutoFocus();
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    public void setContinuousFocusMoveCallback(final ContinuousFocusMoveCallback cb) {
        if (VERSION.SDK_INT < 16) {
            return;
        }
        if (cb != null) {
            try {
                this.camera.setAutoFocusMoveCallback(new AutoFocusMoveCallback() {
                    public void onAutoFocusMoving(boolean start, Camera camera) {
                        cb.onContinuousFocusMove(start);
                    }
                });
                return;
            } catch (RuntimeException e) {
                e.printStackTrace();
                return;
            }
        }
        this.camera.setAutoFocusMoveCallback(null);
    }

    private void clearPending() {
        this.pending_burst_images.clear();
        this.burst_exposures = null;
        this.n_burst = 0;
    }

    private void takePictureNow(final PictureCallback picture, final ErrorCallback error) {
        Camera.PictureCallback camera_jpeg = null;
        ShutterCallback shutter = new TakePictureShutterCallback();
        if (picture != null) {
            camera_jpeg = new Camera.PictureCallback() {
                public void onPictureTaken(byte[] data, Camera cam) {
                    if (!CameraController1.this.want_expo_bracketing || CameraController1.this.n_burst <= 1) {
                        picture.onPictureTaken(data);
                        picture.onCompleted();
                        return;
                    }
                    CameraController1.this.pending_burst_images.add(data);
                    if (CameraController1.this.pending_burst_images.size() >= CameraController1.this.n_burst) {
                        int i;
                        if (CameraController1.this.pending_burst_images.size() > CameraController1.this.n_burst) {
                            Log.e(CameraController1.TAG, "pending_burst_images size " + CameraController1.this.pending_burst_images.size() + " is greater than n_burst " + CameraController1.this.n_burst);
                        }
                        CameraController1.this.setExposureCompensation(((Integer) CameraController1.this.burst_exposures.get(0)).intValue());
                        int n_half_images = CameraController1.this.pending_burst_images.size() / 2;
                        List<byte[]> images = new ArrayList();
                        for (i = 0; i < n_half_images; i++) {
                            images.add(CameraController1.this.pending_burst_images.get(i + 1));
                        }
                        images.add(CameraController1.this.pending_burst_images.get(0));
                        for (i = 0; i < n_half_images; i++) {
                            images.add(CameraController1.this.pending_burst_images.get(n_half_images + 1));
                        }
                        picture.onBurstPictureTaken(images);
                        CameraController1.this.pending_burst_images.clear();
                        picture.onCompleted();
                        return;
                    }
                    CameraController1.this.setExposureCompensation(((Integer) CameraController1.this.burst_exposures.get(CameraController1.this.pending_burst_images.size())).intValue());
                    try {
                        CameraController1.this.startPreview();
                    } catch (CameraControllerException e) {
                        e.printStackTrace();
                    }
                    new Handler().postDelayed(new Runnable() {
                        public void run() {
                            if (CameraController1.this.camera != null) {
                                CameraController1.this.takePictureNow(picture, error);
                            }
                        }
                    }, 1000);
                }
            };
        }
        if (picture != null) {
            picture.onStarted();
        }
        try {
            this.camera.takePicture(shutter, null, camera_jpeg);
        } catch (RuntimeException e) {
            e.printStackTrace();
            error.onError();
        }
    }

    public void takePicture(PictureCallback picture, ErrorCallback error) {
        clearPending();
        if (this.want_expo_bracketing) {
            int i;
            Parameters parameters = getParameters();
            int n_half_images = this.expo_bracketing_n_images / 2;
            int min_exposure = parameters.getMinExposureCompensation();
            int max_exposure = parameters.getMaxExposureCompensation();
            float exposure_step = getExposureCompensationStep();
            if (exposure_step == 0.0f) {
                exposure_step = 0.33333334f;
            }
            int exposure_current = getExposureCompensation();
            int steps = Math.max((int) ((1.0E-5d + (this.expo_bracketing_stops / ((double) n_half_images))) / ((double) exposure_step)), 1);
            List<Integer> requests = new ArrayList();
            requests.add(Integer.valueOf(exposure_current));
            for (i = 0; i < n_half_images; i++) {
                requests.add(Integer.valueOf(Math.max(exposure_current - ((n_half_images - i) * steps), min_exposure)));
            }
            for (i = 0; i < n_half_images; i++) {
                requests.add(Integer.valueOf(Math.min(exposure_current + ((i + 1) * steps), max_exposure)));
            }
            this.burst_exposures = requests;
            this.n_burst = requests.size();
        }
        if (this.frontscreen_flash) {
            picture.onFrontScreenTurnOn();
            final PictureCallback pictureCallback = picture;
            final ErrorCallback errorCallback = error;
            new Handler().postDelayed(new Runnable() {
                public void run() {
                    if (CameraController1.this.camera != null) {
                        CameraController1.this.takePictureNow(pictureCallback, errorCallback);
                    }
                }
            }, 1000);
            return;
        }
        takePictureNow(picture, error);
    }

    public void setDisplayOrientation(int degrees) {
        int result;
        if (this.camera_info.facing == 1) {
            result = (360 - ((this.camera_info.orientation + degrees) % 360)) % 360;
        } else {
            result = ((this.camera_info.orientation - degrees) + 360) % 360;
        }
        this.camera.setDisplayOrientation(result);
        this.display_orientation = result;
    }

    public int getDisplayOrientation() {
        return this.display_orientation;
    }

    public int getCameraOrientation() {
        return this.camera_info.orientation;
    }

    public boolean isFrontFacing() {
        return this.camera_info.facing == 1;
    }

    public void unlock() {
        stopPreview();
        this.camera.unlock();
    }

    public void initVideoRecorderPrePrepare(MediaRecorder video_recorder) {
        video_recorder.setCamera(this.camera);
    }

    public void initVideoRecorderPostPrepare(MediaRecorder video_recorder) throws CameraControllerException {
    }

    public String getParametersString() {
        String string = "";
        try {
            string = getParameters().flatten();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return string;
    }
}
