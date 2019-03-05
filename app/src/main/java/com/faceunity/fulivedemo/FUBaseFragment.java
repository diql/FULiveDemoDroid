package com.faceunity.fulivedemo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.opengl.EGL14;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import com.faceunity.FURenderer;
import com.faceunity.encoder.MediaAudioEncoder;
import com.faceunity.encoder.MediaEncoder;
import com.faceunity.encoder.MediaMuxerWrapper;
import com.faceunity.encoder.MediaVideoEncoder;
import com.faceunity.fulivedemo.renderer.CameraRenderer;
import com.faceunity.fulivedemo.ui.CameraFocus;
import com.faceunity.fulivedemo.ui.RecordBtn;
import com.faceunity.fulivedemo.ui.VerticalSeekBar;
import com.faceunity.fulivedemo.utils.NotchInScreenUtil;
import com.faceunity.fulivedemo.utils.ThreadHelper;
import com.faceunity.fulivedemo.utils.ToastUtil;
import com.faceunity.gles.core.GlUtil;
import com.faceunity.utils.BitmapUtil;
import com.faceunity.utils.Constant;
import com.faceunity.utils.MiscUtil;

import java.io.File;
import java.io.IOException;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.opengles.GL10;

/**
 * Base Activity, 主要封装FUBeautyActivity与FUEffectActivity的公用界面与方法
 * CameraRenderer相关回调实现
 * Created by tujh on 2018/1/31.
 */
public abstract class FUBaseFragment extends Fragment
        implements View.OnClickListener,
        CameraRenderer.OnRendererStatusListener,
        SensorEventListener,
        FURenderer.OnFUDebugListener,
        FURenderer.OnTrackingStatusChangedListener {
    public final static String TAG = FUBaseFragment.class.getSimpleName();

    protected ImageView mTopBackground;
    protected GLSurfaceView mGLSurfaceView;
    protected CameraRenderer mCameraRenderer;
    private volatile boolean isDoubleInputType = true;
    private ImageButton mCameraChange;
    private TextView mDebugText;
    private TextView mIsTrackingText;
    private TextView mEffectDescription;
    protected RecordBtn mTakePicBtn;
    protected ViewStub mBottomViewStub;
    protected CheckBox mHeightCheckBox;
    protected ImageView mHeightImg;
    protected ImageView mSelectDataBtn;
    private LinearLayout mLlLight;
    private VerticalSeekBar mVerticalSeekBar;
    private CameraFocus mCameraFocus;
    protected ConstraintLayout mClOperationView;
    protected ConstraintLayout mRootView;

    private SensorManager mSensorManager;
    private Sensor mSensor;
    private final Runnable mCameraFocusDismiss = new Runnable() {
        @Override
        public void run() {
            mCameraFocus.layout(0, 0, 0, 0);
            mLlLight.setVisibility(View.INVISIBLE);
        }
    };

    protected FURenderer mFURenderer;
    private byte[] mFuNV21Byte;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    protected volatile boolean mTakePicing = false;

    protected abstract void onCreate(View view);

    protected abstract FURenderer initFURenderer(Context context);

    // 默认全部使用对焦
    protected boolean showAutoFocus() {
        return true;
    }

    // FIXME: 2019/3/5 点击对焦.
//    @Override
//    public boolean onTouchEvent(MotionEvent event) {
//        if (super.onTouchEvent(event)) {
//            return true;
//        }
//
//        if (!showAutoFocus()) {
//            return false;
//        }
//        if (event.getPointerCount() == 1 && event.getAction() == MotionEvent.ACTION_DOWN) {
//            mCameraRenderer.handleFocus(event.getRawX(), event.getRawY());
//            mCameraFocus.showCameraFocus(event.getRawX(), event.getRawY());
//            mLlLight.setVisibility(View.VISIBLE);
//            mVerticalSeekBar.setProgress((int) (100 * mCameraRenderer.getExposureCompensation()));
//            mHandler.removeCallbacks(mCameraFocusDismiss);
//            mHandler.postDelayed(mCameraFocusDismiss, 1300);
//            return true;
//        }
//        return false;
//    }

    @Override
    public void onResume() {
        super.onResume();
        mCameraRenderer.onCreate();
        mCameraRenderer.onResume();
        mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
        mCameraRenderer.onPause();
        mCameraRenderer.onDestroy();
    }

    protected volatile boolean mIsNeedTakePic = false;
    private volatile long mStartTime = 0;

    private Runnable effectDescriptionHide = new Runnable() {
        @Override
        public void run() {
            mEffectDescription.setText("");
            mEffectDescription.setVisibility(View.INVISIBLE);
        }
    };

    protected void showDescription(int str, int time) {
        if (str == 0) {
            return;
        }
        mEffectDescription.removeCallbacks(effectDescriptionHide);
        mEffectDescription.setVisibility(View.VISIBLE);
        mEffectDescription.setText(str);
        mEffectDescription.postDelayed(effectDescriptionHide, time);
    }

    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~Sensor部分~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            if (Math.abs(x) > 3 || Math.abs(y) > 3) {
                if (Math.abs(x) > Math.abs(y)) {
                    mFURenderer.setTrackOrientation(x > 0 ? 0 : 180);
                } else {
                    mFURenderer.setTrackOrientation(y > 0 ? 90 : 270);
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~FURenderer信息回调~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    @Override
    public void onFpsChange(final double fps, final double renderTime) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mDebugText.setText(String.format(getString(R.string.fu_base_debug), mCameraRenderer.getCameraWidth(), mCameraRenderer.getCameraHeight(), (int) fps, (int) renderTime));
            }
        });
    }

    @Override
    public void onTrackingStatusChanged(final int status) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mIsTrackingText.setVisibility(status > 0 ? View.INVISIBLE : View.VISIBLE);
            }
        });
    }

    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~FURenderer调用部分~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        mFURenderer.onSurfaceCreated();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
    }

    @Override
    public int onDrawFrame(byte[] cameraNV21Byte, int cameraTextureId, int cameraWidth, int cameraHeight, float[] mtx, long timeStamp) {
        int fuTextureId = 0;
        if (isDoubleInputType) {
            fuTextureId = mFURenderer.onDrawFrame(cameraNV21Byte, cameraTextureId, cameraWidth, cameraHeight);
        } else if (cameraNV21Byte != null) {
            if (mFuNV21Byte == null || mFuNV21Byte.length != cameraNV21Byte.length) {
                mFuNV21Byte = new byte[cameraNV21Byte.length];
            }
            System.arraycopy(cameraNV21Byte, 0, mFuNV21Byte, 0, cameraNV21Byte.length);
            fuTextureId = mFURenderer.onDrawFrame(mFuNV21Byte, cameraWidth, cameraHeight);
        }
        sendRecordingData(fuTextureId, mtx, timeStamp / Constant.NANO_IN_ONE_MILLI_SECOND);
        checkPic(fuTextureId, mtx, cameraHeight, cameraWidth);
        return fuTextureId;
    }

    @Override
    public void onSurfaceDestroy() {
        mFURenderer.onSurfaceDestroyed();
    }

    @Override
    public void onCameraChange(int currentCameraType, int cameraOrientation) {
        mFURenderer.onCameraChange(currentCameraType, cameraOrientation);
        mVerticalSeekBar.setProgress((int) (100 * mCameraRenderer.getExposureCompensation()));
    }

    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~拍照录制部分~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    protected BitmapUtil.OnReadBitmapListener mOnReadBitmapListener = new BitmapUtil.OnReadBitmapListener() {

        @Override
        public void onReadBitmapListener(Bitmap bitmap) {
            String name = Constant.APP_NAME + "_" + MiscUtil.getCurrentDate() + ".jpg";
            String result = MiscUtil.saveBitmap(bitmap, Constant.photoFilePath, name);
            if (result != null) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ToastUtil.showToast(FUBaseFragment.this.getContext(), R.string.save_photo_success);
                    }
                });
                File resultFile = new File(result);
                sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(resultFile)));
            }
            mTakePicing = false;
        }
    };

    private MediaVideoEncoder mVideoEncoder;

    public void takePic() {
        if (mTakePicing) {
            return;
        }
        mIsNeedTakePic = true;
        mTakePicing = true;
    }

    private Runnable changeCamera = new Runnable() {
        @Override
        public void run() {
            mCameraRenderer.changeCamera();
        }
    };

    /**
     * 自定义 EGLContext
     */
    private static class ContextFactory implements GLSurfaceView.EGLContextFactory {

        private static double glVersion = 3.0;
        private static int EGL_CONTEXT_CLIENT_VERSION = 0x3098;

        @Override
        public EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig eglConfig) {
            Log.w(TAG, "creating OpenGL ES " + glVersion + " context");
            int[] attrib_list = {EGL_CONTEXT_CLIENT_VERSION, (int) glVersion, EGL10.EGL_NONE};
            // attempt to create a OpenGL ES 3.0 context
            EGLContext context = egl.eglCreateContext(display, eglConfig, EGL10.EGL_NO_CONTEXT, attrib_list);
            Log.d(TAG, "createContext: ctx:" + context);
            return context; // returns null if 3.0 is not supported;
        }

        @Override
        public void destroyContext(EGL10 egl, EGLDisplay display, EGLContext context) {
            if (!egl.eglDestroyContext(display, context)) {
                Log.e("DefaultContextFactory", "display:" + display + " context: " + context);
            }
        }
    }

    /**
     * 拍照
     *
     * @param textureId
     * @param mtx
     * @param texWidth
     * @param texHeight
     */
    protected void checkPic(int textureId, float[] mtx, final int texWidth, final int texHeight) {
        if (!mIsNeedTakePic) {
            return;
        }
        mIsNeedTakePic = false;
        BitmapUtil.glReadBitmap(textureId, mtx, GlUtil.IDENTITY_MATRIX, texWidth, texHeight, mOnReadBitmapListener);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.fu_base_back:
                onBackPressed();
                break;
            case R.id.fu_base_camera_change:
                mCameraChange.removeCallbacks(changeCamera);
                mCameraChange.post(changeCamera);
                break;
            default:
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FragmentActivity activity = getActivity();
        if (NotchInScreenUtil.hasNotch(activity)) {
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.activity_fu_base, container, false);

        FragmentActivity activity = getActivity();

        MiscUtil.checkPermission(activity);
        mGLSurfaceView = (GLSurfaceView) v.findViewById(R.id.fu_base_gl_surface);
        //        mGLSurfaceView.setEGLContextFactory(new ContextFactory());
        mGLSurfaceView.setEGLContextClientVersion(2);
        mCameraRenderer = new CameraRenderer(activity, mGLSurfaceView, this);
        mGLSurfaceView.setRenderer(mCameraRenderer);
        mGLSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        mTopBackground = (ImageView) v.findViewById(R.id.fu_base_top_background);
        mSensorManager = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        RadioGroup inputTypeRadioGroup = (RadioGroup) v.findViewById(R.id.fu_base_input_type_radio_group);
        inputTypeRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                switch (checkedId) {
                    case R.id.fu_base_input_type_double:
                        isDoubleInputType = true;
                        break;
                    case R.id.fu_base_input_type_single:
                        isDoubleInputType = false;
                        break;
                    default:
                }
                mFURenderer.changeInputType();
            }
        });

        mCameraChange = (ImageButton) v.findViewById(R.id.fu_base_camera_change);

        CheckBox debugBox = (CheckBox) v.findViewById(R.id.fu_base_debug);
        mDebugText = (TextView) v.findViewById(R.id.fu_base_debug_text);
        debugBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mDebugText.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            }
        });

        mIsTrackingText = (TextView) v.findViewById(R.id.fu_base_is_tracking_text);
        mEffectDescription = (TextView) v.findViewById(R.id.fu_base_effect_description);
        mTakePicBtn = (RecordBtn) v.findViewById(R.id.fu_base_take_pic);
        mTakePicBtn.setOnRecordListener(new RecordBtn.OnRecordListener() {
            @Override
            public void takePic() {
                FUBaseFragment.this.takePic();
            }

            @Override
            public void startRecord() {
                startRecording();
            }

            @Override
            public void stopRecord() {
                stopRecording();
            }
        });
        mClOperationView = (ConstraintLayout) v.findViewById(R.id.cl_operation_view);
        mRootView = (ConstraintLayout) v.findViewById(R.id.cl_root);
        mHeightCheckBox = (CheckBox) v.findViewById(R.id.fu_base_height);
        mHeightImg = (ImageView) v.findViewById(R.id.fu_base_height_img);
        mHeightImg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mHeightCheckBox.setChecked(!mHeightCheckBox.isChecked());
            }
        });
        mSelectDataBtn = (ImageView) v.findViewById(R.id.fu_base_select_data);
        mBottomViewStub = (ViewStub) v.findViewById(R.id.fu_base_bottom);
        mBottomViewStub.setInflatedId(R.id.fu_base_bottom);
        mLlLight = (LinearLayout) v.findViewById(R.id.photograph_light_layout);
        mVerticalSeekBar = (VerticalSeekBar) v.findViewById(R.id.photograph_light_seek);
        mCameraFocus = (CameraFocus) v.findViewById(R.id.photograph_focus);
        mVerticalSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mCameraRenderer.setExposureCompensation((float) progress / 100);
                mHandler.removeCallbacks(mCameraFocusDismiss);
                mHandler.postDelayed(mCameraFocusDismiss, 1300);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        mFURenderer = initFURenderer(getActivity());
        onCreate(v);

        return v;
    }

    /**
     * 发送录制数据
     *
     * @param texId
     * @param texMatrix
     * @param timeStamp
     */
    protected void sendRecordingData(int texId, final float[] texMatrix, final long timeStamp) {
        if (mVideoEncoder != null) {
            mVideoEncoder.frameAvailableSoon(texId, texMatrix, GlUtil.IDENTITY_MATRIX);
            if (mStartTime == 0) mStartTime = timeStamp;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mTakePicBtn.setSecond(timeStamp - mStartTime);
                }
            });
        }
    }

    private File mOutFile;

    /**
     * 录制封装回调
     */
    private final MediaEncoder.MediaEncoderListener mMediaEncoderListener = new MediaEncoder.MediaEncoderListener() {
        @Override
        public void onPrepared(final MediaEncoder encoder) {
            if (encoder instanceof MediaVideoEncoder) {
                final MediaVideoEncoder videoEncoder = (MediaVideoEncoder) encoder;
                mGLSurfaceView.queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        videoEncoder.setEglContext(EGL14.eglGetCurrentContext());
                        mVideoEncoder = videoEncoder;
                    }
                });
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mTakePicBtn.setSecond(0);
                    }
                });
            }

        }

        @Override
        public void onStopped(final MediaEncoder encoder) {
            mVideoEncoder = null;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.e(TAG, "stop encoder success");
                    ToastUtil.showToast(FUBaseFragment.this.getActivity(), R.string.save_video_success);
                    mTakePicBtn.setSecond(mStartTime = 0);
                    sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(mOutFile)));
                }
            });
        }
    };

    private MediaMuxerWrapper mMuxer;

    /**
     * 开始录制
     */
    private void startRecording() {
        try {
            String videoFileName = Constant.APP_NAME + "_" + MiscUtil.getCurrentDate() + ".mp4";
            mOutFile = new File(Constant.cameraFilePath, videoFileName);
            mMuxer = new MediaMuxerWrapper(mOutFile.getAbsolutePath());

            // for video capturing
            new MediaVideoEncoder(mMuxer, mMediaEncoderListener, mCameraRenderer.getCameraHeight(), mCameraRenderer.getCameraWidth());
            new MediaAudioEncoder(mMuxer, mMediaEncoderListener);

            mMuxer.prepare();
            mMuxer.startRecording();
        } catch (final IOException e) {
            Log.e(TAG, "startCapture:", e);
        }
    }

    /**
     * 停止录制
     */
    private void stopRecording() {
        if (mMuxer != null) {
            mMuxer.stopRecording();
        }
        System.gc();
    }

    ////////////////////////////////////////////////////////////////////////
    // 兼容性方法.
    ////////////////////////////////////////////////////////////////////////
    /**
     * 主线程任务
     *
     * @param r
     */
    public void runOnUiThread(Runnable r) {
        ThreadHelper.getInstance().runOnUiThread(r);
    }

    public void sendBroadcast(Intent intent) {
        Activity activity = getActivity();
        if (activity == null) {
            throw new NullPointerException("activity is null.");
        }
        activity.sendBroadcast(intent);
    }

    public void onBackPressed() {
        FragmentActivity activity = getActivity();
        if (activity == null) {
            Log.w(TAG, "onBackPressed: null.", new NullPointerException("activity is null."));
            return;
        }
        activity.onBackPressed();
    }
}
