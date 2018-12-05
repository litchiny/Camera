package com.litchiny.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Script.LaunchOptions;
import android.renderscript.Type;
import android.support.annotation.RequiresApi;
import android.support.v4.view.MotionEventCompat;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.litchiny.camera.ui.PopupView;

import static android.view.KeyEvent.KEYCODE_MEDIA_PAUSE;

public class HDRProcessor {
    private static final String TAG = "HDRProcessor";
    private final Context context;
    public final int[] offsets_x = new int[]{0, 0, 0};
    public final int[] offsets_y = new int[]{0, 0, 0};
    private RenderScript rs;

    class AnonymousClass1BitmapInfo {
        final Allocation allocation;
        final Bitmap bitmap;
        final int index;
        final LuminanceInfo luminanceInfo;

        AnonymousClass1BitmapInfo(LuminanceInfo luminanceInfo, Bitmap bitmap, Allocation allocation, int index) {
            this.luminanceInfo = luminanceInfo;
            this.bitmap = bitmap;
            this.allocation = allocation;
            this.index = index;
        }
    }

    private enum HDRAlgorithm {
        HDRALGORITHM_STANDARD,
        HDRALGORITHM_SINGLE_IMAGE
    }

    private static class LuminanceInfo {
        final int median_value;
        final boolean noisy;

        LuminanceInfo(int median_value, boolean noisy) {
            this.median_value = median_value;
            this.noisy = noisy;
        }
    }

    private static class ResponseFunction {
        float parameter_A;
        float parameter_B;

        ResponseFunction(Context context, int id, List<Double> x_samples, List<Double> y_samples, List<Double> weights) {
            if (x_samples.size() != y_samples.size()) {
                throw new RuntimeException();
            }
            if (x_samples.size() != weights.size()) {
                throw new RuntimeException();
            } else if (x_samples.size() <= 3) {
                throw new RuntimeException();
            } else {
                int i;
                double x;
                double y;
                double w;
                boolean done = false;
                double sum_wx = 0.0d;
                double sum_wx2 = 0.0d;
                double sum_wxy = 0.0d;
                double sum_wy = 0.0d;
                double sum_w = 0.0d;
                for (i = 0; i < x_samples.size(); i++) {
                    x = ((Double) x_samples.get(i)).doubleValue();
                    y = ((Double) y_samples.get(i)).doubleValue();
                    w = ((Double) weights.get(i)).doubleValue();
                    sum_wx += w * x;
                    sum_wx2 += (w * x) * x;
                    sum_wxy += (w * x) * y;
                    sum_wy += w * y;
                    sum_w += w;
                }
                double A_numer = (sum_wy * sum_wx) - (sum_w * sum_wxy);
                double A_denom = (sum_wx * sum_wx) - (sum_w * sum_wx2);
                if (Math.abs(A_denom) >= 1.0E-5d) {
                    this.parameter_A = (float) (A_numer / A_denom);
                    this.parameter_B = (float) ((sum_wy - (((double) this.parameter_A) * sum_wx)) / sum_w);
                    if (((double) this.parameter_A) >= 1.0E-5d && ((double) this.parameter_B) >= 1.0E-5d) {
                        done = true;
                    }
                }
                if (!done) {
                    double numer = 0.0d;
                    double denom = 0.0d;
                    for (i = 0; i < x_samples.size(); i++) {
                        x = ((Double) x_samples.get(i)).doubleValue();
                        y = ((Double) y_samples.get(i)).doubleValue();
                        w = ((Double) weights.get(i)).doubleValue();
                        numer += (w * x) * y;
                        denom += (w * x) * x;
                    }
                    if (denom < 1.0E-5d) {
                        this.parameter_A = PopupView.ALPHA_BUTTON_SELECTED;
                    } else {
                        this.parameter_A = (float) (numer / denom);
                        if (((double) this.parameter_A) < 1.0E-5d) {
                            this.parameter_A = 1.0E-5f;
                        }
                    }
                    this.parameter_B = 0.0f;
                }
            }
        }
    }

    public interface SortCallback {
        void sortOrder(List<Integer> list);
    }

    public enum TonemappingAlgorithm {
        TONEMAPALGORITHM_CLAMP,
        TONEMAPALGORITHM_REINHARD,
        TONEMAPALGORITHM_FILMIC
    }

    public HDRProcessor(Context context) {
        this.context = context;
    }

    public void onDestroy() {
        if (this.rs != null) {
            this.rs.destroy();
            this.rs = null;
        }
    }

    @RequiresApi(api = 21)
    public void processHDR(List<Bitmap> bitmaps, boolean release_bitmaps, Bitmap output_bitmap, boolean assume_sorted, SortCallback sort_cb, float hdr_alpha, TonemappingAlgorithm tonemapping_algorithm) throws HDRProcessorException {
        if (!(assume_sorted || release_bitmaps)) {
            bitmaps = new ArrayList(bitmaps);
        }
        int n_bitmaps = bitmaps.size();
        if (n_bitmaps == 1 || n_bitmaps == 3) {
            int i = 1;
            while (i < n_bitmaps) {
                if (((Bitmap) bitmaps.get(i)).getWidth() == ((Bitmap) bitmaps.get(0)).getWidth() && ((Bitmap) bitmaps.get(i)).getHeight() == ((Bitmap) bitmaps.get(0)).getHeight()) {
                    i++;
                } else {
                    throw new HDRProcessorException(1);
                }
            }
            switch (n_bitmaps == 1 ? HDRAlgorithm.HDRALGORITHM_SINGLE_IMAGE : HDRAlgorithm.HDRALGORITHM_STANDARD) {
                case HDRALGORITHM_SINGLE_IMAGE:
                    if (!(assume_sorted || sort_cb == null)) {
                        List<Integer> sort_order = new ArrayList();
                        sort_order.add(Integer.valueOf(0));
                        sort_cb.sortOrder(sort_order);
                    }
                    processSingleImage(bitmaps, release_bitmaps, output_bitmap, hdr_alpha);
                    return;
                case HDRALGORITHM_STANDARD:
                    processHDRCore(bitmaps, release_bitmaps, output_bitmap, assume_sorted, sort_cb, hdr_alpha, tonemapping_algorithm);
                    return;
                default:
                    throw new RuntimeException();
            }
        }
        throw new HDRProcessorException(0);
    }

    private ResponseFunction createFunctionFromBitmaps(int id, Bitmap in_bitmap, Bitmap out_bitmap, int offset_x, int offset_y) {
        int i;
        List<Double> x_samples = new ArrayList();
        List<Double> y_samples = new ArrayList();
        List<Double> weights = new ArrayList();
        int n_w_samples = (int) Math.sqrt(100.0d);
        int n_h_samples = 100 / n_w_samples;
        double avg_in = 0.0d;
        double avg_out = 0.0d;
        for (int y = 0; y < n_h_samples; y++) {
            int y_coord = (int) (((double) in_bitmap.getHeight()) * ((((double) y) + 1.0d) / (((double) n_h_samples) + 1.0d)));
            for (int x = 0; x < n_w_samples; x++) {
                int x_coord = (int) (((double) in_bitmap.getWidth()) * ((((double) x) + 1.0d) / (((double) n_w_samples) + 1.0d)));
                if (x_coord + offset_x >= 0 && x_coord + offset_x < in_bitmap.getWidth() && y_coord + offset_y >= 0 && y_coord + offset_y < in_bitmap.getHeight()) {
                    int in_col = in_bitmap.getPixel(x_coord + offset_x, y_coord + offset_y);
                    int out_col = out_bitmap.getPixel(x_coord, y_coord);
                    double in_value = averageRGB(in_col);
                    double out_value = averageRGB(out_col);
                    avg_in += in_value;
                    avg_out += out_value;
                    x_samples.add(Double.valueOf(in_value));
                    y_samples.add(Double.valueOf(out_value));
                }
            }
        }
        if (x_samples.size() == 0) {
            Log.e(TAG, "no samples for response function!");
            avg_in += 255.0d;
            avg_out += 255.0d;
            x_samples.add(Double.valueOf(255.0d));
            y_samples.add(Double.valueOf(255.0d));
        }
        boolean is_dark_exposure = avg_in / ((double) x_samples.size()) < avg_out / ((double) x_samples.size());
        double min_value = ((Double) x_samples.get(0)).doubleValue();
        double max_value = ((Double) x_samples.get(0)).doubleValue();
        for (i = 1; i < x_samples.size(); i++) {
            double value = ((Double) x_samples.get(i)).doubleValue();
            if (value < min_value) {
                min_value = value;
            }
            if (value > max_value) {
                max_value = value;
            }
        }
        double med_value = 0.5d * (min_value + max_value);
        double min_value_y = ((Double) y_samples.get(0)).doubleValue();
        double max_value_y = ((Double) y_samples.get(0)).doubleValue();
        Double value;
        for (i = 1; i < y_samples.size(); i++) {
            value = ((Double) y_samples.get(i)).doubleValue();
            if (value < min_value_y) {
                min_value_y = value;
            }
            if (value > max_value_y) {
                max_value_y = value;
            }
        }
        double med_value_y = 0.5d * (min_value_y + max_value_y);
        for (i = 0; i < x_samples.size(); i++) {
            value = ((Double) x_samples.get(i)).doubleValue();
            double value_y = ((Double) y_samples.get(i)).doubleValue();
            if (is_dark_exposure) {
                double weight = value <= med_value ? value - min_value : max_value - value;
                double weight_y = value_y <= med_value_y ? value_y - min_value_y : max_value_y - value_y;
                if (weight_y < weight) {
                    weight = weight_y;
                }
                weights.add(Double.valueOf(weight));
            } else {
                weights.add(Double.valueOf(value <= med_value ? value - min_value : max_value - value));
            }
        }
        return new ResponseFunction(this.context, id, x_samples, y_samples, weights);
    }

    private double averageRGB(int color) {
        return ((double) ((((16711680 & color) >> 16) + ((MotionEventCompat.ACTION_POINTER_INDEX_MASK & color) >> 8)) + (color & 255))) / 3.0d;
    }

    @RequiresApi(api = 21)
    private void processHDRCore(List<Bitmap> bitmaps, boolean release_bitmaps, Bitmap output_bitmap, boolean assume_sorted, SortCallback sort_cb, float hdr_alpha, TonemappingAlgorithm tonemapping_algorithm) {
        int i;
        Allocation output_allocation;
        long time_s = System.currentTimeMillis();
        int n_bitmaps = bitmaps.size();
        int width = ((Bitmap) bitmaps.get(0)).getWidth();
        int height = ((Bitmap) bitmaps.get(0)).getHeight();
        ResponseFunction[] response_functions = new ResponseFunction[n_bitmaps];
        initRenderscript();
        Allocation[] allocations = new Allocation[n_bitmaps];
        for (i = 0; i < n_bitmaps; i++) {
            allocations[i] = Allocation.createFromBitmap(this.rs, (Bitmap) bitmaps.get(i));
        }
        autoAlignment(this.offsets_x, this.offsets_y, allocations, width, height, bitmaps, assume_sorted, sort_cb, time_s);
        for (i = 0; i < n_bitmaps; i++) {
            ResponseFunction function = null;
            if (i != 1) {
                function = createFunctionFromBitmaps(i, (Bitmap) bitmaps.get(i), (Bitmap) bitmaps.get(1), this.offsets_x[i], this.offsets_y[i]);
            }
            response_functions[i] = function;
        }
        ScriptC_process_hdr scriptC_process_hdr = new ScriptC_process_hdr(this.rs);
        scriptC_process_hdr.set_bitmap0(allocations[0]);
        scriptC_process_hdr.set_bitmap2(allocations[2]);
        scriptC_process_hdr.set_offset_x0(this.offsets_x[0]);
        scriptC_process_hdr.set_offset_y0(this.offsets_y[0]);
        scriptC_process_hdr.set_offset_x2(this.offsets_x[2]);
        scriptC_process_hdr.set_offset_y2(this.offsets_y[2]);
        scriptC_process_hdr.set_parameter_A0(response_functions[0].parameter_A);
        scriptC_process_hdr.set_parameter_B0(response_functions[0].parameter_B);
        scriptC_process_hdr.set_parameter_A2(response_functions[2].parameter_A);
        scriptC_process_hdr.set_parameter_B2(response_functions[2].parameter_B);
        switch (tonemapping_algorithm) {
            case TONEMAPALGORITHM_CLAMP:
                scriptC_process_hdr.set_tonemap_algorithm(scriptC_process_hdr.get_tonemap_algorithm_clamp_c());
                break;
            case TONEMAPALGORITHM_REINHARD:
                scriptC_process_hdr.set_tonemap_algorithm(scriptC_process_hdr.get_tonemap_algorithm_reinhard_c());
                break;
            case TONEMAPALGORITHM_FILMIC:
                scriptC_process_hdr.set_tonemap_algorithm(scriptC_process_hdr.get_tonemap_algorithm_filmic_c());
                break;
        }
        scriptC_process_hdr.set_tonemap_scale(255.0f);
        if (release_bitmaps) {
            output_allocation = allocations[1];
        } else {
            output_allocation = Allocation.createFromBitmap(this.rs, output_bitmap);
        }
        scriptC_process_hdr.forEach_hdr(allocations[1], output_allocation);
        if (release_bitmaps) {
            for (i = 0; i < bitmaps.size(); i++) {
                if (i != 1) {
                    ((Bitmap) bitmaps.get(i)).recycle();
                }
            }
        }
        if (hdr_alpha != 0.0f) {
            adjustHistogram(output_allocation, output_allocation, width, height, hdr_alpha, time_s);
        }
        if (release_bitmaps) {
            allocations[1].copyTo((Bitmap) bitmaps.get(1));
            bitmaps.set(0, bitmaps.get(1));
            for (i = 1; i < bitmaps.size(); i++) {
                bitmaps.set(i, null);
            }
            return;
        }
        output_allocation.copyTo(output_bitmap);
    }

    @RequiresApi(api = 21)
    private void processSingleImage(List<Bitmap> bitmaps, boolean release_bitmaps, Bitmap output_bitmap, float hdr_alpha) {
        Allocation output_allocation;
        long time_s = System.currentTimeMillis();
        int width = ((Bitmap) bitmaps.get(0)).getWidth();
        int height = ((Bitmap) bitmaps.get(0)).getHeight();
        initRenderscript();
        Allocation allocation = Allocation.createFromBitmap(this.rs, (Bitmap) bitmaps.get(0));
        if (release_bitmaps) {
            output_allocation = allocation;
        } else {
            output_allocation = Allocation.createFromBitmap(this.rs, output_bitmap);
        }
        adjustHistogram(allocation, output_allocation, width, height, hdr_alpha, time_s);
        if (release_bitmaps) {
            allocation.copyTo((Bitmap) bitmaps.get(0));
        } else {
            output_allocation.copyTo(output_bitmap);
        }
    }

    private void initRenderscript() {
        if (this.rs == null) {
            this.rs = RenderScript.create(this.context);
        }
    }

    @RequiresApi(api = 21)
    private void autoAlignment(int[] offsets_x, int[] offsets_y, Allocation[] allocations, int width, int height, List<Bitmap> bitmaps, boolean assume_sorted, SortCallback sort_cb, long time_s) {
        int i;
        Allocation[] mtb_allocations = new Allocation[allocations.length];
        int mtb_width = width / 2;
        int mtb_height = height / 2;
        int mtb_x = mtb_width / 2;
        int mtb_y = mtb_height / 2;
        ScriptC_create_mtb scriptC_create_mtb = new ScriptC_create_mtb(this.rs);
        LuminanceInfo[] luminanceInfos = new LuminanceInfo[allocations.length];
        for (i = 0; i < allocations.length; i++) {
            luminanceInfos[i] = computeMedianLuminance((Bitmap) bitmaps.get(i), mtb_x, mtb_y, mtb_width, mtb_height);
        }
        if (!assume_sorted) {
            List<AnonymousClass1BitmapInfo> arrayList = new ArrayList(bitmaps.size());
            for (i = 0; i < bitmaps.size(); i++) {
                arrayList.add(new AnonymousClass1BitmapInfo(luminanceInfos[i], (Bitmap) bitmaps.get(i), allocations[i], i));
            }
            Collections.sort(arrayList, new Comparator<AnonymousClass1BitmapInfo>() {
                public int compare(AnonymousClass1BitmapInfo o1, AnonymousClass1BitmapInfo o2) {
                    return o1.luminanceInfo.median_value - o2.luminanceInfo.median_value;
                }
            });
            bitmaps.clear();
            for (i = 0; i < arrayList.size(); i++) {
                bitmaps.add(((AnonymousClass1BitmapInfo) arrayList.get(i)).bitmap);
                luminanceInfos[i] = ((AnonymousClass1BitmapInfo) arrayList.get(i)).luminanceInfo;
                allocations[i] = ((AnonymousClass1BitmapInfo) arrayList.get(i)).allocation;
            }
            if (sort_cb != null) {
                List<Integer> sort_order = new ArrayList();
                for (i = 0; i < arrayList.size(); i++) {
                    sort_order.add(Integer.valueOf(((AnonymousClass1BitmapInfo) arrayList.get(i)).index));
                }
                sort_cb.sortOrder(sort_order);
            }
        }
        for (i = 0; i < allocations.length; i++) {
            int median_value = luminanceInfos[i].median_value;
            if (luminanceInfos[i].noisy) {
                mtb_allocations[i] = null;
            } else {
                mtb_allocations[i] = Allocation.createTyped(this.rs, Type.createXY(this.rs, Element.U8(this.rs), mtb_width, mtb_height));
                scriptC_create_mtb.set_median_value(median_value);
                scriptC_create_mtb.set_start_x(mtb_x);
                scriptC_create_mtb.set_start_y(mtb_y);
                scriptC_create_mtb.set_out_bitmap(mtb_allocations[i]);
                LaunchOptions launch_options = new LaunchOptions();
                launch_options.setX(mtb_x, mtb_x + mtb_width);
                launch_options.setY(mtb_y, mtb_y + mtb_height);
                scriptC_create_mtb.forEach_create_mtb(allocations[i], launch_options);
            }
        }
        int initial_step_size = 1;
        while (initial_step_size < Math.max(width, height) / 150) {
            initial_step_size *= 2;
        }
        if (mtb_allocations[1] != null) {
            ScriptC_align_mtb alignMTBScript = new ScriptC_align_mtb(this.rs);
            alignMTBScript.set_bitmap0(mtb_allocations[1]);
            i = 0;
            LaunchOptions launch_options = null;
            while (i < 3) {
                if (!(i == 1 || mtb_allocations[i] == null)) {
                    alignMTBScript.set_bitmap1(mtb_allocations[i]);
                    int step_size = initial_step_size;
                    while (step_size > 1) {
                        step_size /= 2;
                        alignMTBScript.set_off_x(offsets_x[i]);
                        alignMTBScript.set_off_y(offsets_y[i]);
                        alignMTBScript.set_step_size(step_size);
                        Allocation errorsAllocation = Allocation.createSized(this.rs, Element.I32(this.rs), 9);
                        alignMTBScript.bind_errors(errorsAllocation);
                        alignMTBScript.invoke_init_errors();
                        launch_options = new LaunchOptions();
                        int stop_y = mtb_height / step_size;
                        launch_options.setX(0, mtb_width / step_size);
                        launch_options.setY(0, stop_y);
                        alignMTBScript.forEach_align_mtb(mtb_allocations[1], launch_options);
                        int best_error = -1;
                        int best_id = -1;
                        int[] errors = new int[9];
                        errorsAllocation.copyTo(errors);
                        for (int j = 0; j < 9; j++) {
                            int this_error = errors[j];
                            if (best_id == -1 || this_error < best_error) {
                                best_error = this_error;
                                best_id = j;
                            }
                        }
                        if (best_id != -1) {
                            int this_off_y = (best_id / 3) - 1;
                            offsets_x[i] = offsets_x[i] + (((best_id % 3) - 1) * step_size);
                            offsets_y[i] = offsets_y[i] + (this_off_y * step_size);
                        }
                    }
                }
                i++;
            }
        }
    }

    private LuminanceInfo computeMedianLuminance(Bitmap bitmap, int mtb_x, int mtb_y, int mtb_width, int mtb_height) {
        int i;
        int n_w_samples = (int) Math.sqrt(100.0d);
        int n_h_samples = 100 / n_w_samples;
        int[] histo = new int[256];
        for (i = 0; i < 256; i++) {
            histo[i] = 0;
        }
        int total = 0;
        for (int y = 0; y < n_h_samples; y++) {
            int y_coord = mtb_y + ((int) (((double) mtb_height) * ((((double) y) + 1.0d) / (((double) n_h_samples) + 1.0d))));
            for (int x = 0; x < n_w_samples; x++) {
                int color = bitmap.getPixel(mtb_x + ((int) (((double) mtb_width) * ((((double) x) + 1.0d) / (((double) n_w_samples) + 1.0d)))), y_coord);
                int luminance = Math.max(Math.max((16711680 & color) >> 16, (MotionEventCompat.ACTION_POINTER_INDEX_MASK & color) >> 8), color & 255);
                histo[luminance] = histo[luminance] + 1;
                total++;
            }
        }
        int middle = total / 2;
        int count = 0;
        boolean noisy = false;
        for (i = 0; i < 256; i++) {
            count += histo[i];
            if (count >= middle) {
                int j;
                int n_below = 0;
                int n_above = 0;
                for (j = 0; j <= i - 4; j++) {
                    n_below += histo[j];
                }
                j = 0;
                while (j <= i + 4 && j < 256) {
                    n_above += histo[j];
                    j++;
                }
                if (((double) n_below) / ((double) total) < 0.2d) {
                    noisy = true;
                }
                return new LuminanceInfo(i, noisy);
            }
        }
        Log.e(TAG, "computeMedianLuminance failed");
        return new LuminanceInfo(KEYCODE_MEDIA_PAUSE, true);
    }

    @RequiresApi(api = 21)
    private void adjustHistogram(Allocation allocation_in, Allocation allocation_out, int width, int height, float hdr_alpha, long time_s) {
        Allocation histogramAllocation = Allocation.createSized(this.rs, Element.I32(this.rs), 256);
        ScriptC_histogram_compute scriptC_histogram_compute = new ScriptC_histogram_compute(this.rs);
        scriptC_histogram_compute.bind_histogram(histogramAllocation);
        int[] c_histogram = new int[4096];
        for (int i = 0; i < 4; i++) {
            int start_x = (int) (((double) width) * (((double) i) / 4.0d));
            int stop_x = (int) (((double) width) * ((((double) i) + 1.0d) / 4.0d));
            if (stop_x != start_x) {
                for (int j = 0; j < 4; j++) {
                    int start_y = (int) (((double) height) * (((double) j) / 4.0d));
                    int stop_y = (int) (((double) height) * ((((double) j) + 1.0d) / 4.0d));
                    if (stop_y != start_y) {
                        int x;
                        LaunchOptions launch_options = new LaunchOptions();
                        launch_options.setX(start_x, stop_x);
                        launch_options.setY(start_y, stop_y);
                        scriptC_histogram_compute.invoke_init_histogram();
                        scriptC_histogram_compute.forEach_histogram_compute(allocation_in, launch_options);
                        int[] histogram = new int[256];
                        histogramAllocation.copyTo(histogram);
                        int clip_limit = (((stop_x - start_x) * (stop_y - start_y)) * 5) / 256;
                        int bottom = 0;
                        int top = clip_limit;
                        while (top - bottom > 1) {
                            int middle = (top + bottom) / 2;
                            int sum = 0;
                            for (x = 0; x < 256; x++) {
                                if (histogram[x] > middle) {
                                    sum += histogram[x] - clip_limit;
                                }
                            }
                            if (sum > (clip_limit - middle) * 256) {
                                top = middle;
                            } else {
                                bottom = middle;
                            }
                        }
                        clip_limit = (top + bottom) / 2;
                        int n_clipped = 0;
                        for (x = 0; x < 256; x++) {
                            if (histogram[x] > clip_limit) {
                                n_clipped += histogram[x] - clip_limit;
                                histogram[x] = clip_limit;
                            }
                        }
                        int n_clipped_per_bucket = n_clipped / 256;
                        for (x = 0; x < 256; x++) {
                            histogram[x] = histogram[x] + n_clipped_per_bucket;
                        }
                        int histogram_offset = ((i * 4) + j) * 256;
                        c_histogram[histogram_offset] = histogram[0];
                        for (x = 1; x < 256; x++) {
                            c_histogram[histogram_offset + x] = c_histogram[(histogram_offset + x) - 1] + histogram[x];
                        }
                    }
                }
            }
        }
        Allocation c_histogramAllocation = Allocation.createSized(this.rs, Element.I32(this.rs), 4096);
        c_histogramAllocation.copyFrom(c_histogram);
        ScriptC_histogram_adjust scriptC_histogram_adjust = new ScriptC_histogram_adjust(this.rs);
        scriptC_histogram_adjust.set_c_histogram(c_histogramAllocation);
        scriptC_histogram_adjust.set_hdr_alpha(hdr_alpha);
        scriptC_histogram_adjust.set_n_tiles(4);
        scriptC_histogram_adjust.set_width(width);
        scriptC_histogram_adjust.set_height(height);
        scriptC_histogram_adjust.forEach_histogram_adjust(allocation_in, allocation_out);
    }

    @RequiresApi(api = 21)
    private Allocation computeHistogramAllocation(Allocation allocation_in, boolean avg, long time_s) {
        Allocation histogramAllocation = Allocation.createSized(this.rs, Element.I32(this.rs), 256);
        ScriptC_histogram_compute histogramScript = new ScriptC_histogram_compute(this.rs);
        histogramScript.bind_histogram(histogramAllocation);
        histogramScript.invoke_init_histogram();
        if (avg) {
            histogramScript.forEach_histogram_compute_avg(allocation_in);
        } else {
            histogramScript.forEach_histogram_compute(allocation_in);
        }
        return histogramAllocation;
    }

    @RequiresApi(api = 21)
    public int[] computeHistogram(Bitmap bitmap, boolean avg) {
        int[] histogram = new int[256];
        computeHistogramAllocation(Allocation.createFromBitmap(this.rs, bitmap), avg, System.currentTimeMillis()).copyTo(histogram);
        return histogram;
    }
}
