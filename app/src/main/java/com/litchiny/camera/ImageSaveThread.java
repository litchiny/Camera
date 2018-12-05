package com.litchiny.camera;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.hardware.camera2.DngCreator;
import android.location.Location;
import android.media.ExifInterface;
import android.media.Image;
import android.net.Uri;
import android.os.Build.VERSION;
import android.support.annotation.RequiresApi;
import android.support.v4.view.ViewCompat;
import android.util.Log;

import com.lwyy.camera.R;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import com.litchiny.camera.ui.PopupView;

public class ImageSaveThread extends Thread {
    private static final String TAG = "ImageSaveThread";
    private static final String TAG_DATETIME_DIGITIZED = "DateTimeDigitized";
    private static final String TAG_DATETIME_ORIGINAL = "DateTimeOriginal";
    private static final String TAG_GPS_IMG_DIRECTION = "GPSImgDirection";
    private static final String TAG_GPS_IMG_DIRECTION_REF = "GPSImgDirectionRef";
    private final HDRProcessor hdrProcessor;
    private final MainActivity main_activity;
    private int n_images_to_save = 0;
    private final Paint p = new Paint();
    private final BlockingQueue<Request> queue = new ArrayBlockingQueue(1);

    private static class LoadBitmapThread extends Thread {
        Bitmap bitmap;
        final byte[] jpeg;
        final Options options;

        LoadBitmapThread(Options options, byte[] jpeg) {
            this.options = options;
            this.jpeg = jpeg;
        }

        public void run() {
            this.bitmap = BitmapFactory.decodeByteArray(this.jpeg, 0, this.jpeg.length, this.options);
        }
    }

    private static class Request {
        final int color;
        final Date current_date;
        final DngCreator dngCreator;
        final boolean do_auto_stabilise;
        final int font_size;
        final double geo_direction;
        final Image image;
        final boolean image_capture_intent;
        final Uri image_capture_intent_uri;
        final int image_quality;
        final boolean is_front_facing;
        final boolean is_hdr;
        final List<byte[]> jpeg_images;
        final double level_angle;
        final Location location;
        final boolean mirror;
        final String pref_style;
        final String preference_stamp;
        final String preference_stamp_dateformat;
        final String preference_stamp_gpsformat;
        final String preference_stamp_timeformat;
        final String preference_textstamp;
        int sample_factor = 1;
        final boolean save_expo;
        final boolean store_geo_direction;
        final boolean store_location;
        Type type = Type.JPEG;
        final boolean using_camera2;

        enum Type {
            JPEG,
            RAW,
            DUMMY
        }

        Request(Type type, boolean is_hdr, boolean save_expo, List<byte[]> jpeg_images, DngCreator dngCreator, Image image, boolean image_capture_intent, Uri image_capture_intent_uri, boolean using_camera2, int image_quality, boolean do_auto_stabilise, double level_angle, boolean is_front_facing, boolean mirror, Date current_date, String preference_stamp, String preference_textstamp, int font_size, int color, String pref_style, String preference_stamp_dateformat, String preference_stamp_timeformat, String preference_stamp_gpsformat, boolean store_location, Location location, boolean store_geo_direction, double geo_direction, int sample_factor) {
            this.type = type;
            this.is_hdr = is_hdr;
            this.save_expo = save_expo;
            this.jpeg_images = jpeg_images;
            this.dngCreator = dngCreator;
            this.image = image;
            this.image_capture_intent = image_capture_intent;
            this.image_capture_intent_uri = image_capture_intent_uri;
            this.using_camera2 = using_camera2;
            this.image_quality = image_quality;
            this.do_auto_stabilise = do_auto_stabilise;
            this.level_angle = level_angle;
            this.is_front_facing = is_front_facing;
            this.mirror = mirror;
            this.current_date = current_date;
            this.preference_stamp = preference_stamp;
            this.preference_textstamp = preference_textstamp;
            this.font_size = font_size;
            this.color = color;
            this.pref_style = pref_style;
            this.preference_stamp_dateformat = preference_stamp_dateformat;
            this.preference_stamp_timeformat = preference_stamp_timeformat;
            this.preference_stamp_gpsformat = preference_stamp_gpsformat;
            this.store_location = store_location;
            this.location = location;
            this.store_geo_direction = store_geo_direction;
            this.geo_direction = geo_direction;
            this.sample_factor = sample_factor;
        }
    }

    ImageSaveThread(MainActivity main_activity) {
        this.main_activity = main_activity;
        this.hdrProcessor = new HDRProcessor(main_activity);
        this.p.setAntiAlias(true);
    }

    void onDestroy() {
        if (this.hdrProcessor != null) {
            this.hdrProcessor.onDestroy();
        }
    }

    public void run() {
        while (true) {
            try {
                Request request = this.queue.take();
                boolean success;
                if (request.type == Request.Type.RAW) {
                    success = saveImageNowRaw(request.dngCreator, request.image, request.current_date);
                } else if (request.type == Request.Type.JPEG) {
                    success = saveImageNow(request);
                } else if (request.type == Request.Type.DUMMY) {
                }
                synchronized (this) {
                    this.n_images_to_save--;
                    notifyAll();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    boolean saveImageJpeg(boolean do_in_background, boolean is_hdr, boolean save_expo, List<byte[]> images, boolean image_capture_intent, Uri image_capture_intent_uri, boolean using_camera2, int image_quality, boolean do_auto_stabilise, double level_angle, boolean is_front_facing, boolean mirror, Date current_date, String preference_stamp, String preference_textstamp, int font_size, int color, String pref_style, String preference_stamp_dateformat, String preference_stamp_timeformat, String preference_stamp_gpsformat,
                          boolean store_location, Location location, boolean store_geo_direction, double geo_direction, int sample_factor) {
        return saveImage(do_in_background, false, is_hdr, save_expo, images, null, null, image_capture_intent, image_capture_intent_uri, using_camera2, image_quality, do_auto_stabilise, level_angle, is_front_facing, mirror, current_date, preference_stamp, preference_textstamp, font_size, color, pref_style, preference_stamp_dateformat, preference_stamp_timeformat, preference_stamp_gpsformat, store_location, location, store_geo_direction, geo_direction, sample_factor);
    }

    boolean saveImageRaw(boolean do_in_background, DngCreator dngCreator, Image image, Date current_date) {
        return saveImage(do_in_background, true, false, false, null, dngCreator, image, false, null, false, 0, false, 0.0d, false, false, current_date, null, null, 0, 0, null, null, null, null, false, null, false, 0.0d, 1);
    }

    private boolean saveImage(boolean do_in_background, boolean is_raw, boolean is_hdr, boolean save_expo, List<byte[]> jpeg_images, DngCreator dngCreator, Image image, boolean image_capture_intent, Uri image_capture_intent_uri, boolean using_camera2, int image_quality, boolean do_auto_stabilise, double level_angle, boolean is_front_facing, boolean mirror, Date current_date, String preference_stamp, String preference_textstamp, int font_size, int color, String pref_style, String preference_stamp_dateformat, String preference_stamp_timeformat, String preference_stamp_gpsformat, boolean store_location, Location location, boolean store_geo_direction, double geo_direction, int sample_factor) {
        Request.Type type;
        if (is_raw) {
            type = Request.Type.RAW;
        } else {
            type = Request.Type.JPEG;
        }
        Request request = new Request(type, is_hdr, save_expo, jpeg_images, dngCreator, image, image_capture_intent, image_capture_intent_uri, using_camera2, image_quality, do_auto_stabilise, level_angle, is_front_facing, mirror, current_date, preference_stamp, preference_textstamp, font_size, color, pref_style, preference_stamp_dateformat, preference_stamp_timeformat, preference_stamp_gpsformat, store_location, location, store_geo_direction, geo_direction, sample_factor);
        if (do_in_background) {
            addRequest(request);
            if ((request.is_hdr && request.jpeg_images.size() > 1) || (!is_raw && request.jpeg_images.size() > 1)) {
                addRequest(new Request(Request.Type.DUMMY, false, false, null, null, null, false, null, false, 0, false, 0.0d, false, false, null, null, null, 0, 0, null, null, null, null, false, null, false, 0.0d, 1));
            }
            return true;
        }
        waitUntilDone();
        if (is_raw) {
            return saveImageNowRaw(request.dngCreator, request.image, request.current_date);
        }
        return saveImageNow(request);
    }

    private void addRequest(Request request) {
        boolean done = false;
        while (!done) {
            try {
                synchronized (this) {
                    this.n_images_to_save++;
                }
                this.queue.put(request);
                done = true;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    void waitUntilDone() {
        synchronized (this) {
            while (this.n_images_to_save > 0) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private Bitmap loadBitmap(byte[] jpeg_image, boolean mutable) {
        Options options = new Options();
        options.inMutable = mutable;
        if (VERSION.SDK_INT <= 19) {
            options.inPurgeable = true;
        }
        Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg_image, 0, jpeg_image.length, options);
        if (bitmap == null) {
            Log.e(TAG, "failed to decode bitmap");
        }
        return bitmap;
    }

    private List<Bitmap> loadBitmaps(List<byte[]> jpeg_images, int mutable_id) {
        int i;
        Options mutable_options = new Options();
        mutable_options.inMutable = true;
        Options options = new Options();
        options.inMutable = false;
        if (VERSION.SDK_INT <= 19) {
            mutable_options.inPurgeable = true;
            options.inPurgeable = true;
        }
        LoadBitmapThread[] threads = new LoadBitmapThread[jpeg_images.size()];
        for (i = 0; i < jpeg_images.size(); i++) {
            Options options2;
            if (i == mutable_id) {
                options2 = mutable_options;
            } else {
                options2 = options;
            }
            threads[i] = new LoadBitmapThread(options2, (byte[]) jpeg_images.get(i));
        }
        for (i = 0; i < jpeg_images.size(); i++) {
            threads[i].start();
        }
        boolean ok = true;
        i = 0;
        while (i < jpeg_images.size()) {
            try {
                threads[i].join();
                i++;
            } catch (InterruptedException e) {
                e.printStackTrace();
                ok = false;
            }
        }
        List<Bitmap> bitmaps = new ArrayList();
        for (i = 0; i < jpeg_images.size() && ok; i++) {
            Bitmap bitmap = threads[i].bitmap;
            if (bitmap == null) {
                Log.e(TAG, "failed to decode bitmap in thread: " + i);
                ok = false;
            }
            bitmaps.add(bitmap);
        }
        if (ok) {
            return bitmaps;
        }
        for (i = 0; i < jpeg_images.size(); i++) {
            if (threads[i].bitmap != null) {
                threads[i].bitmap.recycle();
                threads[i].bitmap = null;
            }
        }
        bitmaps.clear();
        System.gc();
        return null;
    }

    private boolean saveImageNow(Request request) {
        boolean success = false;
        if (request.type != Request.Type.JPEG) {
            throw new RuntimeException();
        } else if (request.jpeg_images.size() == 0) {
            throw new RuntimeException();
        } else if (request.is_hdr) {
            if (request.jpeg_images.size() == 1 || request.jpeg_images.size() == 3) {
                if (request.jpeg_images.size() > 1 && !request.image_capture_intent && request.save_expo) {
                    int i = 0;
                    while (i < request.jpeg_images.size()) {
                        i = !saveSingleImageNow(request, (byte[]) request.jpeg_images.get(i), null, new StringBuilder().append("_EXP").append(i).toString(), false, false) ? i + 1 : i + 1;
                    }
                }
                this.main_activity.savingImage(true);
                List<Bitmap> bitmaps = loadBitmaps(request.jpeg_images, (request.jpeg_images.size() - 1) / 2);
                if (bitmaps == null) {
                    return false;
                }
                try {
                    if (VERSION.SDK_INT >= 21) {
                        this.hdrProcessor.processHDR(bitmaps, true, null, true, null, 0.5f, HDRProcessor.TonemappingAlgorithm.TONEMAPALGORITHM_REINHARD);
                        Bitmap hdr_bitmap = (Bitmap) bitmaps.get(0);
                        bitmaps.clear();
                        System.gc();
                        this.main_activity.savingImage(false);
                        success = saveSingleImageNow(request, (byte[]) request.jpeg_images.get((request.jpeg_images.size() - 1) / 2), hdr_bitmap, request.jpeg_images.size() == 1 ? "_DRO" : "_HDR", true, true);
                        hdr_bitmap.recycle();
                        System.gc();
                        return success;
                    }
                    Log.e(TAG, "shouldn't have offered HDR as an option if not on Android 5");
                    throw new RuntimeException();
                } catch (HDRProcessorException e) {
                    Log.e(TAG, "HDRProcessorException from processHDR: " + e.getCode());
                    e.printStackTrace();
                    if (e.getCode() == 1) {
                        this.main_activity.getPreview().showToast(null, (int) R.string.failed_to_process_hdr);
                        Log.e(TAG, "UNEQUAL_SIZES");
                        bitmaps.clear();
                        System.gc();
                        this.main_activity.savingImage(false);
                        return false;
                    }
                    throw new RuntimeException();
                }
            }
            throw new RuntimeException();
        } else if (request.jpeg_images.size() > 1) {
            int mid_image = request.jpeg_images.size() / 2;
            success = true;
           int i = 0;
            while (i < request.jpeg_images.size()) {
                if (!saveSingleImageNow(request, (byte[]) request.jpeg_images.get(i), null, "_EXP" + i, true, i == mid_image)) {
                    success = false;
                }
                i++;
            }
            return success;
        } else {
            List<Bitmap> bitmaps = loadBitmaps(request.jpeg_images, (request.jpeg_images.size() - 1) / 2);
            return saveSingleImageNow(request, (byte[]) request.jpeg_images.get(0), bitmaps.get(0), "", true, true);
        }
    }

    private Bitmap autoStabilise(byte[] data, Bitmap bitmap, double level_angle, boolean is_front_facing, File exifTempFile) {
        while (level_angle < -90.0d) {
            level_angle += 180.0d;
        }
        while (level_angle > 90.0d) {
            level_angle -= 180.0d;
        }
        if (bitmap == null) {
            bitmap = loadBitmap(data, false);
            if (bitmap == null) {
                this.main_activity.getPreview().showToast(null, (int) R.string.failed_to_auto_stabilise);
                System.gc();
            }
            if (bitmap != null) {
                bitmap = rotateForExif(bitmap, data, exifTempFile);
            }
        }
        if (bitmap != null) {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            Matrix matrix = new Matrix();
            double level_angle_rad_abs = Math.abs(Math.toRadians(level_angle));
            int w1 = width;
            int h1 = height;
            double w0 = (((double) w1) * Math.cos(level_angle_rad_abs)) + (((double) h1) * Math.sin(level_angle_rad_abs));
            double h0 = (((double) w1) * Math.sin(level_angle_rad_abs)) + (((double) h1) * Math.cos(level_angle_rad_abs));
            float scale = (float) Math.sqrt((double) (((float) (w1 * h1)) / ((float) (w0 * h0))));
            if (this.main_activity.test_low_memory) {
                scale *= 2.0f;
            }
            matrix.postScale(scale, scale);
            w0 *= (double) scale;
            h0 *= (double) scale;
            w1 = (int) (((float) w1) * scale);
            h1 = (int) (((float) h1) * scale);
            if (is_front_facing) {
                matrix.postRotate((float) (-level_angle));
            } else {
                matrix.postRotate((float) level_angle);
            }
            Bitmap new_bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
            if (new_bitmap != bitmap) {
                bitmap.recycle();
                bitmap = new_bitmap;
            }
            System.gc();
            double tan_theta = Math.tan(level_angle_rad_abs);
            double sin_theta = Math.sin(level_angle_rad_abs);
            double denom = (h0 / w0) + tan_theta;
            double alt_denom = (w0 / h0) + tan_theta;
            if (denom != 0.0d && denom >= 1.0E-14d && alt_denom != 0.0d && alt_denom >= 1.0E-14d) {
                int w2 = (int) ((((((2.0d * ((double) h1)) * sin_theta) * tan_theta) + h0) - (w0 * tan_theta)) / denom);
                int h2 = (int) ((((double) w2) * h0) / w0);
                int alt_h2 = (int) ((((((2.0d * ((double) w1)) * sin_theta) * tan_theta) + w0) - (h0 * tan_theta)) / alt_denom);
                int alt_w2 = (int) ((((double) alt_h2) * w0) / h0);
                if (alt_w2 < w2) {
                    w2 = alt_w2;
                    h2 = alt_h2;
                }
                if (w2 <= 0) {
                    w2 = 1;
                } else if (w2 >= bitmap.getWidth()) {
                    w2 = bitmap.getWidth() - 1;
                }
                if (h2 <= 0) {
                    h2 = 1;
                } else if (h2 >= bitmap.getHeight()) {
                    h2 = bitmap.getHeight() - 1;
                }
                new_bitmap = Bitmap.createBitmap(bitmap, (bitmap.getWidth() - w2) / 2, (bitmap.getHeight() - h2) / 2, w2, h2);
                if (new_bitmap != bitmap) {
                    bitmap.recycle();
                    bitmap = new_bitmap;
                }
                System.gc();
            }
        }
        return bitmap;
    }

    private Bitmap mirrorImage(byte[] data, Bitmap bitmap, File exifTempFile) {
        if (bitmap == null) {
            bitmap = loadBitmap(data, false);
            if (bitmap == null) {
                System.gc();
            }
            if (bitmap != null) {
                bitmap = rotateForExif(bitmap, data, exifTempFile);
            }
        }
        if (bitmap == null) {
            return bitmap;
        }
        Matrix matrix = new Matrix();
        matrix.preScale(-1.0f, PopupView.ALPHA_BUTTON_SELECTED);
        Bitmap new_bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (new_bitmap == bitmap) {
            return bitmap;
        }
        bitmap.recycle();
        return new_bitmap;
    }

    private Bitmap stampImage(Request request, byte[] data, Bitmap bitmap, File exifTempFile) {
        MyApplicationInterface applicationInterface = this.main_activity.getApplicationInterface();
        boolean dategeo_stamp = request.preference_stamp.equals("preference_stamp_yes");
        boolean text_stamp = request.preference_textstamp.length() > 0;
        if (dategeo_stamp || text_stamp) {
            if (bitmap == null) {
                bitmap = loadBitmap(data, true);
                if (bitmap == null) {
                    this.main_activity.getPreview().showToast(null, (int) R.string.failed_to_stamp);
                    System.gc();
                }
                if (bitmap != null) {
                    bitmap = rotateForExif(bitmap, data, exifTempFile);
                }
            }
            if (bitmap != null) {
                int smallest_size;
                int font_size = request.font_size;
                int color = request.color;
                String pref_style = request.pref_style;
                String preference_stamp_dateformat = request.preference_stamp_dateformat;
                String preference_stamp_timeformat = request.preference_stamp_timeformat;
                String preference_stamp_gpsformat = request.preference_stamp_gpsformat;
                int width = bitmap.getWidth();
                int height = bitmap.getHeight();
                Canvas canvas = new Canvas(bitmap);
                this.p.setColor(-1);
                if (width < height) {
                    smallest_size = width;
                } else {
                    smallest_size = height;
                }
                float scale = ((float) smallest_size) / 288.0f;
                this.p.setTextSize((float) ((int) ((((float) font_size) * scale) + 0.5f)));
                int offset_x = (int) ((8.0f * scale) + 0.5f);
                int diff_y = (int) ((((float) (font_size + 4)) * scale) + 0.5f);
                int ypos = height - ((int) ((8.0f * scale) + 0.5f));
                this.p.setTextAlign(Align.RIGHT);
                boolean draw_shadowed = false;
                if (pref_style.equals("preference_stamp_style_shadowed")) {
                    draw_shadowed = true;
                } else {
                    if (pref_style.equals("preference_stamp_style_plain")) {
                        draw_shadowed = false;
                    }
                }
                if (dategeo_stamp) {
                    String date_stamp = TextFormatter.getDateString(preference_stamp_dateformat, request.current_date);
                    String time_stamp = TextFormatter.getTimeString(preference_stamp_timeformat, request.current_date);
                    if (date_stamp.length() > 0 || time_stamp.length() > 0) {
                        String datetime_stamp = "";
                        if (date_stamp.length() > 0) {
                            datetime_stamp = datetime_stamp + date_stamp;
                        }
                        if (time_stamp.length() > 0) {
                            if (datetime_stamp.length() > 0) {
                                datetime_stamp = datetime_stamp + " ";
                            }
                            datetime_stamp = datetime_stamp + time_stamp;
                        }
                        applicationInterface.drawTextWithBackground(canvas, this.p, datetime_stamp, color, ViewCompat.MEASURED_STATE_MASK, width - offset_x, ypos, MyApplicationInterface.Alignment.ALIGNMENT_BOTTOM, null, draw_shadowed);
                    }
                    ypos -= diff_y;
                    String gps_stamp = this.main_activity.getTextFormatter().getGPSString(preference_stamp_gpsformat, request.store_location, request.location, request.store_geo_direction, request.geo_direction);
                    if (gps_stamp.length() > 0) {
                        applicationInterface.drawTextWithBackground(canvas, this.p, gps_stamp, color, ViewCompat.MEASURED_STATE_MASK, width - offset_x, ypos, MyApplicationInterface.Alignment.ALIGNMENT_BOTTOM, null, draw_shadowed);
                        ypos -= diff_y;
                    }
                }
                if (text_stamp) {
                    applicationInterface.drawTextWithBackground(canvas, this.p, request.preference_textstamp, color, ViewCompat.MEASURED_STATE_MASK, width - offset_x, ypos, MyApplicationInterface.Alignment.ALIGNMENT_BOTTOM, null, draw_shadowed);
                    int i = ypos - diff_y;
                }
            }
        }
        return bitmap;
    }

    @SuppressLint({"SimpleDateFormat"})
    private boolean saveSingleImageNow(Request request, byte[] data, Bitmap bitmap, String filename_suffix, boolean update_thumbnail, boolean share_image) {
        if (request.type != Request.Type.JPEG) {
            throw new RuntimeException();
        } else if (data == null) {
            throw new RuntimeException();
        } else {
            int width;
            int height;
            float scale;
            Matrix matrix;
            OutputStream outputStream = null;
            long time_s = System.currentTimeMillis();
            boolean image_capture_intent = request.image_capture_intent;
            boolean using_camera2 = request.using_camera2;
            Date current_date = request.current_date;
            boolean store_location = request.store_location;
            boolean store_geo_direction = request.store_geo_direction;
            boolean success = false;
            MyApplicationInterface applicationInterface = this.main_activity.getApplicationInterface();
            StorageUtils storageUtils = this.main_activity.getStorageUtils();
            this.main_activity.savingImage(true);
            boolean dategeo_stamp = request.preference_stamp.equals("preference_stamp_yes");
            boolean text_stamp = request.preference_textstamp.length() > 0;
            File exifTempFile = null;
            if (bitmap != null || request.do_auto_stabilise || request.mirror || dategeo_stamp || text_stamp) {
                if (VERSION.SDK_INT < 24) {
                    OutputStream fileOutputStream = null;
                    try {
                        exifTempFile = File.createTempFile("opencamera_exif", "");
                        fileOutputStream = new FileOutputStream(exifTempFile);
                        fileOutputStream.write(data);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        if (null != fileOutputStream) {
                            try {
                                fileOutputStream.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                    }
                }
                if (bitmap != null) {
                    bitmap = rotateForExif(bitmap, data, exifTempFile);
                }
            }
            if (request.do_auto_stabilise) {
                bitmap = autoStabilise(data, bitmap, request.level_angle, request.is_front_facing, exifTempFile);
            }
            if (request.mirror) {
                bitmap = mirrorImage(data, bitmap, exifTempFile);
            }
            bitmap = stampImage(request, data, bitmap, exifTempFile);
            File picFile = null;
            Uri saveUri = null;
            if (image_capture_intent) {
                try {
                    if (request.image_capture_intent_uri != null) {
                        saveUri = request.image_capture_intent_uri;
                    } else {
                        if (bitmap == null) {
                            bitmap = loadBitmap(data, false);
                            if (bitmap != null) {
                                bitmap = rotateForExif(bitmap, data, exifTempFile);
                            }
                        }
                        if (bitmap != null) {
                            width = bitmap.getWidth();
                            height = bitmap.getHeight();
                            if (width > 128) {
                                scale = 128.0f / ((float) width);
                                matrix = new Matrix();
                                matrix.postScale(scale, scale);
                                Bitmap new_bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
                                if (new_bitmap != bitmap) {
                                    bitmap.recycle();
                                    bitmap = new_bitmap;
                                }
                            }
                        }
                        if (bitmap != null) {
                            this.main_activity.setResult(-1, new Intent("inline-data").putExtra("data", bitmap));
                        }
                        if (exifTempFile == null || !exifTempFile.delete()) {
                            exifTempFile = null;
                            this.main_activity.finish();
                        } else {
                            exifTempFile = null;
                            this.main_activity.finish();
                        }
                    }
                } catch (Exception e2) {
                    e2.printStackTrace();
                    this.main_activity.getPreview().showToast(null, (int) R.string.failed_to_save_photo);
                } finally {
                    if (null != outputStream) {
                        try {
                            outputStream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            } else if (storageUtils.isUsingSAF()) {
                try {
                    saveUri = storageUtils.createOutputMediaFileSAF(1, filename_suffix, "jpg", current_date);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                try {
                    picFile = storageUtils.createOutputMediaFile(1, filename_suffix, "jpg", current_date);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            try {
                if (saveUri != null && picFile == null) {
                    picFile = File.createTempFile("picFile", "jpg", this.main_activity.getCacheDir());
                }
                if (picFile != null) {
                    outputStream = new FileOutputStream(picFile);
                    if (bitmap != null) {
                        bitmap.compress(CompressFormat.JPEG, request.image_quality, outputStream);
                    } else {
                        outputStream.write(data);
                    }
                    outputStream.close();
                    if (saveUri == null) {
                        success = true;
                    }
                    ExifInterface exif = null;
                    if (picFile != null) {
                        if (bitmap != null) {
                            if (VERSION.SDK_INT >= 24) {
                                try {
                                    setExifFromData(request, data, picFile);
                                } catch (Throwable throwable) {
                                    throwable.printStackTrace();
                                }
                            } else if (exifTempFile != null) {
                                setExifFromFile(request, exifTempFile, picFile);
                            }
                        } else if (store_geo_direction) {
                            try {
                                exif = new ExifInterface(picFile.getAbsolutePath());
                                modifyExif(exif, using_camera2, current_date, store_location, store_geo_direction, request.geo_direction);
                                exif.saveAttributes();
                            } catch (NoClassDefFoundError exception) {
                                exception.printStackTrace();
                            }
                        } else if (needGPSTimestampHack(using_camera2, store_location)) {
                            try {
                                exif = new ExifInterface(picFile.getAbsolutePath());
                                fixGPSTimestamp(exif, current_date);
                                exif.saveAttributes();
                            } catch (NoClassDefFoundError exception2) {
                                exception2.printStackTrace();
                            }
                        }
                        if (saveUri == null) {
                            storageUtils.broadcastFile(picFile, true, false, update_thumbnail);
                            this.main_activity.test_last_saved_image = picFile.getAbsolutePath();
                        }
                    }
                    if (image_capture_intent) {
                        this.main_activity.setResult(-1);
                        this.main_activity.finish();
                    }
                    if (storageUtils.isUsingSAF()) {
                        storageUtils.clearLastMediaScanned();
                    }
                    if (saveUri != null) {
                        try {
                            copyFileToUri(this.main_activity, saveUri, picFile);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        success = true;
                        File real_file = storageUtils.getFileFromDocumentUriSAF(saveUri, false);
                        if (real_file != null) {
                            storageUtils.broadcastFile(real_file, true, false, true);
                            this.main_activity.test_last_saved_image = real_file.getAbsolutePath();
                        } else if (!image_capture_intent) {
                            storageUtils.announceUri(saveUri, true, false);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            int sample_size;
            Options options;
            Bitmap thumbnail;
            final MyApplicationInterface myApplicationInterface;
            final Bitmap bitmap2;
            if (exifTempFile == null || !exifTempFile.delete()) {
                if (!success && saveUri == null) {
                    applicationInterface.addLastImage(picFile, share_image);
                } else if (success && storageUtils.isUsingSAF()) {
                    applicationInterface.addLastImageSAF(saveUri, share_image);
                }
                if (success && this.main_activity.getPreview().getCameraController() != null && update_thumbnail) {
                    sample_size = Integer.highestOneBit((int) Math.ceil(((double) this.main_activity.getPreview().getCameraController().getPictureSize().width) / ((double) this.main_activity.getPreview().getView().getWidth()))) * request.sample_factor;
                    if (bitmap != null) {
                        options = new Options();
                        options.inMutable = false;
                        if (VERSION.SDK_INT <= 19) {
                            options.inPurgeable = true;
                        }
                        options.inSampleSize = sample_size;
                        thumbnail = rotateForExif(BitmapFactory.decodeByteArray(data, 0, data.length, options), data, picFile);
                    } else {
                        width = bitmap.getWidth();
                        height = bitmap.getHeight();
                        matrix = new Matrix();
                        scale = PopupView.ALPHA_BUTTON_SELECTED / ((float) sample_size);
                        matrix.postScale(scale, scale);
                        thumbnail = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
                    }
                    if (thumbnail != null) {
                        myApplicationInterface = applicationInterface;
                        bitmap2 = thumbnail;
                        this.main_activity.runOnUiThread(new Runnable() {
                            public void run() {
                                myApplicationInterface.updateThumbnail(bitmap2);
                            }
                        });
                    }
                }
                if (bitmap != null) {
                    bitmap.recycle();
                }
                if (picFile == null && saveUri != null && picFile.delete()) {
                    System.gc();
                    this.main_activity.savingImage(false);
                } else {
                    System.gc();
                    this.main_activity.savingImage(false);
                }
                return success;
            }

            applicationInterface.addLastImageSAF(saveUri, share_image);
            sample_size = Integer.highestOneBit((int) Math.ceil(((double) this.main_activity.getPreview().getCameraController().getPictureSize().width) / ((double) this.main_activity.getPreview().getView().getWidth()))) * request.sample_factor;
            if (bitmap != null) {
                width = bitmap.getWidth();
                height = bitmap.getHeight();
                matrix = new Matrix();
                scale = PopupView.ALPHA_BUTTON_SELECTED / ((float) sample_size);
                matrix.postScale(scale, scale);
                thumbnail = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
            } else {
                options = new Options();
                options.inMutable = false;
                if (VERSION.SDK_INT <= 19) {
                    options.inPurgeable = true;
                }
                options.inSampleSize = sample_size;
                thumbnail = rotateForExif(BitmapFactory.decodeByteArray(data, 0, data.length, options), data, picFile);
            }
            if (thumbnail != null) {
                myApplicationInterface = applicationInterface;
                bitmap2 = thumbnail;
                this.main_activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                    }
                });
            }
            if (bitmap != null) {
                bitmap.recycle();
            }
            if (picFile == null) {
            }
            System.gc();
            this.main_activity.savingImage(false);
            return success;
        }
    }

    @RequiresApi(api = 24)
    private void setExifFromData(Request request, byte[] data, File to_file) throws Throwable {
        Throwable th;
        InputStream inputStream = null;
        try {
            InputStream inputStream2 = new ByteArrayInputStream(data);
            try {
                setExif(request, new ExifInterface(inputStream2), new ExifInterface(to_file.getAbsolutePath()));
                if (inputStream2 != null) {
                    inputStream2.close();
                }
            } catch (Throwable th2) {
                th = th2;
                inputStream = inputStream2;
                if (inputStream != null) {
                    inputStream.close();
                }
                throw th;
            }
        } catch (Throwable th3) {
            th = th3;
            if (inputStream != null) {
                inputStream.close();
            }
            throw th;
        }
    }

    private void setExifFromFile(Request request, File from_file, File to_file) throws IOException {
        try {
            setExif(request, new ExifInterface(from_file.getAbsolutePath()), new ExifInterface(to_file.getAbsolutePath()));
        } catch (NoClassDefFoundError exception) {
            exception.printStackTrace();
        }
    }

    private void setExif(Request request, ExifInterface exif, ExifInterface exif_new) throws IOException {
        String exif_aperture = exif.getAttribute("FNumber");
        String exif_datetime = exif.getAttribute("DateTime");
        String exif_exposure_time = exif.getAttribute("ExposureTime");
        String exif_flash = exif.getAttribute("Flash");
        String exif_focal_length = exif.getAttribute("FocalLength");
        String exif_gps_altitude = exif.getAttribute("GPSAltitude");
        String exif_gps_altitude_ref = exif.getAttribute("GPSAltitudeRef");
        String exif_gps_datestamp = exif.getAttribute("GPSDateStamp");
        String exif_gps_latitude = exif.getAttribute("GPSLatitude");
        String exif_gps_latitude_ref = exif.getAttribute("GPSLatitudeRef");
        String exif_gps_longitude = exif.getAttribute("GPSLongitude");
        String exif_gps_longitude_ref = exif.getAttribute("GPSLongitudeRef");
        String exif_gps_processing_method = exif.getAttribute("GPSProcessingMethod");
        String exif_gps_timestamp = exif.getAttribute("GPSTimeStamp");
        String exif_iso = exif.getAttribute("ISOSpeedRatings");
        String exif_make = exif.getAttribute("Make");
        String exif_model = exif.getAttribute("Model");
        String exif_white_balance = exif.getAttribute("WhiteBalance");
        String exif_datetime_digitized = null;
        String exif_subsec_time = null;
        String exif_subsec_time_dig = null;
        String exif_subsec_time_orig = null;
        if (VERSION.SDK_INT >= 23) {
            exif_datetime_digitized = exif.getAttribute(TAG_DATETIME_DIGITIZED);
            exif_subsec_time = exif.getAttribute("SubSecTime");
            exif_subsec_time_dig = exif.getAttribute("SubSecTimeDigitized");
            exif_subsec_time_orig = exif.getAttribute("SubSecTimeOriginal");
        }
        String exif_aperture_value = null;
        String exif_brightness_value = null;
        String exif_cfa_pattern = null;
        String exif_color_space = null;
        String exif_components_configuration = null;
        String exif_compressed_bits_per_pixel = null;
        String exif_compression = null;
        String exif_contrast = null;
        String exif_datetime_original = null;
        String exif_device_setting_description = null;
        String exif_digital_zoom_ratio = null;
        String exif_exposure_bias_value = null;
        String exif_exposure_index = null;
        String exif_exposure_mode = null;
        String exif_exposure_program = null;
        String exif_flash_energy = null;
        String exif_focal_length_in_35mm_film = null;
        String exif_focal_plane_resolution_unit = null;
        String exif_focal_plane_x_resolution = null;
        String exif_focal_plane_y_resolution = null;
        String exif_gain_control = null;
        String exif_gps_area_information = null;
        String exif_gps_differential = null;
        String exif_gps_dop = null;
        String exif_gps_measure_mode = null;
        String exif_image_description = null;
        String exif_light_source = null;
        String exif_maker_note = null;
        String exif_max_aperture_value = null;
        String exif_metering_mode = null;
        String exif_oecf = null;
        String exif_photometric_interpretation = null;
        String exif_saturation = null;
        String exif_scene_capture_type = null;
        String exif_scene_type = null;
        String exif_sensing_method = null;
        String exif_sharpness = null;
        String exif_shutter_speed_value = null;
        String exif_software = null;
        String exif_user_comment = null;
        if (VERSION.SDK_INT >= 24) {
            exif_aperture_value = exif.getAttribute("ApertureValue");
            exif_brightness_value = exif.getAttribute("BrightnessValue");
            exif_cfa_pattern = exif.getAttribute("CFAPattern");
            exif_color_space = exif.getAttribute("ColorSpace");
            exif_components_configuration = exif.getAttribute("ComponentsConfiguration");
            exif_compressed_bits_per_pixel = exif.getAttribute("CompressedBitsPerPixel");
            exif_compression = exif.getAttribute("Compression");
            exif_contrast = exif.getAttribute("Contrast");
            exif_datetime_original = exif.getAttribute(TAG_DATETIME_ORIGINAL);
            exif_device_setting_description = exif.getAttribute("DeviceSettingDescription");
            exif_digital_zoom_ratio = exif.getAttribute("DigitalZoomRatio");
            exif_exposure_bias_value = exif.getAttribute("ExposureBiasValue");
            exif_exposure_index = exif.getAttribute("ExposureIndex");
            exif_exposure_mode = exif.getAttribute("ExposureMode");
            exif_exposure_program = exif.getAttribute("ExposureProgram");
            exif_flash_energy = exif.getAttribute("FlashEnergy");
            exif_focal_length_in_35mm_film = exif.getAttribute("FocalLengthIn35mmFilm");
            exif_focal_plane_resolution_unit = exif.getAttribute("FocalPlaneResolutionUnit");
            exif_focal_plane_x_resolution = exif.getAttribute("FocalPlaneXResolution");
            exif_focal_plane_y_resolution = exif.getAttribute("FocalPlaneYResolution");
            exif_gain_control = exif.getAttribute("GainControl");
            exif_gps_area_information = exif.getAttribute("GPSAreaInformation");
            exif_gps_differential = exif.getAttribute("GPSDifferential");
            exif_gps_dop = exif.getAttribute("GPSDOP");
            exif_gps_measure_mode = exif.getAttribute("GPSMeasureMode");
            exif_image_description = exif.getAttribute("ImageDescription");
            exif_light_source = exif.getAttribute("LightSource");
            exif_maker_note = exif.getAttribute("MakerNote");
            exif_max_aperture_value = exif.getAttribute("MaxApertureValue");
            exif_metering_mode = exif.getAttribute("MeteringMode");
            exif_oecf = exif.getAttribute("OECF");
            exif_photometric_interpretation = exif.getAttribute("PhotometricInterpretation");
            exif_saturation = exif.getAttribute("Saturation");
            exif_scene_capture_type = exif.getAttribute("SceneCaptureType");
            exif_scene_type = exif.getAttribute("SceneType");
            exif_sensing_method = exif.getAttribute("SensingMethod");
            exif_sharpness = exif.getAttribute("Sharpness");
            exif_shutter_speed_value = exif.getAttribute("ShutterSpeedValue");
            exif_software = exif.getAttribute("Software");
            exif_user_comment = exif.getAttribute("UserComment");
        }
        if (exif_aperture != null) {
            exif_new.setAttribute("FNumber", exif_aperture);
        }
        if (exif_datetime != null) {
            exif_new.setAttribute("DateTime", exif_datetime);
        }
        if (exif_exposure_time != null) {
            exif_new.setAttribute("ExposureTime", exif_exposure_time);
        }
        if (exif_flash != null) {
            exif_new.setAttribute("Flash", exif_flash);
        }
        if (exif_focal_length != null) {
            exif_new.setAttribute("FocalLength", exif_focal_length);
        }
        if (exif_gps_altitude != null) {
            exif_new.setAttribute("GPSAltitude", exif_gps_altitude);
        }
        if (exif_gps_altitude_ref != null) {
            exif_new.setAttribute("GPSAltitudeRef", exif_gps_altitude_ref);
        }
        if (exif_gps_datestamp != null) {
            exif_new.setAttribute("GPSDateStamp", exif_gps_datestamp);
        }
        if (exif_gps_latitude != null) {
            exif_new.setAttribute("GPSLatitude", exif_gps_latitude);
        }
        if (exif_gps_latitude_ref != null) {
            exif_new.setAttribute("GPSLatitudeRef", exif_gps_latitude_ref);
        }
        if (exif_gps_longitude != null) {
            exif_new.setAttribute("GPSLongitude", exif_gps_longitude);
        }
        if (exif_gps_longitude_ref != null) {
            exif_new.setAttribute("GPSLongitudeRef", exif_gps_longitude_ref);
        }
        if (exif_gps_processing_method != null) {
            exif_new.setAttribute("GPSProcessingMethod", exif_gps_processing_method);
        }
        if (exif_gps_timestamp != null) {
            exif_new.setAttribute("GPSTimeStamp", exif_gps_timestamp);
        }
        if (exif_iso != null) {
            exif_new.setAttribute("ISOSpeedRatings", exif_iso);
        }
        if (exif_make != null) {
            exif_new.setAttribute("Make", exif_make);
        }
        if (exif_model != null) {
            exif_new.setAttribute("Model", exif_model);
        }
        if (exif_white_balance != null) {
            exif_new.setAttribute("WhiteBalance", exif_white_balance);
        }
        if (VERSION.SDK_INT >= 23) {
            if (exif_datetime_digitized != null) {
                exif_new.setAttribute(TAG_DATETIME_DIGITIZED, exif_datetime_digitized);
            }
            if (exif_subsec_time != null) {
                exif_new.setAttribute("SubSecTime", exif_subsec_time);
            }
            if (exif_subsec_time_dig != null) {
                exif_new.setAttribute("SubSecTimeDigitized", exif_subsec_time_dig);
            }
            if (exif_subsec_time_orig != null) {
                exif_new.setAttribute("SubSecTimeOriginal", exif_subsec_time_orig);
            }
        }
        if (VERSION.SDK_INT >= 24) {
            if (exif_aperture_value != null) {
                exif_new.setAttribute("ApertureValue", exif_aperture_value);
            }
            if (exif_brightness_value != null) {
                exif_new.setAttribute("BrightnessValue", exif_brightness_value);
            }
            if (exif_cfa_pattern != null) {
                exif_new.setAttribute("CFAPattern", exif_cfa_pattern);
            }
            if (exif_color_space != null) {
                exif_new.setAttribute("ColorSpace", exif_color_space);
            }
            if (exif_components_configuration != null) {
                exif_new.setAttribute("ComponentsConfiguration", exif_components_configuration);
            }
            if (exif_compressed_bits_per_pixel != null) {
                exif_new.setAttribute("CompressedBitsPerPixel", exif_compressed_bits_per_pixel);
            }
            if (exif_compression != null) {
                exif_new.setAttribute("Compression", exif_compression);
            }
            if (exif_contrast != null) {
                exif_new.setAttribute("Contrast", exif_contrast);
            }
            if (exif_datetime_original != null) {
                exif_new.setAttribute(TAG_DATETIME_ORIGINAL, exif_datetime_original);
            }
            if (exif_device_setting_description != null) {
                exif_new.setAttribute("DeviceSettingDescription", exif_device_setting_description);
            }
            if (exif_digital_zoom_ratio != null) {
                exif_new.setAttribute("DigitalZoomRatio", exif_digital_zoom_ratio);
            }
            if (exif_exposure_bias_value != null) {
                exif_new.setAttribute("ExposureBiasValue", exif_exposure_bias_value);
            }
            if (exif_exposure_index != null) {
                exif_new.setAttribute("ExposureIndex", exif_exposure_index);
            }
            if (exif_exposure_mode != null) {
                exif_new.setAttribute("ExposureMode", exif_exposure_mode);
            }
            if (exif_exposure_program != null) {
                exif_new.setAttribute("ExposureProgram", exif_exposure_program);
            }
            if (exif_flash_energy != null) {
                exif_new.setAttribute("FlashEnergy", exif_flash_energy);
            }
            if (exif_focal_length_in_35mm_film != null) {
                exif_new.setAttribute("FocalLengthIn35mmFilm", exif_focal_length_in_35mm_film);
            }
            if (exif_focal_plane_resolution_unit != null) {
                exif_new.setAttribute("FocalPlaneResolutionUnit", exif_focal_plane_resolution_unit);
            }
            if (exif_focal_plane_x_resolution != null) {
                exif_new.setAttribute("FocalPlaneXResolution", exif_focal_plane_x_resolution);
            }
            if (exif_focal_plane_y_resolution != null) {
                exif_new.setAttribute("FocalPlaneYResolution", exif_focal_plane_y_resolution);
            }
            if (exif_gain_control != null) {
                exif_new.setAttribute("GainControl", exif_gain_control);
            }
            if (exif_gps_area_information != null) {
                exif_new.setAttribute("GPSAreaInformation", exif_gps_area_information);
            }
            if (exif_gps_differential != null) {
                exif_new.setAttribute("GPSDifferential", exif_gps_differential);
            }
            if (exif_gps_dop != null) {
                exif_new.setAttribute("GPSDOP", exif_gps_dop);
            }
            if (exif_gps_measure_mode != null) {
                exif_new.setAttribute("GPSMeasureMode", exif_gps_measure_mode);
            }
            if (exif_image_description != null) {
                exif_new.setAttribute("ImageDescription", exif_image_description);
            }
            if (exif_light_source != null) {
                exif_new.setAttribute("LightSource", exif_light_source);
            }
            if (exif_maker_note != null) {
                exif_new.setAttribute("MakerNote", exif_maker_note);
            }
            if (exif_max_aperture_value != null) {
                exif_new.setAttribute("MaxApertureValue", exif_max_aperture_value);
            }
            if (exif_metering_mode != null) {
                exif_new.setAttribute("MeteringMode", exif_metering_mode);
            }
            if (exif_oecf != null) {
                exif_new.setAttribute("OECF", exif_oecf);
            }
            if (exif_photometric_interpretation != null) {
                exif_new.setAttribute("PhotometricInterpretation", exif_photometric_interpretation);
            }
            if (exif_saturation != null) {
                exif_new.setAttribute("Saturation", exif_saturation);
            }
            if (exif_scene_capture_type != null) {
                exif_new.setAttribute("SceneCaptureType", exif_scene_capture_type);
            }
            if (exif_scene_type != null) {
                exif_new.setAttribute("SceneType", exif_scene_type);
            }
            if (exif_sensing_method != null) {
                exif_new.setAttribute("SensingMethod", exif_sensing_method);
            }
            if (exif_sharpness != null) {
                exif_new.setAttribute("Sharpness", exif_sharpness);
            }
            if (exif_shutter_speed_value != null) {
                exif_new.setAttribute("ShutterSpeedValue", exif_shutter_speed_value);
            }
            if (exif_software != null) {
                exif_new.setAttribute("Software", exif_software);
            }
            if (exif_user_comment != null) {
                exif_new.setAttribute("UserComment", exif_user_comment);
            }
        }
        modifyExif(exif_new, request.using_camera2, request.current_date, request.store_location, request.store_geo_direction, request.geo_direction);
        exif_new.saveAttributes();
    }

    @TargetApi(21)
    private boolean saveImageNowRaw(DngCreator dngCreator, Image image, Date current_date) {
        if (VERSION.SDK_INT < 21) {
            return false;
        }
        StorageUtils storageUtils = this.main_activity.getStorageUtils();
        boolean success = false;
        this.main_activity.savingImage(true);
        OutputStream output = null;
        File picFile = null;
        Uri saveUri = null;
        try {
            if (storageUtils.isUsingSAF()) {
                saveUri = storageUtils.createOutputMediaFileSAF(1, "", "dng", current_date);
            } else {
                picFile = storageUtils.createOutputMediaFile(1, "", "dng", current_date);
            }
            if (picFile != null) {
                output = new FileOutputStream(picFile);
            } else {
                output = this.main_activity.getContentResolver().openOutputStream(saveUri);
            }
            dngCreator.writeImage(output, image);
            image.close();
            image = null;
            dngCreator.close();
            dngCreator = null;
            output.close();
            output = null;
            if (saveUri == null) {
                success = true;
                storageUtils.broadcastFile(picFile, true, false, false);
            } else {
                success = true;
                File real_file = storageUtils.getFileFromDocumentUriSAF(saveUri, false);
                if (real_file != null) {
                    storageUtils.broadcastFile(real_file, true, false, false);
                } else {
                    storageUtils.announceUri(saveUri, true, false);
                }
            }
            MyApplicationInterface applicationInterface = this.main_activity.getApplicationInterface();
            if (success && saveUri == null) {
                applicationInterface.addLastImage(picFile, false);
            } else if (success) {
                if (storageUtils.isUsingSAF()) {
                    applicationInterface.addLastImageSAF(saveUri, false);
                }
            }
            if (output != null) {
                try {
                    output.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (image != null) {
                image.close();
            }
            if (dngCreator != null) {
                dngCreator.close();
            }
        } catch (FileNotFoundException e2) {
            e2.printStackTrace();
            this.main_activity.getPreview().showToast(null, (int) R.string.failed_to_save_photo_raw);
            if (output != null) {
                try {
                    output.close();
                } catch (IOException e3) {
                    e3.printStackTrace();
                }
            }
            if (image != null) {
                image.close();
            }
            if (dngCreator != null) {
                dngCreator.close();
            }
        } catch (IOException e32) {
            e32.printStackTrace();
            this.main_activity.getPreview().showToast(null, (int) R.string.failed_to_save_photo_raw);
            if (output != null) {
                try {
                    output.close();
                } catch (IOException e322) {
                    e322.printStackTrace();
                }
            }
            if (image != null) {
                image.close();
            }
            if (dngCreator != null) {
                dngCreator.close();
            }
        } catch (Throwable th) {
            if (output != null) {
                try {
                    output.close();
                } catch (IOException e3222) {
                    e3222.printStackTrace();
                }
            }
            if (image != null) {
                image.close();
            }
            if (dngCreator != null) {
                dngCreator.close();
            }
        }
        System.gc();
        this.main_activity.savingImage(false);
        return success;
    }

    private Bitmap rotateForExif(Bitmap bitmap, byte[] data, File exifTempFile) {
        IOException exception;
        Throwable th;
        NoClassDefFoundError exception2;
        InputStream inputStream = null;
        try {
            ExifInterface exif = null;

            if (VERSION.SDK_INT >= 24) {
                InputStream byteArrayInputStream = new ByteArrayInputStream(data);
                try {
                    exif = new ExifInterface(byteArrayInputStream);
                    inputStream = byteArrayInputStream;
                } catch (IOException e) {
                    exception = e;
                    inputStream = byteArrayInputStream;
                    try {
                        exception.printStackTrace();
                        if (inputStream != null) {
                            try {
                                inputStream.close();
                            } catch (IOException e2) {
                                e2.printStackTrace();
                            }
                        }
                        return bitmap;
                    } catch (Throwable th2) {
                        th = th2;
                        if (inputStream != null) {
                            try {
                                inputStream.close();
                            } catch (IOException e22) {
                                e22.printStackTrace();
                            }
                        }
                        try {
                            throw th;
                        } catch (Throwable throwable) {
                            throwable.printStackTrace();
                        }
                    }
                } catch (NoClassDefFoundError e3) {
                    exception2 = e3;
                    inputStream = byteArrayInputStream;
                    exception2.printStackTrace();
                    if (inputStream != null) {
                        try {
                            inputStream.close();
                        } catch (IOException e222) {
                            e222.printStackTrace();
                        }
                    }
                    return bitmap;
                } catch (Throwable th3) {
                    th = th3;
                    inputStream = byteArrayInputStream;
                    if (inputStream != null) {
                        inputStream.close();
                    }
                    try {
                        throw th;
                    } catch (Throwable throwable) {
                        throwable.printStackTrace();
                    }
                }
            } else if (exifTempFile != null) {
                exif = new ExifInterface(exifTempFile.getAbsolutePath());
            } else {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e2222) {
                        e2222.printStackTrace();
                    }
                }
                return bitmap;
            }
            int exif_orientation_s = exif.getAttributeInt("Orientation", 0);
            boolean needs_tf = false;
            int exif_orientation = 0;
            if (!(exif_orientation_s == 0 || exif_orientation_s == 1)) {
                if (exif_orientation_s == 3) {
                    needs_tf = true;
                    exif_orientation = 180;
                } else if (exif_orientation_s == 6) {
                    needs_tf = true;
                    exif_orientation = 90;
                } else if (exif_orientation_s == 8) {
                    needs_tf = true;
                    exif_orientation = 270;
                }
            }
            if (needs_tf) {
                Matrix m = new Matrix();
                m.setRotate((float) exif_orientation, ((float) bitmap.getWidth()) * 0.5f, ((float) bitmap.getHeight()) * 0.5f);
                Bitmap rotated_bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
                if (rotated_bitmap != bitmap) {
                    bitmap.recycle();
                    bitmap = rotated_bitmap;
                }
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e22222) {
                    e22222.printStackTrace();
                }
            }
        } catch (IOException e4) {
            exception = e4;
            exception.printStackTrace();
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return bitmap;
        } catch (NoClassDefFoundError e5) {
            exception2 = e5;
            exception2.printStackTrace();
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return bitmap;
        }
        return bitmap;
    }

    private void modifyExif(ExifInterface exif, boolean using_camera2, Date current_date, boolean store_location, boolean store_geo_direction, double geo_direction) {
        setGPSDirectionExif(exif, store_geo_direction, geo_direction);
        setDateTimeExif(exif);
        if (needGPSTimestampHack(using_camera2, store_location)) {
            fixGPSTimestamp(exif, current_date);
        }
    }

    private void setGPSDirectionExif(ExifInterface exif, boolean store_geo_direction, double geo_direction) {
        if (store_geo_direction) {
            float geo_angle = (float) Math.toDegrees(geo_direction);
            if (geo_angle < 0.0f) {
                geo_angle += 360.0f;
            }
            exif.setAttribute(TAG_GPS_IMG_DIRECTION, Math.round(100.0f * geo_angle) + "/100");
            exif.setAttribute(TAG_GPS_IMG_DIRECTION_REF, "M");
        }
    }

    private void setDateTimeExif(ExifInterface exif) {
        String exif_datetime = exif.getAttribute("DateTime");
        if (exif_datetime != null) {
            exif.setAttribute(TAG_DATETIME_ORIGINAL, exif_datetime);
            exif.setAttribute(TAG_DATETIME_DIGITIZED, exif_datetime);
        }
    }

    private void fixGPSTimestamp(ExifInterface exif, Date current_date) {
        SimpleDateFormat date_fmt = new SimpleDateFormat("yyyy:MM:dd", Locale.US);
        date_fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        String datestamp = date_fmt.format(current_date);
        SimpleDateFormat time_fmt = new SimpleDateFormat("HH:mm:ss", Locale.US);
        time_fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        String timestamp = time_fmt.format(current_date);
        exif.setAttribute("GPSDateStamp", datestamp);
        exif.setAttribute("GPSTimeStamp", timestamp);
    }

    private boolean needGPSTimestampHack(boolean using_camera2, boolean store_location) {
        return using_camera2 ? store_location : false;
    }

    private void copyFileToUri(Context context, Uri saveUri, File picFile) throws IOException {
        Throwable th;
        InputStream inputStream = null;
        OutputStream realOutputStream = null;
        try {
            InputStream inputStream2 = new FileInputStream(picFile);
            try {
                realOutputStream = context.getContentResolver().openOutputStream(saveUri);
                byte[] buffer = new byte[1024];
                while (true) {
                    int len = inputStream2.read(buffer);
                    if (len <= 0) {
                        break;
                    }
                    realOutputStream.write(buffer, 0, len);
                }
                if (inputStream2 != null) {
                    inputStream2.close();
                }
                if (realOutputStream != null) {
                    realOutputStream.close();
                }
            } catch (Throwable th2) {
                th = th2;
                inputStream = inputStream2;
                if (inputStream != null) {
                    inputStream.close();
                }
                if (realOutputStream != null) {
                    realOutputStream.close();
                }
                throw th;
            }
        } catch (Throwable th3) {
            th = th3;
            if (inputStream != null) {
                inputStream.close();
            }
            if (realOutputStream != null) {
                realOutputStream.close();
            }
            try {
                throw th;
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }
    }

    HDRProcessor getHDRProcessor() {
        return this.hdrProcessor;
    }
}
