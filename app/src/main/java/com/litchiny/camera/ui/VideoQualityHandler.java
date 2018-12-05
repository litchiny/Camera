package com.litchiny.camera.ui;

import android.util.Log;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import com.litchiny.camera.controller.CameraController.Size;

public class VideoQualityHandler {
    private static final String TAG = "VideoQualityHandler";
    private int current_video_quality = -1;
    private List<String> video_quality;
    private List<Size> video_sizes;

    public static class Dimension2D {
        final int height;
        final int width;

        public Dimension2D(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    private static class SortVideoSizesComparator implements Comparator<Size>, Serializable {
        private static final long serialVersionUID = 5802214721033718212L;

        private SortVideoSizesComparator() {
        }

        public int compare(Size a, Size b) {
            return (b.width * b.height) - (a.width * a.height);
        }
    }

    void resetCurrentQuality() {
        this.video_quality = null;
        this.current_video_quality = -1;
    }

    public void initialiseVideoQualityFromProfiles(List<Integer> profiles, List<Dimension2D> dimensions) {
        int i;
        this.video_quality = new ArrayList();
        boolean[] done_video_size = null;
        if (this.video_sizes != null) {
            done_video_size = new boolean[this.video_sizes.size()];
            for (i = 0; i < this.video_sizes.size(); i++) {
                done_video_size[i] = false;
            }
        }
        if (profiles.size() != dimensions.size()) {
            Log.e(TAG, "profiles and dimensions have unequal sizes");
            throw new RuntimeException();
        }
        for (i = 0; i < profiles.size(); i++) {
            Dimension2D dim = (Dimension2D) dimensions.get(i);
            addVideoResolutions(done_video_size, ((Integer) profiles.get(i)).intValue(), dim.width, dim.height);
        }
    }

    public void sortVideoSizes() {
        Collections.sort(this.video_sizes, new SortVideoSizesComparator());
    }

    private void addVideoResolutions(boolean[] done_video_size, int base_profile, int min_resolution_w, int min_resolution_h) {
        if (this.video_sizes != null) {
            for (int i = 0; i < this.video_sizes.size(); i++) {
                if (!done_video_size[i]) {
                    Size size = (Size) this.video_sizes.get(i);
                    if (size.width == min_resolution_w && size.height == min_resolution_h) {
                        this.video_quality.add("" + base_profile);
                        done_video_size[i] = true;
                    } else if (base_profile == 0 || size.width * size.height >= min_resolution_w * min_resolution_h) {
                        this.video_quality.add("" + base_profile + "_r" + size.width + "x" + size.height);
                        done_video_size[i] = true;
                    }
                }
            }
        }
    }

    public List<String> getSupportedVideoQuality() {
        return this.video_quality;
    }

    public int getCurrentVideoQualityIndex() {
        return this.current_video_quality;
    }

    public void setCurrentVideoQualityIndex(int current_video_quality) {
        this.current_video_quality = current_video_quality;
    }

    public String getCurrentVideoQuality() {
        if (this.current_video_quality == -1) {
            return null;
        }
        return (String) this.video_quality.get(this.current_video_quality);
    }

    public List<Size> getSupportedVideoSizes() {
        return this.video_sizes;
    }

    public void setVideoSizes(List<Size> video_sizes) {
        this.video_sizes = video_sizes;
        sortVideoSizes();
    }
}
