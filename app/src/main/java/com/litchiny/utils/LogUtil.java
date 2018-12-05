package com.litchiny.utils;

import android.util.Log;

/**
 * Created by ll on 2018/7/18.
 */

public class LogUtil {
    private static final String TAG = "LogUtil";
    private static boolean isDebug = true;

    public static void i(String message) {
        i(TAG, message);
    }

    public static void i(String TAG, String message) {
        if (isDebug)
            Log.i(TAG, message);
    }
}
