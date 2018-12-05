package com.litchiny.camera;

public class HDRProcessorException extends Exception {
    public static final int INVALID_N_IMAGES = 0;
    public static final int UNEQUAL_SIZES = 1;
    private final int code;

    HDRProcessorException(int code) {
        this.code = code;
    }

    public int getCode() {
        return this.code;
    }
}
