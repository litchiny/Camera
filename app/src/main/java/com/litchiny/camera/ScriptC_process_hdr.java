package com.litchiny.camera;

import android.content.res.Resources;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.FieldPacker;
import android.renderscript.RSRuntimeException;
import android.renderscript.RenderScript;
import android.renderscript.ScriptC;
import android.renderscript.Type;
import com.litchiny.camera.ui.PopupView;

public class ScriptC_process_hdr extends ScriptC {
    private static final String __rs_resource_name = "process_hdr";
    public static final float const_W = 11.2f;
    public static final int const_tonemap_algorithm_clamp_c = 0;
    public static final int const_tonemap_algorithm_filmic_c = 2;
    public static final int const_tonemap_algorithm_reinhard_c = 1;
    public static final float const_weight_scale_c = 0.0077816225f;
    private static final int mExportForEachIdx_hdr = 1;
    private static final int mExportVarIdx_W = 18;
    private static final int mExportVarIdx_bitmap0 = 0;
    private static final int mExportVarIdx_bitmap2 = 1;
    private static final int mExportVarIdx_offset_x0 = 2;
    private static final int mExportVarIdx_offset_x2 = 4;
    private static final int mExportVarIdx_offset_y0 = 3;
    private static final int mExportVarIdx_offset_y2 = 5;
    private static final int mExportVarIdx_parameter_A0 = 6;
    private static final int mExportVarIdx_parameter_A1 = 8;
    private static final int mExportVarIdx_parameter_A2 = 10;
    private static final int mExportVarIdx_parameter_B0 = 7;
    private static final int mExportVarIdx_parameter_B1 = 9;
    private static final int mExportVarIdx_parameter_B2 = 11;
    private static final int mExportVarIdx_tonemap_algorithm = 16;
    private static final int mExportVarIdx_tonemap_algorithm_clamp_c = 13;
    private static final int mExportVarIdx_tonemap_algorithm_filmic_c = 15;
    private static final int mExportVarIdx_tonemap_algorithm_reinhard_c = 14;
    private static final int mExportVarIdx_tonemap_scale = 17;
    private static final int mExportVarIdx_weight_scale_c = 12;
    private Element __ALLOCATION;
    private Element __F32;
    private Element __I32;
    private Element __U8_4;
    private FieldPacker __rs_fp_ALLOCATION;
    private FieldPacker __rs_fp_F32;
    private FieldPacker __rs_fp_I32;
    private float mExportVar_W;
    private Allocation mExportVar_bitmap0;
    private Allocation mExportVar_bitmap2;
    private int mExportVar_offset_x0;
    private int mExportVar_offset_x2;
    private int mExportVar_offset_y0;
    private int mExportVar_offset_y2;
    private float mExportVar_parameter_A0;
    private float mExportVar_parameter_A1;
    private float mExportVar_parameter_A2;
    private float mExportVar_parameter_B0;
    private float mExportVar_parameter_B1;
    private float mExportVar_parameter_B2;
    private int mExportVar_tonemap_algorithm;
    private int mExportVar_tonemap_algorithm_clamp_c;
    private int mExportVar_tonemap_algorithm_filmic_c;
    private int mExportVar_tonemap_algorithm_reinhard_c;
    private float mExportVar_tonemap_scale;
    private float mExportVar_weight_scale_c;

    public ScriptC_process_hdr(RenderScript rs) {
        this(rs, rs.getApplicationContext().getResources(), rs.getApplicationContext().getResources().getIdentifier(__rs_resource_name, "raw", rs.getApplicationContext().getPackageName()));
    }

    public ScriptC_process_hdr(RenderScript rs, Resources resources, int id) {
        super(rs, resources, id);
        this.__ALLOCATION = Element.ALLOCATION(rs);
        this.mExportVar_offset_x0 = 0;
        this.__I32 = Element.I32(rs);
        this.mExportVar_offset_y0 = 0;
        this.mExportVar_offset_x2 = 0;
        this.mExportVar_offset_y2 = 0;
        this.mExportVar_parameter_A0 = PopupView.ALPHA_BUTTON_SELECTED;
        this.__F32 = Element.F32(rs);
        this.mExportVar_parameter_B0 = 0.0f;
        this.mExportVar_parameter_A1 = PopupView.ALPHA_BUTTON_SELECTED;
        this.mExportVar_parameter_B1 = 0.0f;
        this.mExportVar_parameter_A2 = PopupView.ALPHA_BUTTON_SELECTED;
        this.mExportVar_parameter_B2 = 0.0f;
        this.mExportVar_weight_scale_c = const_weight_scale_c;
        this.mExportVar_tonemap_algorithm_clamp_c = 0;
        this.mExportVar_tonemap_algorithm_reinhard_c = 1;
        this.mExportVar_tonemap_algorithm_filmic_c = 2;
        this.mExportVar_tonemap_algorithm = 1;
        this.mExportVar_tonemap_scale = PopupView.ALPHA_BUTTON_SELECTED;
        this.mExportVar_W = const_W;
        this.__U8_4 = Element.U8_4(rs);
    }

    public synchronized void set_bitmap0(Allocation v) {
        setVar(0, v);
        this.mExportVar_bitmap0 = v;
    }

    public Allocation get_bitmap0() {
        return this.mExportVar_bitmap0;
    }

    public FieldID getFieldID_bitmap0() {
        return createFieldID(0, null);
    }

    public synchronized void set_bitmap2(Allocation v) {
        setVar(1, v);
        this.mExportVar_bitmap2 = v;
    }

    public Allocation get_bitmap2() {
        return this.mExportVar_bitmap2;
    }

    public FieldID getFieldID_bitmap2() {
        return createFieldID(1, null);
    }

    public synchronized void set_offset_x0(int v) {
        setVar(2, v);
        this.mExportVar_offset_x0 = v;
    }

    public int get_offset_x0() {
        return this.mExportVar_offset_x0;
    }

    public FieldID getFieldID_offset_x0() {
        return createFieldID(2, null);
    }

    public synchronized void set_offset_y0(int v) {
        setVar(3, v);
        this.mExportVar_offset_y0 = v;
    }

    public int get_offset_y0() {
        return this.mExportVar_offset_y0;
    }

    public FieldID getFieldID_offset_y0() {
        return createFieldID(3, null);
    }

    public synchronized void set_offset_x2(int v) {
        setVar(4, v);
        this.mExportVar_offset_x2 = v;
    }

    public int get_offset_x2() {
        return this.mExportVar_offset_x2;
    }

    public FieldID getFieldID_offset_x2() {
        return createFieldID(4, null);
    }

    public synchronized void set_offset_y2(int v) {
        setVar(5, v);
        this.mExportVar_offset_y2 = v;
    }

    public int get_offset_y2() {
        return this.mExportVar_offset_y2;
    }

    public FieldID getFieldID_offset_y2() {
        return createFieldID(5, null);
    }

    public synchronized void set_parameter_A0(float v) {
        setVar(6, v);
        this.mExportVar_parameter_A0 = v;
    }

    public float get_parameter_A0() {
        return this.mExportVar_parameter_A0;
    }

    public FieldID getFieldID_parameter_A0() {
        return createFieldID(6, null);
    }

    public synchronized void set_parameter_B0(float v) {
        setVar(7, v);
        this.mExportVar_parameter_B0 = v;
    }

    public float get_parameter_B0() {
        return this.mExportVar_parameter_B0;
    }

    public FieldID getFieldID_parameter_B0() {
        return createFieldID(7, null);
    }

    public synchronized void set_parameter_A1(float v) {
        setVar(8, v);
        this.mExportVar_parameter_A1 = v;
    }

    public float get_parameter_A1() {
        return this.mExportVar_parameter_A1;
    }

    public FieldID getFieldID_parameter_A1() {
        return createFieldID(8, null);
    }

    public synchronized void set_parameter_B1(float v) {
        setVar(9, v);
        this.mExportVar_parameter_B1 = v;
    }

    public float get_parameter_B1() {
        return this.mExportVar_parameter_B1;
    }

    public FieldID getFieldID_parameter_B1() {
        return createFieldID(9, null);
    }

    public synchronized void set_parameter_A2(float v) {
        setVar(10, v);
        this.mExportVar_parameter_A2 = v;
    }

    public float get_parameter_A2() {
        return this.mExportVar_parameter_A2;
    }

    public FieldID getFieldID_parameter_A2() {
        return createFieldID(10, null);
    }

    public synchronized void set_parameter_B2(float v) {
        setVar(11, v);
        this.mExportVar_parameter_B2 = v;
    }

    public float get_parameter_B2() {
        return this.mExportVar_parameter_B2;
    }

    public FieldID getFieldID_parameter_B2() {
        return createFieldID(11, null);
    }

    public float get_weight_scale_c() {
        return this.mExportVar_weight_scale_c;
    }

    public FieldID getFieldID_weight_scale_c() {
        return createFieldID(12, null);
    }

    public int get_tonemap_algorithm_clamp_c() {
        return this.mExportVar_tonemap_algorithm_clamp_c;
    }

    public FieldID getFieldID_tonemap_algorithm_clamp_c() {
        return createFieldID(13, null);
    }

    public int get_tonemap_algorithm_reinhard_c() {
        return this.mExportVar_tonemap_algorithm_reinhard_c;
    }

    public FieldID getFieldID_tonemap_algorithm_reinhard_c() {
        return createFieldID(14, null);
    }

    public int get_tonemap_algorithm_filmic_c() {
        return this.mExportVar_tonemap_algorithm_filmic_c;
    }

    public FieldID getFieldID_tonemap_algorithm_filmic_c() {
        return createFieldID(15, null);
    }

    public synchronized void set_tonemap_algorithm(int v) {
        setVar(16, v);
        this.mExportVar_tonemap_algorithm = v;
    }

    public int get_tonemap_algorithm() {
        return this.mExportVar_tonemap_algorithm;
    }

    public FieldID getFieldID_tonemap_algorithm() {
        return createFieldID(16, null);
    }

    public synchronized void set_tonemap_scale(float v) {
        setVar(17, v);
        this.mExportVar_tonemap_scale = v;
    }

    public float get_tonemap_scale() {
        return this.mExportVar_tonemap_scale;
    }

    public FieldID getFieldID_tonemap_scale() {
        return createFieldID(17, null);
    }

    public float get_W() {
        return this.mExportVar_W;
    }

    public FieldID getFieldID_W() {
        return createFieldID(18, null);
    }

    public KernelID getKernelID_hdr() {
        return createKernelID(1, 59, null, null);
    }

    public void forEach_hdr(Allocation ain, Allocation aout) {
        forEach_hdr(ain, aout, null);
    }

    public void forEach_hdr(Allocation ain, Allocation aout, LaunchOptions sc) {
        if (!ain.getType().getElement().isCompatible(this.__U8_4)) {
            throw new RSRuntimeException("Type mismatch with U8_4!");
        } else if (aout.getType().getElement().isCompatible(this.__U8_4)) {
            Type t0 = ain.getType();
            Type t1 = aout.getType();
            if (t0.getCount() == t1.getCount() && t0.getX() == t1.getX() && t0.getY() == t1.getY() && t0.getZ() == t1.getZ() && t0.hasFaces() == t1.hasFaces() && t0.hasMipmaps() == t1.hasMipmaps()) {
                forEach(1, ain, aout, null, sc);
                return;
            }
            throw new RSRuntimeException("Dimension mismatch between parameters ain and aout!");
        } else {
            throw new RSRuntimeException("Type mismatch with U8_4!");
        }
    }
}
