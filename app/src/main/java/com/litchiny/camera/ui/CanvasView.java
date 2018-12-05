package com.litchiny.camera.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Handler;
import android.view.View;

public class CanvasView extends View {
    private static final String TAG = "CanvasView";
    private final Handler handler = new Handler();
    private final int[] measure_spec = new int[2];
    private final Preview preview;
    private final Runnable tick;

    CanvasView(Context context, final Preview preview) {
        super(context);
        this.preview = preview;
        this.tick = new Runnable() {
            public void run() {
                preview.test_ticker_called = true;
                CanvasView.this.invalidate();
                CanvasView.this.handler.postDelayed(this, preview.isTakingPhoto() ? 500 : 100);
            }
        };
    }

    public void onDraw(Canvas canvas) {
        this.preview.draw(canvas);
    }

    protected void onMeasure(int widthSpec, int heightSpec) {
        this.preview.getMeasureSpec(this.measure_spec, widthSpec, heightSpec);
        super.onMeasure(this.measure_spec[0], this.measure_spec[1]);
    }

    void onPause() {
        this.handler.removeCallbacks(this.tick);
    }

    void onResume() {
        this.tick.run();
    }
}
