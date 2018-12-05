package com.litchiny.camera.ui.surface;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Matrix;
import android.media.MediaRecorder;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import com.litchiny.camera.controller.CameraController;
import com.litchiny.camera.controller.CameraControllerException;
import com.litchiny.camera.ui.Preview;

public class MyTextureView extends TextureView implements CameraSurface {
    private static final String TAG = "MyTextureView";
    private final int[] measure_spec = new int[2];
    private final Preview preview;

    public MyTextureView(Context context, Preview preview) {
        super(context);
        this.preview = preview;
        setSurfaceTextureListener(preview);
    }

    public View getView() {
        return this;
    }

    public void setPreviewDisplay(CameraController camera_controller) {
        try {
            camera_controller.setPreviewTexture(getSurfaceTexture());
        } catch (CameraControllerException e) {
            e.printStackTrace();
        }
    }

    public void setVideoRecorder(MediaRecorder video_recorder) {
    }

    @SuppressLint({"ClickableViewAccessibility"})
    public boolean onTouchEvent(MotionEvent event) {
        return this.preview.touchEvent(event);
    }

    protected void onMeasure(int widthSpec, int heightSpec) {
        this.preview.getMeasureSpec(this.measure_spec, widthSpec, heightSpec);
        super.onMeasure(this.measure_spec[0], this.measure_spec[1]);
    }

    public void setTransform(Matrix matrix) {
        super.setTransform(matrix);
    }

    public void onPause() {
    }

    public void onResume() {
    }
}
