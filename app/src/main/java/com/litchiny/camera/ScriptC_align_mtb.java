package com.litchiny.camera;

import android.content.res.Resources;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.FieldPacker;
import android.renderscript.RSRuntimeException;
import android.renderscript.RenderScript;
import android.renderscript.Script.FieldID;
import android.renderscript.Script.KernelID;
import android.renderscript.Script.LaunchOptions;
import android.renderscript.ScriptC;

public class ScriptC_align_mtb extends ScriptC {
    private static final String __rs_resource_name = "align_mtb";
    private static final int mExportForEachIdx_align_mtb = 1;
    private static final int mExportFuncIdx_init_errors = 0;
    private static final int mExportVarIdx_bitmap0 = 0;
    private static final int mExportVarIdx_bitmap1 = 1;
    private static final int mExportVarIdx_errors = 5;
    private static final int mExportVarIdx_off_x = 3;
    private static final int mExportVarIdx_off_y = 4;
    private static final int mExportVarIdx_step_size = 2;
    private Element __ALLOCATION;
    private Element __I32;
    private Element __U8;
    private FieldPacker __rs_fp_ALLOCATION;
    private FieldPacker __rs_fp_I32;
    private Allocation mExportVar_bitmap0;
    private Allocation mExportVar_bitmap1;
    private Allocation mExportVar_errors;
    private int mExportVar_off_x;
    private int mExportVar_off_y;
    private int mExportVar_step_size;

    public ScriptC_align_mtb(RenderScript rs) {
        this(rs, rs.getApplicationContext().getResources(), rs.getApplicationContext().getResources().getIdentifier(__rs_resource_name, "raw", rs.getApplicationContext().getPackageName()));
    }

    public ScriptC_align_mtb(RenderScript rs, Resources resources, int id) {
        super(rs, resources, id);
        this.__ALLOCATION = Element.ALLOCATION(rs);
        this.mExportVar_step_size = 1;
        this.__I32 = Element.I32(rs);
        this.mExportVar_off_x = 0;
        this.mExportVar_off_y = 0;
        this.__U8 = Element.U8(rs);
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

    public synchronized void set_bitmap1(Allocation v) {
        setVar(1, v);
        this.mExportVar_bitmap1 = v;
    }

    public Allocation get_bitmap1() {
        return this.mExportVar_bitmap1;
    }

    public FieldID getFieldID_bitmap1() {
        return createFieldID(1, null);
    }

    public synchronized void set_step_size(int v) {
        setVar(2, v);
        this.mExportVar_step_size = v;
    }

    public int get_step_size() {
        return this.mExportVar_step_size;
    }

    public FieldID getFieldID_step_size() {
        return createFieldID(2, null);
    }

    public synchronized void set_off_x(int v) {
        setVar(3, v);
        this.mExportVar_off_x = v;
    }

    public int get_off_x() {
        return this.mExportVar_off_x;
    }

    public FieldID getFieldID_off_x() {
        return createFieldID(3, null);
    }

    public synchronized void set_off_y(int v) {
        setVar(4, v);
        this.mExportVar_off_y = v;
    }

    public int get_off_y() {
        return this.mExportVar_off_y;
    }

    public FieldID getFieldID_off_y() {
        return createFieldID(4, null);
    }

    public void bind_errors(Allocation v) {
        this.mExportVar_errors = v;
        if (v == null) {
            bindAllocation(null, 5);
        } else {
            bindAllocation(v, 5);
        }
    }

    public Allocation get_errors() {
        return this.mExportVar_errors;
    }

    public KernelID getKernelID_align_mtb() {
        return createKernelID(1, 57, null, null);
    }

    public void forEach_align_mtb(Allocation ain) {
        forEach_align_mtb(ain, null);
    }

    public void forEach_align_mtb(Allocation ain, LaunchOptions sc) {
        if (ain.getType().getElement().isCompatible(this.__U8)) {
            forEach(1, ain, null, null, sc);
            return;
        }
        throw new RSRuntimeException("Type mismatch with U8!");
    }

    public void invoke_init_errors() {
        invoke(0);
    }
}
