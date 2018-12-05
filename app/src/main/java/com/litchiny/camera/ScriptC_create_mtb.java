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

public class ScriptC_create_mtb extends ScriptC {
    private static final String __rs_resource_name = "create_mtb";
    private static final int mExportForEachIdx_create_mtb = 1;
    private static final int mExportVarIdx_median_value = 1;
    private static final int mExportVarIdx_out_bitmap = 0;
    private static final int mExportVarIdx_start_x = 2;
    private static final int mExportVarIdx_start_y = 3;
    private Element __ALLOCATION;
    private Element __I32;
    private Element __U8_4;
    private FieldPacker __rs_fp_ALLOCATION;
    private FieldPacker __rs_fp_I32;
    private int mExportVar_median_value;
    private Allocation mExportVar_out_bitmap;
    private int mExportVar_start_x;
    private int mExportVar_start_y;

    public ScriptC_create_mtb(RenderScript rs) {
        this(rs, rs.getApplicationContext().getResources(), rs.getApplicationContext().getResources().getIdentifier(__rs_resource_name, "raw", rs.getApplicationContext().getPackageName()));
    }

    public ScriptC_create_mtb(RenderScript rs, Resources resources, int id) {
        super(rs, resources, id);
        this.__ALLOCATION = Element.ALLOCATION(rs);
        this.mExportVar_median_value = 0;
        this.__I32 = Element.I32(rs);
        this.mExportVar_start_x = 0;
        this.mExportVar_start_y = 0;
        this.__U8_4 = Element.U8_4(rs);
    }

    public synchronized void set_out_bitmap(Allocation v) {
        setVar(0, v);
        this.mExportVar_out_bitmap = v;
    }

    public Allocation get_out_bitmap() {
        return this.mExportVar_out_bitmap;
    }

    public FieldID getFieldID_out_bitmap() {
        return createFieldID(0, null);
    }

    public synchronized void set_median_value(int v) {
        setVar(1, v);
        this.mExportVar_median_value = v;
    }

    public int get_median_value() {
        return this.mExportVar_median_value;
    }

    public FieldID getFieldID_median_value() {
        return createFieldID(1, null);
    }

    public synchronized void set_start_x(int v) {
        setVar(2, v);
        this.mExportVar_start_x = v;
    }

    public int get_start_x() {
        return this.mExportVar_start_x;
    }

    public FieldID getFieldID_start_x() {
        return createFieldID(2, null);
    }

    public synchronized void set_start_y(int v) {
        setVar(3, v);
        this.mExportVar_start_y = v;
    }

    public int get_start_y() {
        return this.mExportVar_start_y;
    }

    public FieldID getFieldID_start_y() {
        return createFieldID(3, null);
    }

    public KernelID getKernelID_create_mtb() {
        return createKernelID(1, 57, null, null);
    }

    public void forEach_create_mtb(Allocation ain) {
        forEach_create_mtb(ain, null);
    }

    public void forEach_create_mtb(Allocation ain, LaunchOptions sc) {
        if (ain.getType().getElement().isCompatible(this.__U8_4)) {
            forEach(1, ain, null, null, sc);
            return;
        }
        throw new RSRuntimeException("Type mismatch with U8_4!");
    }
}
