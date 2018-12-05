package com.litchiny.utils;

import android.app.Activity;
import android.os.Build;
import android.os.StatFs;
import android.preference.PreferenceManager;

import com.litchiny.camera.MainActivity;
import com.litchiny.camera.MyApplicationInterface;
import com.litchiny.camera.PreferenceKeys;
import com.litchiny.camera.ui.Preview;
import com.litchiny.camera.StorageUtils;

import java.io.File;

/**
 * Created by ll on 2018/7/18.
 */

public class MainUtil {
    public static boolean supportsDRO() {
        return Build.VERSION.SDK_INT >= 21;
    }

    public static boolean supportsExposureButton(MainActivity activity) {
        Preview preview = activity.getPreview();
        if (preview.getCameraController() == null) {
            return false;
        }
        boolean manual_iso;
        if (PreferenceManager.getDefaultSharedPreferences(activity).getString(PreferenceKeys.getISOPreferenceKey(), preview.getCameraController().getDefaultISO()).equals("auto")) {
            manual_iso = false;
        } else {
            manual_iso = true;
        }
        if (preview.supportsExposures() || (manual_iso && preview.supportsISORange())) {
            return true;
        }
        return false;
    }


    public static boolean supportsHDR(MainActivity activity) {
        return Build.VERSION.SDK_INT >= 21 && activity.supportsAutoStabilise() && activity.getPreview().supportsExpoBracketing();
    }

    public static boolean supportsExpoBracketing(MainActivity activity) {
        return activity.getPreview().supportsExpoBracketing();
    }

    public static int maxExpoBracketingNImages(MainActivity activity) {
        return activity.getPreview().maxExpoBracketingNImages();
    }

    public static long freeMemory(MyApplicationInterface applicationInterface) {
        StatFs statFs;
        try {
            File folder = applicationInterface.getStorageUtils().getImageFolder();
            if (folder == null) {
                throw new IllegalArgumentException();
            }
            statFs = new StatFs(folder.getAbsolutePath());
            return (((long) statFs.getAvailableBlocks()) * ((long) statFs.getBlockSize())) / 1048576;
        } catch (IllegalArgumentException e) {
            try {
                if (!(applicationInterface.getStorageUtils().isUsingSAF() || applicationInterface.getStorageUtils().getSaveLocation().startsWith("/"))) {
                    statFs = new StatFs(StorageUtils.getBaseFolder().getAbsolutePath());
                    return (((long) statFs.getAvailableBlocks()) * ((long) statFs.getBlockSize())) / 1048576;
                }
            } catch (IllegalArgumentException e2) {
            }
            return -1;
        }
    }

    public static boolean usingKitKatImmersiveMode(Activity activity) {
        String immersive_mode = PreferenceManager.getDefaultSharedPreferences(activity).getString(PreferenceKeys.getImmersiveModePreferenceKey(), "immersive_mode_low_profile");
        if (immersive_mode.equals("immersive_mode_gui") || immersive_mode.equals("immersive_mode_everything")) {
            return true;
        }
        return false;
    }

    public static boolean usingKitKatImmersiveModeEverything(Activity activity) {
        if (!PreferenceManager.getDefaultSharedPreferences(activity).getString(PreferenceKeys.getImmersiveModePreferenceKey(), "immersive_mode_low_profile").equals("immersive_mode_everything")) {
            return false;
        }
        return true;
    }
}
