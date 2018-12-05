package com.litchiny.camera.ui;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Matrix.ScaleToFit;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.hardware.camera2.DngCreator;
import android.media.CamcorderProfile;
import android.media.Image;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewCompat;
import android.util.Log;
import android.util.Pair;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.ScaleGestureDetector;
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.TextureView.SurfaceTextureListener;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import com.lwyy.camera.R;

import com.litchiny.camera.controller.CameraController;
import com.litchiny.camera.controller.CameraController.Area;
import com.litchiny.camera.controller.CameraController.AutoFocusCallback;
import com.litchiny.camera.controller.CameraController.CameraFeatures;
import com.litchiny.camera.controller.CameraController.ContinuousFocusMoveCallback;
import com.litchiny.camera.controller.CameraController.ErrorCallback;
import com.litchiny.camera.controller.CameraController.Face;
import com.litchiny.camera.controller.CameraController.FaceDetectionListener;
import com.litchiny.camera.controller.CameraController.PictureCallback;
import com.litchiny.camera.controller.CameraController.Size;
import com.litchiny.camera.controller.CameraController.SupportedValues;
import com.litchiny.camera.controller.CameraController1;
import com.litchiny.camera.controller.CameraController2;
import com.litchiny.camera.controller.CameraControllerException;
import com.litchiny.camera.controller.CameraControllerManager;
import com.litchiny.camera.controller.CameraControllerManager1;
import com.litchiny.camera.controller.CameraControllerManager2;
import com.litchiny.camera.ui.surface.CameraSurface;
import com.litchiny.camera.ui.surface.MySurfaceView;
import com.litchiny.camera.ui.surface.MyTextureView;
import com.litchiny.camera.TakePhoto;
import com.litchiny.camera.ToastBoxer;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class Preview implements Callback, SurfaceTextureListener {
    private static final String TAG = "Preview";
    private static final long min_safe_restart_video_time = 1000;
    private static final float sensor_alpha = 0.8f;
    private final ApplicationInterface applicationInterface;
    private final Timer beepTimer = new Timer();
    private final float[] cameraRotation = new float[9];
    private final CameraSurface cameraSurface;
    private final CameraControllerManager camera_controller_manager;
    private final Matrix camera_to_preview_matrix = new Matrix();
    private final DecimalFormat decimal_format_1dp = new DecimalFormat("#.#");
    private final DecimalFormat decimal_format_2dp = new DecimalFormat("#.##");
    private final float[] deviceInclination = new float[9];
    private final float[] deviceRotation = new float[9];
    private final ToastBoxer flash_toast = new ToastBoxer();
    private final ToastBoxer focus_toast = new ToastBoxer();
    private final float[] geo_direction = new float[3];
    private final float[] geomagnetic = new float[3];
    private final GestureDetector gestureDetector;
    private final float[] gravity = new float[3];
    private final float[] new_geo_direction = new float[3];
    private final Matrix preview_to_camera_matrix = new Matrix();
    private final Handler reset_continuous_focus_handler = new Handler();
    private final ScaleGestureDetector scaleGestureDetector;
    private final ToastBoxer seekbar_toast = new ToastBoxer();
    private final Timer takePictureTimer = new Timer();
    private final ToastBoxer take_photo_toast = new ToastBoxer();
    private final boolean using_android_l;
    public volatile int count_cameraAutoFocus;
    public volatile int count_cameraContinuousFocusMoving;
    public volatile int count_cameraStartPreview;
    public volatile int count_cameraTakePicture;
    public volatile boolean test_fail_open_camera;
    public volatile boolean test_ticker_called;
    private boolean app_is_paused = true;
    private double aspect_ratio;
    private boolean autofocus_in_continuous_mode;
    private TimerTask beepTimerTask;
    private CameraController camera_controller;
    private boolean can_disable_shutter_sound;
    private CanvasView canvasView;
    private List<String> color_effects;
    private boolean continuous_focus_move_is_started;
    private int current_flash_index = -1;
    private int current_focus_index = -1;
    private int current_orientation;
    private int current_rotation;
    private int current_size_index = -1;
    private float exposure_step;
    private List<String> exposures;
    private Face[] faces_detected;
    private long focus_complete_time = -1;
    private int focus_screen_x;
    private int focus_screen_y;
    private long focus_started_time = -1;
    private int focus_success = 3;
    private boolean has_aspect_ratio;
    private boolean has_focus_area;
    private boolean has_geo_direction;
    private boolean has_geomagnetic;
    private boolean has_gravity;
    private boolean has_level_angle;
    private boolean has_permissions = true;
    private boolean has_pitch_angle;
    private boolean has_surface;
    private boolean has_zoom;
    private boolean is_exposure_lock_supported;
    private boolean is_exposure_locked;
    private boolean is_preview_started;
    private boolean is_test;
    private List<String> isos;
    private Toast last_toast;
    private double level_angle;
    private int max_expo_bracketing_n_images;
    private int max_exposure;
    private long max_exposure_time;
    private int max_iso;
    private int max_num_focus_areas;
    private int max_temperature;
    private int max_zoom_factor;
    private int min_exposure;
    private long min_exposure_time;
    private int min_iso;
    private int min_temperature;
    private float minimum_focus_distance;
    private double natural_level_angle;
    private double orig_level_angle;
    private volatile int phase = 0;
    private double pitch_angle;
    private int preview_h;
    private double preview_targetRatio;
    private int preview_w;
    private int remaining_burst_photos;
    private Runnable reset_continuous_focus_runnable;
    private List<String> scene_modes;
    private String set_flash_value_after_autofocus = "";
    private boolean set_preview_size;
    private boolean set_textureview_size;
    private List<Size> sizes;
    private boolean successfully_focused;
    private long successfully_focused_time = -1;
    private List<String> supported_flash_values;
    private List<String> supported_focus_values;
    private List<Size> supported_preview_sizes;
    private boolean supports_expo_bracketing;
    private boolean supports_exposure_time;
    private boolean supports_face_detection;
    private boolean supports_iso_range;
    private boolean supports_raw;
    private boolean supports_video_stabilization;
    private boolean supports_white_balance_temperature;
    private TimerTask takePictureTimerTask;
    private boolean take_photo_after_autofocus;
    private long take_photo_time;
    private int textureview_h;
    private int textureview_w;
    private float touch_orig_x;
    private float touch_orig_y;
    private boolean touch_was_multitouch;
    private int ui_rotation;
    private boolean using_face_detection;
    private float view_angle_x;
    private float view_angle_y;
    private List<String> white_balances;
    private List<Integer> zoom_ratios;

    public Preview(ApplicationInterface applicationInterface, ViewGroup parent) {
        boolean z = true;
        this.applicationInterface = applicationInterface;
        Activity activity = (Activity) getContext();
        if (!(activity.getIntent() == null || activity.getIntent().getExtras() == null)) {
            this.is_test = activity.getIntent().getExtras().getBoolean("test_project");
        }
        if (VERSION.SDK_INT < 21 || !applicationInterface.useCamera2()) {
            z = false;
        }
        this.using_android_l = z;
        boolean using_texture_view = false;
        if (this.using_android_l) {
            using_texture_view = true;
        }
        if (using_texture_view) {
            this.cameraSurface = new MyTextureView(getContext(), this);
            this.canvasView = new CanvasView(getContext(), this);
            this.camera_controller_manager = new CameraControllerManager2(getContext());
        } else {
            this.cameraSurface = new MySurfaceView(getContext(), this);
            this.camera_controller_manager = new CameraControllerManager1();
        }
        this.gestureDetector = new GestureDetector(getContext(), new SimpleOnGestureListener());
        this.gestureDetector.setOnDoubleTapListener(new DoubleTapListener());
        this.scaleGestureDetector = new ScaleGestureDetector(getContext(), new ScaleListener());
        parent.addView(this.cameraSurface.getView());
        if (this.canvasView != null) {
            parent.addView(this.canvasView);
        }
    }

    public static int[] chooseBestPreviewFps(List<int[]> fps_ranges) {
        int selected_min_fps = -1;
        int selected_max_fps = -1;
        for (int[] fps_range : fps_ranges) {
            int min_fps = fps_range[0];
            int max_fps = fps_range[1];
            if (max_fps >= 30000) {
                if (selected_min_fps == -1 || min_fps < selected_min_fps) {
                    selected_min_fps = min_fps;
                    selected_max_fps = max_fps;
                } else if (min_fps == selected_min_fps && max_fps > selected_max_fps) {
                    selected_min_fps = min_fps;
                    selected_max_fps = max_fps;
                }
            }
        }
        int min_fps = 0, max_fps = 0;
        if (selected_min_fps == -1) {
            int selected_diff = -1;
            for (int[] fps_range2 : fps_ranges) {
                min_fps = fps_range2[0];
                max_fps = fps_range2[1];
                int diff = max_fps - min_fps;
                if (selected_diff == -1 || diff > selected_diff) {
                    selected_min_fps = min_fps;
                    selected_max_fps = max_fps;
                    selected_diff = diff;
                } else if (diff == selected_diff && max_fps > selected_max_fps) {
                    selected_min_fps = min_fps;
                    selected_max_fps = max_fps;
                    selected_diff = diff;
                }
            }
        }
        return new int[]{selected_min_fps, selected_max_fps};
    }

    public static String getAspectRatioMPString(int width, int height) {
        return "(" + getAspectRatio(width, height) + ", " + getMPString(width, height) + ")";
    }

    private static String getAspectRatio(int width, int height) {
        int gcf = greatestCommonFactor(width, height);
        if (gcf > 0) {
            width /= gcf;
            height /= gcf;
        }
        return width + ":" + height;
    }

    public static String getMPString(int width, int height) {
        return formatFloatToString(((float) (width * height)) / 1000000.0f) + "MP";
    }

    private static int greatestCommonFactor(int a, int b) {
        while (b > 0) {
            int temp = b;
            b = a % b;
            a = temp;
        }
        return a;
    }

    private static String formatFloatToString(float f) {
        int i = (int) f;
        if (f == ((float) i)) {
            return Integer.toString(i);
        }
        return String.format(Locale.getDefault(), "%.2f", new Object[]{Float.valueOf(f)});
    }

    @TargetApi(21)
    private Context getContext() {
        return this.applicationInterface.getContext();
    }

    public View getView() {
        return this.cameraSurface.getView();
    }

    private void calculatePreviewToCameraMatrix() {
        if (this.camera_controller != null) {
            calculateCameraToPreviewMatrix();
        }
    }

    public Matrix getCameraToPreviewMatrix() {
        calculateCameraToPreviewMatrix();
        return this.camera_to_preview_matrix;
    }

    private void calculateCameraToPreviewMatrix() {
        float f = -1.0f;
        if (this.camera_controller != null) {
            this.camera_to_preview_matrix.reset();
            boolean mirror;
            Matrix matrix;
            if (this.using_android_l) {
                mirror = this.camera_controller.isFrontFacing();
                matrix = this.camera_to_preview_matrix;
                if (!mirror) {
                    f = PopupView.ALPHA_BUTTON_SELECTED;
                }
                matrix.setScale(PopupView.ALPHA_BUTTON_SELECTED, f);
                this.camera_to_preview_matrix.postRotate((float) (((this.camera_controller.getCameraOrientation() - getDisplayRotationDegrees()) + 360) % 360));
            } else {
                mirror = this.camera_controller.isFrontFacing();
                matrix = this.camera_to_preview_matrix;
                if (!mirror) {
                    f = PopupView.ALPHA_BUTTON_SELECTED;
                }
                matrix.setScale(f, PopupView.ALPHA_BUTTON_SELECTED);
                this.camera_to_preview_matrix.postRotate((float) this.camera_controller.getDisplayOrientation());
            }
            this.camera_to_preview_matrix.postScale(((float) this.cameraSurface.getView().getWidth()) / 2000.0f, ((float) this.cameraSurface.getView().getHeight()) / 2000.0f);
            this.camera_to_preview_matrix.postTranslate(((float) this.cameraSurface.getView().getWidth()) / 2.0f, ((float) this.cameraSurface.getView().getHeight()) / 2.0f);
        }
    }

    private int getDisplayRotationDegrees() {
        switch (getDisplayRotation()) {
            case 0:
                return 0;
            case 1:
                return 90;
            case 2:
                return 180;
            case 3:
                return 270;
            default:
                return 0;
        }
    }

    public int getDisplayRotation() {
        int rotation = ((Activity) getContext()).getWindowManager().getDefaultDisplay().getRotation();
        if (!this.applicationInterface.getPreviewRotationPref().equals("180")) {
            return rotation;
        }
        switch (rotation) {
            case 0:
                return 2;
            case 1:
                return 3;
            case 2:
                return 0;
            case 3:
                return 1;
            default:
                return rotation;
        }
    }

    private ArrayList<Area> getAreas(float x, float y) {
        float[] coords = new float[]{x, y};
        calculatePreviewToCameraMatrix();
        this.preview_to_camera_matrix.mapPoints(coords);
        float focus_x = coords[0];
        float focus_y = coords[1];
        Rect rect = new Rect();
        rect.left = ((int) focus_x) - 50;
        rect.right = ((int) focus_x) + 50;
        rect.top = ((int) focus_y) - 50;
        rect.bottom = ((int) focus_y) + 50;
        if (rect.left < NotificationManagerCompat.IMPORTANCE_UNSPECIFIED) {
            rect.left = NotificationManagerCompat.IMPORTANCE_UNSPECIFIED;
            rect.right = rect.left + 100;
        } else if (rect.right > 1000) {
            rect.right = 1000;
            rect.left = rect.right - 100;
        }
        if (rect.top < NotificationManagerCompat.IMPORTANCE_UNSPECIFIED) {
            rect.top = NotificationManagerCompat.IMPORTANCE_UNSPECIFIED;
            rect.bottom = rect.top + 100;
        } else if (rect.bottom > 1000) {
            rect.bottom = 1000;
            rect.top = rect.bottom - 100;
        }
        ArrayList<Area> areas = new ArrayList();
        areas.add(new Area(rect, 1000));
        return areas;
    }

    public boolean touchEvent(MotionEvent event) {
        if (!this.gestureDetector.onTouchEvent(event)) {
            this.scaleGestureDetector.onTouchEvent(event);
            if (this.camera_controller == null) {
                openCamera();
            } else {
                this.applicationInterface.touchEvent(event);
                if (event.getPointerCount() != 1) {
                    this.touch_was_multitouch = true;
                } else if (event.getAction() != 1) {
                    if (event.getAction() == 0 && event.getPointerCount() == 1) {
                        this.touch_was_multitouch = false;
                        if (event.getAction() == 0) {
                            this.touch_orig_x = event.getX();
                            this.touch_orig_y = event.getY();
                        }
                    }
                } else if (!this.touch_was_multitouch && (!isTakingPhotoOrOnTimer())) {
                    float x = event.getX();
                    float diff_x = x - this.touch_orig_x;
                    float diff_y = event.getY() - this.touch_orig_y;
                    float tol = (31.0f * getResources().getDisplayMetrics().density) + 0.5f;
                    if ((diff_x * diff_x) + (diff_y * diff_y) <= tol * tol) {
                        startCameraPreview();
                        cancelAutoFocus();
                        if (!(this.camera_controller == null || this.using_face_detection)) {
                            this.has_focus_area = false;
                            if (this.camera_controller.setFocusAndMeteringArea(getAreas(event.getX(), event.getY()))) {
                                this.has_focus_area = true;
                                this.focus_screen_x = (int) event.getX();
                                this.focus_screen_y = (int) event.getY();
                            }
                        }
                        if (!this.applicationInterface.getTouchCapturePref()) {
                            tryAutoFocus(false, true);
                        } else {
                            takePicturePressed();
                        }
                    }
                }
            }
        }
        return true;
    }

    public boolean onDoubleTap() {
        if (this.applicationInterface.getDoubleTapCapturePref()) {
            takePicturePressed();
        }
        return true;
    }

    public void clearFocusAreas() {
        if (this.camera_controller != null) {
            this.camera_controller.clearFocusAndMetering();
            this.has_focus_area = false;
            this.focus_success = 3;
            this.successfully_focused = false;
        }
    }

    @SuppressLint("WrongConstant")
    public void getMeasureSpec(int[] spec, int widthSpec, int heightSpec) {
        if (hasAspectRatio()) {
            int longSide;
            int shortSide;
            double aspect_ratio = getAspectRatio();
            int hPadding = this.cameraSurface.getView().getPaddingLeft() + this.cameraSurface.getView().getPaddingRight();
            int vPadding = this.cameraSurface.getView().getPaddingTop() + this.cameraSurface.getView().getPaddingBottom();
            int previewWidth = MeasureSpec.getSize(widthSpec) - hPadding;
            int previewHeight = MeasureSpec.getSize(heightSpec) - vPadding;
            boolean widthLonger = previewWidth > previewHeight;
            if (widthLonger) {
                longSide = previewWidth;
            } else {
                longSide = previewHeight;
            }
            if (widthLonger) {
                shortSide = previewHeight;
            } else {
                shortSide = previewWidth;
            }
            if (((double) longSide) > ((double) shortSide) * aspect_ratio) {
                longSide = (int) (((double) shortSide) * aspect_ratio);
            } else {
                shortSide = (int) (((double) longSide) / aspect_ratio);
            }
            if (widthLonger) {
                previewWidth = longSide;
                previewHeight = shortSide;
            } else {
                previewWidth = shortSide;
                previewHeight = longSide;
            }
            previewHeight += vPadding;
            spec[0] = MeasureSpec.makeMeasureSpec(previewWidth + hPadding, 1073741824);
            spec[1] = MeasureSpec.makeMeasureSpec(previewHeight, 1073741824);
            return;
        }
        spec[0] = widthSpec;
        spec[1] = heightSpec;
    }

    private boolean hasAspectRatio() {
        return this.has_aspect_ratio;
    }

    private double getAspectRatio() {
        return this.aspect_ratio;
    }

    private void setAspectRatio(double ratio) {
        if (ratio <= 0.0d) {
            throw new IllegalArgumentException();
        }
        this.has_aspect_ratio = true;
        if (this.aspect_ratio != ratio) {
            this.aspect_ratio = ratio;
            this.cameraSurface.getView().requestLayout();
            if (this.canvasView != null) {
                this.canvasView.requestLayout();
            }
        }
    }

    private void mySurfaceCreated() {
        this.has_surface = true;
        openCamera();
    }

    private void mySurfaceDestroyed() {
        this.has_surface = false;
        closeCamera();
    }

    public void surfaceCreated(SurfaceHolder holder) {
        mySurfaceCreated();
        this.cameraSurface.getView().setWillNotDraw(false);
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        if (holder.getSurface() != null) {
            mySurfaceChanged();
        }
    }

    private void mySurfaceChanged() {
        if (this.camera_controller != null) {
            this.applicationInterface.layoutUI();
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        mySurfaceDestroyed();
    }

    public void onSurfaceTextureAvailable(SurfaceTexture arg0, int width, int height) {
        this.set_textureview_size = true;
        this.textureview_w = width;
        this.textureview_h = height;
        mySurfaceCreated();
        configureTransform();
    }

    public void onSurfaceTextureSizeChanged(SurfaceTexture arg0, int width, int height) {
        this.set_textureview_size = true;
        this.textureview_w = width;
        this.textureview_h = height;
        mySurfaceChanged();
        configureTransform();
    }

    public boolean onSurfaceTextureDestroyed(SurfaceTexture arg0) {
        this.set_textureview_size = false;
        this.textureview_w = 0;
        this.textureview_h = 0;
        mySurfaceDestroyed();
        return true;
    }

    public void onSurfaceTextureUpdated(SurfaceTexture arg0) {
    }

    private void configureTransform() {
        if (this.camera_controller != null && this.set_preview_size && this.set_textureview_size) {
            int rotation = getDisplayRotation();
            Matrix matrix = new Matrix();
            RectF viewRect = new RectF(0.0f, 0.0f, (float) this.textureview_w, (float) this.textureview_h);
            RectF bufferRect = new RectF(0.0f, 0.0f, (float) this.preview_h, (float) this.preview_w);
            float centerX = viewRect.centerX();
            float centerY = viewRect.centerY();
            if (1 == rotation || 3 == rotation) {
                bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
                matrix.setRectToRect(viewRect, bufferRect, ScaleToFit.FILL);
                float scale = Math.max(((float) this.textureview_h) / ((float) this.preview_h), ((float) this.textureview_w) / ((float) this.preview_w));
                matrix.postScale(scale, scale, centerX, centerY);
                matrix.postRotate((float) ((rotation - 2) * 90), centerX, centerY);
            }
            this.cameraSurface.setTransform(matrix);
        }
    }

    private void reconnectCamera(boolean quiet) {
        if (this.camera_controller != null) {
            try {
                this.camera_controller.reconnect();
                setPreviewPaused(false);
            } catch (CameraControllerException e) {
                e.printStackTrace();
                this.applicationInterface.onFailedReconnectError();
                closeCamera();
            }
            try {
                tryAutoFocus(false, false);
            } catch (RuntimeException e2) {
                e2.printStackTrace();
                this.is_preview_started = false;
                this.camera_controller.release();
                this.camera_controller = null;
                openCamera();
            }
        }
    }

    private void closeCamera() {
        removePendingContinuousFocusReset();
        this.has_focus_area = false;
        this.focus_success = 3;
        this.focus_started_time = -1;
        synchronized (this) {
            this.take_photo_after_autofocus = false;
        }
        this.set_flash_value_after_autofocus = "";
        this.successfully_focused = false;
        this.preview_targetRatio = 0.0d;
        if (this.continuous_focus_move_is_started) {
            this.continuous_focus_move_is_started = false;
            this.applicationInterface.onContinuousFocusMove(false);
        }
        this.applicationInterface.cameraClosed();
        cancelTimer();
        if (this.camera_controller != null) {
            updateFocusForVideo();
            if (this.camera_controller != null) {
                pausePreview();
                this.camera_controller.release();
                this.camera_controller = null;
            }
        }
    }

    public void cancelTimer() {
        if (isOnTimer()) {
            this.takePictureTimerTask.cancel();
            this.takePictureTimerTask = null;
            if (this.beepTimerTask != null) {
                this.beepTimerTask.cancel();
                this.beepTimerTask = null;
            }
            this.phase = 0;
        }
    }

    public void pausePreview() {
        if (this.camera_controller != null) {
            updateFocusForVideo();
            setPreviewPaused(false);
            this.camera_controller.stopPreview();
            this.phase = 0;
            this.is_preview_started = false;
            this.applicationInterface.cameraInOperation(false);
        }
    }

    private void openCamera() {
        this.is_preview_started = false;
        this.set_preview_size = false;
        this.preview_w = 0;
        this.preview_h = 0;
        this.has_focus_area = false;
        this.focus_success = 3;
        this.focus_started_time = -1;
        synchronized (this) {
            this.take_photo_after_autofocus = false;
        }
        this.set_flash_value_after_autofocus = "";
        this.successfully_focused = false;
        this.preview_targetRatio = 0.0d;
        this.scene_modes = null;
        this.has_zoom = false;
        this.max_zoom_factor = 0;
        this.minimum_focus_distance = 0.0f;
        this.zoom_ratios = null;
        this.faces_detected = null;
        this.supports_face_detection = false;
        this.using_face_detection = false;
        this.supports_video_stabilization = false;
        this.can_disable_shutter_sound = false;
        this.color_effects = null;
        this.white_balances = null;
        this.isos = null;
        this.supports_white_balance_temperature = false;
        this.min_temperature = 0;
        this.max_temperature = 0;
        this.supports_iso_range = false;
        this.min_iso = 0;
        this.max_iso = 0;
        this.supports_exposure_time = false;
        this.min_exposure_time = 0;
        this.max_exposure_time = 0;
        this.exposures = null;
        this.min_exposure = 0;
        this.max_exposure = 0;
        this.exposure_step = 0.0f;
        this.supports_expo_bracketing = false;
        this.max_expo_bracketing_n_images = 0;
        this.supports_raw = false;
        this.view_angle_x = 55.0f;
        this.view_angle_y = 43.0f;
        this.sizes = null;
        this.current_size_index = -1;
        this.supported_flash_values = null;
        this.current_flash_index = -1;
        this.supported_focus_values = null;
        this.current_focus_index = -1;
        this.max_num_focus_areas = 0;
        this.applicationInterface.cameraInOperation(false);
        if (this.has_surface && !this.app_is_paused) {
            if (VERSION.SDK_INT >= 23) {
                if (ContextCompat.checkSelfPermission(getContext(), "android.permission.CAMERA") != 0) {
                    this.has_permissions = false;
                    this.applicationInterface.requestCameraPermission();
                    return;
                } else if (ContextCompat.checkSelfPermission(getContext(), "android.permission.WRITE_EXTERNAL_STORAGE") != 0) {
                    this.has_permissions = false;
                    this.applicationInterface.requestStoragePermission();
                    return;
                }
            }
            this.has_permissions = true;
            try {
                int cameraId = this.applicationInterface.getCameraIdPref();
                if (cameraId < 0 || cameraId >= this.camera_controller_manager.getNumberOfCameras()) {
                    cameraId = 0;
                    this.applicationInterface.setCameraIdPref(0);
                }
                if (this.test_fail_open_camera) {
                    throw new CameraControllerException();
                }
                ErrorCallback cameraErrorCallback = new ErrorCallback() {
                    public void onError() {
                        if (Preview.this.camera_controller != null) {
                            Preview.this.camera_controller = null;
                            Preview.this.applicationInterface.onCameraError();
                        }
                    }
                };
                if (this.using_android_l) {
                    this.camera_controller = new CameraController2(getContext(), cameraId, new ErrorCallback() {
                        public void onError() {
                            Preview.this.applicationInterface.onFailedStartPreview();
                        }
                    }, cameraErrorCallback);
                    if (this.applicationInterface.useCamera2FakeFlash()) {
                        this.camera_controller.setUseCamera2FakeFlash(true);
                    }
                } else {
                    this.camera_controller = new CameraController1(cameraId, cameraErrorCallback);
                }
                boolean take_photo = false;
                if (this.camera_controller != null) {
                    Activity activity = (Activity) getContext();
                    if (!(activity.getIntent() == null || activity.getIntent().getExtras() == null)) {
                        take_photo = activity.getIntent().getExtras().getBoolean(TakePhoto.TAKE_PHOTO);
                        activity.getIntent().removeExtra(TakePhoto.TAKE_PHOTO);
                    }
                    setCameraDisplayOrientation();
                    new OrientationEventListener(activity) {
                        public void onOrientationChanged(int orientation) {
                            Preview.this.onOrientationChanged(orientation);
                        }
                    }.enable();
                    this.cameraSurface.setPreviewDisplay(this.camera_controller);
                    setupCamera(take_photo);
                }
            } catch (CameraControllerException e) {
                e.printStackTrace();
                this.camera_controller = null;
            }
        }
    }

    public void retryOpenCamera() {
        if (this.camera_controller == null) {
            openCamera();
        }
    }

    public boolean hasPermissions() {
        return this.has_permissions;
    }

    public void setupCamera(boolean take_photo) {
        if (this.camera_controller != null) {
            boolean do_startup_focus;
            if (take_photo || !this.applicationInterface.getStartupFocusPref()) {
                do_startup_focus = false;
            } else {
                do_startup_focus = true;
            }
            updateFocusForVideo();
            setupCameraParameters();

            if (do_startup_focus && this.using_android_l && this.camera_controller.supportsAutoFocus()) {
                this.set_flash_value_after_autofocus = "";
                String old_flash_value = this.camera_controller.getFlashValue();
                if (!(old_flash_value.length() <= 0 || old_flash_value.equals("flash_off") || old_flash_value.equals("flash_torch"))) {
                    this.set_flash_value_after_autofocus = old_flash_value;
                    this.camera_controller.setFlashValue("flash_off");
                }
            }
            if (this.supports_raw && this.applicationInterface.isRawPref()) {
                this.camera_controller.setRaw(true);
            } else {
                this.camera_controller.setRaw(false);
            }
            if (this.supports_expo_bracketing && this.applicationInterface.isExpoBracketingPref()) {
                this.camera_controller.setExpoBracketing(true);
                this.camera_controller.setExpoBracketingNImages(this.applicationInterface.getExpoBracketingNImagesPref());
                this.camera_controller.setExpoBracketingStops(this.applicationInterface.getExpoBracketingStopsPref());
            } else {
                this.camera_controller.setExpoBracketing(false);
            }
            this.camera_controller.setOptimiseAEForDRO(this.applicationInterface.getOptimiseAEForDROPref());
            setPreviewSize();
            startCameraPreview();
            if (this.has_zoom && this.applicationInterface.getZoomPref() != 0) {
                zoomTo(this.applicationInterface.getZoomPref());
            }

            this.applicationInterface.cameraSetup();
            if (take_photo) {
                String focus_value = getCurrentFocusValue();
                int delay = (focus_value == null || !focus_value.equals("focus_mode_continuous_picture")) ? 500 : 1500;
                new Handler().postDelayed(new Runnable() {
                    public void run() {
                        Preview.this.takePicture(false);
                    }
                }, (long) delay);
            }
            if (do_startup_focus) {
                new Handler().postDelayed(new Runnable() {
                    public void run() {
                        Preview.this.tryAutoFocus(true, false);
                    }
                }, 500);
            }
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void setupCameraParameters() {
        int i;
        Size size;
        Size current_size;
        SupportedValues supported_values = this.camera_controller.setSceneMode(this.applicationInterface.getSceneModePref());
        if (supported_values != null) {
            this.scene_modes = supported_values.values;
            this.applicationInterface.setSceneModePref(supported_values.selected_value);
        } else {
            this.applicationInterface.clearSceneModePref();
        }
        CameraFeatures camera_features = this.camera_controller.getCameraFeatures();
        this.has_zoom = camera_features.is_zoom_supported;
        if (this.has_zoom) {
            this.max_zoom_factor = camera_features.max_zoom;
            this.zoom_ratios = camera_features.zoom_ratios;
        }
        this.minimum_focus_distance = camera_features.minimum_focus_distance;
        this.supports_face_detection = camera_features.supports_face_detection;
        this.sizes = camera_features.picture_sizes;
        this.supported_flash_values = camera_features.supported_flash_values;
        this.supported_focus_values = camera_features.supported_focus_values;
        this.max_num_focus_areas = camera_features.max_num_focus_areas;
        this.is_exposure_lock_supported = camera_features.is_exposure_lock_supported;
        this.supports_video_stabilization = camera_features.is_video_stabilization_supported;
        this.can_disable_shutter_sound = camera_features.can_disable_shutter_sound;
        this.supports_white_balance_temperature = camera_features.supports_white_balance_temperature;
        this.min_temperature = camera_features.min_temperature;
        this.max_temperature = camera_features.max_temperature;
        this.supports_iso_range = camera_features.supports_iso_range;
        this.min_iso = camera_features.min_iso;
        this.max_iso = camera_features.max_iso;
        this.supports_exposure_time = camera_features.supports_exposure_time;
        this.min_exposure_time = camera_features.min_exposure_time;
        this.max_exposure_time = camera_features.max_exposure_time;
        this.min_exposure = camera_features.min_exposure;
        this.max_exposure = camera_features.max_exposure;
        this.exposure_step = camera_features.exposure_step;
        this.supports_expo_bracketing = camera_features.supports_expo_bracketing;
        this.max_expo_bracketing_n_images = camera_features.max_expo_bracketing_n_images;
        this.supports_raw = camera_features.supports_raw;
        this.view_angle_x = camera_features.view_angle_x;
        this.view_angle_y = camera_features.view_angle_y;
        this.supported_preview_sizes = camera_features.preview_sizes;
        this.faces_detected = null;
        if (this.supports_face_detection) {
            this.using_face_detection = this.applicationInterface.getFaceDetectionPref();
        } else {
            this.using_face_detection = false;
        }
        if (this.using_face_detection) {
            this.camera_controller.setFaceDetectionListener(new FaceDetectionListener() {
                public void onFaceDetection(Face[] faces) {
                    Preview.this.faces_detected = new Face[faces.length];
                    System.arraycopy(faces, 0, Preview.this.faces_detected, 0, faces.length);
                }
            });
        }

        supported_values = this.camera_controller.setColorEffect(this.applicationInterface.getColorEffectPref());
        if (supported_values != null) {
            this.color_effects = supported_values.values;
            this.applicationInterface.setColorEffectPref(supported_values.selected_value);
        } else {
            this.applicationInterface.clearColorEffectPref();
        }
        supported_values = this.camera_controller.setWhiteBalance(this.applicationInterface.getWhiteBalancePref());
        if (supported_values != null) {
            this.white_balances = supported_values.values;
            this.applicationInterface.setWhiteBalancePref(supported_values.selected_value);
            if (supported_values.selected_value.equals("manual") && this.supports_white_balance_temperature) {
                this.camera_controller.setWhiteBalanceTemperature(this.applicationInterface.getWhiteBalanceTemperaturePref());
            }
        } else {
            this.applicationInterface.clearWhiteBalancePref();
        }
        boolean is_manual_iso = false;
        if (this.supports_iso_range) {
            this.isos = null;

        } else {
            if (supported_values != null) {
                this.isos = supported_values.values;
                if (!supported_values.selected_value.equals("auto")) {
                    is_manual_iso = true;
                }
                this.applicationInterface.setISOPref(supported_values.selected_value);
            } else {
            }
        }
        if (is_manual_iso) {
            if (this.supports_exposure_time) {
                long exposure_time_value = this.applicationInterface.getExposureTimePref();
                if (exposure_time_value < this.min_exposure_time) {
                    exposure_time_value = this.min_exposure_time;
                } else if (exposure_time_value > this.max_exposure_time) {
                    exposure_time_value = this.max_exposure_time;
                }
                this.camera_controller.setExposureTime(exposure_time_value);
                this.applicationInterface.setExposureTimePref(exposure_time_value);
            } else {
                this.applicationInterface.clearExposureTimePref();
            }
            if (this.using_android_l && this.supported_flash_values != null) {
                this.supported_flash_values = null;
            }
        }
        this.exposures = null;
        if (this.min_exposure == 0 && this.max_exposure == 0) {
            this.applicationInterface.clearExposureCompensationPref();
        } else {
            this.exposures = new ArrayList();
            for (i = this.min_exposure; i <= this.max_exposure; i++) {
                this.exposures.add("" + i);
            }
            if (!is_manual_iso) {
                int exposure = this.applicationInterface.getExposureCompensationPref();
                if (exposure < this.min_exposure || exposure > this.max_exposure) {
                    exposure = 0;
                    if (0 < this.min_exposure || 0 > this.max_exposure) {
                        exposure = this.min_exposure;
                    }
                }
                this.camera_controller.setExposureCompensation(exposure);
                this.applicationInterface.setExposureCompensationPref(exposure);
            }
        }
        this.current_size_index = -1;
        Pair<Integer, Integer> resolution = this.applicationInterface.getCameraResolutionPref();
        if (resolution != null) {
            int resolution_w = ((Integer) resolution.first).intValue();
            int resolution_h = ((Integer) resolution.second).intValue();
            for (i = 0; i < this.sizes.size() && this.current_size_index == -1; i++) {
                size = (Size) this.sizes.get(i);
                if (size.width == resolution_w && size.height == resolution_h) {
                    this.current_size_index = i;
                }
            }
        }
        if (this.current_size_index == -1) {
            current_size = null;
            for (i = 0; i < this.sizes.size(); i++) {
                size = (Size) this.sizes.get(i);
                if (current_size == null || size.width * size.height > current_size.width * current_size.height) {
                    this.current_size_index = i;
                    current_size = size;
                }
            }
        }
        if (this.current_size_index != -1) {
            current_size = (Size) this.sizes.get(this.current_size_index);
            this.applicationInterface.setCameraResolutionPref(current_size.width, current_size.height);
        }
        this.camera_controller.setJpegQuality(this.applicationInterface.getImageQualityPref());
        this.current_flash_index = -1;
        if (this.supported_flash_values == null || this.supported_flash_values.size() <= 1) {
            this.supported_flash_values = null;
        } else {
            String flash_value = this.applicationInterface.getFlashPref();
            if (flash_value.length() > 0) {
                if (!updateFlash(flash_value, false)) {
                    updateFlash(0, true);
                }
            } else if (this.supported_flash_values.contains("flash_auto")) {
                updateFlash("flash_auto", true);
            } else {
                updateFlash("flash_off", true);
            }
        }
        this.current_focus_index = -1;
        if (this.supported_focus_values == null || this.supported_focus_values.size() <= 1) {
            this.supported_focus_values = null;
        } else {
            setFocusPref(true);
        }
        float focus_distance_value = this.applicationInterface.getFocusDistancePref();
        if (focus_distance_value < 0.0f) {
            focus_distance_value = 0.0f;
        } else if (focus_distance_value > this.minimum_focus_distance) {
            focus_distance_value = this.minimum_focus_distance;
        }
        this.camera_controller.setFocusDistance(focus_distance_value);
        this.applicationInterface.setFocusDistancePref(focus_distance_value);
        this.is_exposure_locked = false;
    }

    private void setPreviewSize() {
        if (this.camera_controller != null) {
            if (this.is_preview_started) {
                throw new RuntimeException();
            }
            if (!this.using_android_l) {
                cancelAutoFocus();
            }
            Size new_size = null;
            if (this.current_size_index != -1) {
                new_size = (Size) this.sizes.get(this.current_size_index);
            }
            if (new_size != null) {
                this.camera_controller.setPictureSize(new_size.width, new_size.height);
                Log.d(TAG, "setPreviewSize: new_size: " + new_size.width + "," + new_size.height);
            }
            if (this.supported_preview_sizes != null && this.supported_preview_sizes.size() > 0) {
                Size best_size = getOptimalPreviewSize(this.supported_preview_sizes);
                Log.d(TAG, "setPreviewSize: best_size: " + best_size.width + "," + best_size.height);
                this.camera_controller.setPreviewSize(best_size.width, best_size.height);
                this.set_preview_size = true;
                this.preview_w = best_size.width;
                this.preview_h = best_size.height;
                setAspectRatio(((double) best_size.width) / ((double) best_size.height));
            }
        }
    }

    public CamcorderProfile getCamcorderProfile() {
        if (this.camera_controller == null) {
            return CamcorderProfile.get(0, 1);
        }
        CamcorderProfile profile;
        int cameraId = this.camera_controller.getCameraId();
        if (this.applicationInterface.getForce4KPref()) {
            profile = CamcorderProfile.get(cameraId, 1);
            profile.videoFrameWidth = 3840;
            profile.videoFrameHeight = 2160;
            profile.videoBitRate = (int) (((double) profile.videoBitRate) * 2.8d);
        } else {
            profile = CamcorderProfile.get(cameraId, 1);
        }
        return profile;
    }

    public String getCamcorderProfileDescriptionShort(String quality) {
        if (this.camera_controller == null) {
            return "";
        }
        CamcorderProfile profile = getCamcorderProfile(quality);
        return profile.videoFrameWidth + "x" + profile.videoFrameHeight;
    }

    private CamcorderProfile getCamcorderProfile(String quality) {
        if (this.camera_controller == null) {
            return CamcorderProfile.get(0, 1);
        }
        int cameraId = this.camera_controller.getCameraId();
        CamcorderProfile camcorder_profile = CamcorderProfile.get(cameraId, 1);
        String profile_string = quality;
        try {
            int index = profile_string.indexOf(95);
            if (index != -1) {
                profile_string = quality.substring(0, index);
            }
            camcorder_profile = CamcorderProfile.get(cameraId, Integer.parseInt(profile_string));
            if (index == -1 || index + 1 >= quality.length()) {
                return camcorder_profile;
            }
            String override_string = quality.substring(index + 1);
            if (override_string.charAt(0) != 'r' || override_string.length() < 4) {
                return camcorder_profile;
            }
            index = override_string.indexOf(120);
            if (index == -1) {
                return camcorder_profile;
            }
            String resolution_w_s = override_string.substring(1, index);
            String resolution_h_s = override_string.substring(index + 1);
            int resolution_w = Integer.parseInt(resolution_w_s);
            int resolution_h = Integer.parseInt(resolution_h_s);
            camcorder_profile.videoFrameWidth = resolution_w;
            camcorder_profile.videoFrameHeight = resolution_h;
            return camcorder_profile;
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return camcorder_profile;
        }
    }

    public String getCamcorderProfileDescription(String quality) {
        if (this.camera_controller == null) {
            return "";
        }
        CamcorderProfile profile = getCamcorderProfile(quality);
        String highest = "";
        if (profile.quality == 1) {
            highest = "Highest: ";
        }
        String type = "";
        if (profile.videoFrameWidth == 3840 && profile.videoFrameHeight == 2160) {
            type = "4K Ultra HD ";
        } else if (profile.videoFrameWidth == 1920 && profile.videoFrameHeight == 1080) {
            type = "Full HD ";
        } else if (profile.videoFrameWidth == 1280 && profile.videoFrameHeight == 720) {
            type = "HD ";
        } else if (profile.videoFrameWidth == 720 && profile.videoFrameHeight == 480) {
            type = "SD ";
        } else if (profile.videoFrameWidth == 640 && profile.videoFrameHeight == 480) {
            type = "VGA ";
        } else if (profile.videoFrameWidth == 352 && profile.videoFrameHeight == 288) {
            type = "CIF ";
        } else if (profile.videoFrameWidth == 320 && profile.videoFrameHeight == 240) {
            type = "QVGA ";
        } else if (profile.videoFrameWidth == 176 && profile.videoFrameHeight == 144) {
            type = "QCIF ";
        }
        return highest + type + profile.videoFrameWidth + "x" + profile.videoFrameHeight + " " + getAspectRatioMPString(profile.videoFrameWidth, profile.videoFrameHeight);
    }

    public double getTargetRatio() {
        return this.preview_targetRatio;
    }

    private double calculateTargetRatioForPreview(Point display_size) {
        double targetRatio;
        if (!this.applicationInterface.getPreviewSizePref().equals("preference_preview_size_wysiwyg")) {
            targetRatio = ((double) display_size.x) / ((double) display_size.y);
        } else {
            Size picture_size = this.camera_controller.getPictureSize();
            targetRatio = ((double) picture_size.width) / ((double) picture_size.height);
        }
        this.preview_targetRatio = targetRatio;
        return targetRatio;
    }

    private Size getClosestSize(List<Size> sizes, double targetRatio) {
        Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;
        for (Size size : sizes) {
            double ratio = ((double) size.width) / ((double) size.height);
            if (Math.abs(ratio - targetRatio) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(ratio - targetRatio);
            }
        }
        return optimalSize;
    }

    public Size getOptimalPreviewSize(List<Size> sizes) {
        if (sizes == null) {
            return null;
        }
        Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;
        Point display_size = new Point();
        ((Activity) getContext()).getWindowManager().getDefaultDisplay().getSize(display_size);
        double targetRatio = calculateTargetRatioForPreview(display_size);
        int targetHeight = Math.min(display_size.y, display_size.x);
        if (targetHeight <= 0) {
            targetHeight = display_size.y;
        }
        for (Size size : sizes) {
            if (Math.abs((((double) size.width) / ((double) size.height)) - targetRatio) <= 0.05d && ((double) Math.abs(size.height - targetHeight)) < minDiff) {
                optimalSize = size;
                minDiff = (double) Math.abs(size.height - targetHeight);
            }
        }
        if (optimalSize == null) {
            return getClosestSize(sizes, targetRatio);
        }
        return optimalSize;
    }

    public void setCameraDisplayOrientation() {
        if (this.camera_controller != null) {
            if (this.using_android_l) {
                configureTransform();
                return;
            }
            this.camera_controller.setDisplayOrientation(getDisplayRotationDegrees());
        }
    }

    private void onOrientationChanged(int orientation) {
        if (orientation != -1 && this.camera_controller != null) {
            int new_rotation;
            orientation = ((orientation + 45) / 90) * 90;
            this.current_orientation = orientation % 360;
            int camera_orientation = this.camera_controller.getCameraOrientation();
            if (this.camera_controller.isFrontFacing()) {
                new_rotation = ((camera_orientation - orientation) + 360) % 360;
            } else {
                new_rotation = (camera_orientation + orientation) % 360;
            }
            if (new_rotation != this.current_rotation) {
                this.current_rotation = new_rotation;
            }
        }
    }

    private int getDeviceDefaultOrientation() {
        WindowManager windowManager = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        Configuration config = getResources().getConfiguration();
        int rotation = windowManager.getDefaultDisplay().getRotation();
        if ((rotation == 0 || rotation == 2) && config.orientation == 2) {
            return 2;
        }
        if ((rotation == 1 || rotation == 3) && config.orientation == 1) {
            return 2;
        }
        return 1;
    }

    private int getImageVideoRotation() {
        String lock_orientation = this.applicationInterface.getLockOrientationPref();
        int camera_orientation;
        if (lock_orientation.equals("landscape")) {
            camera_orientation = this.camera_controller.getCameraOrientation();
            if (getDeviceDefaultOrientation() != 1) {
                return camera_orientation;
            }
            if (this.camera_controller.isFrontFacing()) {
                return (camera_orientation + 90) % 360;
            }
            return (camera_orientation + 270) % 360;
        } else if (!lock_orientation.equals("portrait")) {
            return this.current_rotation;
        } else {
            camera_orientation = this.camera_controller.getCameraOrientation();
            if (getDeviceDefaultOrientation() == 1) {
                return camera_orientation;
            }
            if (this.camera_controller.isFrontFacing()) {
                return (camera_orientation + 270) % 360;
            }
            return (camera_orientation + 90) % 360;
        }
    }

    public void draw(Canvas canvas) {
        if (!this.app_is_paused) {
            if (!(this.focus_success == 3 || this.focus_complete_time == -1 || System.currentTimeMillis() <= this.focus_complete_time + min_safe_restart_video_time)) {
                this.focus_success = 3;
            }
            this.applicationInterface.onDrawPreview(canvas);
        }
    }

    public void scaleZoom(float scale_factor) {
        if (this.camera_controller != null && this.has_zoom) {
            int zoom_factor = this.camera_controller.getZoom();
            float zoom_ratio = (((float) ((Integer) this.zoom_ratios.get(zoom_factor)).intValue()) / 100.0f) * scale_factor;
            int new_zoom_factor = zoom_factor;
            if (zoom_ratio <= PopupView.ALPHA_BUTTON_SELECTED) {
                new_zoom_factor = 0;
            } else if (zoom_ratio >= ((float) ((Integer) this.zoom_ratios.get(this.max_zoom_factor)).intValue()) / 100.0f) {
                new_zoom_factor = this.max_zoom_factor;
            } else if (scale_factor > PopupView.ALPHA_BUTTON_SELECTED) {
                int i = 0;
                for (i = zoom_factor; i < this.zoom_ratios.size(); i++) {
                    if (((float) ((Integer) this.zoom_ratios.get(i)).intValue()) / 100.0f >= zoom_ratio) {
                        new_zoom_factor = i;
                        break;
                    }
                }
            } else {
                int i = 0;
                for (i = zoom_factor; i >= 0; i--) {
                    if (((float) ((Integer) this.zoom_ratios.get(i)).intValue()) / 100.0f <= zoom_ratio) {
                        new_zoom_factor = i;
                        break;
                    }
                }
            }
            this.applicationInterface.multitouchZoom(new_zoom_factor);
        }
    }

    public void zoomTo(int new_zoom_factor) {
        if (new_zoom_factor < 0) {
            new_zoom_factor = 0;
        } else if (new_zoom_factor > this.max_zoom_factor) {
            new_zoom_factor = this.max_zoom_factor;
        }
        if (this.camera_controller != null && this.has_zoom) {
            this.camera_controller.setZoom(new_zoom_factor);
            this.applicationInterface.setZoomPref(new_zoom_factor);
            clearFocusAreas();
        }
    }

    public void setFocusDistance(float new_focus_distance) {
        if (this.camera_controller != null) {
            if (new_focus_distance < 0.0f) {
                new_focus_distance = 0.0f;
            } else if (new_focus_distance > this.minimum_focus_distance) {
                new_focus_distance = this.minimum_focus_distance;
            }
            if (this.camera_controller.setFocusDistance(new_focus_distance)) {
                String focus_distance_s;
                this.applicationInterface.setFocusDistancePref(new_focus_distance);
                if (new_focus_distance > 0.0f) {
                    focus_distance_s = this.decimal_format_2dp.format((double) (PopupView.ALPHA_BUTTON_SELECTED / new_focus_distance)) + getResources().getString(R.string.metres_abbreviation);
                } else {
                    focus_distance_s = getResources().getString(R.string.infinite);
                }
                showToast(this.seekbar_toast, getResources().getString(R.string.focus_distance) + " " + focus_distance_s);
            }
        }
    }

    private Resources getResources() {
        return this.cameraSurface.getView().getResources();
    }

    public void showToast(ToastBoxer clear_toast, String message) {
        showToast(clear_toast, message, 32);
    }

    private void showToast(ToastBoxer clear_toast, String message, int offset_y_dp) {
        if (this.applicationInterface.getShowToastsPref()) {
            final Activity activity = (Activity) getContext();
            final ToastBoxer toastBoxer = clear_toast;
            final String str = message;
            final int i = offset_y_dp;
            activity.runOnUiThread(new Runnable() {
                public void run() {
                    Toast toast;
                    if (toastBoxer == null || toastBoxer.toast == null || toastBoxer.toast != Preview.this.last_toast) {
                        if (!(toastBoxer == null || toastBoxer.toast == null)) {
                            toastBoxer.toast.cancel();
                        }
                        toast = new Toast(activity);
                        if (toastBoxer != null) {
                            toastBoxer.toast = toast;
                        }
                        toast.setView(new AnonymousClass1RotatedTextView(str, activity, i));
                    } else {
                        toast = toastBoxer.toast;
                        AnonymousClass1RotatedTextView view = (AnonymousClass1RotatedTextView) toast.getView();
                        view.setText(str);
                        view.invalidate();
                        toast.setView(view);
                    }
                    toast.setDuration(Toast.LENGTH_SHORT);
                    toast.show();
                    Preview.this.last_toast = toast;
                }
            });
        }
    }

    public void setExposure(int new_exposure) {
        if (this.camera_controller == null) {
            return;
        }
        if (this.min_exposure != 0 || this.max_exposure != 0) {
            cancelAutoFocus();
            if (new_exposure < this.min_exposure) {
                new_exposure = this.min_exposure;
            } else if (new_exposure > this.max_exposure) {
                new_exposure = this.max_exposure;
            }
            if (this.camera_controller.setExposureCompensation(new_exposure)) {
                this.applicationInterface.setExposureCompensationPref(new_exposure);
                showToast(this.seekbar_toast, getExposureCompensationString(new_exposure), 96);
            }
        }
    }

    public void setWhiteBalanceTemperature(int new_temperature) {
        if (this.camera_controller != null && this.camera_controller.setWhiteBalanceTemperature(new_temperature)) {
            this.applicationInterface.setWhiteBalanceTemperaturePref(new_temperature);
            showToast(this.seekbar_toast, getResources().getString(R.string.white_balance) + " " + new_temperature, 96);
        }
    }

    public void setISO(int new_iso) {
        if (this.camera_controller != null && this.supports_iso_range) {
            if (new_iso < this.min_iso) {
                new_iso = this.min_iso;
            } else if (new_iso > this.max_iso) {
                new_iso = this.max_iso;
            }
            if (this.camera_controller.setISO(new_iso)) {
                this.applicationInterface.setISOPref("" + new_iso);
                showToast(this.seekbar_toast, getISOString(new_iso), 96);
            }
        }
    }

    public String getISOString(int iso) {
        return getResources().getString(R.string.iso) + " " + iso;
    }

    public void setExposureTime(long new_exposure_time) {
        if (this.camera_controller != null && this.supports_exposure_time) {
            if (new_exposure_time < this.min_exposure_time) {
                new_exposure_time = this.min_exposure_time;
            } else if (new_exposure_time > this.max_exposure_time) {
                new_exposure_time = this.max_exposure_time;
            }
            if (this.camera_controller.setExposureTime(new_exposure_time)) {
                this.applicationInterface.setExposureTimePref(new_exposure_time);
                showToast(this.seekbar_toast, getExposureTimeString(new_exposure_time), 96);
            }
        }
    }

    public String getExposureTimeString(long exposure_time) {
        double exposure_time_s = ((double) exposure_time) / 1.0E9d;
        if (exposure_time >= 500000000) {
            return this.decimal_format_1dp.format(exposure_time_s) + getResources().getString(R.string.seconds_abbreviation);
        }
        return " 1/" + this.decimal_format_1dp.format(1.0d / exposure_time_s) + getResources().getString(R.string.seconds_abbreviation);
    }

    public String getExposureCompensationString(int exposure) {
        return getResources().getString(R.string.exposure_compensation) + " " + (exposure > 0 ? "+" : "") + this.decimal_format_2dp.format((double) (((float) exposure) * this.exposure_step)) + " EV";
    }

    public boolean canSwitchCamera() {
        if (this.phase == 2 || this.camera_controller_manager.getNumberOfCameras() == 0) {
            return false;
        }
        return true;
    }

    public void setCamera(int cameraId) {
        if (cameraId < 0 || cameraId >= this.camera_controller_manager.getNumberOfCameras()) {
            cameraId = 0;
        }
        if (canSwitchCamera()) {
            closeCamera();
            this.applicationInterface.setCameraIdPref(cameraId);
            openCamera();
        }
    }

    private void setPreviewFps() {
        CamcorderProfile profile = getCamcorderProfile();
        List<int[]> fps_ranges = this.camera_controller.getSupportedPreviewFpsRange();
        if (fps_ranges != null && fps_ranges.size() != 0) {
            int[] selected_fps;
            selected_fps = chooseBestPreviewFps(fps_ranges);
            this.camera_controller.setPreviewFpsRange(selected_fps[0], selected_fps[1]);
        }
    }


    private boolean focusIsVideo() {
        if (this.camera_controller != null) {
            return this.camera_controller.focusIsVideo();
        }
        return false;
    }

    private void setFocusPref(boolean auto_focus) {
        String focus_value = this.applicationInterface.getFocusPref(false);
        if (focus_value.length() <= 0) {
            updateFocus("focus_mode_continuous_picture", true, true, auto_focus);
        } else if (!updateFocus(focus_value, true, false, auto_focus)) {
            updateFocus(0, true, true, auto_focus);
        }
    }

    public String updateFocusForVideo() {
        if (this.supported_focus_values == null || this.camera_controller == null) {
            return null;
        }
        String old_focus_mode = getCurrentFocusValue();
        updateFocus("focus_mode_continuous_video", true, false, false);
        return old_focus_mode;
    }

    public String getErrorFeatures(CamcorderProfile profile) {
        boolean was_4k = false;
        boolean was_bitrate = false;
        boolean was_fps = false;
        if (profile.videoFrameWidth == 3840 && profile.videoFrameHeight == 2160 && this.applicationInterface.getForce4KPref()) {
            was_4k = true;
        }

        String features = "";
        if (!was_4k && !was_bitrate && !was_fps) {
            return features;
        }
        if (was_4k) {
            features = "4K UHD";
        }
        if (was_bitrate) {
            if (features.length() == 0) {
                features = "Bitrate";
            } else {
                features = features + "/Bitrate";
            }
        }
        if (!was_fps) {
            return features;
        }
        if (features.length() == 0) {
            return "Frame rate";
        }
        return features + "/Frame rate";
    }

    public void updateFlash(String focus_value) {
        if (this.phase != 2) {
            updateFlash(focus_value, true);
        }
    }

    private boolean updateFlash(String flash_value, boolean save) {
        if (this.supported_flash_values != null) {
            int new_flash_index = this.supported_flash_values.indexOf(flash_value);
            if (new_flash_index != -1) {
                updateFlash(new_flash_index, save);
                return true;
            }
        }
        return false;
    }

    private void updateFlash(int new_flash_index, boolean save) {
        if (this.supported_flash_values != null && new_flash_index != this.current_flash_index) {
            boolean initial = this.current_flash_index == -1;
            this.current_flash_index = new_flash_index;
            String[] flash_entries = getResources().getStringArray(R.array.flash_entries);
            String flash_value = (String) this.supported_flash_values.get(this.current_flash_index);
            String[] flash_values = getResources().getStringArray(R.array.flash_values);
            for (int i = 0; i < flash_values.length; i++) {
                if (flash_value.equals(flash_values[i])) {
                    if (!initial) {
                        showToast(this.flash_toast, flash_entries[i]);
                    }
                    setFlash(flash_value);
                    if (save) {
                        this.applicationInterface.setFlashPref(flash_value);
                    }
                }
            }
            setFlash(flash_value);
            if (save) {
                this.applicationInterface.setFlashPref(flash_value);
            }
        }
    }

    private void setFlash(String flash_value) {
        this.set_flash_value_after_autofocus = "";
        if (this.camera_controller != null) {
            cancelAutoFocus();
            this.camera_controller.setFlashValue(flash_value);
        }
    }

    public String getCurrentFlashValue() {
        if (this.current_flash_index == -1) {
            return null;
        }
        return (String) this.supported_flash_values.get(this.current_flash_index);
    }

    public void updateFocus(String focus_value, boolean quiet, boolean auto_focus) {
        if (this.phase != 2) {
            updateFocus(focus_value, quiet, true, auto_focus);
        }
    }

    private boolean supportedFocusValue(String focus_value) {
        if (this.supported_focus_values == null || this.supported_focus_values.indexOf(focus_value) == -1) {
            return false;
        }
        return true;
    }

    private boolean updateFocus(String focus_value, boolean quiet, boolean save, boolean auto_focus) {
        if (this.supported_focus_values != null) {
            int new_focus_index = this.supported_focus_values.indexOf(focus_value);
            if (new_focus_index != -1) {
                updateFocus(new_focus_index, quiet, save, auto_focus);
                return true;
            }
        }
        return false;
    }

    private String findEntryForValue(String value, int entries_id, int values_id) {
        String[] entries = getResources().getStringArray(entries_id);
        String[] values = getResources().getStringArray(values_id);
        for (int i = 0; i < values.length; i++) {
            if (value.equals(values[i])) {
                return entries[i];
            }
        }
        return null;
    }

    public String findFocusEntryForValue(String focus_value) {
        return findEntryForValue(focus_value, R.array.focus_mode_entries, R.array.focus_mode_values);
    }

    private void updateFocus(int new_focus_index, boolean quiet, boolean save, boolean auto_focus) {
        if (this.supported_focus_values != null && new_focus_index != this.current_focus_index) {
            this.current_focus_index = new_focus_index;
            String focus_value = (String) this.supported_focus_values.get(this.current_focus_index);
            if (!quiet) {
                String focus_entry = findFocusEntryForValue(focus_value);
                if (focus_entry != null) {
                    showToast(this.focus_toast, focus_entry);
                }
            }
            setFocusValue(focus_value, auto_focus);
            if (save) {
                this.applicationInterface.setFocusPref(focus_value, false);
            }
        }
    }

    public String getCurrentFocusValue() {
        if (this.camera_controller == null || this.supported_focus_values == null || this.current_focus_index == -1) {
            return null;
        }
        return this.supported_focus_values.get(this.current_focus_index);
    }

    private void setFocusValue(String focus_value, boolean auto_focus) {
        if (this.camera_controller != null) {
            cancelAutoFocus();
            removePendingContinuousFocusReset();
            this.autofocus_in_continuous_mode = false;
            this.camera_controller.setFocusValue(focus_value);
            setupContinuousFocusMove();
            clearFocusAreas();
            if (auto_focus && !focus_value.equals("focus_mode_locked")) {
                tryAutoFocus(false, false);
            }
        }
    }

    private void setupContinuousFocusMove() {
        if (this.continuous_focus_move_is_started) {
            this.continuous_focus_move_is_started = false;
            this.applicationInterface.onContinuousFocusMove(false);
        }
        String focus_value;
        if (this.current_focus_index != -1) {
            focus_value = (String) this.supported_focus_values.get(this.current_focus_index);
        } else {
            focus_value = null;
        }
        if (this.camera_controller != null && focus_value != null && focus_value.equals("focus_mode_continuous_picture")) {
            this.camera_controller.setContinuousFocusMoveCallback(new ContinuousFocusMoveCallback() {
                public void onContinuousFocusMove(boolean start) {
                    if (start != Preview.this.continuous_focus_move_is_started) {
                        Preview.this.continuous_focus_move_is_started = start;
                        Preview preview = Preview.this;
                        preview.count_cameraContinuousFocusMoving++;
                        Preview.this.applicationInterface.onContinuousFocusMove(start);
                    }
                }
            });
        } else if (this.camera_controller != null) {
            this.camera_controller.setContinuousFocusMoveCallback(null);
        }
    }

    public void toggleExposureLock() {
        if (this.camera_controller != null && this.is_exposure_lock_supported) {
            this.is_exposure_locked = !this.is_exposure_locked;
            cancelAutoFocus();
            this.camera_controller.setAutoExposureLock(this.is_exposure_locked);
        }
    }

    public void takePicturePressed() {
        if (this.camera_controller == null) {
            this.phase = 0;
        } else if (!this.has_surface) {
            this.phase = 0;
        } else if (isOnTimer()) {
            cancelTimer();
            showToast(this.take_photo_toast, (int) R.string.cancelled_timer);
        } else if (this.phase != 2) {
            startCameraPreview();
            long timer_delay = this.applicationInterface.getTimerPref();
            String burst_mode_value = this.applicationInterface.getRepeatPref();
            if (burst_mode_value.equals("unlimited")) {
                this.remaining_burst_photos = -1;
            } else {
                int n_burst;
                try {
                    n_burst = Integer.parseInt(burst_mode_value);
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                    n_burst = 1;
                }
                this.remaining_burst_photos = n_burst - 1;
            }
            if (timer_delay == 0) {
                takePicture(false);
            } else {
                takePictureOnTimer(timer_delay, false);
            }
        } else if (this.remaining_burst_photos != 0) {
            this.remaining_burst_photos = 0;
            showToast(this.take_photo_toast, (int) R.string.cancelled_burst_mode);
        }
    }

    private void takePictureOnTimer(final long timer_delay, boolean repeated) {
        this.phase = 1;
        this.take_photo_time = System.currentTimeMillis() + timer_delay;
        Timer timer = this.takePictureTimer;
        TimerTask anonymousClass1TakePictureTimerTask = new TimerTask() {
            public void run() {
                if (Preview.this.beepTimerTask != null) {
                    Preview.this.beepTimerTask.cancel();
                    Preview.this.beepTimerTask = null;
                }
                ((Activity) Preview.this.getContext()).runOnUiThread(new Runnable() {
                    public void run() {
                        if (Preview.this.camera_controller != null && Preview.this.takePictureTimerTask != null) {
                            Preview.this.takePicture(false);
                        }
                    }
                });
            }
        };
        this.takePictureTimerTask = anonymousClass1TakePictureTimerTask;
        timer.schedule(anonymousClass1TakePictureTimerTask, timer_delay);
        timer = this.beepTimer;
        anonymousClass1TakePictureTimerTask = new TimerTask() {
            long remaining_time = timer_delay;

            public void run() {
                if (this.remaining_time > 0) {
                    Preview.this.applicationInterface.timerBeep(this.remaining_time);
                }
                this.remaining_time -= Preview.min_safe_restart_video_time;
            }
        };
        this.beepTimerTask = anonymousClass1TakePictureTimerTask;
        timer.schedule(anonymousClass1TakePictureTimerTask, 0, min_safe_restart_video_time);
    }

    private void takePicture(boolean max_filesize_restart) {
        this.phase = 2;
        synchronized (this) {
            this.take_photo_after_autofocus = false;
        }
        if (this.camera_controller == null) {
            this.phase = 0;
            this.applicationInterface.cameraInOperation(false);
        } else if (!this.has_surface) {
            this.phase = 0;
            this.applicationInterface.cameraInOperation(false);
        } else {
            takePhoto(false);
        }
    }

    private void takePhoto(boolean skip_autofocus) {
        this.applicationInterface.cameraInOperation(true);
        String current_ui_focus_value = getCurrentFocusValue();
        if (this.autofocus_in_continuous_mode) {
            synchronized (this) {
                if (this.focus_success == 0) {
                    this.take_photo_after_autofocus = true;
                    this.camera_controller.setCaptureFollowAutofocusHint(true);
                } else {
                    takePhotoWhenFocused();
                }
            }
        } else if (this.camera_controller.focusIsContinuous()) {
            this.camera_controller.autoFocus(new AutoFocusCallback() {
                public void onAutoFocus(boolean success) {
                    Preview.this.takePhotoWhenFocused();
                }
            }, true);
        } else if (skip_autofocus || recentlyFocused()) {
            takePhotoWhenFocused();
        } else if (current_ui_focus_value == null || !(current_ui_focus_value.equals("focus_mode_auto") || current_ui_focus_value.equals("focus_mode_macro"))) {
            takePhotoWhenFocused();
        } else {
            synchronized (this) {
                if (this.focus_success == 0) {
                    this.take_photo_after_autofocus = true;
                    this.camera_controller.setCaptureFollowAutofocusHint(true);
                } else {
                    this.focus_success = 3;
                    this.camera_controller.autoFocus(new AutoFocusCallback() {
                        public void onAutoFocus(boolean success) {
                            Preview.this.ensureFlashCorrect();
                            Preview.this.prepareAutoFocusPhoto();
                            Preview.this.takePhotoWhenFocused();
                        }
                    }, true);
                    this.count_cameraAutoFocus++;
                }
            }
        }
    }

    private void prepareAutoFocusPhoto() {
        if (this.using_android_l) {
            String flash_value = this.camera_controller.getFlashValue();
            if (flash_value.length() <= 0) {
                return;
            }
            if (flash_value.equals("flash_auto") || flash_value.equals("flash_red_eye")) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void takePhotoWhenFocused() {
        if (this.camera_controller == null) {
            this.phase = 0;
            this.applicationInterface.cameraInOperation(false);
        } else if (this.has_surface) {
            final String focus_value = this.current_focus_index != -1 ? (String) this.supported_focus_values.get(this.current_focus_index) : null;
            if (focus_value != null && focus_value.equals("focus_mode_locked") && this.focus_success == 0) {
                cancelAutoFocus();
            }
            removePendingContinuousFocusReset();
            updateParametersFromLocation();
            this.focus_success = 3;
            this.successfully_focused = false;
            PictureCallback pictureCallback = new PictureCallback() {
                private Date current_date = null;
                private boolean has_date = false;
                private boolean success = false;

                public void onBurstPictureTaken(List<byte[]> images) {
                    initDate();
                    this.success = true;
                    if (!Preview.this.applicationInterface.onBurstPictureTaken(images, this.current_date)) {
                        this.success = false;
                    }
                }

                public void onCompleted() {
                    Preview.this.applicationInterface.onPictureCompleted();
                    if (!Preview.this.using_android_l) {
                        Preview.this.is_preview_started = false;
                    }
                    Preview.this.phase = 0;
                    if (Preview.this.remaining_burst_photos != -1 && Preview.this.remaining_burst_photos <= 0) {
                        Preview.this.phase = 0;
                        if (Preview.this.applicationInterface.getPausePreviewPref() && this.success) {
                            if (Preview.this.is_preview_started) {
                                Preview.this.camera_controller.stopPreview();
                                Preview.this.is_preview_started = false;
                            }
                            Preview.this.setPreviewPaused(true);
                        } else {
                            if (!Preview.this.is_preview_started) {
                                Preview.this.startCameraPreview();
                            }
                            Preview.this.applicationInterface.cameraInOperation(false);
                        }
                    } else if (!Preview.this.is_preview_started) {
                        Preview.this.startCameraPreview();
                    }
                    Preview.this.continuousFocusReset();
                    if (!(Preview.this.camera_controller == null || focus_value == null || (!focus_value.equals("focus_mode_continuous_picture") && !focus_value.equals("focus_mode_continuous_video")))) {
                        Preview.this.camera_controller.cancelAutoFocus();
                    }
                    if (Preview.this.remaining_burst_photos == -1 || Preview.this.remaining_burst_photos > 0) {
                        if (Preview.this.remaining_burst_photos > 0) {
                            Preview.this.remaining_burst_photos = Preview.this.remaining_burst_photos - 1;
                        }
                        long timer_delay = Preview.this.applicationInterface.getRepeatIntervalPref();
                        if (timer_delay == 0) {
                            Preview.this.phase = 2;
                            Preview.this.takePhoto(true);
                            return;
                        }
                        Preview.this.takePictureOnTimer(timer_delay, true);
                    }
                }

                public void onFrontScreenTurnOn() {
                    Preview.this.applicationInterface.turnFrontScreenFlashOn();
                }

                public void onPictureTaken(byte[] data) {
                    initDate();
                    if (Preview.this.applicationInterface.onPictureTaken(data, this.current_date)) {
                        this.success = true;
                    } else {
                        this.success = false;
                    }
                }

                private void initDate() {
                    if (!this.has_date) {
                        this.has_date = true;
                        this.current_date = new Date();
                    }
                }

                public void onRawPictureTaken(DngCreator dngCreator, Image image) {
                    initDate();
                    if (!Preview.this.applicationInterface.onRawPictureTaken(dngCreator, image, this.current_date)) {
                    }
                }

                public void onStarted() {
                    Preview.this.applicationInterface.onCaptureStarted();
                }
            };
            ErrorCallback errorCallback = new ErrorCallback() {
                public void onError() {
                    Preview preview = Preview.this;
                    preview.count_cameraTakePicture--;
                    Preview.this.applicationInterface.onPhotoError();
                    Preview.this.phase = 0;
                    Preview.this.startCameraPreview();
                    Preview.this.applicationInterface.cameraInOperation(false);
                }
            };
            this.camera_controller.setRotation(getImageVideoRotation());
            this.camera_controller.enableShutterSound(this.applicationInterface.getShutterSoundPref());
            if (this.using_android_l) {
                this.camera_controller.setUseExpoFastBurst(this.applicationInterface.useCamera2FastBurst());
            }
            this.camera_controller.takePicture(pictureCallback, errorCallback);
            this.count_cameraTakePicture++;
        } else {
            this.phase = 0;
            this.applicationInterface.cameraInOperation(false);
        }
    }

    public void requestAutoFocus() {
        cancelAutoFocus();
        tryAutoFocus(false, true);
    }

    private void tryAutoFocus(boolean startup, final boolean manual) {
        if (this.camera_controller == null || !this.has_surface || !this.is_preview_started) {
            return;
        }
        if (!isTakingPhotoOrOnTimer()) {
            if (manual) {
                removePendingContinuousFocusReset();
            }
            if (manual && this.camera_controller.focusIsContinuous() && supportedFocusValue("focus_mode_auto")) {
                this.camera_controller.setFocusValue("focus_mode_auto");
                this.autofocus_in_continuous_mode = true;
            }
            if (this.camera_controller.supportsAutoFocus()) {
                if (!this.using_android_l) {
                    this.set_flash_value_after_autofocus = "";
                    String old_flash_value = this.camera_controller.getFlashValue();
                    if (startup && old_flash_value.length() > 0 && !old_flash_value.equals("flash_off") && !old_flash_value.equals("flash_torch")) {
                        this.set_flash_value_after_autofocus = old_flash_value;
                        this.camera_controller.setFlashValue("flash_off");
                    }
                }
                AutoFocusCallback autoFocusCallback = new AutoFocusCallback() {
                    public void onAutoFocus(boolean success) {
                        Preview.this.autoFocusCompleted(manual, success, false);
                    }
                };
                this.focus_success = 0;
                this.focus_complete_time = -1;
                this.successfully_focused = false;
                this.camera_controller.autoFocus(autoFocusCallback, false);
                this.count_cameraAutoFocus++;
                this.focus_started_time = System.currentTimeMillis();
            } else if (this.has_focus_area) {
                this.focus_success = 1;
                this.focus_complete_time = System.currentTimeMillis();
            }
        }
    }

    private void removePendingContinuousFocusReset() {
        if (this.reset_continuous_focus_runnable != null) {
            this.reset_continuous_focus_handler.removeCallbacks(this.reset_continuous_focus_runnable);
            this.reset_continuous_focus_runnable = null;
        }
    }

    private void continuousFocusReset() {
        if (this.camera_controller != null && this.autofocus_in_continuous_mode) {
            this.autofocus_in_continuous_mode = false;
            String current_ui_focus_value = getCurrentFocusValue();
            if (current_ui_focus_value != null && !this.camera_controller.getFocusValue().equals(current_ui_focus_value) && this.camera_controller.getFocusValue().equals("focus_mode_auto")) {
                this.camera_controller.cancelAutoFocus();
                this.camera_controller.setFocusValue(current_ui_focus_value);
            }
        }
    }

    private void cancelAutoFocus() {
        if (this.camera_controller != null) {
            this.camera_controller.cancelAutoFocus();
            autoFocusCompleted(false, false, true);
        }
    }

    private void ensureFlashCorrect() {
        if (this.set_flash_value_after_autofocus.length() > 0 && this.camera_controller != null) {
            this.camera_controller.setFlashValue(this.set_flash_value_after_autofocus);
            this.set_flash_value_after_autofocus = "";
        }
    }

    private void autoFocusCompleted(boolean manual, boolean success, boolean cancelled) {
        if (cancelled) {
            this.focus_success = 3;
        } else {
            this.focus_success = success ? 1 : 2;
            this.focus_complete_time = System.currentTimeMillis();
        }
        if (manual && !cancelled && (success || this.applicationInterface.isTestAlwaysFocus())) {
            this.successfully_focused = true;
            this.successfully_focused_time = this.focus_complete_time;
        }
        if (manual && this.camera_controller != null && this.autofocus_in_continuous_mode) {
            String current_ui_focus_value = getCurrentFocusValue();
            if (!(current_ui_focus_value == null || this.camera_controller.getFocusValue().equals(current_ui_focus_value) || !this.camera_controller.getFocusValue().equals("focus_mode_auto"))) {
                this.reset_continuous_focus_runnable = new Runnable() {
                    public void run() {
                        Preview.this.reset_continuous_focus_runnable = null;
                        Preview.this.continuousFocusReset();
                    }
                };
                this.reset_continuous_focus_handler.postDelayed(this.reset_continuous_focus_runnable, 3000);
            }
        }
        ensureFlashCorrect();
        if (!(!this.using_face_detection || cancelled || this.camera_controller == null)) {
            this.camera_controller.cancelAutoFocus();
        }
        synchronized (this) {
            if (this.take_photo_after_autofocus) {
                this.take_photo_after_autofocus = false;
                prepareAutoFocusPhoto();
                takePhotoWhenFocused();
            }
        }
    }

    public void startCameraPreview() {
        if (!(this.camera_controller == null || isTakingPhotoOrOnTimer() || this.is_preview_started)) {
            this.camera_controller.setRecordingHint(false);
            setPreviewFps();
            try {
                this.camera_controller.startPreview();
                this.count_cameraStartPreview++;
                this.is_preview_started = true;
                if (this.using_face_detection) {
                    this.camera_controller.startFaceDetection();
                    this.faces_detected = null;
                }
            } catch (CameraControllerException e) {
                e.printStackTrace();
                this.applicationInterface.onFailedStartPreview();
                return;
            }
        }
        setPreviewPaused(false);
        setupContinuousFocusMove();
    }

    public void onAccelerometerSensorChanged(SensorEvent event) {
        this.has_gravity = true;
        for (int i = 0; i < 3; i++) {
            this.gravity[i] = (sensor_alpha * this.gravity[i]) + (0.19999999f * event.values[i]);
        }
        calculateGeoDirection();
        double x = (double) this.gravity[0];
        double y = (double) this.gravity[1];
        double z = (double) this.gravity[2];
        double mag = Math.sqrt(((x * x) + (y * y)) + (z * z));
        this.has_pitch_angle = false;
        if (mag > 1.0E-8d) {
            this.has_pitch_angle = true;
            this.pitch_angle = (Math.asin((-z) / mag) * 180.0d) / 3.141592653589793d;
            if (this.is_test || Math.abs(this.pitch_angle) <= 70.0d) {
                this.has_level_angle = true;
                this.natural_level_angle = (Math.atan2(-x, y) * 180.0d) / 3.141592653589793d;
                if (this.natural_level_angle < -0.0d) {
                    this.natural_level_angle += 360.0d;
                }
                updateLevelAngles();
                return;
            }
            this.has_level_angle = false;
            return;
        }
        Log.e(TAG, "accel sensor has zero mag: " + mag);
        this.has_level_angle = false;
    }

    private void calculateGeoDirection() {
        if (this.has_gravity && this.has_geomagnetic && SensorManager.getRotationMatrix(this.deviceRotation, this.deviceInclination, this.gravity, this.geomagnetic)) {
            SensorManager.remapCoordinateSystem(this.deviceRotation, 1, 3, this.cameraRotation);
            boolean has_old_geo_direction = this.has_geo_direction;
            this.has_geo_direction = true;
            SensorManager.getOrientation(this.cameraRotation, this.new_geo_direction);
            for (int i = 0; i < 3; i++) {
                float old_compass = (float) Math.toDegrees((double) this.geo_direction[i]);
                float new_compass = (float) Math.toDegrees((double) this.new_geo_direction[i]);
                if (has_old_geo_direction) {
                    old_compass = lowPassFilter(old_compass, new_compass, 0.1f, 10.0f);
                } else {
                    old_compass = new_compass;
                }
                this.geo_direction[i] = (float) Math.toRadians((double) old_compass);
            }
        }
    }

    public void updateLevelAngles() {
        if (this.has_level_angle) {
            this.level_angle = this.natural_level_angle;
            this.level_angle -= this.applicationInterface.getCalibratedLevelAngle();
            this.orig_level_angle = this.level_angle;
            this.level_angle -= (double) ((float) this.current_orientation);
            if (this.level_angle < -180.0d) {
                this.level_angle += 360.0d;
            } else if (this.level_angle > 180.0d) {
                this.level_angle -= 360.0d;
            }
        }
    }

    private float lowPassFilter(float old_value, float new_value, float smoothFactorCompass, float smoothThresholdCompass) {
        float diff = Math.abs(new_value - old_value);
        if (diff < 180.0f) {
            if (diff > smoothThresholdCompass) {
                return new_value;
            }
            return old_value + ((new_value - old_value) * smoothFactorCompass);
        } else if (360.0d - ((double) diff) > ((double) smoothThresholdCompass)) {
            return new_value;
        } else {
            if (old_value > new_value) {
                return ((((((360.0f + new_value) - old_value) % 360.0f) * smoothFactorCompass) + old_value) + 360.0f) % 360.0f;
            }
            return ((old_value - ((((360.0f - new_value) + old_value) % 360.0f) * smoothFactorCompass)) + 360.0f) % 360.0f;
        }
    }

    public boolean hasLevelAngle() {
        return this.has_level_angle;
    }

    public double getLevelAngleUncalibrated() {
        return this.natural_level_angle - ((double) this.current_orientation);
    }

    public double getLevelAngle() {
        return this.level_angle;
    }

    public double getOrigLevelAngle() {
        return this.orig_level_angle;
    }

    public boolean hasPitchAngle() {
        return this.has_pitch_angle;
    }

    public double getPitchAngle() {
        return this.pitch_angle;
    }

    public void onMagneticSensorChanged(SensorEvent event) {
        this.has_geomagnetic = true;
        for (int i = 0; i < 3; i++) {
            this.geomagnetic[i] = (sensor_alpha * this.geomagnetic[i]) + (0.19999999f * event.values[i]);
        }
        calculateGeoDirection();
    }

    public boolean hasGeoDirection() {
        return this.has_geo_direction;
    }

    public double getGeoDirection() {
        return (double) this.geo_direction[0];
    }

    public boolean supportsFaceDetection() {
        return this.supports_face_detection;
    }

    public boolean supportsVideoStabilization() {
        return this.supports_video_stabilization;
    }

    public boolean canDisableShutterSound() {
        return this.can_disable_shutter_sound;
    }

    public List<String> getSupportedColorEffects() {
        return this.color_effects;
    }

    public List<String> getSupportedSceneModes() {
        return this.scene_modes;
    }

    public List<String> getSupportedWhiteBalances() {
        return this.white_balances;
    }

    public String getISOKey() {
        return this.camera_controller == null ? "" : this.camera_controller.getISOKey();
    }

    public boolean supportsWhiteBalanceTemperature() {
        return this.supports_white_balance_temperature;
    }

    public int getMinimumWhiteBalanceTemperature() {
        return this.min_temperature;
    }

    public int getMaximumWhiteBalanceTemperature() {
        return this.max_temperature;
    }

    public boolean supportsISORange() {
        return this.supports_iso_range;
    }

    public List<String> getSupportedISOs() {
        return this.isos;
    }

    public int getMinimumISO() {
        return this.min_iso;
    }

    public int getMaximumISO() {
        return this.max_iso;
    }

    public float getMinimumFocusDistance() {
        return this.minimum_focus_distance;
    }

    public boolean supportsExposureTime() {
        return this.supports_exposure_time;
    }

    public long getMinimumExposureTime() {
        return this.min_exposure_time;
    }

    public long getMaximumExposureTime() {
        return this.max_exposure_time;
    }

    public boolean supportsExposures() {
        return this.exposures != null;
    }

    public int getMinimumExposure() {
        return this.min_exposure;
    }

    public int getMaximumExposure() {
        return this.max_exposure;
    }

    public int getCurrentExposure() {
        if (this.camera_controller == null) {
            return 0;
        }
        return this.camera_controller.getExposureCompensation();
    }

    public boolean supportsExpoBracketing() {
        return this.supports_expo_bracketing;
    }

    public int maxExpoBracketingNImages() {
        return this.max_expo_bracketing_n_images;
    }

    public boolean supportsRaw() {
        return this.supports_raw;
    }

    public float getViewAngleX() {
        return this.view_angle_x;
    }

    public float getViewAngleY() {
        return this.view_angle_y;
    }

    public List<Size> getSupportedPreviewSizes() {
        return this.supported_preview_sizes;
    }

    public Size getCurrentPreviewSize() {
        return new Size(this.preview_w, this.preview_h);
    }

    public List<Size> getSupportedPictureSizes() {
        return this.sizes;
    }

    public int getCurrentPictureSizeIndex() {
        return this.current_size_index;
    }

    public Size getCurrentPictureSize() {
        if (this.current_size_index == -1 || this.sizes == null) {
            return null;
        }
        return (Size) this.sizes.get(this.current_size_index);
    }

    public List<String> getSupportedFlashValues() {
        return this.supported_flash_values;
    }

    public List<String> getSupportedFocusValues() {
        return this.supported_focus_values;
    }

    public int getCameraId() {
        if (this.camera_controller == null) {
            return 0;
        }
        return this.camera_controller.getCameraId();
    }

    public String getCameraAPI() {
        if (this.camera_controller == null) {
            return "None";
        }
        return this.camera_controller.getAPI();
    }

    public void onResume() {
        this.app_is_paused = false;
        this.cameraSurface.onResume();
        if (this.canvasView != null) {
            this.canvasView.onResume();
        }
        openCamera();
    }

    public void onPause() {
        this.app_is_paused = true;
        closeCamera();
        this.cameraSurface.onPause();
        if (this.canvasView != null) {
            this.canvasView.onPause();
        }
    }

    public void onSaveInstanceState(Bundle state) {
    }

    public void showToast(ToastBoxer clear_toast, int message_id) {
        showToast(clear_toast, getResources().getString(message_id));
    }

    public int getUIRotation() {
        return this.ui_rotation;
    }

    public void setUIRotation(int ui_rotation) {
        this.ui_rotation = ui_rotation;
    }

    private void updateParametersFromLocation() {
        if (this.camera_controller == null) {
            return;
        }
    }

    public boolean isTakingPhoto() {
        return this.phase == 2;
    }

    public boolean usingCamera2API() {
        return this.using_android_l;
    }

    public CameraController getCameraController() {
        return this.camera_controller;
    }

    public CameraControllerManager getCameraControllerManager() {
        return this.camera_controller_manager;
    }

    public boolean supportsFocus() {
        return this.supported_focus_values != null;
    }

    public boolean supportsFlash() {
        return this.supported_flash_values != null;
    }

    public boolean supportsExposureLock() {
        return this.is_exposure_lock_supported;
    }

    public boolean isExposureLocked() {
        return this.is_exposure_locked;
    }

    public boolean supportsZoom() {
        return this.has_zoom;
    }

    public int getMaxZoom() {
        return this.max_zoom_factor;
    }

    public boolean hasFocusArea() {
        return this.has_focus_area;
    }

    public Pair<Integer, Integer> getFocusPos() {
        return new Pair(Integer.valueOf(this.focus_screen_x), Integer.valueOf(this.focus_screen_y));
    }

    public int getMaxNumFocusAreas() {
        return this.max_num_focus_areas;
    }

    public boolean isTakingPhotoOrOnTimer() {
        return this.phase == 2 || this.phase == 1;
    }

    public boolean isOnTimer() {
        return this.phase == 1;
    }

    public long getTimerEndTime() {
        return this.take_photo_time;
    }

    public boolean isPreviewPaused() {
        return this.phase == 3;
    }

    private void setPreviewPaused(boolean paused) {
        if (paused) {
            this.phase = 3;
            return;
        }
        this.phase = 0;
        this.applicationInterface.cameraInOperation(false);
    }

    public boolean isPreviewStarted() {
        return this.is_preview_started;
    }

    public boolean isFocusWaiting() {
        return this.focus_success == 0;
    }

    public boolean isFocusRecentSuccess() {
        return this.focus_success == 1;
    }

    public long timeSinceStartedAutoFocus() {
        if (this.focus_started_time != -1) {
            return System.currentTimeMillis() - this.focus_started_time;
        }
        return 0;
    }

    public boolean isFocusRecentFailure() {
        return this.focus_success == 2;
    }

    private boolean recentlyFocused() {
        return this.successfully_focused && System.currentTimeMillis() < this.successfully_focused_time + 5000;
    }

    public Face[] getFacesDetected() {
        return this.faces_detected;
    }

    public float getZoomRatio() {
        if (this.zoom_ratios == null) {
            return PopupView.ALPHA_BUTTON_SELECTED;
        }
        return ((float) ((Integer) this.zoom_ratios.get(this.camera_controller.getZoom())).intValue()) / 100.0f;
    }

    class AnonymousClass1RotatedTextView extends View {
        final /* synthetic */ int val$offset_y_dp;
        private final Rect bounds = new Rect();
        private final Paint paint = new Paint();
        private final RectF rect = new RectF();
        private final Rect sub_bounds = new Rect();
        private String[] lines;

        public AnonymousClass1RotatedTextView(String text, Context context, int i) {
            super(context);
            this.val$offset_y_dp = i;
            this.lines = text.split("\n");
        }

        void setText(String text) {
            this.lines = text.split("\n");
        }

        protected void onDraw(Canvas canvas) {
            float scale = Preview.this.getResources().getDisplayMetrics().density;
            this.paint.setTextSize((14.0f * scale) + 0.5f);
            this.paint.setShadowLayer(PopupView.ALPHA_BUTTON_SELECTED, 0.0f, PopupView.ALPHA_BUTTON_SELECTED, ViewCompat.MEASURED_STATE_MASK);
            boolean first_line = true;
            for (String line : this.lines) {
                this.paint.getTextBounds(line, 0, line.length(), this.sub_bounds);
                if (first_line) {
                    this.bounds.set(this.sub_bounds);
                    first_line = false;
                } else {
                    this.bounds.top = Math.min(this.sub_bounds.top, this.bounds.top);
                    this.bounds.bottom = Math.max(this.sub_bounds.bottom, this.bounds.bottom);
                    this.bounds.left = Math.min(this.sub_bounds.left, this.bounds.left);
                    this.bounds.right = Math.max(this.sub_bounds.right, this.bounds.right);
                }
            }
            String reference_text = "Ap";
            this.paint.getTextBounds("Ap", 0, "Ap".length(), this.sub_bounds);
            this.bounds.top = this.sub_bounds.top;
            this.bounds.bottom = this.sub_bounds.bottom;
            int height = this.bounds.bottom - this.bounds.top;
            Rect rect = this.bounds;
            rect.bottom += ((this.lines.length - 1) * height) / 2;
            rect = this.bounds;
            rect.top -= ((this.lines.length - 1) * height) / 2;
            int padding = (int) ((14.0f * scale) + 0.5f);
            int offset_y = (int) ((((float) this.val$offset_y_dp) * scale) + 0.5f);
            canvas.save();
            canvas.rotate((float) Preview.this.ui_rotation, ((float) canvas.getWidth()) / 2.0f, ((float) canvas.getHeight()) / 2.0f);
            this.rect.left = (float) ((((canvas.getWidth() / 2) - (this.bounds.width() / 2)) + this.bounds.left) - padding);
            this.rect.top = (float) ((((canvas.getHeight() / 2) + this.bounds.top) - padding) + offset_y);
            this.rect.right = (float) ((((canvas.getWidth() / 2) - (this.bounds.width() / 2)) + this.bounds.right) + padding);
            this.rect.bottom = (float) ((((canvas.getHeight() / 2) + this.bounds.bottom) + padding) + offset_y);
            this.paint.setStyle(Style.FILL);
            this.paint.setColor(Color.rgb(50, 50, 50));
            float radius = (24.0f * scale) + 0.5f;
            canvas.drawRoundRect(this.rect, radius, radius, this.paint);
            this.paint.setColor(-1);
            int ypos = ((canvas.getHeight() / 2) + offset_y) - (((this.lines.length - 1) * height) / 2);
            for (String line2 : this.lines) {
                canvas.drawText(line2, (float) ((canvas.getWidth() / 2) - (this.bounds.width() / 2)), (float) ypos, this.paint);
                ypos += height;
            }
            canvas.restore();
        }
    }

    private class DoubleTapListener extends SimpleOnGestureListener {
        private DoubleTapListener() {
        }

        public boolean onDoubleTap(MotionEvent e) {
            return Preview.this.onDoubleTap();
        }
    }

    private class ScaleListener extends SimpleOnScaleGestureListener {
        private ScaleListener() {
        }

        public boolean onScale(ScaleGestureDetector detector) {
            if (Preview.this.camera_controller != null && Preview.this.has_zoom) {
                Preview.this.scaleZoom(detector.getScaleFactor());
            }
            return true;
        }
    }
}
