package com.litchiny.camera;

import android.content.Context;
import android.location.Location;

import com.lwyy.camera.R;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TextFormatter {
    private static final String TAG = "TextFormatter";
    private final Context context;
    private final DecimalFormat decimalFormat = new DecimalFormat("#0.0");

    TextFormatter(Context context) {
        this.context = context;
    }

    public static String getDateString(String preference_stamp_dateformat, Date date) {
        String date_stamp = "";
        if (preference_stamp_dateformat.equals("preference_stamp_dateformat_none")) {
            return date_stamp;
        }
        int obj = -1;
        switch (preference_stamp_dateformat.hashCode()) {
            case -1966818982:
                if (preference_stamp_dateformat.equals("preference_stamp_dateformat_ddmmyyyy")) {
                    obj = 1;
                    break;
                }
                break;
            case -34803366:
                if (preference_stamp_dateformat.equals("preference_stamp_dateformat_mmddyyyy")) {
                    obj = 2;
                    break;
                }
                break;
            case 2084430170:
                if (preference_stamp_dateformat.equals("preference_stamp_dateformat_yyyymmdd")) {
                    obj = 3;
                    break;
                }
                break;
        }
        switch (obj) {
            case 3:
                return new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(date);
            case 1:
                return new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date);
            case 2:
                return new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).format(date);
            default:
                return DateFormat.getDateInstance().format(date);
        }
    }

    public static String getTimeString(String preference_stamp_timeformat, Date date) {
        String time_stamp = "";
        if (preference_stamp_timeformat.equals("preference_stamp_timeformat_none")) {
            return time_stamp;
        }
        int obj = -1;
        switch (preference_stamp_timeformat.hashCode()) {
            case 2061556288:
                if (preference_stamp_timeformat.equals("preference_stamp_timeformat_12hour")) {
                    obj = 2;
                    break;
                }
                break;
            case 2092032481:
                if (preference_stamp_timeformat.equals("preference_stamp_timeformat_24hour")) {
                    obj = 1;
                    break;
                }
                break;
        }
        switch (obj) {
            case 2:
                return new SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(date);
            case 1:
                return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(date);
            default:
                return DateFormat.getTimeInstance().format(date);
        }
    }

    public String getGPSString(String preference_stamp_gpsformat, boolean store_location, Location location, boolean store_geo_direction, double geo_direction) {
        String gps_stamp = "";
        if (preference_stamp_gpsformat.equals("preference_stamp_gpsformat_none")) {
            return gps_stamp;
        }
        if (store_location) {
            if (!preference_stamp_gpsformat.equals("preference_stamp_gpsformat_dms")) {
                gps_stamp = gps_stamp + Location.convert(location.getLatitude(), 0) + ", " + Location.convert(location.getLongitude(), 0);
            }
            if (location.hasAltitude()) {
                gps_stamp = gps_stamp + ", " + this.decimalFormat.format(location.getAltitude()) + this.context.getResources().getString(R.string.metres_abbreviation);
            }
        }
        if (!store_geo_direction) {
            return gps_stamp;
        }
        float geo_angle = (float) Math.toDegrees(geo_direction);
        if (geo_angle < 0.0f) {
            geo_angle += 360.0f;
        }
        if (gps_stamp.length() > 0) {
            gps_stamp = gps_stamp + ", ";
        }
        return gps_stamp + "" + Math.round(geo_angle) + '°';
    }

    public static String formatTimeMS(long time_ms) {
        int ms = ((int) time_ms) % 1000;
        int seconds = ((int) (time_ms / 1000)) % 60;
        int minutes = (int) ((time_ms / 60000) % 60);
        int hours = (int) (time_ms / 3600000);
        return String.format(Locale.getDefault(), "%02d:%02d:%02d,%03d", new Object[]{Integer.valueOf(hours), Integer.valueOf(minutes), Integer.valueOf(seconds), Integer.valueOf(ms)});
    }
}
