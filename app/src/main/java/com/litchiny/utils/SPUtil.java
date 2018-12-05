package com.litchiny.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;

import com.litchiny.camera.PreferenceKeys;

import java.util.Locale;

/**
 * Created by ll on 2018/8/26.
 */

public class SPUtil {
    public static void setDeviceDefaults(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean is_samsung = Build.MANUFACTURER.toLowerCase(Locale.US).contains("samsung");
        boolean is_oneplus = Build.MANUFACTURER.toLowerCase(Locale.US).contains("oneplus");
        if (is_samsung || is_oneplus) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(PreferenceKeys.getCamera2FakeFlashPreferenceKey(), true);
            editor.apply();
        }
    }

    public static void setFirstTimeFlag(Context context) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putBoolean(PreferenceKeys.getFirstTimePreferenceKey(), true);
        editor.apply();
    }
}
