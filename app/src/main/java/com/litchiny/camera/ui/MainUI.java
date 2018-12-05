package com.litchiny.camera.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.media.AudioManager;
import android.preference.PreferenceManager;
import android.support.v4.view.MotionEventCompat;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

import com.lwyy.camera.R;
import com.litchiny.utils.MainUtil;

import com.litchiny.camera.MainActivity;
import com.litchiny.camera.PreferenceKeys;

public class MainUI {
    private static final String TAG = "MainUI";
    private final MainActivity main_activity;
    private int current_orientation;
    private boolean immersive_mode;
    private boolean keydown_volume_down;
    private boolean keydown_volume_up;
    private boolean show_gui = true;
    private boolean ui_placement_right = true;

    public MainUI(MainActivity main_activity) {
        this.main_activity = main_activity;
        setIcon(R.id.gallery);
        setIcon(R.id.switch_camera);
    }

    private void setIcon(int id) {
        ((ImageButton) this.main_activity.findViewById(id)).setBackgroundColor(Color.argb(63, 63, 63, 63));
    }

    public void setSwitchCameraContentDescription() {
        if (this.main_activity.getPreview() != null && this.main_activity.getPreview().canSwitchCamera()) {
            int content_description;
            ImageButton view = (ImageButton) this.main_activity.findViewById(R.id.switch_camera);
            if (this.main_activity.getPreview().getCameraControllerManager().isFrontFacing(this.main_activity.getNextCameraId())) {
                content_description = R.string.switch_to_front_camera;
            } else {
                content_description = R.string.switch_to_back_camera;
            }
            view.setContentDescription(this.main_activity.getResources().getString(content_description));
        }
    }

    public boolean getUIPlacementRight() {
        return this.ui_placement_right;
    }

    public void onOrientationChanged(int orientation) {
        if (orientation != -1) {
            int diff = Math.abs(orientation - this.current_orientation);
            if (diff > 180) {
                diff = 360 - diff;
            }
            if (diff > 60) {
                orientation = (((orientation + 45) / 90) * 90) % 360;
                if (orientation != this.current_orientation) {
                    this.current_orientation = orientation;
                    layoutUI();
                }
            }
        }
    }

    public void layoutUI() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.main_activity);
        this.ui_placement_right = sharedPreferences.getString(PreferenceKeys.getUIPlacementPreferenceKey(), "ui_right").equals("ui_right");
        int degrees = 0;
        switch (this.main_activity.getWindowManager().getDefaultDisplay().getRotation()) {
            case 0:
                degrees = 0;
                break;
            case 1:
                degrees = 90;
                break;
            case 2:
                degrees = 180;
                break;
            case 3:
                degrees = 270;
                break;
        }
        int ui_rotation = (360 - ((this.current_orientation + degrees) % 360)) % 360;
        this.main_activity.getPreview().setUIRotation(ui_rotation);
        int align_parent_top = 10;
        int align_parent_bottom = 12;
        if (!this.ui_placement_right) {
            align_parent_top = 12;
            align_parent_bottom = 10;
        }

        View view = this.main_activity.findViewById(R.id.gallery);
        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) view.getLayoutParams();
        layoutParams.addRule(9, 0);
        layoutParams.addRule(11, -1);
        layoutParams.addRule(align_parent_top, -1);
        layoutParams.addRule(align_parent_bottom, 0);
        layoutParams.addRule(0, 0);
        layoutParams.addRule(1, 0);
        view.setLayoutParams(layoutParams);
        view.setPadding(20, 0, 0, 0);
        setViewRotation(view, (float) ui_rotation);

        view = this.main_activity.findViewById(R.id.switch_camera);
        layoutParams = (RelativeLayout.LayoutParams) view.getLayoutParams();
        layoutParams.addRule(9, 0);
        layoutParams.addRule(11, 0);
        layoutParams.addRule(align_parent_top, -1);
        layoutParams.addRule(align_parent_bottom, 0);
        layoutParams.addRule(0, R.id.gallery);
        layoutParams.addRule(1, 0);
        view.setLayoutParams(layoutParams);
        setViewRotation(view, (float) ui_rotation);

        view = this.main_activity.findViewById(R.id.take_photo);
        layoutParams = (RelativeLayout.LayoutParams) view.getLayoutParams();
        layoutParams.addRule(9, 0);
        layoutParams.addRule(11, -1);
        view.setLayoutParams(layoutParams);
        setViewRotation(view, (float) ui_rotation);

        setTakePhotoIcon();
    }

    private void setViewRotation(View view, float ui_rotation) {
        float rotate_by = ui_rotation - view.getRotation();
        if (rotate_by > 181.0f) {
            rotate_by -= 360.0f;
        } else if (rotate_by < -181.0f) {
            rotate_by += 360.0f;
        }
        view.animate().rotationBy(rotate_by).setDuration(100).setInterpolator(new AccelerateDecelerateInterpolator()).start();
    }

    public void setTakePhotoIcon() {
        if (this.main_activity.getPreview() != null) {
            int resource;
            int content_description;
            ImageButton view = (ImageButton) this.main_activity.findViewById(R.id.take_photo);
            resource = R.drawable.take_photo;
            content_description = R.string.take_photo;
            view.setImageResource(resource);
            view.setContentDescription(this.main_activity.getResources().getString(content_description));
            view.setTag(Integer.valueOf(resource));
        }
    }

    public void setImmersiveMode(final boolean immersive_mode) {
        this.immersive_mode = immersive_mode;
        this.main_activity.runOnUiThread(new Runnable() {
            public void run() {
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainUI.this.main_activity);
                int visibility = immersive_mode ? View.GONE : View.VISIBLE;
                View switchCameraButton = MainUI.this.main_activity.findViewById(R.id.switch_camera);
                View galleryButton = MainUI.this.main_activity.findViewById(R.id.gallery);
                if (MainUI.this.main_activity.getPreview().getCameraControllerManager().getNumberOfCameras() > 1) {
                    switchCameraButton.setVisibility(visibility);
                }

                galleryButton.setVisibility(visibility);

                if (sharedPreferences.getString(PreferenceKeys.getImmersiveModePreferenceKey(), "immersive_mode_low_profile").equals("immersive_mode_everything")) {
                    if (sharedPreferences.getBoolean(PreferenceKeys.getShowTakePhotoPreferenceKey(), true)) {
                        MainUI.this.main_activity.findViewById(R.id.take_photo).setVisibility(visibility);
                    }
                }
                if (!immersive_mode) {
                    MainUI.this.showGUI(MainUI.this.show_gui);
                }
            }
        });
    }

    public void showGUI(final boolean show) {
        this.show_gui = show;
        if (!inImmersiveMode()) {
            if (show && MainUtil.usingKitKatImmersiveMode(this.main_activity)) {
                this.main_activity.initImmersiveMode();
            }
            this.main_activity.runOnUiThread(new Runnable() {
                public void run() {
                    int visibility = show ? View.VISIBLE : View.GONE;
                    View switchCameraButton = MainUI.this.main_activity.findViewById(R.id.switch_camera);
                    if (MainUI.this.main_activity.getPreview().getCameraControllerManager().getNumberOfCameras() > 1) {
                        switchCameraButton.setVisibility(visibility);
                    }
                }
            });
        }
    }

    public boolean inImmersiveMode() {
        return this.immersive_mode;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case MotionEventCompat.AXIS_DISTANCE /*24*/:
            case 25:
            case 85:
            case 86:
            case 88:
                if (keyCode == 24) {
                    this.keydown_volume_up = true;
                } else if (keyCode == 25) {
                    this.keydown_volume_down = true;
                }
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.main_activity);
                String volume_keys = sharedPreferences.getString(PreferenceKeys.getVolumeKeysPreferenceKey(), "volume_take_photo");
                if ((keyCode == 88 || keyCode == 85 || keyCode == 86) && !volume_keys.equals("volume_take_photo")) {
                    AudioManager audioManager = (AudioManager) this.main_activity.getSystemService(Context.AUDIO_SERVICE);
                    if (audioManager != null) {
                        break;
                    }
                }
                int obj = -1;
                switch (volume_keys.hashCode()) {
                    case -1359912077:
                        if (volume_keys.equals("volume_focus")) {
                            obj = 1;
                            break;
                        }
                        break;
                    case -925372737:
                        if (volume_keys.equals("volume_take_photo")) {
                            obj = 6;
                            break;
                        }
                        break;
                    case -874555944:
                        if (volume_keys.equals("volume_zoom")) {
                            obj = 2;
                            break;
                        }
                        break;
                    case -692640628:
                        if (volume_keys.equals("volume_exposure")) {
                            obj = 3;
                            break;
                        }
                        break;
                    case 529947390:
                        if (volume_keys.equals("volume_really_nothing")) {
                            obj = 5;
                            break;
                        }
                        break;
                    case 915660971:
                        if (volume_keys.equals("volume_auto_stabilise")) {
                            obj = 4;
                            break;
                        }
                        break;
                }
                switch (obj) {
                    case 6:
                        this.main_activity.takePicture();
                        return true;
                    case 1:
                        if (this.keydown_volume_up && this.keydown_volume_down) {
                            this.main_activity.takePicture();
                        } else if (this.main_activity.getPreview().getCurrentFocusValue() == null || !this.main_activity.getPreview().getCurrentFocusValue().equals("focus_mode_manual2")) {
                            if (event.getDownTime() == event.getEventTime() && !this.main_activity.getPreview().isFocusWaiting()) {
                                this.main_activity.getPreview().requestAutoFocus();
                            }
                        }
                        return true;
                    case 2:

                        return true;
                    case 3:

                        return true;
                    case 4:
                        if (this.main_activity.supportsAutoStabilise()) {
                            boolean auto_stabilise = !sharedPreferences.getBoolean(PreferenceKeys.getAutoStabilisePreferenceKey(), false);
                            Editor editor = sharedPreferences.edit();
                            editor.putBoolean(PreferenceKeys.getAutoStabilisePreferenceKey(), auto_stabilise);
                            editor.apply();
                            this.main_activity.getPreview().showToast(this.main_activity.getChangedAutoStabiliseToastBoxer(), this.main_activity.getResources().getString(R.string.preference_auto_stabilise) + ": " + this.main_activity.getResources().getString(auto_stabilise ? R.string.on : R.string.off));
                        } else {
                            this.main_activity.getPreview().showToast(this.main_activity.getChangedAutoStabiliseToastBoxer(), (int) R.string.auto_stabilise_not_supported);
                        }
                        return true;
                    case 5:
                        return true;
                    default:
                        break;
                }
            case MotionEventCompat.AXIS_RELATIVE_X /*27*/:
                if (event.getRepeatCount() == 0) {
                    this.main_activity.takePicture();
                    return true;
                }
                break;
            case 80:
                break;
            case 82:
                this.main_activity.openSettings();
                return true;
        }
        return false;
    }

    public void onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == 24) {
            this.keydown_volume_up = false;
        } else if (keyCode == 25) {
            this.keydown_volume_down = false;
        }
    }

}
