package com.litchiny.camera;

import android.content.res.Resources;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RSRuntimeException;
import android.renderscript.RenderScript;
import android.renderscript.Script.KernelID;
import android.renderscript.Script.LaunchOptions;
import android.renderscript.ScriptC;

public class ScriptC_histogram_compute extends ScriptC {
    private static final String __rs_resource_name = "histogram_compute";
    private static final int mExportForEachIdx_histogram_compute = 1;
    private static final int mExportForEachIdx_histogram_compute_avg = 2;
    private static final int mExportFuncIdx_init_histogram = 0;
    private static final int mExportVarIdx_histogram = 0;
    private Element __U8_4;
    private Allocation mExportVar_histogram;

    public ScriptC_histogram_compute(RenderScript rs) {
        this(rs, rs.getApplicationContext().getResources(), rs.getApplicationContext().getResources().getIdentifier(__rs_resource_name, "raw", rs.getApplicationContext().getPackageName()));
    }

    public ScriptC_histogram_compute(RenderScript rs, Resources resources, int id) {
        super(rs, resources, id);
        this.__U8_4 = Element.U8_4(rs);
    }

    public void bind_histogram(Allocation v) {
        this.mExportVar_histogram = v;
        if (v == null) {
            bindAllocation(null, 0);
        } else {
            bindAllocation(v, 0);
        }
    }

    public Allocation get_histogram() {
        return this.mExportVar_histogram;
    }

    public KernelID getKernelID_histogram_compute() {
        return createKernelID(1, 57, null, null);
    }

    public void forEach_histogram_compute(Allocation ain) {
        forEach_histogram_compute(ain, null);
    }

    public void forEach_histogram_compute(Allocation ain, LaunchOptions sc) {
        if (ain.getType().getElement().isCompatible(this.__U8_4)) {
            forEach(1, ain, null, null, sc);
            return;
        }
        throw new RSRuntimeException("Type mismatch with U8_4!");
    }

    public KernelID getKernelID_histogram_compute_avg() {
        return createKernelID(2, 57, null, null);
    }

    public void forEach_histogram_compute_avg(Allocation ain) {
        forEach_histogram_compute_avg(ain, null);
    }

    public void forEach_histogram_compute_avg(Allocation ain, LaunchOptions sc) {
        if (ain.getType().getElement().isCompatible(this.__U8_4)) {
            forEach(2, ain, null, null, sc);
            return;
        }
        throw new RSRuntimeException("Type mismatch with U8_4!");
    }

    public void invoke_init_histogram() {
        invoke(0);
    }
}
