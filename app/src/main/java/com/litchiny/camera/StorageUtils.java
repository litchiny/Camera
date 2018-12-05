package com.litchiny.camera;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.OnScanCompletedListener;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.support.v4.content.ContextCompat;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class StorageUtils {
    static final int MEDIA_TYPE_IMAGE = 1;
    static final int MEDIA_TYPE_VIDEO = 2;
    private static final String TAG = "StorageUtils";
    private final Context context;
    public volatile boolean failed_to_scan;
    private Uri last_media_scanned;

    static class Media {
        final long date;
        final long id;
        final int orientation;
        final Uri uri;
        final boolean video;

        Media(long id, boolean video, Uri uri, long date, int orientation) {
            this.id = id;
            this.video = video;
            this.uri = uri;
            this.date = date;
            this.orientation = orientation;
        }
    }

    StorageUtils(Context context) {
        this.context = context;
    }

    Uri getLastMediaScanned() {
        return this.last_media_scanned;
    }

    void clearLastMediaScanned() {
        this.last_media_scanned = null;
    }

    void announceUri(Uri uri, boolean is_new_picture, boolean is_new_video) {
        if (VERSION.SDK_INT < 24) {
            if (is_new_picture) {
                this.context.sendBroadcast(new Intent("android.hardware.action.NEW_PICTURE", uri));
                this.context.sendBroadcast(new Intent("com.android.camera.NEW_PICTURE", uri));
            } else if (is_new_video) {
                this.context.sendBroadcast(new Intent("android.hardware.action.NEW_VIDEO", uri));
            }
        }
    }

    public void broadcastFile(File file, final boolean is_new_picture, final boolean is_new_video, final boolean set_last_scanned) {
        if (!file.isDirectory()) {
            this.failed_to_scan = true;
            MediaScannerConnection.scanFile(this.context, new String[]{file.getAbsolutePath()}, null, new OnScanCompletedListener() {
                public void onScanCompleted(String path, Uri uri) {
                    StorageUtils.this.failed_to_scan = false;
                    if (set_last_scanned) {
                        StorageUtils.this.last_media_scanned = uri;
                    }
                    StorageUtils.this.announceUri(uri, is_new_picture, is_new_video);
                    Activity activity = (Activity) StorageUtils.this.context;
                    if ("android.media.action.VIDEO_CAPTURE".equals(activity.getIntent().getAction())) {
                        Intent output = new Intent();
                        output.setData(uri);
                        activity.setResult(-1, output);
                        activity.finish();
                    }
                }
            });
        }
    }

   public boolean isUsingSAF() {
        if ( !PreferenceManager.getDefaultSharedPreferences(this.context).getBoolean(PreferenceKeys.getUsingSAFPreferenceKey(), false)) {
            return false;
        }
        return true;
    }

    public String getSaveLocation() {
        return PreferenceManager.getDefaultSharedPreferences(this.context).getString(PreferenceKeys.getSaveLocationPreferenceKey(), "OpenCamera");
    }

    public String getSaveLocationSAF() {
        return PreferenceManager.getDefaultSharedPreferences(this.context).getString(PreferenceKeys.getSaveLocationSAFPreferenceKey(), "");
    }

    private Uri getTreeUriSAF() {
        return Uri.parse(getSaveLocationSAF());
    }

    public  File getImageFolder() {
        if (isUsingSAF()) {
            return getFileFromDocumentUriSAF(getTreeUriSAF(), true);
        }
        return getImageFolder(getSaveLocation());
    }

    public static File getBaseFolder() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
    }

    public static File getImageFolder(String folder_name) {
        if (folder_name.length() > 0 && folder_name.lastIndexOf(47) == folder_name.length() - 1) {
            folder_name = folder_name.substring(0, folder_name.length() - 1);
        }
        if (folder_name.startsWith("/")) {
            return new File(folder_name);
        }
        return new File(getBaseFolder(), folder_name);
    }

    public   File getFileFromDocumentUriSAF(Uri uri, boolean is_folder) {
        File file = null;
        String[] split = null;
        String type;
        String filename = null;
        if ("com.android.externalstorage.documents".equals(uri.getAuthority())) {
            split = (is_folder ? DocumentsContract.getTreeDocumentId(uri) : DocumentsContract.getDocumentId(uri)).split(":");
            if (split.length < 2) {
                return null;
            }
            type = split[0];
            String path = split[1];
            File[] storagePoints = new File("/storage").listFiles();
            if ("primary".equalsIgnoreCase(type)) {
                file = new File(Environment.getExternalStorageDirectory(), path);
            }
            int i = 0;
            while (storagePoints != null && i < storagePoints.length && file == null) {
                File externalFile = new File(storagePoints[i], path);
                if (externalFile.exists()) {
                    file = externalFile;
                }
                i++;
            }
            if (file == null) {
                return new File(path);
            }
            return file;
        } else if ("com.android.providers.downloads.documents".equals(uri.getAuthority())) {
            filename = getDataColumn(ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.parseLong(DocumentsContract.getDocumentId(uri))), null, null);
            if (filename != null) {
                return new File(filename);
            }
            return null;
        } else if (!"com.android.providers.media.documents".equals(uri.getAuthority())) {
            return null;
        } else {
            type = DocumentsContract.getDocumentId(uri).split(":")[0];
            Uri contentUri = null;
            if ("image".equals(type)) {
                contentUri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            } else if ("video".equals(type)) {
                contentUri = android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            } else if ("audio".equals(type)) {
                contentUri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            }
            String selection = "_id=?";
            filename = getDataColumn(contentUri, "_id=?", new String[]{split[1]});
            if (filename != null) {
                return new File(filename);
            }
            return null;
        }
    }

    private String getDataColumn(Uri uri, String selection, String[] selectionArgs) {
        String column = "_data";
        Cursor cursor = null;
        try {
            cursor = this.context.getContentResolver().query(uri, new String[]{"_data"}, selection, selectionArgs, null);
            if (cursor == null || !cursor.moveToFirst()) {
                if (cursor != null) {
                    cursor.close();
                }
                return null;
            }
            String string = cursor.getString(cursor.getColumnIndexOrThrow("_data"));
            return string;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private String createMediaFilename(int type, String suffix, int count, String extension, Date current_date) {
        String timeStamp;
        String index = "";
        if (count > 0) {
            index = "_" + count;
        }
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.context);
        if (sharedPreferences.getString(PreferenceKeys.getSaveZuluTimePreferenceKey(), "local").equals("zulu")) {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd_HHmmss'Z'", Locale.US);
            fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
            timeStamp = fmt.format(current_date);
        } else {
            timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(current_date);
        }
        if (type == 1) {
            return sharedPreferences.getString(PreferenceKeys.getSavePhotoPrefixPreferenceKey(), "IMG_") + timeStamp + suffix + index + "." + extension;
        } else if (type == 2) {
            return sharedPreferences.getString(PreferenceKeys.getSaveVideoPrefixPreferenceKey(), "VID_") + timeStamp + suffix + index + "." + extension;
        } else {
            throw new RuntimeException();
        }
    }

    @SuppressLint({"SimpleDateFormat"})
    File createOutputMediaFile(int type, String suffix, String extension, Date current_date) throws IOException {
        File mediaStorageDir = getImageFolder();
        if (!mediaStorageDir.exists()) {
            if (mediaStorageDir.mkdirs()) {
                broadcastFile(mediaStorageDir, false, false, false);
            } else {
                throw new IOException();
            }
        }
        File mediaFile = null;
        for (int count = 0; count < 100; count++) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator + createMediaFilename(type, suffix, count, extension, current_date));
            if (!mediaFile.exists()) {
                break;
            }
        }
        if (mediaFile != null) {
            return mediaFile;
        }
        throw new IOException();
    }

    @TargetApi(21)
    Uri createOutputFileSAF(String filename, String mimeType) throws IOException {
        try {
            Uri treeUri = getTreeUriSAF();
            Uri fileUri = DocumentsContract.createDocument(this.context.getContentResolver(), DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri)), mimeType, filename);
            if (fileUri != null) {
                return fileUri;
            }
            throw new IOException();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            throw new IOException();
        }
    }

    @TargetApi(21)
    Uri createOutputMediaFileSAF(int type, String suffix, String extension, Date current_date) throws IOException {
        String mimeType;
        if (type == 1) {
            if (extension.equals("dng")) {
                mimeType = "image/dng";
            } else {
                mimeType = "image/jpeg";
            }
        } else if (type == 2) {
            mimeType = "video/mp4";
        } else {
            throw new RuntimeException();
        }
        return createOutputFileSAF(createMediaFilename(type, suffix, 0, extension, current_date), mimeType);
    }

    private Media getLatestMedia(boolean video) {
        if (VERSION.SDK_INT >= 23 && ContextCompat.checkSelfPermission(this.context, "android.permission.READ_EXTERNAL_STORAGE") != 0) {
            return null;
        }
        Uri baseUri = video ? android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI : android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        Cursor cursor = null;
        try {
            Media media;
            cursor = this.context.getContentResolver().query(baseUri, video ? new String[]{"_id", "datetaken", "_data"} : new String[]{"_id", "datetaken", "_data", "orientation"}, video ? "" : "mime_type='image/jpeg'", null, video ? "datetaken DESC,_id DESC" : "datetaken DESC,_id DESC");
            if (cursor == null || !cursor.moveToFirst()) {
                media = null;
            } else {
                String save_folder_string;
                boolean found = false;
                File save_folder = getImageFolder();
                if (save_folder == null) {
                    save_folder_string = null;
                } else {
                    save_folder_string = save_folder.getAbsolutePath() + File.separator;
                }
                do {
                    String path = cursor.getString(2);
                    if ((save_folder_string == null || (path != null && path.contains(save_folder_string))) && cursor.getLong(1) <= 172800000 + System.currentTimeMillis()) {
                        found = true;
                        break;
                    }
                } while (cursor.moveToNext());
                if (!found) {
                    cursor.moveToFirst();
                }
                long id = cursor.getLong(0);
                media = new Media(id, video, ContentUris.withAppendedId(baseUri, id), cursor.getLong(1), video ? 0 : cursor.getInt(3));
            }
            if (cursor == null) {
                return media;
            }
            cursor.close();
            return media;
        } catch (Exception e) {
            e.printStackTrace();
            if (cursor == null) {
                return null;
            }
            cursor.close();
            return null;
        } catch (Throwable th) {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    Media getLatestMedia() {
        Media image_media = getLatestMedia(false);
        Media video_media = getLatestMedia(true);
        if (image_media != null && video_media == null) {
            return image_media;
        }
        if (image_media == null && video_media != null) {
            return video_media;
        }
        if (image_media == null || video_media == null) {
            return null;
        }
        if (image_media.date >= video_media.date) {
            return image_media;
        }
        return video_media;
    }
}
