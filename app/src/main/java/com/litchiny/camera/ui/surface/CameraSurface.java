package com.litchiny.camera.ui.surface;

import android.graphics.Matrix;
import android.media.MediaRecorder;
import android.view.View;
import com.litchiny.camera.controller.CameraController;

public interface CameraSurface {
    View getView();

    void onPause();

    void onResume();

    void setPreviewDisplay(CameraController cameraController);

    void setTransform(Matrix matrix);

    void setVideoRecorder(MediaRecorder mediaRecorder);
}
