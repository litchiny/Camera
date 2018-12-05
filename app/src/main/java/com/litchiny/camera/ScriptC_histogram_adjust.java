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
import android.renderscript.Type;

public class ScriptC_histogram_adjust extends ScriptC {
    private static final String __rs_resource_name = "histogram_adjust";
    private static final int mExportForEachIdx_histogram_adjust = 1;
    private static final int mExportVarIdx_c_histogram = 0;
    private static final int mExportVarIdx_hdr_alpha = 1;
    private static final int mExportVarIdx_height = 4;
    private static final int mExportVarIdx_n_tiles = 2;
    private static final int mExportVarIdx_width = 3;
    private Element __ALLOCATION;
    private Element __F32;
    private Element __I32;
    private Element __U8_4;
    private FieldPacker __rs_fp_ALLOCATION;
    private FieldPacker __rs_fp_F32;
    private FieldPacker __rs_fp_I32;
    private Allocation mExportVar_c_histogram;
    private float mExportVar_hdr_alpha;
    private int mExportVar_height;
    private int mExportVar_n_tiles;
    private int mExportVar_width;

    public ScriptC_histogram_adjust(RenderScript rs) {
        this(rs, rs.getApplicationContext().getResources(), rs.getApplicationContext().getResources().getIdentifier(__rs_resource_name, "raw", rs.getApplicationContext().getPackageName()));
    }

    public ScriptC_histogram_adjust(RenderScript rs, Resources resources, int id) {
        super(rs, resources, id);
        this.__ALLOCATION = Element.ALLOCATION(rs);
        this.mExportVar_hdr_alpha = 0.5f;
        this.__F32 = Element.F32(rs);
        this.mExportVar_n_tiles = 0;
        this.__I32 = Element.I32(rs);
        this.mExportVar_width = 0;
        this.mExportVar_height = 0;
        this.__U8_4 = Element.U8_4(rs);
    }

    public synchronized void set_c_histogram(Allocation v) {
        setVar(0, v);
        this.mExportVar_c_histogram = v;
    }

    public Allocation get_c_histogram() {
        return this.mExportVar_c_histogram;
    }

    public FieldID getFieldID_c_histogram() {
        return createFieldID(0, null);
    }

    public synchronized void set_hdr_alpha(float v) {
        setVar(1, v);
        this.mExportVar_hdr_alpha = v;
    }

    public float get_hdr_alpha() {
        return this.mExportVar_hdr_alpha;
    }

    public FieldID getFieldID_hdr_alpha() {
        return createFieldID(1, null);
    }

    public synchronized void set_n_tiles(int v) {
        setVar(2, v);
        this.mExportVar_n_tiles = v;
    }

    public int get_n_tiles() {
        return this.mExportVar_n_tiles;
    }

    public FieldID getFieldID_n_tiles() {
        return createFieldID(2, null);
    }

    public synchronized void set_width(int v) {
        setVar(3, v);
        this.mExportVar_width = v;
    }

    public int get_width() {
        return this.mExportVar_width;
    }

    public FieldID getFieldID_width() {
        return createFieldID(3, null);
    }

    public synchronized void set_height(int v) {
        setVar(4, v);
        this.mExportVar_height = v;
    }

    public int get_height() {
        return this.mExportVar_height;
    }

    public FieldID getFieldID_height() {
        return createFieldID(4, null);
    }

    public KernelID getKernelID_histogram_adjust() {
        return createKernelID(1, 59, null, null);
    }

    public void forEach_histogram_adjust(Allocation ain, Allocation aout) {
        forEach_histogram_adjust(ain, aout, null);
    }

    public void forEach_histogram_adjust(Allocation ain, Allocation aout, LaunchOptions sc) {
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
