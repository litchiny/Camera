package com.litchiny.camera;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog.Builder;
import android.app.KeyguardManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioAttributes;
import android.media.CamcorderProfile;
import android.media.SoundPool;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video.Thumbnails;
import android.renderscript.RenderScript;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.ViewCompat;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.KeyEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.View.OnSystemUiVisibilityChangeListener;
import android.view.ViewGroup;
import android.view.WindowManager.LayoutParams;

import com.litchiny.camera.controller.CameraController;
import com.litchiny.camera.controller.CameraController.Size;
import com.litchiny.camera.controller.CameraControllerManager2;
import com.litchiny.camera.ui.MainUI;
import com.litchiny.camera.ui.PopupView;
import com.litchiny.camera.ui.Preview;
import com.litchiny.utils.MainUtil;
import com.litchiny.utils.SPUtil;
import com.lwyy.camera.R;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private final ToastBoxer changed_auto_stabilise_toast = new ToastBoxer();
    private final Map<Integer, Bitmap> preloaded_bitmap_resources = new Hashtable();
    private final ToastBoxer switch_video_toast = new ToastBoxer();
    public volatile Bitmap gallery_bitmap;
    public boolean is_test;
    public volatile float test_angle;
    public volatile boolean test_have_angle;
    public volatile String test_last_saved_image;
    public volatile boolean test_low_memory;
    private MyApplicationInterface applicationInterface;
    private int audio_noise_sensitivity = -1;
    private boolean block_startup_toast = false;
    private boolean camera_in_background;
    private ValueAnimator gallery_save_anim;
    private Handler immersive_timer_handler = null;
    private Runnable immersive_timer_runnable = null;
    private int last_level = -1;
    private Sensor mSensorAccelerometer;
    private Sensor mSensorMagnetic;
    private SensorManager mSensorManager;
    private MainUI mainUI;
    private OrientationEventListener orientationEventListener;
    private Preview preview;
    private final SensorEventListener accelerometerListener = new SensorEventListener() {
        public void onSensorChanged(SensorEvent event) {
            MainActivity.this.preview.onAccelerometerSensorChanged(event);
        }        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };
    private final SensorEventListener magneticListener = new SensorEventListener() {
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }

        public void onSensorChanged(SensorEvent event) {
            MainActivity.this.preview.onMagneticSensorChanged(event);
        }
    };
    private boolean saf_dialog_from_preferences;
    private SparseIntArray sound_ids;
    private SoundPool sound_pool;
    private boolean supports_auto_stabilise;
    private boolean supports_camera2;
    private TextFormatter textFormatter;
    private TextToSpeech textToSpeech;
    private boolean textToSpeechSuccess;
    private long time_last_audio_trigger_photo = -1;
    private long time_quiet_loud = -1;


    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences sharedPreferences;
        ActivityManager activityManager;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (!(getIntent() == null || getIntent().getExtras() == null)) {
            this.is_test = getIntent().getExtras().getBoolean("test_project");
        }
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager.getLargeMemoryClass() >= 128) {
            this.supports_auto_stabilise = true;
        }

        this.mainUI = new MainUI(this);
        this.applicationInterface = new MyApplicationInterface(this, savedInstanceState);
        this.textFormatter = new TextFormatter(this);
        initCamera2Support();
        setWindowFlagsForCamera();

        this.mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (this.mSensorManager.getDefaultSensor(1) != null) {
            this.mSensorAccelerometer = this.mSensorManager.getDefaultSensor(1);
        }
        if (this.mSensorManager.getDefaultSensor(2) != null) {
            this.mSensorMagnetic = this.mSensorManager.getDefaultSensor(2);
        }
        this.preview = new Preview(this.applicationInterface, (ViewGroup) findViewById(R.id.preview));
        findViewById(R.id.switch_camera).setVisibility(this.preview.getCameraControllerManager().getNumberOfCameras() > 1 ? View.VISIBLE : View.GONE);
        this.orientationEventListener = new OrientationEventListener(this) {
            public void onOrientationChanged(int orientation) {
                MainActivity.this.mainUI.onOrientationChanged(orientation);
            }
        };

        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(new OnSystemUiVisibilityChangeListener() {
            public void onSystemUiVisibilityChange(int visibility) {
                if (!MainUtil.usingKitKatImmersiveMode(MainActivity.this)) {
                    return;
                }
                if ((visibility & 4) == 0) {
                    MainActivity.this.mainUI.setImmersiveMode(false);
                    MainActivity.this.setImmersiveTimer();
                    return;
                }
                MainActivity.this.mainUI.setImmersiveMode(true);
            }
        });
        boolean has_done_first_time = sharedPreferences.contains(PreferenceKeys.getFirstTimePreferenceKey());
        if (!has_done_first_time) {
            SPUtil.setDeviceDefaults(this);
        }

        setModeFromIntents(savedInstanceState);
        this.textToSpeechSuccess = false;
        new Thread(new Runnable() {
            public void run() {
                MainActivity.this.textToSpeech = new TextToSpeech(MainActivity.this, new OnInitListener() {
                    public void onInit(int status) {
                        if (status == 0) {
                            MainActivity.this.textToSpeechSuccess = true;
                        }
                    }
                });
            }
        }).start();
    }

    protected void onResume() {
        super.onResume();
        getWindow().getDecorView().getRootView().setBackgroundColor(ViewCompat.MEASURED_STATE_MASK);
        this.mSensorManager.registerListener(this.accelerometerListener, this.mSensorAccelerometer, 3);
        this.mSensorManager.registerListener(this.magneticListener, this.mSensorMagnetic, 3);
        this.orientationEventListener.enable();
        initSound();
        loadSound(R.raw.beep);
        loadSound(R.raw.beep_hi);
        this.mainUI.layoutUI();
        updateGalleryIcon();
        this.preview.onResume();
    }

    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (this.preview != null) {
            this.preview.onSaveInstanceState(state);
        }
        if (this.applicationInterface != null) {
            this.applicationInterface.onSaveInstanceState(state);
        }
    }

    protected void onPause() {
        waitUntilImageQueueEmpty();
        super.onPause();
        this.mSensorManager.unregisterListener(this.accelerometerListener);
        this.mSensorManager.unregisterListener(this.magneticListener);
        this.orientationEventListener.disable();
        this.applicationInterface.getGyroSensor().stopRecording();
        releaseSound();
        this.applicationInterface.clearLastImages();
        this.preview.onPause();
    }

    protected void onDestroy() {
        if (this.applicationInterface != null) {
            this.applicationInterface.onDestroy();
        }
        if (VERSION.SDK_INT >= 23) {
            RenderScript.releaseAllContexts();
        }
        for (Entry<Integer, Bitmap> entry : this.preloaded_bitmap_resources.entrySet()) {
            entry.getValue().recycle();
        }
        this.preloaded_bitmap_resources.clear();
        if (this.textToSpeech != null) {
            Log.d(TAG, "free textToSpeech");
            this.textToSpeech.stop();
            this.textToSpeech.shutdown();
            this.textToSpeech = null;
        }
        super.onDestroy();
    }

    public void onConfigurationChanged(Configuration newConfig) {
        this.preview.setCameraDisplayOrientation();
        super.onConfigurationChanged(newConfig);
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (this.mainUI.onKeyDown(keyCode, event)) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        this.mainUI.onKeyUp(keyCode, event);
        return super.onKeyUp(keyCode, event);
    }

    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!this.camera_in_background && hasFocus) {
            initImmersiveMode();
        }
    }

    public void initImmersiveMode() {
        if (MainUtil.usingKitKatImmersiveMode(this)) {
            setImmersiveTimer();
        } else {
            setImmersiveMode(true);
        }
    }

    private void setImmersiveTimer() {
        if (!(this.immersive_timer_handler == null || this.immersive_timer_runnable == null)) {
            this.immersive_timer_handler.removeCallbacks(this.immersive_timer_runnable);
        }
        this.immersive_timer_handler = new Handler();
        Handler handler = this.immersive_timer_handler;
        Runnable anonymousClass9 = new Runnable() {
            public void run() {
                if (!MainActivity.this.camera_in_background && MainUtil.usingKitKatImmersiveMode(MainActivity.this)) {
                    MainActivity.this.setImmersiveMode(true);
                }
            }
        };
        this.immersive_timer_runnable = anonymousClass9;
        handler.postDelayed(anonymousClass9, 5000);
    }

    void setImmersiveMode(boolean on) {
        if (!on) {
            getWindow().getDecorView().setSystemUiVisibility(0);
        } else if (MainUtil.usingKitKatImmersiveMode(this)) {
            getWindow().getDecorView().setSystemUiVisibility(2310);
        } else if (PreferenceManager.getDefaultSharedPreferences(this).getString(PreferenceKeys.getImmersiveModePreferenceKey(), "immersive_mode_low_profile").equals("immersive_mode_low_profile")) {
            getWindow().getDecorView().setSystemUiVisibility(1);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(0);
        }
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (VERSION.SDK_INT >= 23) {
            switch (requestCode) {
                case 0:
                case 1:
                    if (grantResults.length > 0 && grantResults[0] == 0) {
                        this.preview.retryOpenCamera();
                        return;
                    }
                    return;
                case 2:
                    if (grantResults.length > 0 && grantResults[0] == 0) {
                        return;
                    }
                    return;
            }
        }
    }

    @TargetApi(21)
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == 42) {
            SharedPreferences sharedPreferences;
            Editor editor;
            if (resultCode != -1 || resultData == null) {
                sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
                if (sharedPreferences.getString(PreferenceKeys.getSaveLocationSAFPreferenceKey(), "").length() == 0) {
                    editor = sharedPreferences.edit();
                    editor.putBoolean(PreferenceKeys.getUsingSAFPreferenceKey(), false);
                    editor.apply();
                    this.preview.showToast(null, (int) R.string.saf_cancelled);
                }
            } else {
                Uri treeUri = resultData.getData();
                try {
                    getContentResolver().takePersistableUriPermission(treeUri, resultData.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
                    editor.putString(PreferenceKeys.getSaveLocationSAFPreferenceKey(), treeUri.toString());
                    editor.apply();
                    File file = this.applicationInterface.getStorageUtils().getImageFolder();
                    if (file != null) {
                        this.preview.showToast(null, getResources().getString(R.string.changed_save_location) + "\n" + file.getAbsolutePath());
                    }
                } catch (SecurityException e) {
                    Log.e(TAG, "SecurityException failed to take permission");
                    e.printStackTrace();
                    sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
                    if (sharedPreferences.getString(PreferenceKeys.getSaveLocationSAFPreferenceKey(), "").length() == 0) {
                        editor = sharedPreferences.edit();
                        editor.putBoolean(PreferenceKeys.getUsingSAFPreferenceKey(), false);
                        editor.apply();
                        this.preview.showToast(null, (int) R.string.saf_permission_failed);
                    }
                }
            }
            if (!this.saf_dialog_from_preferences) {
                setWindowFlagsForCamera();
                showPreview(true);
            }
        }
    }

    public void showPreview(boolean show) {
        ViewGroup container = (ViewGroup) findViewById(R.id.hide_container);
        container.setBackgroundColor(ViewCompat.MEASURED_STATE_MASK);
        container.setAlpha(show ? 0.0f : PopupView.ALPHA_BUTTON_SELECTED);
    }

    public void setWindowFlagsForCamera() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        if (sharedPreferences.getBoolean(PreferenceKeys.getKeepDisplayOnPreferenceKey(), true)) {
            getWindow().addFlags(128);
        } else {
            getWindow().clearFlags(128);
        }
        if (sharedPreferences.getBoolean(PreferenceKeys.getShowWhenLockedPreferenceKey(), true)) {
            getWindow().addFlags(524288);
        } else {
            getWindow().clearFlags(524288);
        }
        setBrightnessForCamera(false);
        initImmersiveMode();
        this.camera_in_background = false;
    }

    public void updateForSettings() {
        updateForSettings(null);
    }

    void setBrightnessForCamera(boolean force_max) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        LayoutParams layout = getWindow().getAttributes();
        if (force_max || sharedPreferences.getBoolean(PreferenceKeys.getMaxBrightnessPreferenceKey(), true)) {
            layout.screenBrightness = PopupView.ALPHA_BUTTON_SELECTED;
        } else {
            layout.screenBrightness = -1.0f;
        }
        getWindow().setAttributes(layout);
    }

    public void updateForSettings(String toast_message) {
        String saved_focus_value = this.preview.updateFocusForVideo();
        boolean need_reopen = false;
        if (this.preview.getCameraController() != null) {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            if (!sharedPreferences.getString(PreferenceKeys.getSceneModePreferenceKey(), this.preview.getCameraController().getDefaultSceneMode()).equals(this.preview.getCameraController().getSceneMode())) {
                need_reopen = true;
            } else if (this.applicationInterface.useCamera2() && this.applicationInterface.useCamera2FakeFlash() != this.preview.getCameraController().getUseCamera2FakeFlash()) {
                need_reopen = true;
            }
        }
        this.mainUI.layoutUI();
        if (toast_message != null) {
            this.block_startup_toast = true;
        }
        if (need_reopen || this.preview.getCameraController() == null) {
            this.preview.onPause();
            this.preview.onResume();
        } else {
            this.preview.setCameraDisplayOrientation();
            this.preview.pausePreview();
            this.preview.setupCamera(false);
        }
        this.block_startup_toast = false;
        if (toast_message != null && toast_message.length() > 0) {
            this.preview.showToast(null, toast_message);
        }
        if (saved_focus_value != null) {
            this.preview.updateFocus(saved_focus_value, true, false);
        }
    }

    public void waitUntilImageQueueEmpty() {
        this.applicationInterface.getImageSaveThread().waitUntilDone();
    }

    private void releaseSound() {
        if (this.sound_pool != null) {
            this.sound_pool.release();
            this.sound_pool = null;
            this.sound_ids = null;
        }
    }

    private void initSound() {
        if (this.sound_pool == null) {
            if (VERSION.SDK_INT >= 21) {
                this.sound_pool = new SoundPool.Builder().setMaxStreams(1).setAudioAttributes(new AudioAttributes.Builder().setLegacyStreamType(1).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
            } else {
                this.sound_pool = new SoundPool(1, 1, 0);
            }
            this.sound_ids = new SparseIntArray();
        }
    }

    private void loadSound(int resource_id) {
        if (this.sound_pool != null) {
            this.sound_ids.put(resource_id, this.sound_pool.load(this, resource_id, 1));
        }
    }

    public void updateGalleryIcon() {
        new AsyncTask<Void, Void, Bitmap>() {
            private static final String TAG = "MainActivity/AsyncTask";

            protected Bitmap doInBackground(Void... params) {
                boolean is_locked = true;
                StorageUtils.Media media = MainActivity.this.applicationInterface.getStorageUtils().getLatestMedia();
                Bitmap thumbnail = null;
                KeyguardManager keyguard_manager = (KeyguardManager) MainActivity.this.getSystemService(Context.KEYGUARD_SERVICE);
                if (keyguard_manager == null || !keyguard_manager.inKeyguardRestrictedInputMode()) {
                    is_locked = false;
                }
                if (media == null || MainActivity.this.getContentResolver() == null || is_locked) {
                    return thumbnail;
                }
                try {
                    if (media.video) {
                        thumbnail = Thumbnails.getThumbnail(MainActivity.this.getContentResolver(), media.id, 1, null);
                    } else {
                        thumbnail = Images.Thumbnails.getThumbnail(MainActivity.this.getContentResolver(), media.id, 1, null);
                    }
                } catch (Throwable exception) {
                    exception.printStackTrace();
                }
                if (thumbnail == null || media.orientation == 0) {
                    return thumbnail;
                }
                Matrix matrix = new Matrix();
                matrix.setRotate((float) media.orientation, ((float) thumbnail.getWidth()) * 0.5f, ((float) thumbnail.getHeight()) * 0.5f);
                try {
                    Bitmap rotated_thumbnail = Bitmap.createBitmap(thumbnail, 0, 0, thumbnail.getWidth(), thumbnail.getHeight(), matrix, true);
                    if (rotated_thumbnail == thumbnail) {
                        return thumbnail;
                    }
                    thumbnail.recycle();
                    return rotated_thumbnail;
                } catch (Throwable th) {
                    return thumbnail;
                }
            }

            protected void onPostExecute(Bitmap thumbnail) {
                MainActivity.this.applicationInterface.getStorageUtils().clearLastMediaScanned();
                if (thumbnail != null) {
                    MainActivity.this.updateGalleryIcon(thumbnail);
                } else {
                    MainActivity.this.updateGalleryIconToBlank();
                }
            }
        }.execute(new Void[0]);
    }

    void updateGalleryIcon(Bitmap thumbnail) {
        this.gallery_bitmap = thumbnail;
    }

    private void updateGalleryIconToBlank() {
        this.gallery_bitmap = null;
    }

    private void setModeFromIntents(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            String action = getIntent().getAction();
            if (VERSION.SDK_INT >= 24) {
                if (MyTileServiceFrontCamera.TILE_ID.equals(action)) {
                    for (int i = 0; i < this.preview.getCameraControllerManager().getNumberOfCameras(); i++) {
                        if (this.preview.getCameraControllerManager().isFrontFacing(i)) {
                            this.applicationInterface.setCameraIdPref(i);
                            return;
                        }
                    }
                }
            }
        }
    }

    private void initCamera2Support() {
        this.supports_camera2 = false;
        if (VERSION.SDK_INT >= 21) {
            CameraControllerManager2 manager2 = new CameraControllerManager2(this);
            this.supports_camera2 = manager2.getNumberOfCameras() != 0;
            for (int i = 0; i < manager2.getNumberOfCameras() && this.supports_camera2; i++) {
                if (!manager2.allowCamera2Support(i)) {
                    this.supports_camera2 = false;
                }
            }
        }
    }

    private void preloadIcons(int icons_id) {
        for (String icon : getResources().getStringArray(icons_id)) {
            int resource = getResources().getIdentifier(icon, null, getApplicationContext().getPackageName());
//            this.preloaded_bitmap_resources.put(Integer.valueOf(resource), BitmapFactory.decodeResource(getResources(), resource));
        }
    }

    void launchOnlineHelp() {
        startActivity(new Intent("android.intent.action.VIEW", Uri.parse("http://opencamera.sourceforge.net/")));
    }

    public void onAudio(int level) {
        boolean audio_trigger = false;
        if (this.last_level == -1) {
            this.last_level = level;
            return;
        }
        int diff = level - this.last_level;
        if (diff > this.audio_noise_sensitivity) {
            this.time_quiet_loud = System.currentTimeMillis();
        } else if (diff < (-this.audio_noise_sensitivity) && this.time_quiet_loud != -1) {
            if (System.currentTimeMillis() - this.time_quiet_loud < 1500) {
                audio_trigger = true;
            }
            this.time_quiet_loud = -1;
        }
        this.last_level = level;
        if (audio_trigger) {
            long time_now = System.currentTimeMillis();
            boolean want_audio_listener = PreferenceManager.getDefaultSharedPreferences(this).getString(PreferenceKeys.getAudioControlPreferenceKey(), "none").equals("noise");
            if ((this.time_last_audio_trigger_photo == -1 || time_now - this.time_last_audio_trigger_photo >= 5000) && want_audio_listener) {
                this.time_last_audio_trigger_photo = time_now;
                audioTrigger();
            }
        }
    }

    private void audioTrigger() {
        if (!this.camera_in_background && !this.preview.isTakingPhotoOrOnTimer()) {
            runOnUiThread(new Runnable() {
                public void run() {
                    MainActivity.this.takePicture();
                }
            });
        }
    }

    public void takePicture() {
        takePicturePressed();
    }

    void takePicturePressed() {
        if (this.applicationInterface.getGyroSensor().isRecording()) {
            this.applicationInterface.setNextPanoramaPoint();
        }
        this.preview.takePicturePressed();
    }


    public void clickedTakePhoto(View view) {
        takePicture();
    }

    public void clickedSwitchCamera(View view) {
        if (this.preview.canSwitchCamera()) {
            int cameraId = getNextCameraId();
            View switchCameraButton = findViewById(R.id.switch_camera);
            switchCameraButton.setEnabled(false);
            this.preview.setCamera(cameraId);
            switchCameraButton.setEnabled(true);
            this.mainUI.setSwitchCameraContentDescription();
        }
    }

    public int getNextCameraId() {
        int cameraId = this.preview.getCameraId();
        if (!this.preview.canSwitchCamera()) {
            return cameraId;
        }
        return (cameraId + 1) % this.preview.getCameraControllerManager().getNumberOfCameras();
    }


    public Bitmap getPreloadedBitmap(int resource) {
        return (Bitmap) this.preloaded_bitmap_resources.get(Integer.valueOf(resource));
    }

    public void openSettings() {
        int[] widths;
        int[] heights;
        int i;
        waitUntilImageQueueEmpty();
        this.preview.cancelTimer();
        Bundle bundle = new Bundle();
        bundle.putInt("cameraId", this.preview.getCameraId());
        bundle.putInt("nCameras", this.preview.getCameraControllerManager().getNumberOfCameras());
        bundle.putString("camera_api", this.preview.getCameraAPI());
        bundle.putBoolean("using_android_l", this.preview.usingCamera2API());
        bundle.putBoolean("supports_auto_stabilise", this.supports_auto_stabilise);
        bundle.putBoolean("supports_camera2", this.supports_camera2);
        bundle.putBoolean("supports_face_detection", this.preview.supportsFaceDetection());
        bundle.putBoolean("supports_raw", this.preview.supportsRaw());
        bundle.putBoolean("supports_hdr", MainUtil.supportsHDR(this));
        bundle.putBoolean("supports_expo_bracketing", MainUtil.supportsExpoBracketing(this));
        bundle.putInt("max_expo_bracketing_n_images", MainUtil.maxExpoBracketingNImages(this));
        bundle.putBoolean("supports_exposure_compensation", this.preview.supportsExposures());
        bundle.putBoolean("supports_iso_range", this.preview.supportsISORange());
        bundle.putBoolean("supports_exposure_time", this.preview.supportsExposureTime());
        bundle.putBoolean("supports_white_balance_temperature", this.preview.supportsWhiteBalanceTemperature());
        bundle.putBoolean("supports_video_stabilization", this.preview.supportsVideoStabilization());
        bundle.putBoolean("can_disable_shutter_sound", this.preview.canDisableShutterSound());
        putBundleExtra(bundle, "color_effects", this.preview.getSupportedColorEffects());
        putBundleExtra(bundle, "scene_modes", this.preview.getSupportedSceneModes());
        putBundleExtra(bundle, "white_balances", this.preview.getSupportedWhiteBalances());
        putBundleExtra(bundle, "isos", this.preview.getSupportedISOs());
        bundle.putString("iso_key", this.preview.getISOKey());
        if (this.preview.getCameraController() != null) {
            bundle.putString("parameters_string", this.preview.getCameraController().getParametersString());
        }
        List<Size> preview_sizes = this.preview.getSupportedPreviewSizes();
        if (preview_sizes != null) {
            widths = new int[preview_sizes.size()];
            heights = new int[preview_sizes.size()];
            i = 0;
            for (Size size : preview_sizes) {
                widths[i] = size.width;
                heights[i] = size.height;
                i++;
            }
            bundle.putIntArray("preview_widths", widths);
            bundle.putIntArray("preview_heights", heights);
        }
        bundle.putInt("preview_width", this.preview.getCurrentPreviewSize().width);
        bundle.putInt("preview_height", this.preview.getCurrentPreviewSize().height);
        List<Size> sizes = this.preview.getSupportedPictureSizes();
        if (sizes != null) {
            widths = new int[sizes.size()];
            heights = new int[sizes.size()];
            i = 0;
            for (Size size2 : sizes) {
                widths[i] = size2.width;
                heights[i] = size2.height;
                i++;
            }
            bundle.putIntArray("resolution_widths", widths);
            bundle.putIntArray("resolution_heights", heights);
        }
        if (this.preview.getCurrentPictureSize() != null) {
            bundle.putInt("resolution_width", this.preview.getCurrentPictureSize().width);
            bundle.putInt("resolution_height", this.preview.getCurrentPictureSize().height);
        }


        CamcorderProfile camcorder_profile = this.preview.getCamcorderProfile();
        bundle.putInt("video_frame_width", camcorder_profile.videoFrameWidth);
        bundle.putInt("video_frame_height", camcorder_profile.videoFrameHeight);
        bundle.putInt("video_bit_rate", camcorder_profile.videoBitRate);
        bundle.putInt("video_frame_rate", camcorder_profile.videoFrameRate);

        putBundleExtra(bundle, "flash_values", this.preview.getSupportedFlashValues());
        putBundleExtra(bundle, "focus_values", this.preview.getSupportedFocusValues());
        setWindowFlagsForSettings();
    }

    private static void putBundleExtra(Bundle bundle, String key, List<String> values) {
        if (values != null) {
            String[] values_arr = new String[values.size()];
            int i = 0;
            for (String value : values) {
                values_arr[i] = value;
                i++;
            }
            bundle.putStringArray(key, values_arr);
        }
    }

    public void setWindowFlagsForSettings() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        getWindow().clearFlags(128);
        getWindow().clearFlags(524288);
        LayoutParams layout = getWindow().getAttributes();
        layout.screenBrightness = -1.0f;
        getWindow().setAttributes(layout);
        setImmersiveMode(false);
        this.camera_in_background = true;
    }

    void savingImage(final boolean started) {
        runOnUiThread(new Runnable() {
            public void run() {
                if (started) {
                    if (gallery_save_anim == null) {
                        gallery_save_anim = ValueAnimator.ofInt(new int[]{Color.argb(200, 255, 255, 255), Color.argb(63, 255, 255, 255)});
                        gallery_save_anim.setEvaluator(new ArgbEvaluator());
                        gallery_save_anim.setRepeatCount(-1);
                        gallery_save_anim.setRepeatMode(ValueAnimator.REVERSE);
                        gallery_save_anim.setDuration(500);
                    }
                    MainActivity.this.gallery_save_anim.addUpdateListener(new AnimatorUpdateListener() {
                        public void onAnimationUpdate(ValueAnimator animation) {
                        }
                    });
                    MainActivity.this.gallery_save_anim.start();
                } else if (MainActivity.this.gallery_save_anim != null) {
                    MainActivity.this.gallery_save_anim.cancel();
                }
            }
        });
    }

    public void clickedGallery(View view) {
        Uri uri = this.applicationInterface.getStorageUtils().getLastMediaScanned();
        if (uri == null) {
            StorageUtils.Media media = this.applicationInterface.getStorageUtils().getLatestMedia();
            if (media != null) {
                uri = media.uri;
            }
        }
        if (uri != null) {
            try {
                ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
                if (pfd == null) {
                    uri = null;
                } else {
                    pfd.close();
                }
            } catch (IOException e) {
                uri = null;
            }
        }
        if (uri == null) {
            uri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        }
        if (!this.is_test) {
            String REVIEW_ACTION = "com.android.camera.action.REVIEW";
            try {
                startActivity(new Intent(REVIEW_ACTION, uri));
            } catch (ActivityNotFoundException e2) {
                Intent intent = new Intent("android.intent.action.VIEW", uri);
                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivity(intent);
                } else {
                    this.preview.showToast(null, (int) R.string.no_gallery_app);
                }
            }
        }
    }

    @TargetApi(21)
    void openFolderChooserDialogSAF(boolean from_preferences) {
        this.saf_dialog_from_preferences = from_preferences;
        startActivityForResult(new Intent("android.intent.action.OPEN_DOCUMENT_TREE"), 42);
    }

    void updateSaveFolder(String new_save_location) {
        if (new_save_location != null) {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            if (!this.applicationInterface.getStorageUtils().getSaveLocation().equals(new_save_location)) {
                Editor editor = sharedPreferences.edit();
                editor.putString(PreferenceKeys.getSaveLocationPreferenceKey(), new_save_location);
                editor.apply();
                this.preview.showToast(null, getResources().getString(R.string.changed_save_location) + "\n" + this.applicationInterface.getStorageUtils().getSaveLocation());
            }
        }
    }

    void cameraSetup() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        View takePhotoButton = findViewById(R.id.take_photo);
        if (!sharedPreferences.getBoolean(PreferenceKeys.getShowTakePhotoPreferenceKey(), true)) {
            takePhotoButton.setVisibility(View.INVISIBLE);
        } else if (!this.mainUI.inImmersiveMode()) {
            takePhotoButton.setVisibility(View.VISIBLE);
        }

        this.mainUI.setTakePhotoIcon();
        this.mainUI.setSwitchCameraContentDescription();
        if (!this.block_startup_toast) {
            showPhotoVideoToast(false);
        }
    }

    public Preview getPreview() {
        return this.preview;
    }

    private void showPhotoVideoToast(boolean always_show) {
        CameraController camera_controller = this.preview.getCameraController();
        if (camera_controller != null && !this.camera_in_background) {
            String toast_string;
            String[] entries_array;
            int index;
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            boolean simple = true;
            toast_string = getResources().getString(R.string.photo);
            Size current_size = this.preview.getCurrentPictureSize();
            toast_string = toast_string + " " + current_size.width + "x" + current_size.height;
            if (this.preview.supportsFocus() && this.preview.getSupportedFocusValues().size() > 1) {
                String focus_value = this.preview.getCurrentFocusValue();
                if (!(focus_value == null || focus_value.equals("focus_mode_auto") || focus_value.equals("focus_mode_continuous_picture"))) {
                    String focus_entry = this.preview.findFocusEntryForValue(focus_value);
                    if (focus_entry != null) {
                        toast_string = toast_string + "\n" + focus_entry;
                    }
                }
            }
            if (sharedPreferences.getBoolean(PreferenceKeys.getAutoStabilisePreferenceKey(), false)) {
                toast_string = toast_string + "\n" + getResources().getString(R.string.preference_auto_stabilise);
                simple = false;
            }
            String photo_mode_string = null;
            MyApplicationInterface.PhotoMode photo_mode = this.applicationInterface.getPhotoMode();
            if (photo_mode == MyApplicationInterface.PhotoMode.DRO) {
                photo_mode_string = getResources().getString(R.string.photo_mode_dro);
            } else if (photo_mode == MyApplicationInterface.PhotoMode.HDR) {
                photo_mode_string = getResources().getString(R.string.photo_mode_hdr);
            } else if (photo_mode == MyApplicationInterface.PhotoMode.ExpoBracketing) {
                photo_mode_string = getResources().getString(R.string.photo_mode_expo_bracketing_full);
            }
            if (photo_mode_string != null) {
                toast_string = toast_string + "\n" + getResources().getString(R.string.photo_mode) + ": " + photo_mode_string;
                simple = false;
            }

            if (this.applicationInterface.getFaceDetectionPref()) {
                toast_string = toast_string + "\n" + getResources().getString(R.string.preference_face_detection);
                simple = false;
            }
            String iso_value = sharedPreferences.getString(PreferenceKeys.getISOPreferenceKey(), camera_controller.getDefaultISO());
            if (!iso_value.equals(camera_controller.getDefaultISO())) {
                toast_string = toast_string + "\nISO: " + iso_value;
                if (this.preview.supportsExposureTime()) {
                    toast_string = toast_string + " " + this.preview.getExposureTimeString(sharedPreferences.getLong(PreferenceKeys.getExposureTimePreferenceKey(), camera_controller.getDefaultExposureTime()));
                }
                simple = false;
            }
            int current_exposure = camera_controller.getExposureCompensation();
            if (current_exposure != 0) {
                toast_string = toast_string + "\n" + this.preview.getExposureCompensationString(current_exposure);
                simple = false;
            }
            String scene_mode = camera_controller.getSceneMode();
            if (!(scene_mode == null || scene_mode.equals(camera_controller.getDefaultSceneMode()))) {
                toast_string = toast_string + "\n" + getResources().getString(R.string.scene_mode) + ": " + scene_mode;
                simple = false;
            }
            String white_balance = camera_controller.getWhiteBalance();
            if (!(white_balance == null || white_balance.equals(camera_controller.getDefaultWhiteBalance()))) {
                toast_string = toast_string + "\n" + getResources().getString(R.string.white_balance) + ": " + white_balance;
                if (white_balance.equals("manual") && this.preview.supportsWhiteBalanceTemperature()) {
                    toast_string = toast_string + " " + camera_controller.getWhiteBalanceTemperature();
                }
                simple = false;
            }
            String color_effect = camera_controller.getColorEffect();
            if (!(color_effect == null || color_effect.equals(camera_controller.getDefaultColorEffect()))) {
                toast_string = toast_string + "\n" + getResources().getString(R.string.color_effect) + ": " + color_effect;
                simple = false;
            }
            String lock_orientation = sharedPreferences.getString(PreferenceKeys.getLockOrientationPreferenceKey(), "none");
            if (!lock_orientation.equals("none")) {
                entries_array = getResources().getStringArray(R.array.preference_lock_orientation_entries);
                index = Arrays.asList(getResources().getStringArray(R.array.preference_lock_orientation_values)).indexOf(lock_orientation);
                if (index != -1) {
                    toast_string = toast_string + "\n" + entries_array[index];
                    simple = false;
                }
            }
            String timer = sharedPreferences.getString(PreferenceKeys.getTimerPreferenceKey(), "0");
            if (!timer.equals("0")) {
                entries_array = getResources().getStringArray(R.array.preference_timer_entries);
                index = Arrays.asList(getResources().getStringArray(R.array.preference_timer_values)).indexOf(timer);
                if (index != -1) {
                    toast_string = toast_string + "\n" + getResources().getString(R.string.preference_timer) + ": " + entries_array[index];
                    simple = false;
                }
            }
            String repeat = this.applicationInterface.getRepeatPref();
            if (!repeat.equals("1")) {
                entries_array = getResources().getStringArray(R.array.preference_burst_mode_entries);
                index = Arrays.asList(getResources().getStringArray(R.array.preference_burst_mode_values)).indexOf(repeat);
                if (index != -1) {
                    toast_string = toast_string + "\n" + getResources().getString(R.string.preference_burst_mode) + ": " + entries_array[index];
                    simple = false;
                }
            }
            if (!simple || always_show) {
                this.preview.showToast(this.switch_video_toast, toast_string);
            }
        }
    }

    private static double seekbarScalingInverse(double scaling) {
        return Math.log((99.0d * scaling) + 1.0d) / Math.log(100.0d);
    }

    private static double exponentialScalingInverse(double value, double min, double max) {
        return Math.log(value / min) / Math.log(max / min);
    }

    public boolean supportsAutoStabilise() {
        return this.supports_auto_stabilise;
    }

    public boolean supportsCamera2() {
        return this.supports_camera2;
    }

    public MainUI getMainUI() {
        return this.mainUI;
    }

    public MyApplicationInterface getApplicationInterface() {
        return this.applicationInterface;
    }

    public TextFormatter getTextFormatter() {
        return this.textFormatter;
    }

    public StorageUtils getStorageUtils() {
        return this.applicationInterface.getStorageUtils();
    }

    public ToastBoxer getChangedAutoStabiliseToastBoxer() {
        return this.changed_auto_stabilise_toast;
    }

    void playSound(int resource_id) {
        if (this.sound_pool != null && this.sound_ids.indexOfKey(resource_id) >= 0) {
            this.sound_pool.play(this.sound_ids.get(resource_id), PopupView.ALPHA_BUTTON_SELECTED, PopupView.ALPHA_BUTTON_SELECTED, 0, 0, PopupView.ALPHA_BUTTON_SELECTED);
        }
    }

    void speak(String text) {
        if (this.textToSpeech != null && this.textToSpeechSuccess) {
            this.textToSpeech.speak(text, 0, null);
        }
    }

    void requestCameraPermission() {
        if (VERSION.SDK_INT >= 23) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, "android.permission.CAMERA")) {
                showRequestPermissionRationale(0);
                return;
            }
            ActivityCompat.requestPermissions(this, new String[]{"android.permission.CAMERA"}, 0);
        }
    }

    @TargetApi(17)
    private void showRequestPermissionRationale(final int permission_code) {
        if (VERSION.SDK_INT >= 23) {
            boolean ok = true;
            String[] permissions = null;
            int message_id = 0;
            if (permission_code == 0) {
                permissions = new String[]{"android.permission.CAMERA"};
                message_id = R.string.permission_rationale_camera;
            } else if (permission_code == 1) {
                permissions = new String[]{"android.permission.WRITE_EXTERNAL_STORAGE"};
                message_id = R.string.permission_rationale_storage;
            } else if (permission_code == 2) {
                permissions = new String[]{"android.permission.RECORD_AUDIO"};
                message_id = R.string.permission_rationale_record_audio;
            } else if (permission_code == 3) {
                permissions = new String[]{"android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"};
                message_id = R.string.permission_rationale_location;
            } else {
                ok = false;
            }
            if (ok) {
                final String[] permissions_f = permissions;
                new Builder(this).setTitle(R.string.permission_rationale_title).setMessage(message_id).setIcon(R.drawable.take_photo).setPositiveButton(R.string.intro_ok, null).setOnDismissListener(new OnDismissListener() {
                    public void onDismiss(DialogInterface dialog) {
                        ActivityCompat.requestPermissions(MainActivity.this, permissions_f, permission_code);
                    }
                }).show();
            }
        }
    }

    void requestStoragePermission() {
        if (VERSION.SDK_INT >= 23) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, "android.permission.WRITE_EXTERNAL_STORAGE")) {
                showRequestPermissionRationale(1);
                return;
            }
            ActivityCompat.requestPermissions(this, new String[]{"android.permission.WRITE_EXTERNAL_STORAGE"}, 1);
        }
    }

}
