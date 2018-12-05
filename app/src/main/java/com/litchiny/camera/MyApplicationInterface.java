package com.litchiny.camera;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.hardware.camera2.DngCreator;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.util.Pair;
import android.view.MotionEvent;

import com.lwyy.camera.R;
import com.litchiny.utils.MainUtil;

import com.litchiny.camera.controller.CameraController;

import com.litchiny.camera.ui.ApplicationInterface;
import com.litchiny.camera.ui.Preview;
import com.litchiny.camera.ui.MainUI;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MyApplicationInterface implements ApplicationInterface {
    private static final String TAG = "MyApplicationInterface";
    private final GyroSensor gyroSensor;
    private final ImageSaveThread imageSaveThread;
    private final List<LastImage> last_images = new ArrayList();
    private final MainActivity main_activity;
    private final StorageUtils storageUtils;
    private final Rect text_bounds = new Rect();
    private int cameraId = 0;
    private float focus_distance = 0.0f;
    private boolean last_images_saf;
    private int n_panorama_pics = 0;
    private boolean used_front_screen_flash;
    private int zoom_factor = 0;

    MyApplicationInterface(MainActivity main_activity, Bundle savedInstanceState) {
        this.main_activity = main_activity;
        this.gyroSensor = new GyroSensor(main_activity);
        this.storageUtils = new StorageUtils(main_activity);
        this.imageSaveThread = new ImageSaveThread(main_activity);
        this.imageSaveThread.start();
        if (savedInstanceState != null) {
            this.cameraId = savedInstanceState.getInt("cameraId", 0);
            this.zoom_factor = savedInstanceState.getInt("zoom_factor", 0);
            this.focus_distance = savedInstanceState.getFloat("focus_distance", 0.0f);
        }
    }

    void onSaveInstanceState(Bundle state) {
        state.putInt("cameraId", this.cameraId);
        state.putInt("zoom_factor", this.zoom_factor);
        state.putFloat("focus_distance", this.focus_distance);
    }

    void onDestroy() {
        if (this.imageSaveThread != null) {
            this.imageSaveThread.onDestroy();
        }
    }


    public GyroSensor getGyroSensor() {
        return this.gyroSensor;
    }

    public StorageUtils getStorageUtils() {
        return this.storageUtils;
    }

    public ImageSaveThread getImageSaveThread() {
        return this.imageSaveThread;
    }

    public Context getContext() {
        return this.main_activity;
    }

    public boolean useCamera2() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        if (this.main_activity.supportsCamera2()) {
            return sharedPreferences.getBoolean(PreferenceKeys.getUseCamera2PreferenceKey(), false);
        }
        return false;
    }

    public int getCameraIdPref() {
        return this.cameraId;
    }

    public void setCameraIdPref(int cameraId) {
        this.cameraId = cameraId;
    }

    public String getFlashPref() {
        return PreferenceManager.getDefaultSharedPreferences(getContext()).getString(PreferenceKeys.getFlashPreferenceKey(this.cameraId), "");
    }

    public void setFlashPref(String flash_value) {
        Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
        editor.putString(PreferenceKeys.getFlashPreferenceKey(this.cameraId), flash_value);
        editor.apply();
    }

    public String getFocusPref(boolean is_video) {
        return PreferenceManager.getDefaultSharedPreferences(getContext()).getString(PreferenceKeys.getFocusPreferenceKey(this.cameraId, is_video), "");
    }

    public String getSceneModePref() {
        return PreferenceManager.getDefaultSharedPreferences(getContext()).getString(PreferenceKeys.getSceneModePreferenceKey(), "auto");
    }

    public void setSceneModePref(String scene_mode) {
        Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
        editor.putString(PreferenceKeys.getSceneModePreferenceKey(), scene_mode);
        editor.apply();
    }

    public String getColorEffectPref() {
        return PreferenceManager.getDefaultSharedPreferences(getContext()).getString(PreferenceKeys.getColorEffectPreferenceKey(), "none");
    }

    public void setColorEffectPref(String color_effect) {
        Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
        editor.putString(PreferenceKeys.getColorEffectPreferenceKey(), color_effect);
        editor.apply();
    }

    public String getWhiteBalancePref() {
        return PreferenceManager.getDefaultSharedPreferences(getContext()).getString(PreferenceKeys.getWhiteBalancePreferenceKey(), "auto");
    }

    public void setWhiteBalancePref(String white_balance) {
        Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
        editor.putString(PreferenceKeys.getWhiteBalancePreferenceKey(), white_balance);
        editor.apply();
    }

    public int getWhiteBalanceTemperaturePref() {
        return PreferenceManager.getDefaultSharedPreferences(getContext()).getInt(PreferenceKeys.getWhiteBalanceTemperaturePreferenceKey(), 5000);
    }

    public void setWhiteBalanceTemperaturePref(int white_balance_temperature) {
        Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
        editor.putInt(PreferenceKeys.getWhiteBalanceTemperaturePreferenceKey(), white_balance_temperature);
        editor.apply();
    }

    public void setISOPref(String iso) {
        Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
        editor.putString(PreferenceKeys.getISOPreferenceKey(), iso);
        editor.apply();
    }

    public int getExposureCompensationPref() {
        int exposure = 0;
        try {
            exposure = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(getContext()).getString(PreferenceKeys.getExposurePreferenceKey(), "0"));
        } catch (NumberFormatException e) {
        }
        return exposure;
    }

    public void setExposureCompensationPref(int exposure) {
        Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
        editor.putString(PreferenceKeys.getExposurePreferenceKey(), "" + exposure);
        editor.apply();
    }

    public Pair<Integer, Integer> getCameraResolutionPref() {
        String resolution_value = PreferenceManager.getDefaultSharedPreferences(getContext()).getString(PreferenceKeys.getResolutionPreferenceKey(this.cameraId), "");
        if (resolution_value.length() > 0) {
            int index = resolution_value.indexOf(32);
            if (index != -1) {
                String resolution_w_s = resolution_value.substring(0, index);
                String resolution_h_s = resolution_value.substring(index + 1);
                try {
                    return new Pair(Integer.valueOf(Integer.parseInt(resolution_w_s)), Integer.valueOf(Integer.parseInt(resolution_h_s)));
                } catch (NumberFormatException e) {
                }
            }
        }
        return null;
    }

    private int getSaveImageQualityPref() {
        try {
            return Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(getContext()).getString(PreferenceKeys.getQualityPreferenceKey(), "90"));
        } catch (NumberFormatException e) {
            return 90;
        }
    }

    public int getImageQualityPref() {
        if (getPhotoMode() == PhotoMode.DRO) {
            return 100;
        }
        return getSaveImageQualityPref();
    }

    public boolean getFaceDetectionPref() {
        return PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean(PreferenceKeys.getFaceDetectionPreferenceKey(), false);
    }

    public boolean getForce4KPref() {

        return false;
    }

    public String getPreviewSizePref() {
        return PreferenceManager.getDefaultSharedPreferences(getContext()).getString(PreferenceKeys.getPreviewSizePreferenceKey(), "preference_preview_size_wysiwyg");
    }

    public String getPreviewRotationPref() {
        return PreferenceManager.getDefaultSharedPreferences(getContext()).getString(PreferenceKeys.getRotatePreviewPreferenceKey(), "0");
    }

    public String getLockOrientationPref() {
        return PreferenceManager.getDefaultSharedPreferences(getContext()).getString(PreferenceKeys.getLockOrientationPreferenceKey(), "none");
    }

    public boolean getTouchCapturePref() {
        return PreferenceManager.getDefaultSharedPreferences(getContext()).getString(PreferenceKeys.getTouchCapturePreferenceKey(), "none").equals("single");
    }

    public boolean getDoubleTapCapturePref() {
        return PreferenceManager.getDefaultSharedPreferences(getContext()).getString(PreferenceKeys.getTouchCapturePreferenceKey(), "none").equals("double");
    }

    public boolean getPausePreviewPref() {
        return PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean(PreferenceKeys.getPausePreviewPreferenceKey(), false);
    }

    public boolean getShowToastsPref() {
        return PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean(PreferenceKeys.getShowToastsPreferenceKey(), true);
    }

    public boolean getThumbnailAnimationPref() {
        return PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean(PreferenceKeys.getThumbnailAnimationPreferenceKey(), true);
    }

    public boolean getShutterSoundPref() {
        return PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean(PreferenceKeys.getShutterSoundPreferenceKey(), true);
    }

    public boolean getStartupFocusPref() {
        return PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean(PreferenceKeys.getStartupFocusPreferenceKey(), true);
    }

    public long getTimerPref() {
        try {
            return ((long) Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(getContext()).getString(PreferenceKeys.getTimerPreferenceKey(), "0"))) * 1000;
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public String getRepeatPref() {
        return PreferenceManager.getDefaultSharedPreferences(getContext()).getString(PreferenceKeys.getBurstModePreferenceKey(), "1");
    }

    public long getRepeatIntervalPref() {
        try {
            return ((long) Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(getContext()).getString(PreferenceKeys.getBurstIntervalPreferenceKey(), "0"))) * 1000;
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return 0;
        }
    }

    private boolean getGeodirectionPref() {
        return PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean(PreferenceKeys.getGPSDirectionPreferenceKey(), false);
    }

    public boolean getAutoStabilisePref() {
        if (PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean(PreferenceKeys.getAutoStabilisePreferenceKey(), false) && this.main_activity.supportsAutoStabilise()) {
            return true;
        }
        return false;
    }

    public String getStampPref() {
        return PreferenceManager.getDefaultSharedPreferences(getContext()).getString(PreferenceKeys.getStampPreferenceKey(), "preference_stamp_no");
    }

    private String getStampDateFormatPref() {
        return PreferenceManager.getDefaultSharedPreferences(getContext()).getString(PreferenceKeys.getStampDateFormatPreferenceKey(), "preference_stamp_dateformat_default");
    }

    private String getStampTimeFormatPref() {
        return PreferenceManager.getDefaultSharedPreferences(getContext()).getString(PreferenceKeys.getStampTimeFormatPreferenceKey(), "preference_stamp_timeformat_default");
    }

    private String getStampGPSFormatPref() {
        return PreferenceManager.getDefaultSharedPreferences(getContext()).getString(PreferenceKeys.getStampGPSFormatPreferenceKey(), "preference_stamp_gpsformat_default");
    }

    private String getTextStampPref() {
        return PreferenceManager.getDefaultSharedPreferences(getContext()).getString(PreferenceKeys.getTextStampPreferenceKey(), "");
    }

    private int getTextStampFontSizePref() {
        int font_size = 12;
        try {
            font_size = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(getContext()).getString(PreferenceKeys.getStampFontSizePreferenceKey(), "12"));
        } catch (NumberFormatException e) {
        }
        return font_size;
    }

    public int getZoomPref() {
        return this.zoom_factor;
    }

    public void setZoomPref(int zoom) {
        this.zoom_factor = zoom;
    }

    public double getCalibratedLevelAngle() {
        return (double) PreferenceManager.getDefaultSharedPreferences(getContext()).getFloat(PreferenceKeys.getCalibratedLevelAnglePreferenceKey(), 0.0f);
    }

    public long getExposureTimePref() {
        return PreferenceManager.getDefaultSharedPreferences(getContext()).getLong(PreferenceKeys.getExposureTimePreferenceKey(), CameraController.EXPOSURE_TIME_DEFAULT);
    }

    public void setExposureTimePref(long exposure_time) {
        Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
        editor.putLong(PreferenceKeys.getExposureTimePreferenceKey(), exposure_time);
        editor.apply();
    }

    public float getFocusDistancePref() {
        return this.focus_distance;
    }

    public void setFocusDistancePref(float focus_distance) {
        this.focus_distance = focus_distance;
    }

    public boolean isExpoBracketingPref() {
        PhotoMode photo_mode = getPhotoMode();
        if (photo_mode == PhotoMode.HDR || photo_mode == PhotoMode.ExpoBracketing) {
            return true;
        }
        return false;
    }

    public int getExpoBracketingNImagesPref() {
        if (getPhotoMode() == PhotoMode.HDR) {
            return 3;
        }
        try {
            return Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(getContext()).getString(PreferenceKeys.getExpoBracketingNImagesPreferenceKey(), "3"));
        } catch (NumberFormatException e) {
            return 3;
        }
    }

    public double getExpoBracketingStopsPref() {
        if (getPhotoMode() == PhotoMode.HDR) {
            return 2.0d;
        }
        try {
            return Double.parseDouble(PreferenceManager.getDefaultSharedPreferences(getContext()).getString(PreferenceKeys.getExpoBracketingStopsPreferenceKey(), "2"));
        } catch (NumberFormatException e) {
            return 2.0d;
        }
    }

    public PhotoMode getPhotoMode() {
        String photo_mode_pref = PreferenceManager.getDefaultSharedPreferences(getContext()).getString(PreferenceKeys.getPhotoModePreferenceKey(), "preference_photo_mode_std");
        if (photo_mode_pref.equals("preference_photo_mode_dro")) {
            return PhotoMode.DRO;
        }
        if (photo_mode_pref.equals("preference_photo_mode_hdr") && MainUtil.supportsHDR(this.main_activity)) {
            return PhotoMode.HDR;
        }
        if (photo_mode_pref.equals("preference_photo_mode_expo_bracketing") && MainUtil.supportsExpoBracketing(this.main_activity)) {
            return PhotoMode.ExpoBracketing;
        }
        return PhotoMode.Standard;
    }

    public boolean getOptimiseAEForDROPref() {
        return getPhotoMode() == PhotoMode.DRO;
    }

    public boolean isRawPref() {
        if (isImageCaptureIntent()) {
            return false;
        }
        return PreferenceManager.getDefaultSharedPreferences(getContext()).getString(PreferenceKeys.getRawPreferenceKey(), "preference_raw_no").equals("preference_raw_yes");
    }

    public boolean useCamera2FakeFlash() {
        return PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean(PreferenceKeys.getCamera2FakeFlashPreferenceKey(), false);
    }

    public boolean useCamera2FastBurst() {
        return PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean(PreferenceKeys.getCamera2FastBurstPreferenceKey(), true);
    }

    public boolean isTestAlwaysFocus() {
        return this.main_activity.is_test;
    }

    public void cameraSetup() {
        this.main_activity.cameraSetup();
    }

    void setNextPanoramaPoint() {
        float camera_angle_y = this.main_activity.getPreview().getViewAngleY();
        this.n_panorama_pics++;
        float angle = ((float) Math.toRadians((double) camera_angle_y)) * ((float) this.n_panorama_pics);
        setNextPanoramaPoint((float) Math.sin((double) (angle / 2.0f)), 0.0f, (float) (-Math.cos((double) (angle / 2.0f))));
    }

    private void setNextPanoramaPoint(float x, float y, float z) {
        this.gyroSensor.setTarget(x, y, z, 0.034906585f, new GyroSensor.TargetCallback() {
            public void onAchieved() {
                MyApplicationInterface.this.clearPanoramaPoint();
                MyApplicationInterface.this.main_activity.takePicturePressed();
            }
        });
    }

    void clearPanoramaPoint() {
        this.gyroSensor.clearTarget();
    }

    public void touchEvent(MotionEvent event) {
        if (MainUtil.usingKitKatImmersiveMode(this.main_activity)) {
            this.main_activity.setImmersiveMode(false);
        }
    }

    public void onFailedStartPreview() {
        this.main_activity.getPreview().showToast(null, (int) R.string.failed_to_start_camera_preview);
    }

    public void onCameraError() {
        this.main_activity.getPreview().showToast(null, (int) R.string.camera_error);
    }

    @Override
    public void onCaptureStarted() {

    }

    @Override
    public void onContinuousFocusMove(boolean z) {

    }

    @Override
    public void onDrawPreview(Canvas canvas) {

    }

    public void onPhotoError() {
        this.main_activity.getPreview().showToast(null, (int) R.string.failed_to_take_picture);
    }

    @Override
    public void onPictureCompleted() {

    }

    public void onFailedReconnectError() {
        this.main_activity.getPreview().showToast(null, (int) R.string.failed_to_reconnect_camera);
    }

    @Override
    public void cameraClosed() {

    }

    public void cameraInOperation(boolean in_operation) {
        boolean z = false;
        if (!in_operation && this.used_front_screen_flash) {
            this.main_activity.setBrightnessForCamera(false);
            this.used_front_screen_flash = false;
        }
        MainUI mainUI = this.main_activity.getMainUI();
        if (!in_operation) {
            z = true;
        }
        mainUI.showGUI(z);
    }

    public void turnFrontScreenFlashOn() {
        this.used_front_screen_flash = true;
        this.main_activity.setBrightnessForCamera(true);
    }


    void updateThumbnail(Bitmap thumbnail) {
        this.main_activity.updateGalleryIcon(thumbnail);
    }

    public void timerBeep(long remaining_time) {
        boolean is_last = true;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        if (sharedPreferences.getBoolean(PreferenceKeys.getTimerBeepPreferenceKey(), true)) {
            if (remaining_time > 1000) {
                is_last = false;
            }
            this.main_activity.playSound(is_last ? R.raw.beep : R.raw.beep);
        }
        if (sharedPreferences.getBoolean(PreferenceKeys.getTimerSpeakPreferenceKey(), false)) {
            int remaining_time_s = (int) (remaining_time / 1000);
            if (remaining_time_s <= 60) {
                this.main_activity.speak("" + remaining_time_s);
            }
        }
    }

    public void layoutUI() {
        this.main_activity.getMainUI().layoutUI();
    }

    @Override
    public void multitouchZoom(int i) {

    }


    public void setFocusPref(String focus_value, boolean is_video) {
        Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
        editor.putString(PreferenceKeys.getFocusPreferenceKey(this.cameraId, is_video), focus_value);
        editor.apply();
    }

    public void clearSceneModePref() {
        Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
        editor.remove(PreferenceKeys.getSceneModePreferenceKey());
        editor.apply();
    }

    public void clearColorEffectPref() {
        Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
        editor.remove(PreferenceKeys.getColorEffectPreferenceKey());
        editor.apply();
    }

    public void clearWhiteBalancePref() {
        Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
        editor.remove(PreferenceKeys.getWhiteBalancePreferenceKey());
        editor.apply();
    }

    public void clearExposureCompensationPref() {
        Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
        editor.remove(PreferenceKeys.getExposurePreferenceKey());
        editor.apply();
    }

    public void setCameraResolutionPref(int width, int height) {
        String resolution_value = width + " " + height;
        Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
        editor.putString(PreferenceKeys.getResolutionPreferenceKey(this.cameraId), resolution_value);
        editor.apply();
    }

    public void requestCameraPermission() {
        this.main_activity.requestCameraPermission();
    }

    public void requestStoragePermission() {
        this.main_activity.requestStoragePermission();
    }

    public void clearExposureTimePref() {
        Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
        editor.remove(PreferenceKeys.getExposureTimePreferenceKey());
        editor.apply();
    }

    private int getStampFontColor() {
        return Color.parseColor(PreferenceManager.getDefaultSharedPreferences(getContext()).getString(PreferenceKeys.getStampFontColorPreferenceKey(), "#ffffff"));
    }

    public void drawTextWithBackground(Canvas canvas, Paint paint, String text, int foreground, int background, int location_x, int location_y) {
        drawTextWithBackground(canvas, paint, text, foreground, background, location_x, location_y, Alignment.ALIGNMENT_BOTTOM);
    }

    public void drawTextWithBackground(Canvas canvas, Paint paint, String text, int foreground, int background, int location_x, int location_y, Alignment alignment_y) {
        drawTextWithBackground(canvas, paint, text, foreground, background, location_x, location_y, alignment_y, null, true);
    }

    public void drawTextWithBackground(Canvas canvas, Paint paint, String text, int foreground, int background, int location_x, int location_y, Alignment alignment_y, String ybounds_text, boolean shadow) {
        Rect rect;
        float scale = getContext().getResources().getDisplayMetrics().density;
        paint.setStyle(Style.FILL);
        paint.setColor(background);
        paint.setAlpha(64);
        int alt_height = 0;
        if (ybounds_text != null) {
            paint.getTextBounds(ybounds_text, 0, ybounds_text.length(), this.text_bounds);
            alt_height = this.text_bounds.bottom - this.text_bounds.top;
        }
        paint.getTextBounds(text, 0, text.length(), this.text_bounds);
        if (ybounds_text != null) {
            this.text_bounds.bottom = this.text_bounds.top + alt_height;
        }
        int padding = (int) ((2.0f * scale) + 0.5f);
        if (paint.getTextAlign() == Align.RIGHT || paint.getTextAlign() == Align.CENTER) {
            float width = paint.measureText(text);
            if (paint.getTextAlign() == Align.CENTER) {
                width /= 2.0f;
            }
            rect = this.text_bounds;
            rect.left = (int) (((float) rect.left) - width);
            rect = this.text_bounds;
            rect.right = (int) (((float) rect.right) - width);
        }
        rect = this.text_bounds;
        rect.left += location_x - padding;
        rect = this.text_bounds;
        rect.right += location_x + padding;
        int top_y_diff = ((-this.text_bounds.top) + padding) - 1;
        int height;
        if (alignment_y == Alignment.ALIGNMENT_TOP) {
            height = (this.text_bounds.bottom - this.text_bounds.top) + (padding * 2);
            this.text_bounds.top = location_y - 1;
            this.text_bounds.bottom = this.text_bounds.top + height;
            location_y += top_y_diff;
        } else if (alignment_y == Alignment.ALIGNMENT_CENTRE) {
            height = (this.text_bounds.bottom - this.text_bounds.top) + (padding * 2);
            int y_diff = ((-this.text_bounds.top) + padding) - 1;
            this.text_bounds.top = (int) (0.5d * ((double) ((location_y - 1) + ((this.text_bounds.top + location_y) - padding))));
            this.text_bounds.bottom = this.text_bounds.top + height;
            location_y += (int) (0.5d * ((double) top_y_diff));
        } else {
            rect = this.text_bounds;
            rect.top += location_y - padding;
            rect = this.text_bounds;
            rect.bottom += location_y + padding;
        }
        if (shadow) {
            canvas.drawRect(this.text_bounds, paint);
        }
        paint.setColor(foreground);
        canvas.drawText(text, (float) location_x, (float) location_y, paint);
    }

    private boolean saveInBackground(boolean image_capture_intent) {
        if (!PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean(PreferenceKeys.getBackgroundPhotoSavingPreferenceKey(), true)) {
            return false;
        }
        if (image_capture_intent) {
            return false;
        }
        if (getPausePreviewPref()) {
            return false;
        }
        return true;
    }

    private boolean isImageCaptureIntent() {
        String action = this.main_activity.getIntent().getAction();
        if ("android.media.action.IMAGE_CAPTURE".equals(action) || "android.media.action.IMAGE_CAPTURE_SECURE".equals(action)) {
            return true;
        }
        return false;
    }

    private boolean saveImage(boolean is_hdr, boolean save_expo, List<byte[]> images, Date current_date) {
        boolean mirror;
        String preference_stamp;
        String preference_textstamp;
        int font_size;
        int color;
        String pref_style;
        String preference_stamp_dateformat;
        String preference_stamp_timeformat;
        String preference_stamp_gpsformat;
        boolean store_geo_direction = false;
        double geo_direction = 0;
        boolean has_thumbnail_animation;
        boolean do_in_background;
        int sample_factor;
        System.gc();
        boolean image_capture_intent = isImageCaptureIntent();
        Uri image_capture_intent_uri = null;
        if (image_capture_intent) {
            Bundle myExtras = this.main_activity.getIntent().getExtras();
            if (myExtras != null) {
                image_capture_intent_uri = (Uri) myExtras.getParcelable("output");
            }
        }
        boolean using_camera2 = this.main_activity.getPreview().usingCamera2API();
        int image_quality = getSaveImageQualityPref();
        boolean do_auto_stabilise = getAutoStabilisePref() && this.main_activity.getPreview().hasLevelAngle();
        double levelAngle = do_auto_stabilise ? this.main_activity.getPreview().getLevelAngle() : 0.0d;
        if (do_auto_stabilise && this.main_activity.test_have_angle) {
            levelAngle = (double) this.main_activity.test_angle;
        }
        if (do_auto_stabilise && this.main_activity.test_low_memory) {
            levelAngle = 45.0d;
        }
        boolean is_front_facing = this.main_activity.getPreview().getCameraController() != null && this.main_activity.getPreview().getCameraController().isFrontFacing();
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        if (is_front_facing) {
            if (sharedPreferences.getString(PreferenceKeys.getFrontCameraMirrorKey(), "preference_front_camera_mirror_no").equals("preference_front_camera_mirror_photo")) {
                mirror = true;
                preference_stamp = getStampPref();
                preference_textstamp = getTextStampPref();
                font_size = getTextStampFontSizePref();
                color = getStampFontColor();
                pref_style = sharedPreferences.getString(PreferenceKeys.getStampStyleKey(), "preference_stamp_style_shadowed");
                preference_stamp_dateformat = getStampDateFormatPref();
                preference_stamp_timeformat = getStampTimeFormatPref();
                preference_stamp_gpsformat = getStampGPSFormatPref();
                store_geo_direction = this.main_activity.getPreview().hasGeoDirection() && getGeodirectionPref();
                geo_direction = store_geo_direction ? this.main_activity.getPreview().getGeoDirection() : 0.0d;
                has_thumbnail_animation = getThumbnailAnimationPref();
                do_in_background = saveInBackground(image_capture_intent);
                sample_factor = 1;
                if (!getPausePreviewPref()) {
                    sample_factor = 1 * 4;
                    if (!has_thumbnail_animation) {
                        sample_factor *= 4;
                    }
                }
                return this.imageSaveThread.saveImageJpeg(do_in_background, is_hdr, save_expo, images, image_capture_intent, image_capture_intent_uri, using_camera2,
                        image_quality, do_auto_stabilise, levelAngle, is_front_facing, mirror, current_date, preference_stamp, preference_textstamp, font_size,
                        color, pref_style, preference_stamp_dateformat, preference_stamp_timeformat, preference_stamp_gpsformat, false, null, store_geo_direction, geo_direction, sample_factor);
            }
        }
        mirror = false;
        preference_stamp = getStampPref();
        preference_textstamp = getTextStampPref();
        font_size = getTextStampFontSizePref();
        color = getStampFontColor();
        pref_style = sharedPreferences.getString(PreferenceKeys.getStampStyleKey(), "preference_stamp_style_shadowed");
        preference_stamp_dateformat = getStampDateFormatPref();
        preference_stamp_timeformat = getStampTimeFormatPref();
        preference_stamp_gpsformat = getStampGPSFormatPref();

        has_thumbnail_animation = getThumbnailAnimationPref();
        do_in_background = saveInBackground(image_capture_intent);
        sample_factor = 1;
        if (getPausePreviewPref()) {
            sample_factor = 1 * 4;
            if (has_thumbnail_animation) {
                sample_factor *= 4;
            }
        }
        return this.imageSaveThread.saveImageJpeg(do_in_background, is_hdr, save_expo, images, image_capture_intent, image_capture_intent_uri,
                using_camera2, image_quality, do_auto_stabilise, levelAngle, is_front_facing, mirror, current_date, preference_stamp, preference_textstamp,
                font_size, color, pref_style, preference_stamp_dateformat, preference_stamp_timeformat, preference_stamp_gpsformat, false,
                null, store_geo_direction, geo_direction, sample_factor);
    }

    public boolean onPictureTaken(byte[] data, Date current_date) {
        List<byte[]> images = new ArrayList();
        images.add(data);
        boolean is_hdr = false;
        if (getPhotoMode() == PhotoMode.DRO) {
            is_hdr = true;
        }
        return saveImage(is_hdr, false, images, current_date);
    }

    public boolean onBurstPictureTaken(List<byte[]> images, Date current_date) {
        if (getPhotoMode() == PhotoMode.HDR) {
            return saveImage(true, PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean(PreferenceKeys.getHDRSaveExpoPreferenceKey(), false), images, current_date);
        }
        return saveImage(false, true, images, current_date);
    }

    public boolean onRawPictureTaken(DngCreator dngCreator, Image image, Date current_date) {
        System.gc();
        return this.imageSaveThread.saveImageRaw(saveInBackground(false), dngCreator, image, current_date);
    }

    void addLastImage(File file, boolean share) {
        this.last_images_saf = false;
        this.last_images.add(new LastImage(file.getAbsolutePath(), share));
    }

    void addLastImageSAF(Uri uri, boolean share) {
        this.last_images_saf = true;
        this.last_images.add(new LastImage(uri, share));
    }

    void clearLastImages() {
        this.last_images_saf = false;
        this.last_images.clear();
    }

    @TargetApi(21)
    private void trashImage(boolean image_saf, Uri image_uri, String image_name) {
        Preview preview = this.main_activity.getPreview();
        File file;
        if (image_saf && image_uri != null) {
            file = this.storageUtils.getFileFromDocumentUriSAF(image_uri, false);
            try {
                if (DocumentsContract.deleteDocument(this.main_activity.getContentResolver(), image_uri)) {
                    preview.showToast(null, (int) R.string.photo_deleted);
                    if (file != null) {
                        this.storageUtils.broadcastFile(file, false, false, true);
                    }
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        } else if (image_name != null) {
            file = new File(image_name);
            if (file.delete()) {
                preview.showToast(null, (int) R.string.photo_deleted);
                this.storageUtils.broadcastFile(file, false, false, true);
            }
        }
    }

    public enum Alignment {
        ALIGNMENT_TOP,
        ALIGNMENT_CENTRE,
        ALIGNMENT_BOTTOM
    }

    public enum PhotoMode {
        Standard,
        DRO,
        HDR,
        ExpoBracketing
    }

    private static class LastImage {
        public final String name;
        public final boolean share;
        final Uri uri;

        LastImage(Uri uri, boolean share) {
            this.name = null;
            this.uri = uri;
            this.share = share;
        }

        LastImage(String filename, boolean share) {
            this.name = filename;
            this.uri = Uri.parse("file://" + this.name);
            this.share = share;
        }
    }
}
