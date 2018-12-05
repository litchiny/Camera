package com.litchiny.camera.ui.surface;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.media.MediaRecorder;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import com.litchiny.camera.controller.CameraController;
import com.litchiny.camera.controller.CameraControllerException;
import com.litchiny.camera.ui.Preview;

public class MySurfaceView extends SurfaceView implements CameraSurface {
    private static final String TAG = "MySurfaceView";
    private final Handler handler = new Handler();
    private final int[] measure_spec = new int[2];
    private final Preview preview;
    private final Runnable tick;

    public MySurfaceView(Context context, final Preview preview) {
        super(context);
        this.preview = preview;
        getHolder().addCallback(preview);
        getHolder().setType(3);
        this.tick = new Runnable() {
            public void run() {
                preview.test_ticker_called = true;
                MySurfaceView.this.invalidate();
                MySurfaceView.this.handler.postDelayed(this, preview.isTakingPhoto() ? 500 : 100);
            }
        };
    }

    public View getView() {
        return this;
    }

    public void setPreviewDisplay(CameraController camera_controller) {
        try {
            camera_controller.setPreviewDisplay(getHolder());
        } catch (CameraControllerException e) {
            e.printStackTrace();
        }
    }

    public void setVideoRecorder(MediaRecorder video_recorder) {
        video_recorder.setPreviewDisplay(getHolder().getSurface());
    }

    @SuppressLint({"ClickableViewAccessibility"})
    public boolean onTouchEvent(MotionEvent event) {
        return this.preview.touchEvent(event);
    }

    public void onDraw(Canvas canvas) {
        this.preview.draw(canvas);
    }

    protected void onMeasure(int widthSpec, int heightSpec) {
        this.preview.getMeasureSpec(this.measure_spec, widthSpec, heightSpec);
        super.onMeasure(this.measure_spec[0], this.measure_spec[1]);
    }

    public void setTransform(Matrix matrix) {
        throw new RuntimeException();
    }

    public void onPause() {
        this.handler.removeCallbacks(this.tick);
    }

    public void onResume() {
        this.tick.run();
    }
}
