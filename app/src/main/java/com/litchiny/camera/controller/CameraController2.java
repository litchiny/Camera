package com.litchiny.camera.controller;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCaptureSession.CaptureCallback;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraDevice.StateCallback;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureRequest.Builder;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.DngCreator;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.RggbChannelVector;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.location.Location;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.media.MediaActionSound;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.view.MotionEventCompat;
import android.util.Log;
import android.util.Range;
import android.util.Rational;
import android.util.SizeF;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@TargetApi(21)
public class CameraController2 extends CameraController {
    private static final int STATE_NORMAL = 0;
    private static final int STATE_WAITING_AUTOFOCUS = 1;
    private static final int STATE_WAITING_FAKE_PRECAPTURE_DONE = 5;
    private static final int STATE_WAITING_FAKE_PRECAPTURE_START = 4;
    private static final int STATE_WAITING_PRECAPTURE_DONE = 3;
    private static final int STATE_WAITING_PRECAPTURE_START = 2;
    private static final String TAG = "CameraController2";
    private static final boolean do_af_trigger_for_continuous = true;
    private static final int max_expo_bracketing_n_images = 5;
    private static final int max_white_balance_temperature_c = 15000;
    private static final int min_white_balance_temperature_c = 1000;
    private static final long precapture_done_timeout_c = 3000;
    private static final long precapture_start_timeout_c = 2000;
    private AutoFocusCallback autofocus_cb;
    private List<CaptureRequest> burst_capture_requests;
    private long burst_start_ms = 0;
    private CameraDevice camera;
    private String cameraIdS;
    private final ErrorCallback camera_error_cb;
    private final CameraSettings camera_settings = new CameraSettings();
    private CameraCaptureSession captureSession;
    private boolean capture_follows_autofocus_hint;
    private Integer capture_result_ae;
    private long capture_result_exposure_time;
    private long capture_result_frame_duration;
    private boolean capture_result_has_exposure_time;
    private boolean capture_result_has_frame_duration;
    private boolean capture_result_has_iso;
    private boolean capture_result_has_white_balance_rggb;
    private boolean capture_result_is_ae_scanning;
    private int capture_result_iso;
    private RggbChannelVector capture_result_white_balance_rggb;
    private CameraCharacteristics characteristics;
    private final Context context;
    private ContinuousFocusMoveCallback continuous_focus_move_callback;
    private final Object create_capture_session_lock = new Object();
    private int current_zoom_value;
    private int expo_bracketing_n_images = 3;
    private double expo_bracketing_stops = 2.0d;
    private FaceDetectionListener face_detection_listener;
    private boolean fake_precapture_torch_focus_performed;
    private boolean fake_precapture_torch_performed;
    private CaptureRequest fake_precapture_turn_on_torch_id = null;
    private boolean fake_precapture_use_flash;
    private long fake_precapture_use_flash_time_ms = -1;
    private Handler handler;
    private ImageReader imageReader;
    private ImageReader imageReaderRaw;
    private final Object image_reader_lock = new Object();
    private boolean is_flash_required;
    private PictureCallback jpeg_cb;
    private final MediaActionSound media_action_sound = new MediaActionSound();
    private int n_burst;
    private OnRawImageAvailableListener onRawImageAvailableListener;
    //    private android.media.ImageReader.OnImageAvailableListener onRawImageAvailableListener;
    private final Object open_camera_lock = new Object();
    private boolean optimise_ae_for_dro = false;
    private final List<byte[]> pending_burst_images = new ArrayList();
    private DngCreator pending_dngCreator;
    private Image pending_image;
    private int picture_height;
    private int picture_width;
    private long precapture_state_change_time_ms = -1;
    private Builder previewBuilder;
    private final CaptureCallback previewCaptureCallback = new CaptureCallback() {
        private int last_af_state = -1;
        private long last_process_frame_number = 0;

        public void onCaptureBufferLost(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull Surface target, long frameNumber) {
            super.onCaptureBufferLost(session, request, target, frameNumber);
        }

        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
        }

        public void onCaptureSequenceAborted(@NonNull CameraCaptureSession session, int sequenceId) {
            super.onCaptureSequenceAborted(session, sequenceId);
        }

        public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session, int sequenceId, long frameNumber) {
            super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
        }

        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            if (request.getTag() == RequestTag.CAPTURE) {
                super.onCaptureStarted(session, request, timestamp, frameNumber);
            } else {
                super.onCaptureStarted(session, request, timestamp, frameNumber);
            }
        }

        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            super.onCaptureProgressed(session, request, partialResult);
        }

        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            if (request.getTag() == RequestTag.CAPTURE) {
                process(request, result);
                processCompleted(request, result);
                super.onCaptureCompleted(session, request, result);
            } else {
                process(request, result);
                processCompleted(request, result);
                super.onCaptureCompleted(session, request, result);
            }
        }

        private void process(CaptureRequest request, CaptureResult result) {
            if (result.getFrameNumber() >= this.last_process_frame_number) {
                boolean focus_success;
                CameraController2 cameraController2;
                this.last_process_frame_number = result.getFrameNumber();
                Integer af_state = (Integer) result.get(CaptureResult.CONTROL_AF_STATE);
                Integer ae_state = (Integer) result.get(CaptureResult.CONTROL_AE_STATE);
                Integer flash_mode = (Integer) result.get(CaptureResult.FLASH_MODE);
                if (!(CameraController2.this.use_fake_precapture_mode && ((CameraController2.this.fake_precapture_torch_focus_performed || CameraController2.this.fake_precapture_torch_performed) && flash_mode != null && flash_mode.intValue() == 2))) {
                    if (ae_state == null) {
                        CameraController2.this.capture_result_ae = null;
                        CameraController2.this.is_flash_required = false;
                    } else if (!ae_state.equals(CameraController2.this.capture_result_ae)) {
                        CameraController2.this.capture_result_ae = ae_state;
                        if (CameraController2.this.capture_result_ae.intValue() == 4 && !CameraController2.this.is_flash_required) {
                            CameraController2.this.is_flash_required = CameraController2.do_af_trigger_for_continuous;
                        } else if (CameraController2.this.capture_result_ae.intValue() == 2 && CameraController2.this.is_flash_required) {
                            CameraController2.this.is_flash_required = false;
                        }
                    }
                }
                if (af_state == null || af_state.intValue() != 1) {
                    CameraController2.this.ready_for_capture = CameraController2.do_af_trigger_for_continuous;
                    if (CameraController2.this.autofocus_cb != null && CameraController2.this.use_fake_precapture_mode && CameraController2.this.focusIsContinuous()) {
                        Integer focus_mode = (Integer) CameraController2.this.previewBuilder.get(CaptureRequest.CONTROL_AF_MODE);
                        if (focus_mode != null && focus_mode.intValue() == 4) {
                            if (af_state == null || !(af_state.intValue() == 4 || af_state.intValue() == 2)) {
                                focus_success = false;
                            } else {
                                focus_success = CameraController2.do_af_trigger_for_continuous;
                            }
                            if (af_state == null) {
                                cameraController2 = CameraController2.this;
                                cameraController2.test_af_state_null_focus++;
                            }
                            CameraController2.this.autofocus_cb.onAutoFocus(focus_success);
                            CameraController2.this.autofocus_cb = null;
                            CameraController2.this.capture_follows_autofocus_hint = false;
                        }
                    }
                } else {
                    CameraController2.this.ready_for_capture = false;
                }
                if (ae_state == null || ae_state.intValue() != 1) {
                    CameraController2.this.capture_result_is_ae_scanning = false;
                } else {
                    CameraController2.this.capture_result_is_ae_scanning = CameraController2.do_af_trigger_for_continuous;
                }
                if (CameraController2.this.fake_precapture_turn_on_torch_id != null && CameraController2.this.fake_precapture_turn_on_torch_id == request) {
                    CameraController2.this.fake_precapture_turn_on_torch_id = null;
                }
                if (CameraController2.this.state != 0) {
                    if (CameraController2.this.state == 1) {
                        if (af_state == null) {
                            cameraController2 = CameraController2.this;
                            cameraController2.test_af_state_null_focus++;
                            CameraController2.this.state = 0;
                            CameraController2.this.precapture_state_change_time_ms = -1;
                            if (CameraController2.this.autofocus_cb != null) {
                                CameraController2.this.autofocus_cb.onAutoFocus(false);
                                CameraController2.this.autofocus_cb = null;
                            }
                            CameraController2.this.capture_follows_autofocus_hint = false;
                        } else if (af_state.intValue() != this.last_af_state && (af_state.intValue() == 4 || af_state.intValue() == 5)) {
                            focus_success = (af_state.intValue() == 4 || af_state.intValue() == 2) ? CameraController2.do_af_trigger_for_continuous : false;
                            CameraController2.this.state = 0;
                            CameraController2.this.precapture_state_change_time_ms = -1;
                            if (CameraController2.this.use_fake_precapture_mode && CameraController2.this.fake_precapture_torch_focus_performed) {
                                CameraController2.this.fake_precapture_torch_focus_performed = false;
                                if (!CameraController2.this.capture_follows_autofocus_hint) {
                                    String saved_flash_value = CameraController2.this.camera_settings.flash_value;
                                    CameraController2.this.camera_settings.flash_value = "flash_off";
                                    CameraController2.this.camera_settings.setAEMode(CameraController2.this.previewBuilder, false);
                                    try {
                                        CameraController2.this.capture();
                                    } catch (CameraAccessException e) {
                                        e.printStackTrace();
                                    }
                                    CameraController2.this.camera_settings.flash_value = saved_flash_value;
                                    CameraController2.this.camera_settings.setAEMode(CameraController2.this.previewBuilder, false);
                                    try {
                                        CameraController2.this.setRepeatingRequest();
                                    } catch (CameraAccessException e2) {
                                        e2.printStackTrace();
                                    }
                                }
                            }
                            if (CameraController2.this.autofocus_cb != null) {
                                CameraController2.this.autofocus_cb.onAutoFocus(focus_success);
                                CameraController2.this.autofocus_cb = null;
                            }
                            CameraController2.this.capture_follows_autofocus_hint = false;
                        }
                    } else if (CameraController2.this.state == 2) {
                        if (ae_state == null || ae_state.intValue() == 5) {
                            CameraController2.this.state = 3;
                            CameraController2.this.precapture_state_change_time_ms = System.currentTimeMillis();
                        } else if (CameraController2.this.precapture_state_change_time_ms != -1 && System.currentTimeMillis() - CameraController2.this.precapture_state_change_time_ms > CameraController2.precapture_start_timeout_c) {
                            Log.e(CameraController2.TAG, "precapture start timeout");
                            cameraController2 = CameraController2.this;
                            cameraController2.count_precapture_timeout++;
                            CameraController2.this.state = 3;
                            CameraController2.this.precapture_state_change_time_ms = System.currentTimeMillis();
                        }
                    } else if (CameraController2.this.state == 3) {
                        if (ae_state == null || ae_state.intValue() != 5) {
                            CameraController2.this.state = 0;
                            CameraController2.this.precapture_state_change_time_ms = -1;
                            CameraController2.this.takePictureAfterPrecapture();
                        } else if (CameraController2.this.precapture_state_change_time_ms != -1 && System.currentTimeMillis() - CameraController2.this.precapture_state_change_time_ms > CameraController2.precapture_done_timeout_c) {
                            Log.e(CameraController2.TAG, "precapture done timeout");
                            cameraController2 = CameraController2.this;
                            cameraController2.count_precapture_timeout++;
                            CameraController2.this.state = 0;
                            CameraController2.this.precapture_state_change_time_ms = -1;
                            CameraController2.this.takePictureAfterPrecapture();
                        }
                    } else if (CameraController2.this.state == 4) {
                        if (CameraController2.this.fake_precapture_turn_on_torch_id != null) {
                        }
                        if (CameraController2.this.fake_precapture_turn_on_torch_id == null && (ae_state == null || ae_state.intValue() == 1)) {
                            CameraController2.this.state = 5;
                            CameraController2.this.precapture_state_change_time_ms = System.currentTimeMillis();
                        } else if (CameraController2.this.precapture_state_change_time_ms != -1 && System.currentTimeMillis() - CameraController2.this.precapture_state_change_time_ms > CameraController2.precapture_start_timeout_c) {
                            Log.e(CameraController2.TAG, "fake precapture start timeout");
                            cameraController2 = CameraController2.this;
                            cameraController2.count_precapture_timeout++;
                            CameraController2.this.state = 5;
                            CameraController2.this.precapture_state_change_time_ms = System.currentTimeMillis();
                            CameraController2.this.fake_precapture_turn_on_torch_id = null;
                        }
                    } else if (CameraController2.this.state == 5) {
                        if (CameraController2.this.ready_for_capture && (ae_state == null || ae_state.intValue() != 1)) {
                            CameraController2.this.state = 0;
                            CameraController2.this.precapture_state_change_time_ms = -1;
                            CameraController2.this.takePictureAfterPrecapture();
                        } else if (CameraController2.this.precapture_state_change_time_ms != -1 && System.currentTimeMillis() - CameraController2.this.precapture_state_change_time_ms > CameraController2.precapture_done_timeout_c) {
                            Log.e(CameraController2.TAG, "fake precapture done timeout");
                            cameraController2 = CameraController2.this;
                            cameraController2.count_precapture_timeout++;
                            CameraController2.this.state = 0;
                            CameraController2.this.precapture_state_change_time_ms = -1;
                            CameraController2.this.takePictureAfterPrecapture();
                        }
                    }
                }
                if (af_state == null || af_state.intValue() != 1 || af_state.intValue() == this.last_af_state) {
                    if (!(af_state == null || this.last_af_state != 1 || af_state.intValue() == this.last_af_state || CameraController2.this.continuous_focus_move_callback == null)) {
                        CameraController2.this.continuous_focus_move_callback.onContinuousFocusMove(false);
                    }
                } else if (CameraController2.this.continuous_focus_move_callback != null) {
                    CameraController2.this.continuous_focus_move_callback.onContinuousFocusMove(CameraController2.do_af_trigger_for_continuous);
                }
                if (af_state != null && af_state.intValue() != this.last_af_state) {
                    this.last_af_state = af_state.intValue();
                }
            }
        }

        private void processCompleted(CaptureRequest request, CaptureResult result) {
            if (result.get(CaptureResult.SENSOR_SENSITIVITY) != null) {
                CameraController2.this.capture_result_has_iso = CameraController2.do_af_trigger_for_continuous;
                CameraController2.this.capture_result_iso = ((Integer) result.get(CaptureResult.SENSOR_SENSITIVITY)).intValue();
                if (CameraController2.this.camera_settings.has_iso && Math.abs(CameraController2.this.camera_settings.iso - CameraController2.this.capture_result_iso) > 10) {
                    try {
                        CameraController2.this.setRepeatingRequest();
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                CameraController2.this.capture_result_has_iso = false;
            }
            if (result.get(CaptureResult.SENSOR_EXPOSURE_TIME) != null) {
                CameraController2.this.capture_result_has_exposure_time = CameraController2.do_af_trigger_for_continuous;
                CameraController2.this.capture_result_exposure_time = ((Long) result.get(CaptureResult.SENSOR_EXPOSURE_TIME)).longValue();
            } else {
                CameraController2.this.capture_result_has_exposure_time = false;
            }
            if (result.get(CaptureResult.SENSOR_FRAME_DURATION) != null) {
                CameraController2.this.capture_result_has_frame_duration = CameraController2.do_af_trigger_for_continuous;
                CameraController2.this.capture_result_frame_duration = ((Long) result.get(CaptureResult.SENSOR_FRAME_DURATION)).longValue();
            } else {
                CameraController2.this.capture_result_has_frame_duration = false;
            }
            RggbChannelVector vector = (RggbChannelVector) result.get(CaptureResult.COLOR_CORRECTION_GAINS);
            if (vector != null) {
                CameraController2.this.capture_result_has_white_balance_rggb = CameraController2.do_af_trigger_for_continuous;
                CameraController2.this.capture_result_white_balance_rggb = vector;
            }
            if (!(CameraController2.this.face_detection_listener == null || CameraController2.this.previewBuilder == null || CameraController2.this.previewBuilder.get(CaptureRequest.STATISTICS_FACE_DETECT_MODE) == null || ((Integer) CameraController2.this.previewBuilder.get(CaptureRequest.STATISTICS_FACE_DETECT_MODE)).intValue() == 0)) {
                Rect sensor_rect = CameraController2.this.getViewableRect();
                android.hardware.camera2.params.Face[] camera_faces = result.get(CaptureResult.STATISTICS_FACES);
                if (camera_faces != null) {
                    Face[] faces = new Face[camera_faces.length];
                    for (int i = 0; i < camera_faces.length; i++) {
                        faces[i] = CameraController2.this.convertFromCameraFace(sensor_rect, camera_faces[i]);
                    }
                    CameraController2.this.face_detection_listener.onFaceDetection(faces);
                }
            }
            if (CameraController2.this.push_repeating_request_when_torch_off && CameraController2.this.push_repeating_request_when_torch_off_id == request) {
                Integer flash_state = (Integer) result.get(CaptureResult.FLASH_STATE);
                if (flash_state != null && flash_state.intValue() == 2) {
                    CameraController2.this.push_repeating_request_when_torch_off = false;
                    CameraController2.this.push_repeating_request_when_torch_off_id = null;
                    try {
                        CameraController2.this.setRepeatingRequest();
                    } catch (CameraAccessException e2) {
                        e2.printStackTrace();
                    }
                }
            }
            if (request.getTag() == RequestTag.CAPTURE) {
                CameraController2 cameraController2 = CameraController2.this;
                cameraController2.test_capture_results++;
                if (CameraController2.this.onRawImageAvailableListener != null) {
                    if (CameraController2.this.test_wait_capture_result) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e3) {
                            e3.printStackTrace();
                        }
                    }
                    CameraController2.this.onRawImageAvailableListener.setCaptureResult(result);
                }
                CameraController2.this.previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, Integer.valueOf(2));
                String saved_flash_value = CameraController2.this.camera_settings.flash_value;
                if (CameraController2.this.use_fake_precapture_mode && CameraController2.this.fake_precapture_torch_performed) {
                    CameraController2.this.camera_settings.flash_value = "flash_off";
                }
                CameraController2.this.camera_settings.setAEMode(CameraController2.this.previewBuilder, false);
                try {
                    CameraController2.this.capture();
                } catch (CameraAccessException e22) {
                    e22.printStackTrace();
                }
                if (CameraController2.this.use_fake_precapture_mode && CameraController2.this.fake_precapture_torch_performed) {
                    CameraController2.this.camera_settings.flash_value = saved_flash_value;
                    CameraController2.this.camera_settings.setAEMode(CameraController2.this.previewBuilder, false);
                }
                CameraController2.this.previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, Integer.valueOf(0));
                try {
                    CameraController2.this.setRepeatingRequest();
                } catch (CameraAccessException e222) {
                    e222.printStackTrace();
                    CameraController2.this.preview_error_cb.onError();
                }
                CameraController2.this.fake_precapture_torch_performed = false;
            }
        }
    };
    private final ErrorCallback preview_error_cb;
    private int preview_height;
    private int preview_width;
    private boolean push_repeating_request_when_torch_off = false;
    private CaptureRequest push_repeating_request_when_torch_off_id = null;
    private PictureCallback raw_cb;
    private android.util.Size raw_size;
    private boolean ready_for_capture;
    private boolean sounds_enabled = do_af_trigger_for_continuous;
    private int state = 0;
    private boolean supports_face_detect_mode_full;
    private boolean supports_face_detect_mode_simple;
    private Surface surface_texture;
    private ErrorCallback take_picture_error_cb;
    private SurfaceTexture texture;
    private HandlerThread thread;
    private boolean use_expo_fast_burst = do_af_trigger_for_continuous;
    private boolean use_fake_precapture;
    private boolean use_fake_precapture_mode;
    private boolean want_expo_bracketing;
    private boolean want_raw;
    private List<Integer> zoom_ratios;

    private class CameraSettings {
        private int ae_exposure_compensation;
        private boolean ae_lock;
        private MeteringRectangle[] ae_regions;
        private int af_mode;
        private MeteringRectangle[] af_regions;
        private int color_effect;
        private long exposure_time;
        private int face_detect_mode;
        private String flash_value;
        private float focus_distance;
        private float focus_distance_manual;
        private boolean has_ae_exposure_compensation;
        private boolean has_af_mode;
        private boolean has_face_detect_mode;
        private boolean has_iso;
        private int iso;
        private byte jpeg_quality;
        private Location location;
        private int rotation;
        private Rect scalar_crop_region;
        private int scene_mode;
        private boolean video_stabilization;
        private int white_balance;
        private int white_balance_temperature;

        private CameraSettings() {
            this.jpeg_quality = (byte) 90;
            this.scene_mode = 0;
            this.color_effect = 0;
            this.white_balance = 1;
            this.white_balance_temperature = 5000;
            this.flash_value = "flash_off";
            this.exposure_time = CameraController.EXPOSURE_TIME_DEFAULT;
            this.af_mode = 1;
            this.face_detect_mode = 0;
        }

        private int getExifOrientation() {
            int exif_orientation;
            switch ((this.rotation + 360) % 360) {
                case 0:
                    return 1;
                case 90:
                    if (CameraController2.this.isFrontFacing()) {
                        exif_orientation = 8;
                    } else {
                        exif_orientation = 6;
                    }
                    return exif_orientation;
                case 180:
                    return 3;
                case 270:
                    if (CameraController2.this.isFrontFacing()) {
                        exif_orientation = 6;
                    } else {
                        exif_orientation = 8;
                    }
                    return exif_orientation;
                default:
                    return 1;
            }
        }

        private void setupBuilder(Builder builder, boolean is_still) {
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, Integer.valueOf(0));
            setSceneMode(builder);
            setColorEffect(builder);
            setWhiteBalance(builder);
            setAEMode(builder, is_still);
            setCropRegion(builder);
            setExposureCompensation(builder);
            setFocusMode(builder);
            setFocusDistance(builder);
            setAutoExposureLock(builder);
            setAFRegions(builder);
            setAERegions(builder);
            setFaceDetectMode(builder);
            setRawMode(builder);
            setVideoStabilization(builder);
            if (is_still) {
                if (this.location != null) {
                    builder.set(CaptureRequest.JPEG_GPS_LOCATION, this.location);
                }
                builder.set(CaptureRequest.JPEG_ORIENTATION, Integer.valueOf(this.rotation));
                builder.set(CaptureRequest.JPEG_QUALITY, Byte.valueOf(this.jpeg_quality));
            }
        }

        private boolean setSceneMode(Builder builder) {
            if (builder.get(CaptureRequest.CONTROL_SCENE_MODE) != null && ((Integer) builder.get(CaptureRequest.CONTROL_SCENE_MODE)).intValue() == this.scene_mode) {
                return false;
            }
            if (this.scene_mode == 0) {
                builder.set(CaptureRequest.CONTROL_MODE, Integer.valueOf(1));
            } else {
                builder.set(CaptureRequest.CONTROL_MODE, Integer.valueOf(2));
            }
            builder.set(CaptureRequest.CONTROL_SCENE_MODE, Integer.valueOf(this.scene_mode));
            return CameraController2.do_af_trigger_for_continuous;
        }

        private boolean setColorEffect(Builder builder) {
            if (builder.get(CaptureRequest.CONTROL_EFFECT_MODE) != null && ((Integer) builder.get(CaptureRequest.CONTROL_EFFECT_MODE)).intValue() == this.color_effect) {
                return false;
            }
            builder.set(CaptureRequest.CONTROL_EFFECT_MODE, Integer.valueOf(this.color_effect));
            return CameraController2.do_af_trigger_for_continuous;
        }

        private boolean setWhiteBalance(Builder builder) {
            boolean changed = false;
            if (builder.get(CaptureRequest.CONTROL_AWB_MODE) == null || ((Integer) builder.get(CaptureRequest.CONTROL_AWB_MODE)).intValue() != this.white_balance) {
                builder.set(CaptureRequest.CONTROL_AWB_MODE, Integer.valueOf(this.white_balance));
                changed = CameraController2.do_af_trigger_for_continuous;
            }
            if (this.white_balance != 0) {
                return changed;
            }
            RggbChannelVector rggbChannelVector = CameraController2.this.convertTemperatureToRggb(this.white_balance_temperature);
            builder.set(CaptureRequest.COLOR_CORRECTION_MODE, Integer.valueOf(0));
            builder.set(CaptureRequest.COLOR_CORRECTION_GAINS, rggbChannelVector);
            return CameraController2.do_af_trigger_for_continuous;
        }

        private boolean setAEMode(Builder builder, boolean is_still) {
            if (!this.has_iso) {
                String str = this.flash_value;
                int i = -1;
                switch (str.hashCode()) {
                    case -1524012984:
                        if (str.equals("flash_frontscreen_auto")) {
                            i = 5;
                            break;
                        }
                        break;
                    case -1195303778:
                        if (str.equals("flash_auto")) {
//                            i = CameraController2.do_af_trigger_for_continuous;
                            break;
                        }
                        break;
                    case -1146923872:
                        if (str.equals("flash_off")) {
                            i = 0;
                            break;
                        }
                        break;
                    case -10523976:
                        if (str.equals("flash_frontscreen_on")) {
                            i = 6;
                            break;
                        }
                        break;
                    case 1617654509:
                        if (str.equals("flash_torch")) {
                            i = 3;
                            break;
                        }
                        break;
                    case 1625570446:
                        if (str.equals("flash_on")) {
                            i = 2;
                            break;
                        }
                        break;
                    case 2008442932:
                        if (str.equals("flash_red_eye")) {
                            i = 4;
                            break;
                        }
                        break;
                }
                switch (i) {
                    case 0:
                        builder.set(CaptureRequest.CONTROL_AE_MODE, Integer.valueOf(1));
                        builder.set(CaptureRequest.FLASH_MODE, Integer.valueOf(0));
                        break;
                    case 1:
                        builder.set(CaptureRequest.CONTROL_AE_MODE, Integer.valueOf(2));
                        builder.set(CaptureRequest.FLASH_MODE, Integer.valueOf(0));
                        break;
                    case 2:
                        builder.set(CaptureRequest.CONTROL_AE_MODE, Integer.valueOf(3));
                        builder.set(CaptureRequest.FLASH_MODE, Integer.valueOf(0));
                        break;
                    case 3:
                        builder.set(CaptureRequest.CONTROL_AE_MODE, Integer.valueOf(1));
                        builder.set(CaptureRequest.FLASH_MODE, Integer.valueOf(2));
                        break;
                    case 4:
                        if (CameraController2.this.want_expo_bracketing) {
                            builder.set(CaptureRequest.CONTROL_AE_MODE, Integer.valueOf(1));
                        } else {
                            builder.set(CaptureRequest.CONTROL_AE_MODE, Integer.valueOf(4));
                        }
                        builder.set(CaptureRequest.FLASH_MODE, Integer.valueOf(0));
                        break;
                    case 5:
                    case 6:
                        builder.set(CaptureRequest.CONTROL_AE_MODE, Integer.valueOf(1));
                        builder.set(CaptureRequest.FLASH_MODE, Integer.valueOf(0));
                        break;
                    default:
                        break;
                }
            }
            builder.set(CaptureRequest.CONTROL_AE_MODE, Integer.valueOf(0));
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, Integer.valueOf(this.iso));
            long actual_exposure_time = this.exposure_time;
            if (!is_still) {
                actual_exposure_time = Math.min(this.exposure_time, 83333333);
            }
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, Long.valueOf(actual_exposure_time));
            builder.set(CaptureRequest.FLASH_MODE, Integer.valueOf(0));
            return CameraController2.do_af_trigger_for_continuous;
        }

        private void setCropRegion(Builder builder) {
            if (this.scalar_crop_region != null) {
                builder.set(CaptureRequest.SCALER_CROP_REGION, this.scalar_crop_region);
            }
        }

        private boolean setExposureCompensation(Builder builder) {
            if (!this.has_ae_exposure_compensation) {
                return false;
            }
            if (this.has_iso) {
                return false;
            }
            if (builder.get(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION) != null && this.ae_exposure_compensation == ((Integer) builder.get(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION)).intValue()) {
                return false;
            }
            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, Integer.valueOf(this.ae_exposure_compensation));
            return CameraController2.do_af_trigger_for_continuous;
        }

        private void setFocusMode(Builder builder) {
            if (this.has_af_mode) {
                builder.set(CaptureRequest.CONTROL_AF_MODE, Integer.valueOf(this.af_mode));
            }
        }

        private void setFocusDistance(Builder builder) {
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, Float.valueOf(this.focus_distance));
        }

        private void setAutoExposureLock(Builder builder) {
            builder.set(CaptureRequest.CONTROL_AE_LOCK, Boolean.valueOf(this.ae_lock));
        }

        private void setAFRegions(Builder builder) {
            if (this.af_regions != null && ((Integer) CameraController2.this.characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF)).intValue() > 0) {
                builder.set(CaptureRequest.CONTROL_AF_REGIONS, this.af_regions);
            }
        }

        private void setAERegions(Builder builder) {
            if (this.ae_regions != null && ((Integer) CameraController2.this.characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE)).intValue() > 0) {
                builder.set(CaptureRequest.CONTROL_AE_REGIONS, this.ae_regions);
            }
        }

        private void setFaceDetectMode(Builder builder) {
            if (this.has_face_detect_mode) {
                builder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, Integer.valueOf(this.face_detect_mode));
            } else {
                builder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, Integer.valueOf(0));
            }
        }

        private void setRawMode(Builder builder) {
            if (CameraController2.this.want_raw) {
                builder.set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, Integer.valueOf(1));
            }
        }

        private void setVideoStabilization(Builder builder) {
            builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, Integer.valueOf(this.video_stabilization ? 1 : 0));
        }
    }

    private class OnRawImageAvailableListener implements OnImageAvailableListener {
        private CaptureResult capture_result;
        private Image image;

        private OnRawImageAvailableListener() {
        }

        void setCaptureResult(CaptureResult capture_result) {
            synchronized (CameraController2.this.image_reader_lock) {
                this.capture_result = capture_result;
                if (this.image != null) {
                    processImage();
                }
            }
        }

        void clear() {
            synchronized (CameraController2.this.image_reader_lock) {
                this.capture_result = null;
                this.image = null;
            }
        }

        private void processImage() {
            if (this.capture_result != null && this.image != null) {
                DngCreator dngCreator = new DngCreator(CameraController2.this.characteristics, this.capture_result);
                dngCreator.setOrientation(CameraController2.this.camera_settings.getExifOrientation());
                if (CameraController2.this.camera_settings.location != null) {
                    dngCreator.setLocation(CameraController2.this.camera_settings.location);
                }
                CameraController2.this.pending_dngCreator = dngCreator;
                CameraController2.this.pending_image = this.image;
                PictureCallback cb = CameraController2.this.raw_cb;
                if (CameraController2.this.jpeg_cb == null) {
                    CameraController2.this.takePendingRaw();
                    cb.onCompleted();
                }
            }
        }

        public void onImageAvailable(ImageReader reader) {
            if (CameraController2.this.raw_cb != null) {
                synchronized (CameraController2.this.image_reader_lock) {
                    this.image = reader.acquireNextImage();
                    processImage();
                }
            }
        }
    }

    private enum RequestTag {
        CAPTURE
    }

    private RggbChannelVector convertTemperatureToRggb(int temperature_kelvin) {
        float red;
        float green;
        float blue;
        float temperature = ((float) temperature_kelvin) / 100.0f;
        if (temperature <= 66.0f) {
            red = 255.0f;
        } else {
            red = (float) (329.698727446d * Math.pow((double) (temperature - 60.0f), -0.1332047592d));
            if (red < 0.0f) {
                red = 0.0f;
            }
            if (red > 255.0f) {
                red = 255.0f;
            }
        }
        if (temperature <= 66.0f) {
            green = (float) ((99.4708025861d * Math.log((double) temperature)) - 161.1195681661d);
            if (green < 0.0f) {
                green = 0.0f;
            }
            if (green > 255.0f) {
                green = 255.0f;
            }
        } else {
            green = (float) (288.1221695283d * Math.pow((double) (temperature - 60.0f), -0.0755148492d));
            if (green < 0.0f) {
                green = 0.0f;
            }
            if (green > 255.0f) {
                green = 255.0f;
            }
        }
        if (temperature >= 66.0f) {
            blue = 255.0f;
        } else if (temperature <= 19.0f) {
            blue = 0.0f;
        } else {
            blue = (float) ((138.5177312231d * Math.log((double) (temperature - 10.0f))) - 305.0447927307d);
            if (blue < 0.0f) {
                blue = 0.0f;
            }
            if (blue > 255.0f) {
                blue = 255.0f;
            }
        }
        return new RggbChannelVector((red / 255.0f) * 2.0f, green / 255.0f, green / 255.0f, (blue / 255.0f) * 2.0f);
    }

    private int convertRggbToTemperature(RggbChannelVector rggbChannelVector) {
        int temperature;
        float red = rggbChannelVector.getRed();
        float green_even = rggbChannelVector.getGreenEven();
        float green_odd = rggbChannelVector.getGreenOdd();
        float blue = rggbChannelVector.getBlue();
        float green = 0.5f * (green_even + green_odd);
        float max = Math.max(red, blue);
        if (green > max) {
            green = max;
        }
        float scale = 255.0f / max;
        int red_i = (int) (red * scale);
        int green_i = (int) (green * scale);
        int blue_i = (int) (blue * scale);
        if (red_i == blue_i) {
            temperature = 6600;
        } else if (red_i > blue_i) {
            int t_g = (int) (100.0d * Math.exp((((double) green_i) + 161.1195681661d) / 99.4708025861d));
            if (blue_i == 0) {
                temperature = t_g;
            } else {
                temperature = (t_g + ((int) (100.0d * (Math.exp((((double) blue_i) + 305.0447927307d) / 138.5177312231d) + 10.0d)))) / 2;
            }
        } else if (red_i <= 1 || green_i <= 1) {
            temperature = max_white_balance_temperature_c;
        } else {
            temperature = (((int) (100.0d * (Math.pow(((double) red_i) / 329.698727446d, -7.507239275877164d) + 60.0d))) + ((int) (100.0d * (Math.pow(((double) green_i) / 288.1221695283d, -13.24242861627803d) + 60.0d)))) / 2;
        }
        return Math.min(Math.max(temperature, 1000), max_white_balance_temperature_c);
    }

    public void onError() {
        Log.e(TAG, "onError");
        if (this.camera != null) {
            onError(this.camera);
        }
    }

    private void onError(@NonNull CameraDevice cam) {
        Log.e(TAG, "onError");
        boolean camera_already_opened = this.camera != null ? do_af_trigger_for_continuous : false;
        this.camera = null;
        cam.close();
        if (camera_already_opened) {
            Log.e(TAG, "error occurred after camera was opened");
            this.camera_error_cb.onError();
        }
    }

    final class AnonymousClass1MyStateCallback extends StateCallback {
        public boolean callback_done;
        public boolean first_callback = CameraController2.do_af_trigger_for_continuous;
        private CameraManager manager;

        public AnonymousClass1MyStateCallback(CameraManager manager) {
            this.manager = manager;
        }

        public void onOpened(@NonNull CameraDevice cam) {
            if (this.first_callback) {
                this.first_callback = false;
                try {
                    CameraController2.this.characteristics = manager.getCameraCharacteristics(CameraController2.this.cameraIdS);
                    CameraController2.this.camera = cam;
                    CameraController2.this.createPreviewRequest();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
                synchronized (CameraController2.this.open_camera_lock) {
                    this.callback_done = CameraController2.do_af_trigger_for_continuous;
                    CameraController2.this.open_camera_lock.notifyAll();
                }
            }
        }

        public void onClosed(@NonNull CameraDevice cam) {
            if (this.first_callback) {
                this.first_callback = false;
            }
        }

        public void onDisconnected(@NonNull CameraDevice cam) {
            if (this.first_callback) {
                this.first_callback = false;
                CameraController2.this.camera = null;
                cam.close();
                synchronized (CameraController2.this.open_camera_lock) {
                    this.callback_done = CameraController2.do_af_trigger_for_continuous;
                    CameraController2.this.open_camera_lock.notifyAll();
                }
            }
        }

        public void onError(@NonNull CameraDevice cam, int error) {
            Log.e(CameraController2.TAG, "camera error: " + error);
            if (this.first_callback) {
                this.first_callback = false;
            }
            CameraController2.this.onError(cam);
            synchronized (CameraController2.this.open_camera_lock) {
                this.callback_done = CameraController2.do_af_trigger_for_continuous;
                CameraController2.this.open_camera_lock.notifyAll();
            }
        }
    }


    public CameraController2(Context context, int cameraId, ErrorCallback preview_error_cb, ErrorCallback camera_error_cb) throws CameraControllerException {
        super(cameraId);
        this.context = context;
        this.preview_error_cb = preview_error_cb;
        this.camera_error_cb = camera_error_cb;
        this.thread = new HandlerThread("CameraBackground");
        this.thread.start();
        this.handler = new Handler(this.thread.getLooper());
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        final AnonymousClass1MyStateCallback myStateCallback = new AnonymousClass1MyStateCallback(manager);
        try {
            this.cameraIdS = manager.getCameraIdList()[cameraId];
            manager.openCamera(this.cameraIdS, myStateCallback, this.handler);
            this.handler.postDelayed(new Runnable() {
                public void run() {
                    synchronized (CameraController2.this.open_camera_lock) {
                        if (!myStateCallback.callback_done) {
                            Log.e(CameraController2.TAG, "timeout waiting for camera callback");
                            myStateCallback.first_callback = CameraController2.do_af_trigger_for_continuous;
                            myStateCallback.callback_done = CameraController2.do_af_trigger_for_continuous;
                            CameraController2.this.open_camera_lock.notifyAll();
                        }
                    }
                }
            }, 10000);
            synchronized (this.open_camera_lock) {
                while (!myStateCallback.callback_done) {
                    try {
                        this.open_camera_lock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            if (this.camera == null) {
                Log.e(TAG, "camera failed to open");
                throw new CameraControllerException();
            }
            this.media_action_sound.load(2);
            this.media_action_sound.load(3);
            this.media_action_sound.load(0);
        } catch (CameraAccessException e2) {
            e2.printStackTrace();
            throw new CameraControllerException();
        } catch (UnsupportedOperationException e3) {
            e3.printStackTrace();
            throw new CameraControllerException();
        } catch (SecurityException e4) {
            e4.printStackTrace();
            throw new CameraControllerException();
        }
    }

    public void release() {
        if (this.thread != null) {
            this.thread.quitSafely();
            try {
                this.thread.join();
                this.thread = null;
                this.handler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (this.captureSession != null) {
            this.captureSession.close();
            this.captureSession = null;
        }
        this.previewBuilder = null;
        if (this.camera != null) {
            this.camera.close();
            this.camera = null;
        }
        closePictureImageReader();
    }

    private void closePictureImageReader() {
        if (this.imageReader != null) {
            this.imageReader.close();
            this.imageReader = null;
        }
        if (this.imageReaderRaw != null) {
            this.imageReaderRaw.close();
            this.imageReaderRaw = null;
            this.onRawImageAvailableListener = null;
        }
    }

    private List<String> convertFocusModesToValues(int[] supported_focus_modes_arr, float minimum_focus_distance) {
        if (supported_focus_modes_arr.length == 0) {
            return null;
        }
        List<Integer> supported_focus_modes = new ArrayList();
        for (int valueOf : supported_focus_modes_arr) {
            supported_focus_modes.add(Integer.valueOf(valueOf));
        }
        List<String> output_modes = new ArrayList();
        if (supported_focus_modes.contains(Integer.valueOf(1))) {
            output_modes.add("focus_mode_auto");
        }
        if (supported_focus_modes.contains(Integer.valueOf(2))) {
            output_modes.add("focus_mode_macro");
        }
        if (supported_focus_modes.contains(Integer.valueOf(1))) {
            output_modes.add("focus_mode_locked");
        }
        if (supported_focus_modes.contains(Integer.valueOf(0))) {
            output_modes.add("focus_mode_infinity");
            if (minimum_focus_distance > 0.0f) {
                output_modes.add("focus_mode_manual2");
            }
        }
        if (supported_focus_modes.contains(Integer.valueOf(5))) {
            output_modes.add("focus_mode_edof");
        }
        if (supported_focus_modes.contains(Integer.valueOf(4))) {
            output_modes.add("focus_mode_continuous_picture");
        }
        if (!supported_focus_modes.contains(Integer.valueOf(3))) {
            return output_modes;
        }
        output_modes.add("focus_mode_continuous_video");
        return output_modes;
    }

    public String getAPI() {
        return "Camera2 (Android L)";
    }

    public CameraFeatures getCameraFeatures() {
        CameraFeatures camera_features = new CameraFeatures();
        float max_zoom = ((Float) this.characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)).floatValue();
        camera_features.is_zoom_supported = max_zoom > 0.0f ? do_af_trigger_for_continuous : false;
        if (camera_features.is_zoom_supported) {
            int n_steps = (int) ((20.0d * Math.log(((double) max_zoom) + 1.0E-11d)) / Math.log(2.0d));
            double scale_factor = Math.pow((double) max_zoom, 1.0d / ((double) n_steps));
            camera_features.zoom_ratios = new ArrayList();
            camera_features.zoom_ratios.add(Integer.valueOf(100));
            double zoom = 1.0d;
            for (int i = 0; i < n_steps - 1; i++) {
                zoom *= scale_factor;
                camera_features.zoom_ratios.add(Integer.valueOf((int) (100.0d * zoom)));
            }
            camera_features.zoom_ratios.add(Integer.valueOf((int) (100.0f * max_zoom)));
            camera_features.max_zoom = camera_features.zoom_ratios.size() - 1;
            this.zoom_ratios = camera_features.zoom_ratios;
        } else {
            this.zoom_ratios = null;
        }
        int[] face_modes = (int[]) this.characteristics.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES);
        camera_features.supports_face_detection = false;
        this.supports_face_detect_mode_simple = false;
        this.supports_face_detect_mode_full = false;
        for (int face_mode : face_modes) {
            if (face_mode == 1) {
                camera_features.supports_face_detection = do_af_trigger_for_continuous;
                this.supports_face_detect_mode_simple = do_af_trigger_for_continuous;
            } else if (face_mode == 2) {
                camera_features.supports_face_detection = do_af_trigger_for_continuous;
                this.supports_face_detect_mode_full = do_af_trigger_for_continuous;
            }
        }
        if (camera_features.supports_face_detection && ((Integer) this.characteristics.get(CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT)).intValue() <= 0) {
            camera_features.supports_face_detection = false;
            this.supports_face_detect_mode_simple = false;
            this.supports_face_detect_mode_full = false;
        }
        boolean capabilities_raw = false;
        boolean capabilities_high_speed_video = false;
        for (int capability : (int[]) this.characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)) {
            if (capability == 3) {
                capabilities_raw = do_af_trigger_for_continuous;
            } else if (capability == 9) {
                capabilities_high_speed_video = do_af_trigger_for_continuous;
            }
        }
        StreamConfigurationMap configs = this.characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        android.util.Size[] camera_picture_sizes = configs.getOutputSizes(256);
        camera_features.picture_sizes = new ArrayList();
        for (android.util.Size camera_size : camera_picture_sizes) {
            camera_features.picture_sizes.add(new Size(camera_size.getWidth(), camera_size.getHeight()));
        }
        this.raw_size = null;
        if (capabilities_raw) {
            android.util.Size[] raw_camera_picture_sizes = configs.getOutputSizes(32);
            if (raw_camera_picture_sizes == null) {
                this.want_raw = false;
            } else {
                for (android.util.Size size : raw_camera_picture_sizes) {
                    if (this.raw_size == null || size.getWidth() * size.getHeight() > this.raw_size.getWidth() * this.raw_size.getHeight()) {
                        this.raw_size = size;
                    }
                }
                if (this.raw_size == null) {
                    this.want_raw = false;
                } else {
                    camera_features.supports_raw = do_af_trigger_for_continuous;
                }
            }
        } else {
            this.want_raw = false;
        }
        android.util.Size[] camera_video_sizes = configs.getOutputSizes(MediaRecorder.class);
        camera_features.video_sizes = new ArrayList();
        for (android.util.Size camera_size2 : camera_video_sizes) {
            if (camera_size2.getWidth() <= 4096 && camera_size2.getHeight() <= 2160) {
                camera_features.video_sizes.add(new Size(camera_size2.getWidth(), camera_size2.getHeight()));
            }
        }
        if (capabilities_high_speed_video) {
            android.util.Size[] camera_video_sizes_high_speed = configs.getHighSpeedVideoSizes();
            camera_features.video_sizes_high_speed = new ArrayList();
            for (android.util.Size camera_size22 : camera_video_sizes_high_speed) {
                if (camera_size22.getWidth() <= 4096 && camera_size22.getHeight() <= 2160) {
                    camera_features.video_sizes_high_speed.add(new Size(camera_size22.getWidth(), camera_size22.getHeight()));
                }
            }
        }
        android.util.Size[] camera_preview_sizes = configs.getOutputSizes(SurfaceTexture.class);
        camera_features.preview_sizes = new ArrayList();
        Point display_size = new Point();
        ((Activity) this.context).getWindowManager().getDefaultDisplay().getRealSize(display_size);
        for (android.util.Size camera_size222 : camera_preview_sizes) {
            if (camera_size222.getWidth() <= display_size.x && camera_size222.getHeight() <= display_size.y) {
                camera_features.preview_sizes.add(new Size(camera_size222.getWidth(), camera_size222.getHeight()));
            }
        }
        if (((Boolean) this.characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)).booleanValue()) {
            camera_features.supported_flash_values = new ArrayList();
            camera_features.supported_flash_values.add("flash_off");
            camera_features.supported_flash_values.add("flash_auto");
            camera_features.supported_flash_values.add("flash_on");
            camera_features.supported_flash_values.add("flash_torch");
            if (!this.use_fake_precapture) {
                camera_features.supported_flash_values.add("flash_red_eye");
            }
        } else if (isFrontFacing()) {
            camera_features.supported_flash_values = new ArrayList();
            camera_features.supported_flash_values.add("flash_off");
            camera_features.supported_flash_values.add("flash_frontscreen_auto");
            camera_features.supported_flash_values.add("flash_frontscreen_on");
        }
        Float minimum_focus_distance = (Float) this.characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
        if (minimum_focus_distance != null) {
            camera_features.minimum_focus_distance = minimum_focus_distance.floatValue();
        } else {
            camera_features.minimum_focus_distance = 0.0f;
        }
        camera_features.supported_focus_values = convertFocusModesToValues((int[]) this.characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES), camera_features.minimum_focus_distance);
        camera_features.max_num_focus_areas = ((Integer) this.characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF)).intValue();
        camera_features.is_exposure_lock_supported = do_af_trigger_for_continuous;
        camera_features.is_video_stabilization_supported = do_af_trigger_for_continuous;
        int[] white_balance_modes = (int[]) this.characteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES);
        if (white_balance_modes != null) {
            for (int value : white_balance_modes) {
                if (value == 0 && allowManualWB()) {
                    camera_features.supports_white_balance_temperature = do_af_trigger_for_continuous;
                    camera_features.min_temperature = 1000;
                    camera_features.max_temperature = max_white_balance_temperature_c;
                }
            }
        }
        Range<Integer> iso_range = (Range) this.characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
        if (iso_range != null) {
            camera_features.supports_iso_range = do_af_trigger_for_continuous;
            camera_features.min_iso = ((Integer) iso_range.getLower()).intValue();
            camera_features.max_iso = ((Integer) iso_range.getUpper()).intValue();
            Range<Long> exposure_time_range = (Range) this.characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
            if (exposure_time_range != null) {
                camera_features.supports_exposure_time = do_af_trigger_for_continuous;
                camera_features.supports_expo_bracketing = do_af_trigger_for_continuous;
                camera_features.max_expo_bracketing_n_images = 5;
                camera_features.min_exposure_time = ((Long) exposure_time_range.getLower()).longValue();
                camera_features.max_exposure_time = ((Long) exposure_time_range.getUpper()).longValue();
            }
        }
        Range<Integer> exposure_range = (Range) this.characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
        camera_features.min_exposure = ((Integer) exposure_range.getLower()).intValue();
        camera_features.max_exposure = ((Integer) exposure_range.getUpper()).intValue();
        camera_features.exposure_step = ((Rational) this.characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)).floatValue();
        camera_features.can_disable_shutter_sound = do_af_trigger_for_continuous;
        SizeF physical_size = (SizeF) this.characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
        float[] focal_lengths = (float[]) this.characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
        camera_features.view_angle_x = (float) Math.toDegrees(2.0d * Math.atan2((double) physical_size.getWidth(), 2.0d * ((double) focal_lengths[0])));
        camera_features.view_angle_y = (float) Math.toDegrees(2.0d * Math.atan2((double) physical_size.getHeight(), 2.0d * ((double) focal_lengths[0])));
        return camera_features;
    }

    private String convertSceneMode(int value2) {
        switch (value2) {
            case 0:
                return "auto";
            case 2:
                return "action";
            case 3:
                return "portrait";
            case 4:
                return "landscape";
            case 5:
                return "night";
            case 6:
                return "night-portrait";
            case 7:
                return "theatre";
            case 8:
                return "beach";
            case 9:
                return "snow";
            case 10:
                return "sunset";
            case 11:
                return "steadyphoto";
            case MotionEventCompat.AXIS_RX /*12*/:
                return "fireworks";
            case MotionEventCompat.AXIS_RY /*13*/:
                return "sports";
            case MotionEventCompat.AXIS_RZ /*14*/:
                return "party";
            case 15:
                return "candlelight";
            case 16:
                return "barcode";
            default:
                return null;
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public SupportedValues setSceneMode(String value) {
        int i = 0;
        String default_value = getDefaultSceneMode();
        int[] values2 = (int[]) this.characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES);
        boolean has_disabled = false;
        List<String> values = new ArrayList();
        for (int value2 : values2) {
            if (value2 == 0) {
                has_disabled = do_af_trigger_for_continuous;
            }
            String this_value = convertSceneMode(value2);
            if (this_value != null) {
                values.add(this_value);
            }
        }
        if (!has_disabled) {
            values.add(0, "auto");
        }
        SupportedValues supported_values = checkModeIsSupported(values, value, default_value);
        if (supported_values != null) {
            int selected_value2 = 0;
            String str = supported_values.selected_value;
            switch (str.hashCode()) {
                case -1422950858:
                    if (str.equals("action")) {
                        break;
                    }
                case -1350043241:
                    if (str.equals("theatre")) {
                        i = 15;
                        break;
                    }
                case -895760513:
                    if (str.equals("sports")) {
                        i = 12;
                        break;
                    }
                case -891172202:
                    if (str.equals("sunset")) {
                        i = 14;
                        break;
                    }
                case -333584256:
                    if (str.equals("barcode")) {
                        i = 1;
                        break;
                    }
                case -300277408:
                    if (str.equals("steadyphoto")) {
                        i = 13;
                        break;
                    }
                case -264202484:
                    if (str.equals("fireworks")) {
                        i = 5;
                        break;
                    }
                case 3005871:
                    if (str.equals("auto")) {
                        i = 4;
                        break;
                    }
                case 3535235:
                    if (str.equals("snow")) {
                        i = 11;
                        break;
                    }
                case 93610339:
                    if (str.equals("beach")) {
                        i = 2;
                        break;
                    }
                case 104817688:
                    if (str.equals("night")) {
                        i = 7;
                        break;
                    }
                case 106437350:
                    if (str.equals("party")) {
                        i = 9;
                        break;
                    }
                case 729267099:
                    if (str.equals("portrait")) {
                        i = 10;
                        break;
                    }
                case 1430647483:
                    if (str.equals("landscape")) {
                        i = 6;
                        break;
                    }
                case 1664284080:
                    if (str.equals("night-portrait")) {
                        i = 8;
                        break;
                    }
                case 1900012073:
                    if (str.equals("candlelight")) {
                        i = 3;
                        break;
                    }
                default:
                    i = -1;
                    break;
            }
            switch (i) {
                case 0:
                    selected_value2 = 2;
                    break;
                case 1:
                    selected_value2 = 16;
                    break;
                case 2:
                    selected_value2 = 8;
                    break;
                case 3:
                    selected_value2 = 15;
                    break;
                case 4:
                    selected_value2 = 0;
                    break;
                case 5:
                    selected_value2 = 12;
                    break;
                case 6:
                    selected_value2 = 4;
                    break;
                case 7:
                    selected_value2 = 5;
                    break;
                case 8:
                    selected_value2 = 6;
                    break;
                case 9:
                    selected_value2 = 14;
                    break;
                case 10:
                    selected_value2 = 3;
                    break;
                case 11:
                    selected_value2 = 9;
                    break;
                case MotionEventCompat.AXIS_RX /*12*/:
                    selected_value2 = 13;
                    break;
                case MotionEventCompat.AXIS_RY /*13*/:
                    selected_value2 = 11;
                    break;
                case MotionEventCompat.AXIS_RZ /*14*/:
                    selected_value2 = 10;
                    break;
                case 15:
                    selected_value2 = 7;
                    break;
            }
            this.camera_settings.scene_mode = selected_value2;
            if (this.camera_settings.setSceneMode(this.previewBuilder)) {
                try {
                    setRepeatingRequest();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        }
        return supported_values;
    }

    public String getSceneMode() {
        if (this.previewBuilder.get(CaptureRequest.CONTROL_SCENE_MODE) == null) {
            return null;
        }
        return convertSceneMode(((Integer) this.previewBuilder.get(CaptureRequest.CONTROL_SCENE_MODE)).intValue());
    }

    private String convertColorEffect(int value2) {
        switch (value2) {
            case 0:
                return "none";
            case 1:
                return "mono";
            case 2:
                return "negative";
            case 3:
                return "solarize";
            case 4:
                return "sepia";
            case 5:
                return "posterize";
            case 6:
                return "whiteboard";
            case 7:
                return "blackboard";
            case 8:
                return "aqua";
            default:
                return null;
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public SupportedValues setColorEffect(String value) {
        int obj = -2;
        String default_value = getDefaultColorEffect();
        int[] values2 = (int[]) this.characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_EFFECTS);
        List<String> values = new ArrayList();
        for (int value2 : values2) {
            String this_value = convertColorEffect(value2);
            if (this_value != null) {
                values.add(this_value);
            }
        }
        SupportedValues supported_values = checkModeIsSupported(values, value, default_value);
        if (supported_values != null) {
            int selected_value2 = 0;
            String str = supported_values.selected_value;
            switch (str.hashCode()) {
                case -1635350969:
                    if (str.equals("blackboard")) {
                        obj = 1;
                        break;
                    }
                case 3002044:
                    if (str.equals("aqua")) {
                        break;
                    }
                case 3357411:
                    if (str.equals("mono")) {
                        obj = 2;
                        break;
                    }
                case 3387192:
                    if (str.equals("none")) {
                        obj = 4;
                        break;
                    }
                case 109324790:
                    if (str.equals("sepia")) {
                        obj = 6;
                        break;
                    }
                case 261182557:
                    if (str.equals("whiteboard")) {
                        obj = 8;
                        break;
                    }
                case 921111605:
                    if (str.equals("negative")) {
                        obj = 3;
                        break;
                    }
                case 1473417203:
                    if (str.equals("solarize")) {
                        obj = 7;
                        break;
                    }
                case 2008448231:
                    if (str.equals("posterize")) {
                        obj = 5;
                        break;
                    }
                default:
                    obj = -1;
                    break;
            }
            switch (obj) {
                case -1:
                    selected_value2 = 8;
                    break;
                case 1:
                    selected_value2 = 7;
                    break;
                case 2:
                    selected_value2 = 1;
                    break;
                case 3:
                    selected_value2 = 2;
                    break;
                case 4:
                    selected_value2 = 0;
                    break;
                case 5:
                    selected_value2 = 5;
                    break;
                case 6:
                    selected_value2 = 4;
                    break;
                case 7:
                    selected_value2 = 3;
                    break;
                case 8:
                    selected_value2 = 6;
                    break;
            }
            this.camera_settings.color_effect = selected_value2;
            if (this.camera_settings.setColorEffect(this.previewBuilder)) {
                try {
                    setRepeatingRequest();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        }
        return supported_values;
    }

    public String getColorEffect() {
        if (this.previewBuilder.get(CaptureRequest.CONTROL_EFFECT_MODE) == null) {
            return null;
        }
        return convertColorEffect(((Integer) this.previewBuilder.get(CaptureRequest.CONTROL_EFFECT_MODE)).intValue());
    }

    private String convertWhiteBalance(int value2) {
        switch (value2) {
            case 0:
                return "manual";
            case 1:
                return "auto";
            case 2:
                return "incandescent";
            case 3:
                return "fluorescent";
            case 4:
                return "warm-fluorescent";
            case 5:
                return "daylight";
            case 6:
                return "cloudy-daylight";
            case 7:
                return "twilight";
            case 8:
                return "shade";
            default:
                return null;
        }
    }

    private boolean allowManualWB() {
        return !Build.MODEL.toLowerCase(Locale.US).contains("nexus 6") ? do_af_trigger_for_continuous : false;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public SupportedValues setWhiteBalance(String value) {
        int i = 0;
        String default_value = getDefaultWhiteBalance();
        int[] values2 = (int[]) this.characteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES);
        List<String> values = new ArrayList();
        for (int value2 : values2) {
            String this_value = convertWhiteBalance(value2);
            if (this_value != null && (value2 != 0 || allowManualWB())) {
                values.add(this_value);
            }
        }
        boolean has_auto = values.remove("auto");
        if (values.remove("manual")) {
            values.add(0, "manual");
        }
        if (has_auto) {
            values.add(0, "auto");
        }
        SupportedValues supported_values = checkModeIsSupported(values, value, default_value);
        if (supported_values != null) {
            int selected_value2 = 1;
            String str = supported_values.selected_value;
            switch (str.hashCode()) {
                case -1081415738:
                    if (str.equals("manual")) {
                        i = 8;
                        break;
                    }
                case -939299377:
                    if (str.equals("incandescent")) {
                        i = 4;
                        break;
                    }
                case -719316704:
                    if (str.equals("warm-fluorescent")) {
                        i = 7;
                        break;
                    }
                case 3005871:
                    if (str.equals("auto")) {
                        break;
                    }
                case 109399597:
                    if (str.equals("shade")) {
                        i = 5;
                        break;
                    }
                case 474934723:
                    if (str.equals("cloudy-daylight")) {
                        i = 1;
                        break;
                    }
                case 1650323088:
                    if (str.equals("twilight")) {
                        i = 6;
                        break;
                    }
                case 1902580840:
                    if (str.equals("fluorescent")) {
                        i = 3;
                        break;
                    }
                case 1942983418:
                    if (str.equals("daylight")) {
                        i = 2;
                        break;
                    }
                default:
                    i = -1;
                    break;
            }
            switch (i) {
                case 0:
                    selected_value2 = 1;
                    break;
                case 1:
                    selected_value2 = 6;
                    break;
                case 2:
                    selected_value2 = 5;
                    break;
                case 3:
                    selected_value2 = 3;
                    break;
                case 4:
                    selected_value2 = 2;
                    break;
                case 5:
                    selected_value2 = 8;
                    break;
                case 6:
                    selected_value2 = 7;
                    break;
                case 7:
                    selected_value2 = 4;
                    break;
                case 8:
                    selected_value2 = 0;
                    break;
            }
            this.camera_settings.white_balance = selected_value2;
            if (this.camera_settings.setWhiteBalance(this.previewBuilder)) {
                try {
                    setRepeatingRequest();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        }
        return supported_values;
    }

    public String getWhiteBalance() {
        if (this.previewBuilder.get(CaptureRequest.CONTROL_AWB_MODE) == null) {
            return null;
        }
        return convertWhiteBalance(((Integer) this.previewBuilder.get(CaptureRequest.CONTROL_AWB_MODE)).intValue());
    }

    public boolean setWhiteBalanceTemperature(int temperature) {
        if (this.camera_settings.white_balance == temperature) {
            return false;
        }
        try {
            this.camera_settings.white_balance_temperature = Math.min(Math.max(temperature, 1000), max_white_balance_temperature_c);
            if (this.camera_settings.setWhiteBalance(this.previewBuilder)) {
                setRepeatingRequest();
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return do_af_trigger_for_continuous;
    }

    public int getWhiteBalanceTemperature() {
        return this.camera_settings.white_balance_temperature;
    }

    public SupportedValues setISO(String value) {
        setManualISO(false, 0);
        return null;
    }

    public String getISOKey() {
        return "";
    }

    public void setManualISO(boolean manual_iso, int iso) {
        if (manual_iso) {
            try {
                Range<Integer> iso_range = (Range) this.characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
                if (iso_range != null) {
                    this.camera_settings.has_iso = do_af_trigger_for_continuous;
                    this.camera_settings.iso = Math.min(Math.max(iso, ((Integer) iso_range.getLower()).intValue()), ((Integer) iso_range.getUpper()).intValue());
                } else {
                    return;
                }
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }
        this.camera_settings.has_iso = false;
        this.camera_settings.iso = 0;
        if (this.camera_settings.setAEMode(this.previewBuilder, false)) {
            try {
                setRepeatingRequest();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean isManualISO() {
        return this.camera_settings.has_iso;
    }

    public boolean setISO(int iso) {
        if (this.camera_settings.iso == iso) {
            return false;
        }
        try {
            this.camera_settings.iso = iso;
            if (this.camera_settings.setAEMode(this.previewBuilder, false)) {
                setRepeatingRequest();
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return do_af_trigger_for_continuous;
    }

    public int getISO() {
        return this.camera_settings.iso;
    }

    public long getExposureTime() {
        return this.camera_settings.exposure_time;
    }

    public boolean setExposureTime(long exposure_time) {
        if (this.camera_settings.exposure_time == exposure_time) {
            return false;
        }
        try {
            this.camera_settings.exposure_time = exposure_time;
            if (this.camera_settings.setAEMode(this.previewBuilder, false)) {
                setRepeatingRequest();
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return do_af_trigger_for_continuous;
    }

    public Size getPictureSize() {
        return new Size(this.picture_width, this.picture_height);
    }

    public void setPictureSize(int width, int height) {
        if (this.camera != null) {
            if (this.captureSession != null) {
                throw new RuntimeException();
            }
            this.picture_width = width;
            this.picture_height = height;
        }
    }

    public void setRaw(boolean want_raw) {
        if (this.camera != null && this.want_raw != want_raw) {
            if (!want_raw || this.raw_size != null) {
                if (this.captureSession != null) {
                    throw new RuntimeException();
                }
                this.want_raw = want_raw;
            }
        }
    }

    public void setExpoBracketing(boolean want_expo_bracketing) {
        if (this.camera != null && this.want_expo_bracketing != want_expo_bracketing) {
            if (this.captureSession != null) {
                throw new RuntimeException();
            }
            this.want_expo_bracketing = want_expo_bracketing;
            updateUseFakePrecaptureMode(this.camera_settings.flash_value);
            this.camera_settings.setAEMode(this.previewBuilder, false);
        }
    }

    public void setExpoBracketingNImages(int n_images) {
        if (n_images <= 1 || n_images % 2 == 0) {
            throw new RuntimeException();
        }
        if (n_images > 5) {
            n_images = 5;
        }
        this.expo_bracketing_n_images = n_images;
    }

    public void setExpoBracketingStops(double stops) {
        if (stops <= 0.0d) {
            throw new RuntimeException();
        }
        this.expo_bracketing_stops = stops;
    }

    public void setUseExpoFastBurst(boolean use_expo_fast_burst) {
        this.use_expo_fast_burst = use_expo_fast_burst;
    }

    public void setOptimiseAEForDRO(boolean optimise_ae_for_dro) {
        if (Build.MANUFACTURER.toLowerCase(Locale.US).contains("oneplus")) {
            this.optimise_ae_for_dro = false;
        } else {
            this.optimise_ae_for_dro = optimise_ae_for_dro;
        }
    }

    public void setUseCamera2FakeFlash(boolean use_fake_precapture) {
        if (this.camera != null && this.use_fake_precapture != use_fake_precapture) {
            this.use_fake_precapture = use_fake_precapture;
            this.use_fake_precapture_mode = use_fake_precapture;
        }
    }

    public boolean getUseCamera2FakeFlash() {
        return this.use_fake_precapture;
    }

    private void createPictureImageReader() {
        if (this.captureSession != null) {
            throw new RuntimeException();
        }
        closePictureImageReader();
        if (this.picture_width == 0 || this.picture_height == 0) {
            throw new RuntimeException();
        }
        this.imageReader = ImageReader.newInstance(this.picture_width, this.picture_height, 256, 2);
        this.imageReader.setOnImageAvailableListener(new OnImageAvailableListener() {
            public void onImageAvailable(ImageReader reader) {
                if (CameraController2.this.jpeg_cb != null) {
                    synchronized (CameraController2.this.image_reader_lock) {
                        Image image = reader.acquireNextImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.remaining()];
                        buffer.get(bytes);
                        image.close();
                        PictureCallback cb;
                        if (!CameraController2.this.want_expo_bracketing || CameraController2.this.n_burst <= 1) {
                            cb = CameraController2.this.jpeg_cb;
                            CameraController2.this.jpeg_cb = null;
                            cb.onPictureTaken(bytes);
                            if (CameraController2.this.raw_cb == null) {
                                cb.onCompleted();
                            } else if (CameraController2.this.pending_dngCreator != null) {
                                CameraController2.this.takePendingRaw();
                                cb.onCompleted();
                            }
                        } else {
                            CameraController2.this.pending_burst_images.add(bytes);
                            if (CameraController2.this.pending_burst_images.size() >= CameraController2.this.n_burst) {
                                if (CameraController2.this.pending_burst_images.size() > CameraController2.this.n_burst) {
                                    Log.e(CameraController2.TAG, "pending_burst_images size " + CameraController2.this.pending_burst_images.size() + " is greater than n_burst " + CameraController2.this.n_burst);
                                }
                                cb = CameraController2.this.jpeg_cb;
                                CameraController2.this.jpeg_cb = null;
                                cb.onBurstPictureTaken(new ArrayList(CameraController2.this.pending_burst_images));
                                CameraController2.this.pending_burst_images.clear();
                                cb.onCompleted();
                            } else if (CameraController2.this.burst_capture_requests != null) {
                                try {
                                    CameraController2.this.captureSession.capture((CaptureRequest) CameraController2.this.burst_capture_requests.get(CameraController2.this.pending_burst_images.size()), CameraController2.this.previewCaptureCallback, CameraController2.this.handler);
                                } catch (CameraAccessException e) {
                                    e.printStackTrace();
                                    CameraController2.this.jpeg_cb = null;
                                    if (CameraController2.this.take_picture_error_cb != null) {
                                        CameraController2.this.take_picture_error_cb.onError();
                                        CameraController2.this.take_picture_error_cb = null;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }, null);
        if (this.want_raw && this.raw_size != null) {
            this.imageReaderRaw = ImageReader.newInstance(this.raw_size.getWidth(), this.raw_size.getHeight(), 32, 2);
            ImageReader imageReader = this.imageReaderRaw;
            OnImageAvailableListener onRawImageAvailableListener = new OnRawImageAvailableListener();
            this.onRawImageAvailableListener = (OnRawImageAvailableListener) onRawImageAvailableListener;
            imageReader.setOnImageAvailableListener(onRawImageAvailableListener, null);
        }
    }

    private void clearPending() {
        this.pending_burst_images.clear();
        this.pending_dngCreator = null;
        this.pending_image = null;
        if (this.onRawImageAvailableListener != null) {
            this.onRawImageAvailableListener.clear();
        }
        this.burst_capture_requests = null;
        this.n_burst = 0;
        this.burst_start_ms = 0;
    }

    private void takePendingRaw() {
        if (this.pending_dngCreator != null) {
            PictureCallback cb = this.raw_cb;
            this.raw_cb = null;
            cb.onRawPictureTaken(this.pending_dngCreator, this.pending_image);
            this.pending_dngCreator = null;
            this.pending_image = null;
            if (this.onRawImageAvailableListener != null) {
                this.onRawImageAvailableListener.clear();
            }
        }
    }

    public Size getPreviewSize() {
        return new Size(this.preview_width, this.preview_height);
    }

    public void setPreviewSize(int width, int height) {
        this.preview_width = width;
        this.preview_height = height;
    }

    public void setVideoStabilization(boolean enabled) {
        this.camera_settings.video_stabilization = enabled;
        this.camera_settings.setVideoStabilization(this.previewBuilder);
        try {
            setRepeatingRequest();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public boolean getVideoStabilization() {
        return this.camera_settings.video_stabilization;
    }

    public int getJpegQuality() {
        return this.camera_settings.jpeg_quality;
    }

    public void setJpegQuality(int quality) {
        if (quality < 0 || quality > 100) {
            throw new RuntimeException();
        }
        this.camera_settings.jpeg_quality = (byte) quality;
    }

    public int getZoom() {
        return this.current_zoom_value;
    }

    public void setZoom(int value) {
        if (this.zoom_ratios != null) {
            if (value < 0 || value > this.zoom_ratios.size()) {
                throw new RuntimeException();
            }
            float zoom = ((float) ((Integer) this.zoom_ratios.get(value)).intValue()) / 100.0f;
            Rect sensor_rect = (Rect) this.characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            int left = sensor_rect.width() / 2;
            int right = left;
            int top = sensor_rect.height() / 2;
            int bottom = top;
            int hwidth = (int) (((double) sensor_rect.width()) / (2.0d * ((double) zoom)));
            int hheight = (int) (((double) sensor_rect.height()) / (2.0d * ((double) zoom)));
            top -= hheight;
            this.camera_settings.scalar_crop_region = new Rect(left - hwidth, top, right + hwidth, bottom + hheight);
            this.camera_settings.setCropRegion(this.previewBuilder);
            this.current_zoom_value = value;
            try {
                setRepeatingRequest();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    public int getExposureCompensation() {
        if (this.previewBuilder.get(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION) == null) {
            return 0;
        }
        return ((Integer) this.previewBuilder.get(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION)).intValue();
    }

    public boolean setExposureCompensation(int new_exposure) {
        this.camera_settings.has_ae_exposure_compensation = do_af_trigger_for_continuous;
        this.camera_settings.ae_exposure_compensation = new_exposure;
        if (!this.camera_settings.setExposureCompensation(this.previewBuilder)) {
            return false;
        }
        try {
            setRepeatingRequest();
            return do_af_trigger_for_continuous;
        } catch (CameraAccessException e) {
            e.printStackTrace();
            return do_af_trigger_for_continuous;
        }
    }

    public void setPreviewFpsRange(int min, int max) {
    }

    public List<int[]> getSupportedPreviewFpsRange() {
        return null;
    }

    public long getDefaultExposureTime() {
        return CameraController.EXPOSURE_TIME_DEFAULT;
    }

    public void setFocusValue(String focus_value) {
        int focus_mode = 1;
        switch (focus_value.hashCode()) {
            case -2084726721:
                if (focus_value.equals("focus_mode_locked")) {
                    focus_mode = 1;
                    break;
                }
                break;
            case -1897460700:
                if (focus_value.equals("focus_mode_auto")) {
                    focus_mode = 1;
                    this.camera_settings.focus_distance = 0.0f;
                    break;
                }
                break;
            case -1897358037:
                if (focus_value.equals("focus_mode_edof")) {
                    focus_mode = 0;
                    this.camera_settings.focus_distance = this.camera_settings.focus_distance_manual;
                    break;
                }
                break;
            case -711944829:
                if (focus_value.equals("focus_mode_continuous_picture")) {
                    focus_mode = 0;
                    break;
                }
                break;
            case 295129751:
                if (focus_value.equals("focus_mode_manual2")) {
                    focus_mode = 2;
                    break;
                }
                break;
            case 402565696:
                if (focus_value.equals("focus_mode_continuous_video")) {
                    focus_mode = 5;
                    break;
                }
                break;
            case 590698013:
                if (focus_value.equals("focus_mode_infinity")) {
                    focus_mode = 4;
                    break;
                }
                break;
            case 1318730743:
                if (focus_value.equals("focus_mode_macro")) {
                    focus_mode = 3;
                    break;
                }
                break;
        }

        this.camera_settings.has_af_mode = do_af_trigger_for_continuous;
        this.camera_settings.af_mode = focus_mode;
        this.camera_settings.setFocusMode(this.previewBuilder);
        this.camera_settings.setFocusDistance(this.previewBuilder);
        try {
            setRepeatingRequest();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private String convertFocusModeToValue(int focus_mode) {
        String focus_value = "";
        if (focus_mode == 1) {
            return "focus_mode_auto";
        }
        if (focus_mode == 2) {
            return "focus_mode_macro";
        }
        if (focus_mode == 5) {
            return "focus_mode_edof";
        }
        if (focus_mode == 4) {
            return "focus_mode_continuous_picture";
        }
        if (focus_mode == 3) {
            return "focus_mode_continuous_video";
        }
        if (focus_mode == 0) {
            return "focus_mode_manual2";
        }
        return focus_value;
    }

    public String getFocusValue() {
        return convertFocusModeToValue(this.previewBuilder.get(CaptureRequest.CONTROL_AF_MODE) != null ? ((Integer) this.previewBuilder.get(CaptureRequest.CONTROL_AF_MODE)).intValue() : 1);
    }

    public float getFocusDistance() {
        return this.camera_settings.focus_distance;
    }

    public boolean setFocusDistance(float focus_distance) {
        if (this.camera_settings.focus_distance == focus_distance) {
            return false;
        }
        this.camera_settings.focus_distance = focus_distance;
        this.camera_settings.focus_distance_manual = focus_distance;
        this.camera_settings.setFocusDistance(this.previewBuilder);
        try {
            setRepeatingRequest();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return do_af_trigger_for_continuous;
    }

    private void updateUseFakePrecaptureMode(String flash_value) {
        boolean frontscreen_flash = (flash_value.equals("flash_frontscreen_auto") || flash_value.equals("flash_frontscreen_on")) ? do_af_trigger_for_continuous : false;
        if (frontscreen_flash) {
            this.use_fake_precapture_mode = do_af_trigger_for_continuous;
        } else if (this.want_expo_bracketing) {
            this.use_fake_precapture_mode = do_af_trigger_for_continuous;
        } else {
            this.use_fake_precapture_mode = this.use_fake_precapture;
        }
    }

    public void setFlashValue(String flash_value) {
        if (!this.camera_settings.flash_value.equals(flash_value)) {
            try {
                updateUseFakePrecaptureMode(flash_value);
                if (!this.camera_settings.flash_value.equals("flash_torch") || flash_value.equals("flash_off")) {
                    this.camera_settings.flash_value = flash_value;
                    if (this.camera_settings.setAEMode(this.previewBuilder, false)) {
                        setRepeatingRequest();
                        return;
                    }
                    return;
                }
                this.camera_settings.flash_value = "flash_off";
                this.camera_settings.setAEMode(this.previewBuilder, false);
                CaptureRequest request = this.previewBuilder.build();
                this.camera_settings.flash_value = flash_value;
                this.camera_settings.setAEMode(this.previewBuilder, false);
                this.push_repeating_request_when_torch_off = do_af_trigger_for_continuous;
                this.push_repeating_request_when_torch_off_id = request;
                setRepeatingRequest(request);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    public String getFlashValue() {
        if (((Boolean) this.characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)).booleanValue()) {
            return this.camera_settings.flash_value;
        }
        return "";
    }

    public void setRecordingHint(boolean hint) {
    }

    public void setAutoExposureLock(boolean enabled) {
        this.camera_settings.ae_lock = enabled;
        this.camera_settings.setAutoExposureLock(this.previewBuilder);
        try {
            setRepeatingRequest();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public boolean getAutoExposureLock() {
        if (this.previewBuilder.get(CaptureRequest.CONTROL_AE_LOCK) == null) {
            return false;
        }
        return ((Boolean) this.previewBuilder.get(CaptureRequest.CONTROL_AE_LOCK)).booleanValue();
    }

    public void setRotation(int rotation) {
        this.camera_settings.rotation = rotation;
    }

    public void setLocationInfo(Location location) {
        this.camera_settings.location = location;
    }

    public void removeLocationInfo() {
        this.camera_settings.location = null;
    }

    public void enableShutterSound(boolean enabled) {
        this.sounds_enabled = enabled;
    }

    private Rect getViewableRect() {
        if (this.previewBuilder != null) {
            Rect crop_rect = (Rect) this.previewBuilder.get(CaptureRequest.SCALER_CROP_REGION);
            if (crop_rect != null) {
                return crop_rect;
            }
        }
        Rect sensor_rect = (Rect) this.characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        sensor_rect.right -= sensor_rect.left;
        sensor_rect.left = 0;
        sensor_rect.bottom -= sensor_rect.top;
        sensor_rect.top = 0;
        return sensor_rect;
    }

    private Rect convertRectToCamera2(Rect crop_rect, Rect rect) {
        int right = (int) (((double) crop_rect.left) + (((double) (crop_rect.width() - 1)) * (((double) (rect.right + 1000)) / 2000.0d)));
        int top = (int) (((double) crop_rect.top) + (((double) (crop_rect.height() - 1)) * (((double) (rect.top + 1000)) / 2000.0d)));
        int bottom = (int) (((double) crop_rect.top) + (((double) (crop_rect.height() - 1)) * (((double) (rect.bottom + 1000)) / 2000.0d)));
        int left = Math.max((int) (((double) crop_rect.left) + (((double) (crop_rect.width() - 1)) * (((double) (rect.left + 1000)) / 2000.0d))), crop_rect.left);
        right = Math.max(right, crop_rect.left);
        top = Math.max(top, crop_rect.top);
        bottom = Math.max(bottom, crop_rect.top);
        return new Rect(Math.min(left, crop_rect.right), Math.min(top, crop_rect.bottom), Math.min(right, crop_rect.right), Math.min(bottom, crop_rect.bottom));
    }

    private MeteringRectangle convertAreaToMeteringRectangle(Rect sensor_rect, Area area) {
        return new MeteringRectangle(convertRectToCamera2(sensor_rect, area.rect), area.weight);
    }

    private Rect convertRectFromCamera2(Rect crop_rect, Rect camera2_rect) {
        int right = ((int) (2000.0d * (((double) (camera2_rect.right - crop_rect.left)) / ((double) (crop_rect.width() - 1))))) + NotificationManagerCompat.IMPORTANCE_UNSPECIFIED;
        int top = ((int) (2000.0d * (((double) (camera2_rect.top - crop_rect.top)) / ((double) (crop_rect.height() - 1))))) + NotificationManagerCompat.IMPORTANCE_UNSPECIFIED;
        int bottom = ((int) (2000.0d * (((double) (camera2_rect.bottom - crop_rect.top)) / ((double) (crop_rect.height() - 1))))) + NotificationManagerCompat.IMPORTANCE_UNSPECIFIED;
        int left = Math.max(((int) (2000.0d * (((double) (camera2_rect.left - crop_rect.left)) / ((double) (crop_rect.width() - 1))))) + NotificationManagerCompat.IMPORTANCE_UNSPECIFIED, NotificationManagerCompat.IMPORTANCE_UNSPECIFIED);
        right = Math.max(right, NotificationManagerCompat.IMPORTANCE_UNSPECIFIED);
        top = Math.max(top, NotificationManagerCompat.IMPORTANCE_UNSPECIFIED);
        bottom = Math.max(bottom, NotificationManagerCompat.IMPORTANCE_UNSPECIFIED);
        return new Rect(Math.min(left, 1000), Math.min(top, 1000), Math.min(right, 1000), Math.min(bottom, 1000));
    }

    private Area convertMeteringRectangleToArea(Rect sensor_rect, MeteringRectangle metering_rectangle) {
        return new Area(convertRectFromCamera2(sensor_rect, metering_rectangle.getRect()), metering_rectangle.getMeteringWeight());
    }

    private Face convertFromCameraFace(Rect sensor_rect, android.hardware.camera2.params.Face camera2_face) {
        return new Face(camera2_face.getScore(), convertRectFromCamera2(sensor_rect, camera2_face.getBounds()));
    }

    public boolean setFocusAndMeteringArea(List<Area> areas) {
        int i;
        Rect sensor_rect = getViewableRect();
        boolean has_focus = false;
        boolean has_metering = false;
        if (((Integer) this.characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF)).intValue() > 0) {
            has_focus = do_af_trigger_for_continuous;
            this.camera_settings.af_regions = new MeteringRectangle[areas.size()];
            i = 0;
            for (Area area : areas) {
                int i2 = i + 1;
                this.camera_settings.af_regions[i] = convertAreaToMeteringRectangle(sensor_rect, area);
                i = i2;
            }
            this.camera_settings.setAFRegions(this.previewBuilder);
        } else {
            this.camera_settings.af_regions = null;
        }
        if (((Integer) this.characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE)).intValue() > 0) {
            has_metering = do_af_trigger_for_continuous;
            this.camera_settings.ae_regions = new MeteringRectangle[areas.size()];
            i = 0;
            int i2 = 0;
            for (Area area2 : areas) {
                i2 = i + 1;
                this.camera_settings.ae_regions[i] = convertAreaToMeteringRectangle(sensor_rect, area2);
                i = i2;
            }
            this.camera_settings.setAERegions(this.previewBuilder);
        } else {
            this.camera_settings.ae_regions = null;
        }
        if (has_focus || has_metering) {
            try {
                setRepeatingRequest();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
        return has_focus;
    }

    public void clearFocusAndMetering() {
        Rect sensor_rect = getViewableRect();
        boolean has_focus = false;
        boolean has_metering = false;
        if (sensor_rect.width() <= 0 || sensor_rect.height() <= 0) {
            this.camera_settings.af_regions = null;
            this.camera_settings.ae_regions = null;
        } else {
            if (((Integer) this.characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF)).intValue() > 0) {
                has_focus = do_af_trigger_for_continuous;
                this.camera_settings.af_regions = new MeteringRectangle[1];
                this.camera_settings.af_regions[0] = new MeteringRectangle(0, 0, sensor_rect.width() - 1, sensor_rect.height() - 1, 0);
                this.camera_settings.setAFRegions(this.previewBuilder);
            } else {
                this.camera_settings.af_regions = null;
            }
            if (((Integer) this.characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE)).intValue() > 0) {
                has_metering = do_af_trigger_for_continuous;
                this.camera_settings.ae_regions = new MeteringRectangle[1];
                this.camera_settings.ae_regions[0] = new MeteringRectangle(0, 0, sensor_rect.width() - 1, sensor_rect.height() - 1, 0);
                this.camera_settings.setAERegions(this.previewBuilder);
            } else {
                this.camera_settings.ae_regions = null;
            }
        }
        if (has_focus || has_metering) {
            try {
                setRepeatingRequest();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    public List<Area> getFocusAreas() {
        List<Area> list = null;
        int i = 0;
        if (((Integer) this.characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF)).intValue() != 0) {
            MeteringRectangle[] metering_rectangles = (MeteringRectangle[]) this.previewBuilder.get(CaptureRequest.CONTROL_AF_REGIONS);
            if (metering_rectangles != null) {
                Rect sensor_rect = getViewableRect();
                this.camera_settings.af_regions[0] = new MeteringRectangle(0, 0, sensor_rect.width() - 1, sensor_rect.height() - 1, 0);
                if (!(metering_rectangles.length == 1 && metering_rectangles[0].getRect().left == 0 && metering_rectangles[0].getRect().top == 0 && metering_rectangles[0].getRect().right == sensor_rect.width() - 1 && metering_rectangles[0].getRect().bottom == sensor_rect.height() - 1)) {
                    list = new ArrayList();
                    int length = metering_rectangles.length;
                    while (i < length) {
                        list.add(convertMeteringRectangleToArea(sensor_rect, metering_rectangles[i]));
                        i++;
                    }
                }
            }
        }
        return list;
    }

    public List<Area> getMeteringAreas() {
        List<Area> list = null;
        if (((Integer) this.characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE)).intValue() != 0) {
            MeteringRectangle[] metering_rectangles = (MeteringRectangle[]) this.previewBuilder.get(CaptureRequest.CONTROL_AE_REGIONS);
            if (metering_rectangles != null) {
                Rect sensor_rect = getViewableRect();
                if (!(metering_rectangles.length == 1 && metering_rectangles[0].getRect().left == 0 && metering_rectangles[0].getRect().top == 0 && metering_rectangles[0].getRect().right == sensor_rect.width() - 1 && metering_rectangles[0].getRect().bottom == sensor_rect.height() - 1)) {
                    list = new ArrayList();
                    for (MeteringRectangle metering_rectangle : metering_rectangles) {
                        list.add(convertMeteringRectangleToArea(sensor_rect, metering_rectangle));
                    }
                }
            }
        }
        return list;
    }

    public boolean supportsAutoFocus() {
        if (this.previewBuilder.get(CaptureRequest.CONTROL_AF_MODE) == null) {
            return false;
        }
        int focus_mode = ((Integer) this.previewBuilder.get(CaptureRequest.CONTROL_AF_MODE)).intValue();
        return (focus_mode == 1 || focus_mode == 2) ? do_af_trigger_for_continuous : false;
    }

    public boolean focusIsContinuous() {
        if (this.previewBuilder == null || this.previewBuilder.get(CaptureRequest.CONTROL_AF_MODE) == null) {
            return false;
        }
        int focus_mode = ((Integer) this.previewBuilder.get(CaptureRequest.CONTROL_AF_MODE)).intValue();
        return (focus_mode == 4 || focus_mode == 3) ? do_af_trigger_for_continuous : false;
    }

    public boolean focusIsVideo() {
        if (this.previewBuilder.get(CaptureRequest.CONTROL_AF_MODE) == null) {
            return false;
        }
        return ((Integer) this.previewBuilder.get(CaptureRequest.CONTROL_AF_MODE)).intValue() == 3 ? do_af_trigger_for_continuous : false;
    }

    public void setPreviewDisplay(SurfaceHolder holder) throws CameraControllerException {
        throw new RuntimeException();
    }

    public void setPreviewTexture(SurfaceTexture texture) throws CameraControllerException {
        if (this.texture != null) {
            throw new RuntimeException();
        }
        this.texture = texture;
    }

    private void setRepeatingRequest() throws CameraAccessException {
        setRepeatingRequest(this.previewBuilder.build());
    }

    private void setRepeatingRequest(CaptureRequest request) throws CameraAccessException {
        if (this.camera != null && this.captureSession != null) {
            this.captureSession.setRepeatingRequest(request, this.previewCaptureCallback, this.handler);
        }
    }

    private void capture() throws CameraAccessException {
        capture(this.previewBuilder.build());
    }

    private void capture(CaptureRequest request) throws CameraAccessException {
        if (this.camera != null && this.captureSession != null) {
            this.captureSession.capture(request, this.previewCaptureCallback, this.handler);
        }
    }

    private void createPreviewRequest() {
        if (this.camera != null) {
            try {
                this.previewBuilder = this.camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                this.previewBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, Integer.valueOf(1));
                this.camera_settings.setupBuilder(this.previewBuilder, false);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    public class AnonymousClass2MyStateCallback extends CameraCaptureSession.StateCallback {
        private boolean callback_done;
        private MediaRecorder video_recorder;

        public AnonymousClass2MyStateCallback(MediaRecorder video_recorder) {
            this.video_recorder = video_recorder;
        }

        public void onConfigured(@NonNull CameraCaptureSession session) {
            if (CameraController2.this.camera == null) {
                synchronized (CameraController2.this.create_capture_session_lock) {
                    this.callback_done = CameraController2.do_af_trigger_for_continuous;
                    CameraController2.this.create_capture_session_lock.notifyAll();
                }
                return;
            }
            CameraController2.this.captureSession = session;
            CameraController2.this.previewBuilder.addTarget(CameraController2.this.getPreviewSurface());
            if (video_recorder != null) {
                CameraController2.this.previewBuilder.addTarget(video_recorder.getSurface());
            }
            try {
                CameraController2.this.setRepeatingRequest();
            } catch (CameraAccessException e) {
                e.printStackTrace();
                CameraController2.this.captureSession = null;
            }
            synchronized (CameraController2.this.create_capture_session_lock) {
                this.callback_done = CameraController2.do_af_trigger_for_continuous;
                CameraController2.this.create_capture_session_lock.notifyAll();
            }
        }

        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            synchronized (CameraController2.this.create_capture_session_lock) {
                this.callback_done = CameraController2.do_af_trigger_for_continuous;
                CameraController2.this.create_capture_session_lock.notifyAll();
            }
        }
    }


    private Surface getPreviewSurface() {
        return this.surface_texture;
    }

    private void createCaptureSession(final MediaRecorder video_recorder) throws CameraControllerException {
        if (this.previewBuilder == null) {
            throw new RuntimeException();
        } else if (this.camera != null) {
            AnonymousClass2MyStateCallback myStateCallback = null;
            Surface preview_surface;
            List<Surface> surfaces;
            if (this.captureSession != null) {
                this.captureSession.close();
                this.captureSession = null;
            }
            if (video_recorder != null) {
                try {
                    closePictureImageReader();
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new CameraControllerException();
                }
            }
            createPictureImageReader();
            if (this.texture != null) {
                if (this.preview_width == 0 || this.preview_height == 0) {
                    throw new RuntimeException();
                }
                this.texture.setDefaultBufferSize(this.preview_width, this.preview_height);
                if (this.surface_texture != null) {
                    this.previewBuilder.removeTarget(this.surface_texture);
                }
                this.surface_texture = new Surface(this.texture);
            }
            if (video_recorder != null) {
                myStateCallback = new AnonymousClass2MyStateCallback(video_recorder);
//                myStateCallback = new CameraCaptureSession.StateCallback() {
//                    private boolean callback_done;
//
//                    public void onConfigured(@NonNull CameraCaptureSession session) {
//                        if (CameraController2.this.camera == null) {
//                            synchronized (CameraController2.this.create_capture_session_lock) {
//                                this.callback_done = CameraController2.do_af_trigger_for_continuous;
//                                CameraController2.this.create_capture_session_lock.notifyAll();
//                            }
//                            return;
//                        }
//                        CameraController2.this.captureSession = session;
//                        CameraController2.this.previewBuilder.addTarget(CameraController2.this.getPreviewSurface());
//                        if (video_recorder != null) {
//                            CameraController2.this.previewBuilder.addTarget(video_recorder.getSurface());
//                        }
//                        try {
//                            CameraController2.this.setRepeatingRequest();
//                        } catch (CameraAccessException e) {
//                            e.printStackTrace();
//                            CameraController2.this.captureSession = null;
//                        }
//                        synchronized (CameraController2.this.create_capture_session_lock) {
//                            this.callback_done = CameraController2.do_af_trigger_for_continuous;
//                            CameraController2.this.create_capture_session_lock.notifyAll();
//                        }
//                    }
//
//                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
//                        synchronized (CameraController2.this.create_capture_session_lock) {
//                            this.callback_done = CameraController2.do_af_trigger_for_continuous;
//                            CameraController2.this.create_capture_session_lock.notifyAll();
//                        }
//                    }
//                };
                preview_surface = getPreviewSurface();
            } else {
                //TODO 未修改的
//                myStateCallback =;
                preview_surface = getPreviewSurface();
            }
            if (video_recorder != null) {
                surfaces = Arrays.asList(new Surface[]{preview_surface, video_recorder.getSurface()});
            } else if (this.imageReaderRaw != null) {
                surfaces = Arrays.asList(new Surface[]{preview_surface, this.imageReader.getSurface(), this.imageReaderRaw.getSurface()});
            } else {
                surfaces = Arrays.asList(new Surface[]{preview_surface, this.imageReader.getSurface()});
            }
            try {
                this.camera.createCaptureSession(surfaces, myStateCallback, this.handler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            synchronized (this.create_capture_session_lock) {
                while (!myStateCallback.callback_done) {
                    try {
                        this.create_capture_session_lock.wait();
                    } catch (InterruptedException e2) {
                        e2.printStackTrace();
                    }
                }
            }
            if (this.captureSession == null) {
                throw new CameraControllerException();
            }
        }
    }

    public void startPreview() throws CameraControllerException {
        if (this.captureSession != null) {
            try {
                setRepeatingRequest();
                return;
            } catch (CameraAccessException e) {
                e.printStackTrace();
                throw new CameraControllerException();
            }
        }
        createCaptureSession(null);
    }

    public void stopPreview() {
        if (this.camera != null && this.captureSession != null) {
            try {
                this.captureSession.stopRepeating();
                this.captureSession.close();
                this.captureSession = null;
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            if (this.camera_settings.has_face_detect_mode) {
                this.camera_settings.has_face_detect_mode = false;
                this.camera_settings.setFaceDetectMode(this.previewBuilder);
            }
        }
    }

    public boolean startFaceDetection() {
        if (this.previewBuilder.get(CaptureRequest.STATISTICS_FACE_DETECT_MODE) != null && ((Integer) this.previewBuilder.get(CaptureRequest.STATISTICS_FACE_DETECT_MODE)).intValue() != 0) {
            return false;
        }
        if (this.supports_face_detect_mode_full) {
            this.camera_settings.has_face_detect_mode = do_af_trigger_for_continuous;
            this.camera_settings.face_detect_mode = 2;
        } else if (this.supports_face_detect_mode_simple) {
            this.camera_settings.has_face_detect_mode = do_af_trigger_for_continuous;
            this.camera_settings.face_detect_mode = 1;
        } else {
            Log.e(TAG, "startFaceDetection() called but face detection not available");
            return false;
        }
        this.camera_settings.setFaceDetectMode(this.previewBuilder);
        try {
            setRepeatingRequest();
            return false;
        } catch (CameraAccessException e) {
            e.printStackTrace();
            return do_af_trigger_for_continuous;
        }
    }

    public void setFaceDetectionListener(FaceDetectionListener listener) {
        this.face_detection_listener = listener;
    }

    public void autoFocus(AutoFocusCallback cb, boolean capture_follows_autofocus_hint) {
        this.fake_precapture_torch_focus_performed = false;
        if (this.camera == null || this.captureSession == null) {
            cb.onAutoFocus(false);
            return;
        }
        Integer focus_mode = (Integer) this.previewBuilder.get(CaptureRequest.CONTROL_AF_MODE);
        if (focus_mode == null) {
            cb.onAutoFocus(do_af_trigger_for_continuous);
        } else if (this.use_fake_precapture_mode && focus_mode.intValue() == 4) {
            this.capture_follows_autofocus_hint = capture_follows_autofocus_hint;
            this.autofocus_cb = cb;
        } else {
            Builder afBuilder = this.previewBuilder;
            this.state = 1;
            this.precapture_state_change_time_ms = -1;
            this.capture_follows_autofocus_hint = capture_follows_autofocus_hint;
            this.autofocus_cb = cb;
            try {
                if (this.use_fake_precapture_mode && !this.camera_settings.has_iso) {
                    boolean want_flash = false;
                    if (this.camera_settings.flash_value.equals("flash_auto") || this.camera_settings.flash_value.equals("flash_frontscreen_auto")) {
                        if (fireAutoFlash()) {
                            want_flash = do_af_trigger_for_continuous;
                        }
                    } else if (this.camera_settings.flash_value.equals("flash_on")) {
                        want_flash = do_af_trigger_for_continuous;
                    }
                    if (want_flash) {
                        afBuilder.set(CaptureRequest.CONTROL_AE_MODE, Integer.valueOf(1));
                        afBuilder.set(CaptureRequest.FLASH_MODE, Integer.valueOf(2));
                        this.test_fake_flash_focus++;
                        this.fake_precapture_torch_focus_performed = do_af_trigger_for_continuous;
                        setRepeatingRequest(afBuilder.build());
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                afBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, Integer.valueOf(0));
                setRepeatingRequest(afBuilder.build());
                afBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, Integer.valueOf(1));
                capture(afBuilder.build());
            } catch (CameraAccessException e2) {
                e2.printStackTrace();
                this.state = 0;
                this.precapture_state_change_time_ms = -1;
                this.autofocus_cb.onAutoFocus(false);
                this.autofocus_cb = null;
                this.capture_follows_autofocus_hint = false;
            }
            afBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, Integer.valueOf(0));
        }
    }

    public void setCaptureFollowAutofocusHint(boolean capture_follows_autofocus_hint) {
        this.capture_follows_autofocus_hint = capture_follows_autofocus_hint;
    }

    public void cancelAutoFocus() {
        if (this.camera != null && this.captureSession != null) {
            this.previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, Integer.valueOf(2));
            try {
                capture();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            this.previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, Integer.valueOf(0));
            this.autofocus_cb = null;
            this.capture_follows_autofocus_hint = false;
            this.state = 0;
            this.precapture_state_change_time_ms = -1;
            try {
                setRepeatingRequest();
            } catch (CameraAccessException e2) {
                e2.printStackTrace();
            }
        }
    }

    public void setContinuousFocusMoveCallback(ContinuousFocusMoveCallback cb) {
        this.continuous_focus_move_callback = cb;
    }

    public static double getScaleForExposureTime(long exposure_time, long fixed_exposure_time, long scaled_exposure_time, double full_exposure_time_scale) {
        double alpha = ((double) (exposure_time - fixed_exposure_time)) / ((double) (scaled_exposure_time - fixed_exposure_time));
        if (alpha < 0.0d) {
            alpha = 0.0d;
        } else if (alpha > 1.0d) {
            alpha = 1.0d;
        }
        return (1.0d - alpha) + (alpha * full_exposure_time_scale);
    }

    private void takePictureAfterPrecapture() {
        if (this.want_expo_bracketing) {
            takePictureBurstExpoBracketing();
        } else if (this.camera != null && this.captureSession != null) {
            try {
                Builder stillBuilder = this.camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                stillBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, Integer.valueOf(2));
                stillBuilder.setTag(RequestTag.CAPTURE);
                this.camera_settings.setupBuilder(stillBuilder, do_af_trigger_for_continuous);
                if (this.use_fake_precapture_mode && this.fake_precapture_torch_performed) {
                    stillBuilder.set(CaptureRequest.CONTROL_AE_MODE, Integer.valueOf(1));
                    stillBuilder.set(CaptureRequest.FLASH_MODE, Integer.valueOf(2));
                    this.test_fake_flash_photo++;
                }
                if (!this.camera_settings.has_iso && this.optimise_ae_for_dro && this.capture_result_has_exposure_time && (this.camera_settings.flash_value.equals("flash_off") || this.camera_settings.flash_value.equals("flash_auto") || this.camera_settings.flash_value.equals("flash_frontscreen_auto"))) {
                    double full_exposure_time_scale = Math.pow(2.0d, -0.5d);
                    long exposure_time = this.capture_result_exposure_time;
                    if (exposure_time <= 16666666) {
                        Range<Long> exposure_time_range = this.characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
                        if (exposure_time_range != null) {
                            double exposure_time_scale = getScaleForExposureTime(exposure_time, 16666666, 8333333, full_exposure_time_scale);
                            long min_exposure_time = (exposure_time_range.getLower()).longValue();
                            long max_exposure_time = (exposure_time_range.getUpper()).longValue();
                            exposure_time = (long) (((double) exposure_time) * exposure_time_scale);
                            if (exposure_time < min_exposure_time) {
                                exposure_time = min_exposure_time;
                            }
                            if (exposure_time > max_exposure_time) {
                                exposure_time = max_exposure_time;
                            }
                            stillBuilder.set(CaptureRequest.CONTROL_AE_MODE, Integer.valueOf(0));
                            if (this.capture_result_has_iso) {
                                stillBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, Integer.valueOf(this.capture_result_iso));
                            } else {
                                stillBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, Integer.valueOf(800));
                            }
                            if (this.capture_result_has_frame_duration) {
                                stillBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, Long.valueOf(this.capture_result_frame_duration));
                            } else {
                                stillBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, Long.valueOf(CameraController.EXPOSURE_TIME_DEFAULT));
                            }
                            stillBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, Long.valueOf(exposure_time));
                        }
                    }
                }
                clearPending();
                stillBuilder.addTarget(getPreviewSurface());
                stillBuilder.addTarget(this.imageReader.getSurface());
                if (this.imageReaderRaw != null) {
                    stillBuilder.addTarget(this.imageReaderRaw.getSurface());
                }
                this.captureSession.stopRepeating();
                if (this.jpeg_cb != null) {
                    this.jpeg_cb.onStarted();
                }
                this.captureSession.capture(stillBuilder.build(), this.previewCaptureCallback, this.handler);
                if (this.sounds_enabled) {
                    this.media_action_sound.play(0);
                }
            } catch (CameraAccessException e) {
                e.printStackTrace();
                this.jpeg_cb = null;
                if (this.take_picture_error_cb != null) {
                    this.take_picture_error_cb.onError();
                    this.take_picture_error_cb = null;
                }
            }
        }
    }

    private void takePictureBurstExpoBracketing() {
        if (this.camera != null && this.captureSession != null) {
            try {
                int i;
                long exposure_time;
                double this_scale;
                int j;
                Builder stillBuilder = this.camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                stillBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, Integer.valueOf(2));
                this.camera_settings.setupBuilder(stillBuilder, do_af_trigger_for_continuous);
                clearPending();
                stillBuilder.addTarget(getPreviewSurface());
                stillBuilder.addTarget(this.imageReader.getSurface());
                List<CaptureRequest> requests = new ArrayList();
                stillBuilder.set(CaptureRequest.CONTROL_AE_MODE, Integer.valueOf(0));
                if (this.use_fake_precapture_mode && this.fake_precapture_torch_performed) {
                    stillBuilder.set(CaptureRequest.FLASH_MODE, Integer.valueOf(2));
                    this.test_fake_flash_photo++;
                }
                if (this.camera_settings.has_iso) {
                    stillBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, Integer.valueOf(this.camera_settings.iso));
                } else if (this.capture_result_has_iso) {
                    stillBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, Integer.valueOf(this.capture_result_iso));
                } else {
                    stillBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, Integer.valueOf(800));
                }
                if (this.capture_result_has_frame_duration) {
                    stillBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, Long.valueOf(this.capture_result_frame_duration));
                } else {
                    stillBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, Long.valueOf(CameraController.EXPOSURE_TIME_DEFAULT));
                }
                long base_exposure_time = CameraController.EXPOSURE_TIME_DEFAULT;
                if (this.camera_settings.has_iso) {
                    base_exposure_time = this.camera_settings.exposure_time;
                } else if (this.capture_result_has_exposure_time) {
                    base_exposure_time = this.capture_result_exposure_time;
                }
                int n_half_images = this.expo_bracketing_n_images / 2;
                long min_exposure_time = base_exposure_time;
                long max_exposure_time = base_exposure_time;
                double scale = Math.pow(2.0d, this.expo_bracketing_stops / ((double) n_half_images));
                Range<Long> exposure_time_range = (Range) this.characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
                if (exposure_time_range != null) {
                    min_exposure_time = ((Long) exposure_time_range.getLower()).longValue();
                    max_exposure_time = ((Long) exposure_time_range.getUpper()).longValue();
                }
                for (i = 0; i < n_half_images; i++) {
                    exposure_time = base_exposure_time;
                    if (exposure_time_range != null) {
                        this_scale = scale;
                        for (j = i; j < n_half_images - 1; j++) {
                            this_scale *= scale;
                        }
                        exposure_time = (long) (((double) exposure_time) / this_scale);
                        if (exposure_time < min_exposure_time) {
                            exposure_time = min_exposure_time;
                        }
                        stillBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, Long.valueOf(exposure_time));
                        requests.add(stillBuilder.build());
                    }
                }
                stillBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, Long.valueOf(base_exposure_time));
                requests.add(stillBuilder.build());
                for (i = 0; i < n_half_images; i++) {
                    exposure_time = base_exposure_time;
                    if (exposure_time_range != null) {
                        this_scale = scale;
                        for (j = 0; j < i; j++) {
                            this_scale *= scale;
                        }
                        exposure_time = (long) (((double) exposure_time) * this_scale);
                        if (exposure_time > max_exposure_time) {
                            exposure_time = max_exposure_time;
                        }
                        stillBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, Long.valueOf(exposure_time));
                        if (i == n_half_images - 1) {
                            stillBuilder.setTag(RequestTag.CAPTURE);
                        }
                        requests.add(stillBuilder.build());
                    }
                }
                this.n_burst = requests.size();
                this.captureSession.stopRepeating();
                if (this.jpeg_cb != null) {
                    this.jpeg_cb.onStarted();
                }
                if (this.use_expo_fast_burst) {
                    this.captureSession.captureBurst(requests, this.previewCaptureCallback, this.handler);
                } else {
                    this.burst_capture_requests = requests;
                    this.burst_start_ms = System.currentTimeMillis();
                    this.captureSession.capture((CaptureRequest) requests.get(0), this.previewCaptureCallback, this.handler);
                }
                if (this.sounds_enabled) {
                    this.media_action_sound.play(0);
                }
            } catch (CameraAccessException e) {
                e.printStackTrace();
                this.jpeg_cb = null;
                if (this.take_picture_error_cb != null) {
                    this.take_picture_error_cb.onError();
                    this.take_picture_error_cb = null;
                }
            }
        }
    }

    private void runPrecapture() {
        try {
            Builder precaptureBuilder = this.camera.createCaptureRequest(2);
            precaptureBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, Integer.valueOf(2));
            this.camera_settings.setupBuilder(precaptureBuilder, false);
            precaptureBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, Integer.valueOf(0));
            precaptureBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, Integer.valueOf(0));
            precaptureBuilder.addTarget(getPreviewSurface());
            this.state = 2;
            this.precapture_state_change_time_ms = System.currentTimeMillis();
            this.captureSession.capture(precaptureBuilder.build(), this.previewCaptureCallback, this.handler);
            this.captureSession.setRepeatingRequest(precaptureBuilder.build(), this.previewCaptureCallback, this.handler);
            precaptureBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, Integer.valueOf(1));
            this.captureSession.capture(precaptureBuilder.build(), this.previewCaptureCallback, this.handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            this.jpeg_cb = null;
            if (this.take_picture_error_cb != null) {
                this.take_picture_error_cb.onError();
                this.take_picture_error_cb = null;
            }
        }
    }

    private void runFakePrecapture() {
        String access$3000 = this.camera_settings.flash_value;
        switch (access$3000.hashCode()) {
            case -1524012984:
                if (access$3000.equals("flash_frontscreen_auto")) {
                    this.previewBuilder.set(CaptureRequest.CONTROL_AE_MODE, Integer.valueOf(1));
                    this.previewBuilder.set(CaptureRequest.FLASH_MODE, Integer.valueOf(2));
                    this.test_fake_flash_precapture++;
                    this.fake_precapture_torch_performed = do_af_trigger_for_continuous;
                    break;
                }
                break;
            case -1195303778:
                if (access$3000.equals("flash_auto")) {
                    this.previewBuilder.set(CaptureRequest.CONTROL_AE_MODE, Integer.valueOf(1));
                    this.previewBuilder.set(CaptureRequest.FLASH_MODE, Integer.valueOf(2));
                    this.test_fake_flash_precapture++;
                    this.fake_precapture_torch_performed = do_af_trigger_for_continuous;
                    break;
                }
                break;
            case -10523976:
                if (access$3000.equals("flash_frontscreen_on")) {
                    if (this.jpeg_cb != null) {
                        this.jpeg_cb.onFrontScreenTurnOn();
                        break;
                    }
                    break;
                }
                break;
            case 1625570446:
                if (access$3000.equals("flash_on")) {
                    if (this.jpeg_cb != null) {
                        this.jpeg_cb.onFrontScreenTurnOn();
                        break;
                    }
                }
                break;
        }

        this.state = 4;
        this.precapture_state_change_time_ms = System.currentTimeMillis();
        this.fake_precapture_turn_on_torch_id = null;
        try {
            CaptureRequest request = this.previewBuilder.build();
            if (this.fake_precapture_torch_performed) {
                this.fake_precapture_turn_on_torch_id = request;
            }
            setRepeatingRequest(request);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            this.jpeg_cb = null;
            if (this.take_picture_error_cb != null) {
                this.take_picture_error_cb.onError();
                this.take_picture_error_cb = null;
            }
        }
    }

    private boolean fireAutoFlash() {
        boolean z = do_af_trigger_for_continuous;
        long time_now = System.currentTimeMillis();
        if (this.fake_precapture_use_flash_time_ms == -1 || time_now - this.fake_precapture_use_flash_time_ms >= precapture_done_timeout_c) {
            String access$3000 = this.camera_settings.flash_value;
            int z2 = -1;
            switch (access$3000.hashCode()) {
                case -1524012984:
                    if (access$3000.equals("flash_frontscreen_auto")) {
                        z2 = do_af_trigger_for_continuous ? 1 : 0;
                        break;
                    }
                    break;
                case -1195303778:
                    if (access$3000.equals("flash_auto")) {
                        z2 = 0;
                        break;
                    }
                    break;
            }
            switch (z2) {
                case 0:
                    this.fake_precapture_use_flash = this.is_flash_required;
                    break;
                case 1:
                    int iso_threshold = this.camera_settings.flash_value.equals("flash_frontscreen_auto") ? 750 : 1000;
                    if (!this.capture_result_has_iso || this.capture_result_iso < iso_threshold) {
                        z = false;
                    }
                    this.fake_precapture_use_flash = z;
                    break;
                default:
                    this.fake_precapture_use_flash = false;
                    break;
            }
            if (this.fake_precapture_use_flash) {
                this.fake_precapture_use_flash_time_ms = time_now;
            } else {
                this.fake_precapture_use_flash_time_ms = -1;
            }
            return this.fake_precapture_use_flash;
        }
        this.fake_precapture_use_flash_time_ms = time_now;
        return this.fake_precapture_use_flash;
    }

    public void takePicture(PictureCallback picture, ErrorCallback error) {
        boolean auto_flash = false;
        if (this.camera == null || this.captureSession == null) {
            error.onError();
            return;
        }
        this.jpeg_cb = picture;
        if (this.imageReaderRaw != null) {
            this.raw_cb = picture;
        } else {
            this.raw_cb = null;
        }
        this.take_picture_error_cb = error;
        this.fake_precapture_torch_performed = false;
        if (this.ready_for_capture) {
        }
        if (this.camera_settings.has_iso || this.camera_settings.flash_value.equals("flash_off") || this.camera_settings.flash_value.equals("flash_torch")) {
            takePictureAfterPrecapture();
        } else if (this.use_fake_precapture_mode) {
            if (this.camera_settings.flash_value.equals("flash_auto") || this.camera_settings.flash_value.equals("flash_frontscreen_auto")) {
                auto_flash = do_af_trigger_for_continuous;
            }
            Integer flash_mode = (Integer) this.previewBuilder.get(CaptureRequest.FLASH_MODE);
            if (auto_flash && !fireAutoFlash()) {
                takePictureAfterPrecapture();
            } else if (flash_mode == null || flash_mode.intValue() != 2) {
                runFakePrecapture();
            } else {
                this.fake_precapture_torch_performed = do_af_trigger_for_continuous;
                this.test_fake_flash_precapture++;
                this.state = 5;
                this.precapture_state_change_time_ms = System.currentTimeMillis();
            }
        } else {
            boolean needs_flash = (this.capture_result_ae == null || this.capture_result_ae.intValue() == 2) ? false : do_af_trigger_for_continuous;
            if (!this.camera_settings.flash_value.equals("flash_auto") || needs_flash) {
                runPrecapture();
            } else {
                takePictureAfterPrecapture();
            }
        }
    }

    public void setDisplayOrientation(int degrees) {
        throw new RuntimeException();
    }

    public int getDisplayOrientation() {
        throw new RuntimeException();
    }

    public int getCameraOrientation() {
        return ((Integer) this.characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)).intValue();
    }

    public boolean isFrontFacing() {
        return ((Integer) this.characteristics.get(CameraCharacteristics.LENS_FACING)).intValue() == 0 ? do_af_trigger_for_continuous : false;
    }

    public void unlock() {
    }

    public void initVideoRecorderPrePrepare(MediaRecorder video_recorder) {
        if (this.sounds_enabled) {
            this.media_action_sound.play(2);
        }
    }

    public void initVideoRecorderPostPrepare(MediaRecorder video_recorder) throws CameraControllerException {
        try {
//            this.previewBuilder = this.camera.createCaptureRequest(3);
            this.previewBuilder = this.camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            this.previewBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, Integer.valueOf(3));
            this.camera_settings.setupBuilder(this.previewBuilder, false);
            createCaptureSession(video_recorder);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            throw new CameraControllerException();
        }
    }

    public void reconnect() throws CameraControllerException {
        if (this.sounds_enabled) {
            this.media_action_sound.play(3);
        }
        createPreviewRequest();
        createCaptureSession(null);
    }

    public String getParametersString() {
        return null;
    }

    public boolean captureResultIsAEScanning() {
        return this.capture_result_is_ae_scanning;
    }

    public boolean needsFlash() {
        return this.is_flash_required;
    }

    public boolean captureResultHasWhiteBalanceTemperature() {
        return this.capture_result_has_white_balance_rggb;
    }

    public int captureResultWhiteBalanceTemperature() {
        return convertRggbToTemperature(this.capture_result_white_balance_rggb);
    }

    public boolean captureResultHasIso() {
        return this.capture_result_has_iso;
    }

    public int captureResultIso() {
        return this.capture_result_iso;
    }

    public boolean captureResultHasExposureTime() {
        return this.capture_result_has_exposure_time;
    }

    public long captureResultExposureTime() {
        return this.capture_result_exposure_time;
    }
}
