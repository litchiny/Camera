package com.litchiny.camera.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import com.lwyy.camera.R;
import com.litchiny.utils.MainUtil;

import com.litchiny.camera.controller.CameraController;
import com.litchiny.camera.controller.CameraController.Size;
import com.litchiny.camera.MainActivity;
import com.litchiny.camera.MyApplicationInterface.PhotoMode;
import com.litchiny.camera.PreferenceKeys;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

public class PopupView extends LinearLayout {
    public static final float ALPHA_BUTTON = 0.6f;
    public static final float ALPHA_BUTTON_SELECTED = 1.0f;
    private static final String TAG = "PopupView";
    private int burst_mode_index = -1;
    private int grid_index = -1;
    private int picture_size_index = -1;
    private final Map<String, View> popup_buttons = new Hashtable();
    private int timer_index = -1;
    private final int total_width;
    private int total_width_dp;
    private int video_size_index = -1;

    private abstract class ArrayOptionsPopupListener {
        public abstract int onClickNext();

        public abstract int onClickPrev();

        private ArrayOptionsPopupListener() {
        }
    }

    private abstract class ButtonOptionsPopupListener {
        public abstract void onClick(String str);

        private ButtonOptionsPopupListener() {
        }
    }

    private abstract class RadioOptionsListener {
        public abstract void onClick(String str);

        private RadioOptionsListener() {
        }
    }

    public PopupView(Context context) {
        super(context);
        long debug_time = System.nanoTime();
        setOrientation(LinearLayout.VERTICAL);
        float scale = getResources().getDisplayMetrics().density;
        this.total_width_dp = 280;
        Display display = ((Activity) getContext()).getWindowManager().getDefaultDisplay();
        DisplayMetrics outMetrics = new DisplayMetrics();
        display.getMetrics(outMetrics);
        int dpHeight = ((int) (((float) outMetrics.heightPixels) / scale)) - 50;
        if (this.total_width_dp > dpHeight) {
            this.total_width_dp = dpHeight;
        }
        this.total_width = (int) ((((float) this.total_width_dp) * scale) + 0.5f);
        MainActivity main_activity = (MainActivity) getContext();
        Preview preview = main_activity.getPreview();
        final Preview preview2 = preview;
        final MainActivity mainActivity = main_activity;
        addButtonOptionsToPopup(preview.getSupportedFlashValues(), R.array.flash_icons, R.array.flash_values, getResources().getString(R.string.flash_mode), preview.getCurrentFlashValue(), "TEST_FLASH", new ButtonOptionsPopupListener() {
            public void onClick(String option) {
                preview2.updateFlash(option);
            }
        });
        if ( !preview.isTakingPhoto()) {
            List<String> supported_isos;
            Preview preview3 = preview;
            List<String> supported_focus_values = preview.getSupportedFocusValues();
            if (supported_focus_values != null) {
                List<String> arrayList = new ArrayList(supported_focus_values);
                arrayList.remove("focus_mode_continuous_video");
                supported_focus_values = arrayList;
            }

            addButtonOptionsToPopup(supported_focus_values, R.array.focus_mode_icons, R.array.focus_mode_values, getResources().getString(R.string.focus_mode), preview.getCurrentFocusValue(), "TEST_FOCUS", new ButtonOptionsPopupListener() {
                public void onClick(String option) {
                    preview2.updateFocus(option, false, true);
                }
            });
            String manual_value = "m";
            if (preview.supportsISORange()) {
                int min_iso = preview.getMinimumISO();
                int max_iso = preview.getMaximumISO();
                List<String> values = new ArrayList();
                values.add("auto");
                values.add("m");
                int[] iArr = new int[8];
                iArr = new int[]{50, 100, 200, 400, 800, 1600, 3200, 6400};
                values.add("" + min_iso);
                for (int iso_value : iArr) {
                    if (iso_value > min_iso && iso_value < max_iso) {
                        values.add("" + iso_value);
                    }
                }
                values.add("" + max_iso);
                supported_isos = values;
            } else {
                supported_isos = preview.getSupportedISOs();
            }
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
            String current_iso = sharedPreferences.getString(PreferenceKeys.getISOPreferenceKey(), "auto");
            if (!(current_iso.equals("auto") || supported_isos == null || !supported_isos.contains("m") || supported_isos.contains(current_iso))) {
                current_iso = "m";
            }

            final Preview preview4 = preview;
            addButtonOptionsToPopup(supported_isos, -1, -1, "ISO", current_iso, "TEST_ISO", new ButtonOptionsPopupListener() {
                public void onClick(String option) {
                    Editor editor = PreferenceManager.getDefaultSharedPreferences(mainActivity).edit();
                    editor.putString(PreferenceKeys.getISOPreferenceKey(), option);
                    String toast_option = option;
                    if (option.equals("auto")) {
                        editor.putLong(PreferenceKeys.getExposureTimePreferenceKey(), CameraController.EXPOSURE_TIME_DEFAULT);
                    } else {
                        if (option.equals("m")) {
                            if (preview4.getCameraController() == null || !preview4.getCameraController().captureResultHasIso()) {
                                editor.putString(PreferenceKeys.getISOPreferenceKey(), "800");
                                toast_option = "800";
                            } else {
                                int iso = preview4.getCameraController().captureResultIso();
                                editor.putString(PreferenceKeys.getISOPreferenceKey(), "" + iso);
                                toast_option = "" + iso;
                            }
                        }
                        if (preview4.usingCamera2API() && preview4.getCameraController() != null && preview4.getCameraController().captureResultHasExposureTime()) {
                            editor.putLong(PreferenceKeys.getExposureTimePreferenceKey(), preview4.getCameraController().captureResultExposureTime());
                        }
                    }
                    editor.apply();
                    mainActivity.updateForSettings("ISO: " + toast_option);
                }
            });
            List<String> photo_modes = new ArrayList();
            List<PhotoMode> photo_mode_values = new ArrayList();
            photo_modes.add(getResources().getString(R.string.photo_mode_standard));
            photo_mode_values.add(PhotoMode.Standard);
            photo_modes.add(getResources().getString(R.string.photo_mode_dro));
            photo_mode_values.add(PhotoMode.DRO);
            if (MainUtil.supportsHDR(main_activity)) {
                photo_modes.add(getResources().getString(R.string.photo_mode_hdr));
                photo_mode_values.add(PhotoMode.HDR);
            }
            if (MainUtil.supportsExpoBracketing(main_activity)) {
                photo_modes.add(getResources().getString(R.string.photo_mode_expo_bracketing));
                photo_mode_values.add(PhotoMode.ExpoBracketing);
            }
            if (photo_modes.size() > 1) {
                PhotoMode photo_mode = main_activity.getApplicationInterface().getPhotoMode();
                String current_mode = null;
                for (int i = 0; i < photo_modes.size() && current_mode == null; i++) {
                    if (photo_mode_values.get(i) == photo_mode) {
                        current_mode = (String) photo_modes.get(i);
                    }
                }
                if (current_mode == null) {
                    current_mode = "";
                }
                addTitleToPopup(getResources().getString(R.string.photo_mode));
                final List<String> list = photo_modes;
                final List<PhotoMode> list2 = photo_mode_values;
                final MainActivity mainActivity2 = main_activity;
                addButtonOptionsToPopup(photo_modes, -1, -1, "", current_mode, "TEST_PHOTO_MODE", new ButtonOptionsPopupListener() {
                    public void onClick(String option) {
                        int option_id = -1;
                        for (int i = 0; i < list.size() && option_id == -1; i++) {
                            if (option.equals(list.get(i))) {
                                option_id = i;
                            }
                        }
                        if (option_id != -1) {
                            PhotoMode new_photo_mode = (PhotoMode) list2.get(option_id);
                            String toast_message = option;
                            if (new_photo_mode == PhotoMode.ExpoBracketing) {
                                toast_message = PopupView.this.getResources().getString(R.string.photo_mode_expo_bracketing_full);
                            }
                            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity2);
                            Editor editor = sharedPreferences.edit();
                            if (new_photo_mode == PhotoMode.Standard) {
                                editor.putString(PreferenceKeys.getPhotoModePreferenceKey(), "preference_photo_mode_std");
                            } else if (new_photo_mode == PhotoMode.DRO) {
                                editor.putString(PreferenceKeys.getPhotoModePreferenceKey(), "preference_photo_mode_dro");
                            } else if (new_photo_mode == PhotoMode.HDR) {
                                editor.putString(PreferenceKeys.getPhotoModePreferenceKey(), "preference_photo_mode_hdr");
                            } else if (new_photo_mode == PhotoMode.ExpoBracketing) {
                                editor.putString(PreferenceKeys.getPhotoModePreferenceKey(), "preference_photo_mode_expo_bracketing");
                            }
                            editor.apply();
                            boolean done_dialog = false;
                            if (new_photo_mode == PhotoMode.HDR && !sharedPreferences.contains(PreferenceKeys.getHDRInfoPreferenceKey())) {
                                PopupView.this.showInfoDialog(R.string.photo_mode_hdr, R.string.hdr_info, PreferenceKeys.getHDRInfoPreferenceKey());
                                done_dialog = true;
                            }
                            if (done_dialog) {
                                toast_message = null;
                            }
                            mainActivity2.updateForSettings(toast_message);
                        }
                    }
                });
            }
            if (main_activity.supportsAutoStabilise()) {
                CheckBox checkBox = new CheckBox(main_activity);
                checkBox.setText(getResources().getString(R.string.preference_auto_stabilise));
                checkBox.setTextColor(-1);
                boolean auto_stabilise = sharedPreferences.getBoolean(PreferenceKeys.getAutoStabilisePreferenceKey(), false);
                if (auto_stabilise) {
                    checkBox.setChecked(auto_stabilise);
                }
                final MainActivity context2 = main_activity;
                preview3 = preview;
                final Preview preview12 = preview3;
                checkBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context2);
                        Editor editor = sharedPreferences.edit();
                        editor.putBoolean(PreferenceKeys.getAutoStabilisePreferenceKey(), isChecked);
                        editor.apply();
                        boolean done_dialog = false;
                        if (isChecked && !sharedPreferences.contains(PreferenceKeys.getAutoStabiliseInfoPreferenceKey())) {
                            PopupView.this.showInfoDialog(R.string.preference_auto_stabilise, R.string.auto_stabilise_info, PreferenceKeys.getAutoStabiliseInfoPreferenceKey());
                            done_dialog = true;
                        }
                        if (!done_dialog) {
                            preview12.showToast(context2.getChangedAutoStabiliseToastBoxer(), PopupView.this.getResources().getString(R.string.preference_auto_stabilise) +
                                    ": " + PopupView.this.getResources().getString(isChecked ? R.string.on : R.string.off));
                        }
                    }
                });
                addView(checkBox);
            }
            List<Size> picture_sizes = preview.getSupportedPictureSizes();
            this.picture_size_index = preview.getCurrentPictureSizeIndex();
            List<String> picture_size_strings = new ArrayList();
            for (Size picture_size : picture_sizes) {
                picture_size_strings.add(picture_size.width + " x " + picture_size.height);
            }
            final MainActivity context3 = main_activity;
            final List<Size> list3 = picture_sizes;
            final Preview preview5 = preview;
            addArrayOptionsToPopup(picture_size_strings, getResources().getString(R.string.preference_resolution), false, this.picture_size_index, false, "PHOTO_RESOLUTIONS", new ArrayOptionsPopupListener() {
                final Handler handler = new Handler();
                final Runnable update_runnable = new Runnable() {
                    public void run() {
                        context3.updateForSettings("");
                    }
                };

                private void update() {
                    if (PopupView.this.picture_size_index != -1) {
                        Size new_size = (Size) list3.get(PopupView.this.picture_size_index);
                        String resolution_string = new_size.width + " " + new_size.height;
                        Editor editor = PreferenceManager.getDefaultSharedPreferences(context3).edit();
                        editor.putString(PreferenceKeys.getResolutionPreferenceKey(preview5.getCameraId()), resolution_string);
                        editor.apply();
                        this.handler.removeCallbacks(this.update_runnable);
                        this.handler.postDelayed(this.update_runnable, 400);
                    }
                }

                public int onClickPrev() {
                    if (PopupView.this.picture_size_index == -1 || PopupView.this.picture_size_index <= 0) {
                        return -1;
                    }
                    PopupView.this.picture_size_index = PopupView.this.picture_size_index - 1;
                    update();
                    return PopupView.this.picture_size_index;
                }

                public int onClickNext() {
                    if (PopupView.this.picture_size_index == -1 || PopupView.this.picture_size_index >= list3.size() - 1) {
                        return -1;
                    }
                    PopupView.this.picture_size_index = PopupView.this.picture_size_index + 1;
                    update();
                    return PopupView.this.picture_size_index;
                }
            });

            String[] timer_values = getResources().getStringArray(R.array.preference_timer_values);
            String[] timer_entries = getResources().getStringArray(R.array.preference_timer_entries);
            this.timer_index = Arrays.asList(timer_values).indexOf(sharedPreferences.getString(PreferenceKeys.getTimerPreferenceKey(), "0"));
            if (this.timer_index == -1) {
                this.timer_index = 0;
            }
            final String[] strArr = timer_values;
            final Context context4 = main_activity;
            addArrayOptionsToPopup(Arrays.asList(timer_entries), getResources().getString(R.string.preference_timer), true, this.timer_index, false, "TIMER", new ArrayOptionsPopupListener() {
                private void update() {
                    if (PopupView.this.timer_index != -1) {
                        String new_timer_value = strArr[PopupView.this.timer_index];
                        Editor editor = PreferenceManager.getDefaultSharedPreferences(context4).edit();
                        editor.putString(PreferenceKeys.getTimerPreferenceKey(), new_timer_value);
                        editor.apply();
                    }
                }

                public int onClickPrev() {
                    if (PopupView.this.timer_index == -1 || PopupView.this.timer_index <= 0) {
                        return -1;
                    }
                    PopupView.this.timer_index = PopupView.this.timer_index - 1;
                    update();
                    return PopupView.this.timer_index;
                }

                public int onClickNext() {
                    if (PopupView.this.timer_index == -1 || PopupView.this.timer_index >= strArr.length - 1) {
                        return -1;
                    }
                    PopupView.this.timer_index = PopupView.this.timer_index + 1;
                    update();
                    return PopupView.this.timer_index;
                }
            });
            String[] burst_mode_values = getResources().getStringArray(R.array.preference_burst_mode_values);
            String[] burst_mode_entries = getResources().getStringArray(R.array.preference_burst_mode_entries);
            this.burst_mode_index = Arrays.asList(burst_mode_values).indexOf(sharedPreferences.getString(PreferenceKeys.getBurstModePreferenceKey(), "1"));
            if (this.burst_mode_index == -1) {
                this.burst_mode_index = 0;
            }
            addArrayOptionsToPopup(Arrays.asList(burst_mode_entries), getResources().getString(R.string.preference_burst_mode), true, this.burst_mode_index, false, "BURST_MODE", new ArrayOptionsPopupListener() {
                private void update() {
                    if (PopupView.this.burst_mode_index != -1) {
                        String new_burst_mode_value = strArr[PopupView.this.burst_mode_index];
                        Editor editor = PreferenceManager.getDefaultSharedPreferences(context4).edit();
                        editor.putString(PreferenceKeys.getBurstModePreferenceKey(), new_burst_mode_value);
                        editor.apply();
                    }
                }

                public int onClickPrev() {
                    if (PopupView.this.burst_mode_index == -1 || PopupView.this.burst_mode_index <= 0) {
                        return -1;
                    }
                    PopupView.this.burst_mode_index = PopupView.this.burst_mode_index - 1;
                    update();
                    return PopupView.this.burst_mode_index;
                }

                public int onClickNext() {
                    if (PopupView.this.burst_mode_index == -1 || PopupView.this.burst_mode_index >= strArr.length - 1) {
                        return -1;
                    }
                    PopupView.this.burst_mode_index = PopupView.this.burst_mode_index + 1;
                    update();
                    return PopupView.this.burst_mode_index;
                }
            });
            String[] grid_values = getResources().getStringArray(R.array.preference_grid_values);
            String[] grid_entries = getResources().getStringArray(R.array.preference_grid_entries);
            this.grid_index = Arrays.asList(grid_values).indexOf(sharedPreferences.getString(PreferenceKeys.getShowGridPreferenceKey(), "preference_grid_none"));
            if (this.grid_index == -1) {
                this.grid_index = 0;
            }
            addArrayOptionsToPopup(Arrays.asList(grid_entries), getResources().getString(R.string.grid), false, this.grid_index, true, "GRID", new ArrayOptionsPopupListener() {
                private void update() {
                    if (PopupView.this.grid_index != -1) {
                        String new_grid_value = strArr[PopupView.this.grid_index];
                        Editor editor = PreferenceManager.getDefaultSharedPreferences(context4).edit();
                        editor.putString(PreferenceKeys.getShowGridPreferenceKey(), new_grid_value);
                        editor.apply();
                    }
                }

                public int onClickPrev() {
                    if (PopupView.this.grid_index == -1) {
                        return -1;
                    }
                    PopupView.this.grid_index = PopupView.this.grid_index - 1;
                    if (PopupView.this.grid_index < 0) {
                        PopupView.this.grid_index = PopupView.this.grid_index + strArr.length;
                    }
                    update();
                    return PopupView.this.grid_index;
                }

                public int onClickNext() {
                    if (PopupView.this.grid_index == -1) {
                        return -1;
                    }
                    PopupView.this.grid_index = PopupView.this.grid_index + 1;
                    if (PopupView.this.grid_index >= strArr.length) {
                        PopupView.this.grid_index = PopupView.this.grid_index - strArr.length;
                    }
                    update();
                    return PopupView.this.grid_index;
                }
            });
            if (preview.getCameraController() != null) {
                preview3 = preview;
                final Preview preview11 = preview3;
                addRadioOptionsToPopup(sharedPreferences, preview.getSupportedWhiteBalances(), getResources().getString(R.string.white_balance), PreferenceKeys.getWhiteBalancePreferenceKey(), preview.getCameraController().getDefaultWhiteBalance(), "TEST_WHITE_BALANCE", new RadioOptionsListener() {
                    public void onClick(String selected_option) {
                        if (selected_option.equals("manual") && preview11.getCameraController() != null) {
                            String current_white_balance = preview11.getCameraController().getWhiteBalance();
                            if ((current_white_balance == null || !current_white_balance.equals("manual")) &&
                                    preview11.getCameraController().captureResultHasWhiteBalanceTemperature()) {
                                int temperature = preview11.getCameraController().captureResultWhiteBalanceTemperature();
                                Editor editor = PreferenceManager.getDefaultSharedPreferences(context4).edit();
                                editor.putInt(PreferenceKeys.getWhiteBalanceTemperaturePreferenceKey(), temperature);
                                editor.apply();
                            }
                        }
                    }
                });
                addRadioOptionsToPopup(sharedPreferences, preview.getSupportedSceneModes(), getResources().getString(R.string.scene_mode), PreferenceKeys.getSceneModePreferenceKey(), preview.getCameraController().getDefaultSceneMode(), "TEST_SCENE_MODE", null);
                addRadioOptionsToPopup(sharedPreferences, preview.getSupportedColorEffects(), getResources().getString(R.string.color_effect), PreferenceKeys.getColorEffectPreferenceKey(), preview.getCameraController().getDefaultColorEffect(), "TEST_COLOR_EFFECT", null);
            }
        }
    }

    private void addButtonOptionsToPopup(List<String> supported_options, int icons_id, int values_id, String prefix_string, String current_value, String test_key, ButtonOptionsPopupListener listener) {
        if (supported_options != null) {
            long debug_time = System.nanoTime();
            View linearLayout = new LinearLayout(getContext());
            ((LinearLayout)linearLayout).setOrientation(LinearLayout.HORIZONTAL);
            String[] icons = icons_id != -1 ? getResources().getStringArray(icons_id) : null;
            String[] values = values_id != -1 ? getResources().getStringArray(values_id) : null;
            float scale = getResources().getDisplayMetrics().density;
            int button_width_dp = this.total_width_dp / supported_options.size();
            boolean use_scrollview = false;
            if (button_width_dp < 40) {
                button_width_dp = 40;
                use_scrollview = true;
            }
            final int button_width = (int) ((((float) button_width_dp) * scale) + 0.5f);
            final ButtonOptionsPopupListener buttonOptionsPopupListener = listener;
            OnClickListener anonymousClass12 = new OnClickListener() {
                public void onClick(View v) {
                    buttonOptionsPopupListener.onClick((String) v.getTag());
                }
            };
            View current_view = null;
            for (String supported_option : supported_options) {
                String button_string;
                View view;
                int resource = -1;
                if (!(icons == null || values == null)) {
                    int index = -1;
                    for (int i = 0; i < values.length && index == -1; i++) {
                        if (values[i].equals(supported_option)) {
                            index = i;
                        }
                    }
                    if (index != -1) {
                        resource = getResources().getIdentifier(icons[index], null, getContext().getApplicationContext().getPackageName());
                    }
                }
                if (prefix_string.length() == 0) {
                    button_string = supported_option;
                } else if (prefix_string.equalsIgnoreCase("ISO") && supported_option.length() >= 4 && supported_option.substring(0, 4).equalsIgnoreCase("ISO_")) {
                    button_string = prefix_string + "\n" + supported_option.substring(4);
                } else if (prefix_string.equalsIgnoreCase("ISO") && supported_option.length() >= 3 && supported_option.substring(0, 3).equalsIgnoreCase("ISO")) {
                    button_string = prefix_string + "\n" + supported_option.substring(3);
                } else {
                    button_string = prefix_string + "\n" + supported_option;
                }
                int padding;

                if (resource != -1) {
                    linearLayout = new ImageButton(getContext());
                    view = linearLayout;
                    ((ViewGroup)linearLayout).addView(view);
                    Bitmap bm = ((MainActivity) getContext()).getPreloadedBitmap(resource);
                    if (bm != null) {
                        ((ImageButton)linearLayout).setImageBitmap(bm);
                    }
                    ((ImageButton)linearLayout).setScaleType(ScaleType.FIT_CENTER);
                    padding = (int) ((10.0f * scale) + 0.5f);
                    view.setPadding(padding, padding, padding, padding);
                } else {
                    Button button = new Button(getContext());
                    button.setBackgroundColor(0);
                    view = button;
                    ((ViewGroup)linearLayout).addView(view);
                    button.setText(button_string);
                    button.setTextSize(1, 12.0f);
                    button.setTextColor(-1);
                    padding = (int) ((0.0f * scale) + 0.5f);
                    view.setPadding(padding, padding, padding, padding);
                }
                LinearLayout.LayoutParams params = (LayoutParams) view.getLayoutParams();
                params.width = button_width;
                params.height = (int) ((50.0f * scale) + 0.5f);
                view.setLayoutParams(params);
                view.setContentDescription(button_string);
                if (supported_option.equals(current_value)) {
                    view.setAlpha(ALPHA_BUTTON_SELECTED);
                    current_view = view;
                } else {
                    view.setAlpha(ALPHA_BUTTON);
                }
                view.setTag(supported_option);
                view.setOnClickListener(anonymousClass12);
                this.popup_buttons.put(test_key + "_" + supported_option, view);
            }
            if (use_scrollview) {
                linearLayout = new HorizontalScrollView(getContext());
                ((HorizontalScrollView)linearLayout).addView(linearLayout);
                linearLayout.setLayoutParams(new LayoutParams(this.total_width, -2));
                addView(linearLayout);
                if (current_view != null) {
                    final View final_current_view = current_view;
                    final View view2 = linearLayout;
                    getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
                        public void onGlobalLayout() {
                            int jump_x = Math.min(final_current_view.getLeft() - ((PopupView.this.total_width - button_width) / 2), PopupView.this.total_width - 1);
                            if (jump_x > 0) {
                                view2.scrollTo(jump_x, 0);
                            }
                        }
                    });
                    return;
                }
                return;
            }
            addView(linearLayout);
        }
    }

    private void addTitleToPopup(String title) {
        TextView text_view = new TextView(getContext());
        text_view.setText(title + ":");
        text_view.setTextColor(-1);
        text_view.setGravity(17);
        text_view.setTypeface(null, 1);
        addView(text_view);
    }

    private void addRadioOptionsToPopup(SharedPreferences sharedPreferences, List<String> supported_options, String title, String preference_key, String default_option, String test_key, RadioOptionsListener listener) {
        if (supported_options != null) {
            final MainActivity main_activity = (MainActivity) getContext();
            long debug_time = System.nanoTime();
            Button button = new Button(getContext());
            button.setBackgroundColor(0);
            button.setText(title + "...");
            addView(button);
            final RadioGroup rg = new RadioGroup(getContext());
            rg.setOrientation(LinearLayout.VERTICAL);
            rg.setVisibility(GONE);
            this.popup_buttons.put(test_key, rg);
            final String str = title;
            final SharedPreferences sharedPreferences2 = sharedPreferences;
            final List<String> list = supported_options;
            final String str2 = preference_key;
            final String str3 = default_option;
            final String str4 = test_key;
            final RadioOptionsListener radioOptionsListener = listener;
            button.setOnClickListener(new OnClickListener() {
                private boolean created = false;
                private boolean opened = false;

                public void onClick(View view) {
                    final ScrollView popup_container;
                    if (this.opened) {
                        rg.setVisibility(GONE);
                    } else {
                        if (!this.created) {
                            PopupView.this.addRadioOptionsToGroup(rg, sharedPreferences2, list, str, str2, str3, str4, radioOptionsListener);
                            this.created = true;
                        }
                        rg.setVisibility(VISIBLE);
                    }
                    this.opened = !this.opened;
                }
            });
            addView(rg);
        }
    }

    private void addRadioOptionsToGroup(RadioGroup rg, SharedPreferences sharedPreferences, List<String> supported_options, String title, String preference_key, String default_option, String test_key, RadioOptionsListener listener) {
        String current_option = sharedPreferences.getString(preference_key, default_option);
        long debug_time = System.nanoTime();
        final MainActivity main_activity = (MainActivity) getContext();
        int count = 0;
        for (final String supported_option : supported_options) {
            RadioButton button = new RadioButton(getContext());
            button.setId(count);
            button.setText(supported_option);
            button.setTextColor(-1);
            rg.addView(button);
            if (supported_option.equals(current_option)) {
                rg.check(count);
            }
            count++;
            button.setContentDescription(supported_option);
            final RadioOptionsListener radioOptionsListener = listener;
            final String str = preference_key;
            final String str2 = title;
            button.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    if (radioOptionsListener != null) {
                        radioOptionsListener.onClick(supported_option);
                    }
                    Editor editor = PreferenceManager.getDefaultSharedPreferences(main_activity).edit();
                    editor.putString(str, supported_option);
                    editor.apply();
                    main_activity.updateForSettings(str2 + ": " + supported_option);
                }
            });
            this.popup_buttons.put(test_key + "_" + supported_option, button);
        }
    }

    private void addArrayOptionsToPopup(List<String> supported_options, String title, boolean title_in_options, int current_index, boolean cyclic, String test_key, ArrayOptionsPopupListener listener) {
        if (supported_options != null && current_index != -1) {
            if (!title_in_options) {
                addTitleToPopup(title);
            }
            LinearLayout ll2 = new LinearLayout(getContext());
            ll2.setOrientation(LinearLayout.HORIZONTAL);
            final TextView resolution_text_view = new TextView(getContext());
            if (title_in_options) {
                resolution_text_view.setText(title + ": " + ((String) supported_options.get(current_index)));
            } else {
                resolution_text_view.setText((CharSequence) supported_options.get(current_index));
            }
            resolution_text_view.setTextColor(-1);
            resolution_text_view.setGravity(17);
            resolution_text_view.setLayoutParams(new LayoutParams(-2, -2, ALPHA_BUTTON_SELECTED));
            float scale = getResources().getDisplayMetrics().density;
            int padding = (int) ((0.0f * scale) + 0.5f);
            int button_w = (int) ((60.0f * scale) + 0.5f);
            int button_h = (int) ((30.0f * scale) + 0.5f);
            final Button prev_button = new Button(getContext());
            prev_button.setBackgroundColor(0);
            ll2.addView(prev_button);
            prev_button.setText("<");
            prev_button.setTextSize(1, 12.0f);
            prev_button.setPadding(padding, padding, padding, padding);
            LayoutParams vg_params = (LayoutParams) prev_button.getLayoutParams();
            vg_params.width = button_w;
            vg_params.height = button_h;
            prev_button.setLayoutParams(vg_params);
            int i = (cyclic || current_index > 0) ? 0 : 4;
            prev_button.setVisibility(i);
            this.popup_buttons.put(test_key + "_PREV", prev_button);
            ll2.addView(resolution_text_view);
            this.popup_buttons.put(test_key, resolution_text_view);
            final Button next_button = new Button(getContext());
            next_button.setBackgroundColor(0);
            ll2.addView(next_button);
            next_button.setText(">");
            next_button.setTextSize(1, 12.0f);
            next_button.setPadding(padding, padding, padding, padding);
            vg_params = (LayoutParams) next_button.getLayoutParams();
            vg_params.width = button_w;
            vg_params.height = button_h;
            next_button.setLayoutParams(vg_params);
            i = (cyclic || current_index < supported_options.size() - 1) ? 0 : 4;
            next_button.setVisibility(i);
            this.popup_buttons.put(test_key + "_NEXT", next_button);
            final ArrayOptionsPopupListener arrayOptionsPopupListener = listener;
            final boolean z = title_in_options;
            final String str = title;
            final List<String> list = supported_options;
            final boolean z2 = cyclic;
            prev_button.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    int i = 4;
                    int new_index = arrayOptionsPopupListener.onClickPrev();
                    if (new_index != -1) {
                        int i2;
                        if (z) {
                            resolution_text_view.setText(str + ": " + ((String) list.get(new_index)));
                        } else {
                            resolution_text_view.setText((CharSequence) list.get(new_index));
                        }
                        Button button = prev_button;
                        if (z2 || new_index > 0) {
                            i2 = 0;
                        } else {
                            i2 = 4;
                        }
                        button.setVisibility(i2);
                        Button button2 = next_button;
                        if (z2 || new_index < list.size() - 1) {
                            i = 0;
                        }
                        button2.setVisibility(i);
                    }
                }
            });
//            arrayOptionsPopupListener = listener;
//            z = title_in_options;
//            str = title;
//            list = supported_options;
//            z2 = cyclic;
            next_button.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    int i = 4;
                    int new_index = arrayOptionsPopupListener.onClickNext();
                    if (new_index != -1) {
                        int i2;
                        if (z) {
                            resolution_text_view.setText(str + ": " + ((String) list.get(new_index)));
                        } else {
                            resolution_text_view.setText((CharSequence) list.get(new_index));
                        }
                        Button button = prev_button;
                        if (z2 || new_index > 0) {
                            i2 = 0;
                        } else {
                            i2 = 4;
                        }
                        button.setVisibility(i2);
                        Button button2 = next_button;
                        if (z2 || new_index < list.size() - 1) {
                            i = 0;
                        }
                        button2.setVisibility(i);
                    }
                }
            });
            addView(ll2);
        }
    }

    private void showInfoDialog(int title_id, int info_id, final String info_preference_key) {
        final MainActivity main_activity = (MainActivity) getContext();
        Builder alertDialog = new Builder(getContext());
        alertDialog.setTitle(title_id);
        alertDialog.setMessage(info_id);
        alertDialog.setPositiveButton(17039370, null);
        alertDialog.setNegativeButton(R.string.dont_show_again, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                Editor editor = PreferenceManager.getDefaultSharedPreferences(main_activity).edit();
                editor.putBoolean(info_preference_key, true);
                editor.apply();
            }
        });
        main_activity.showPreview(false);
        main_activity.setWindowFlagsForSettings();
        AlertDialog alert = alertDialog.create();
        alert.setOnDismissListener(new OnDismissListener() {
            public void onDismiss(DialogInterface arg0) {
                main_activity.setWindowFlagsForCamera();
                main_activity.showPreview(true);
            }
        });
        alert.show();
    }

    public View getPopupButton(String key) {
        return (View) this.popup_buttons.get(key);
    }
}
