package com.cy.camerathree;

/**
 * 权限
 * camera2
 */

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.things.device.TimeManager;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Calendar;

/**
 * Skeleton of an Android Things activity.
 * <p>
 * Android Things peripheral APIs are accessible through the class
 * PeripheralManagerService. For example, the snippet below will open a GPIO pin and
 * set it to HIGH:
 *
 * <pre>{@code
 * PeripheralManagerService service = new PeripheralManagerService();
 * mLedGpio = service.openGpio("BCM6");
 * mLedGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
 * mLedGpio.setValue(true);
 * }</pre>
 * <p>
 * For more complex peripherals, look for an existing user-space driver, or implement one if none
 * is available.
 *
 * @see <a href="https://github.com/androidthings/contrib-drivers#readme">https://github.com/androidthings/contrib-drivers#readme</a>
 */
public class MainActivity extends Activity {

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    //为了使照片竖直显示
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private CameraManager mCameraManager;//摄像头管理器
    private Handler childHandler;
    private String mCameraID;//摄像头Id 0 为后  1 为前
    private ImageReader mImageReader;
    private CameraCaptureSession mCameraCaptureSession;
    private CameraDevice mCameraDevice;
    private ImageView imageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /*
        自动校准系统时间
        导入依赖
        添加设置代码
         */
        try {
            TimeManager timeManager = TimeManager.getInstance();
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.YEAR, 2019);
            timeManager.setTime(calendar.getTimeInMillis());
        } catch (Exception e) {
            Log.e("SET_TIME", "SET_TIME 权限失效");
        }

        //有关设置全屏
//        Window w = getWindow();
//        w.setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
//                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        setContentView(R.layout.activity_main);

        findViewById(R.id.take).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("onClick", "onClick: 点击了拍照");
                //进行相机的连接
                openCamera();
            }
        });
        imageView = (ImageView) findViewById(R.id.iv_show_camera2_activity);
    }

    private void openCamera() {
        HandlerThread handlerThread = new HandlerThread("Camera2");
        handlerThread.start();
        childHandler = new Handler(handlerThread.getLooper());

        mImageReader = ImageReader.newInstance(imageView.getWidth(), imageView.getHeight(), ImageFormat.JPEG, 1);
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() { //处理临时照片
            @Override
            public void onImageAvailable(ImageReader reader) {
                mCameraDevice.close();//将相机关闭
                mCameraDevice = null;
                // 拿到拍照照片数据
                Image image = reader.acquireNextImage();
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                Log.d("onImageAvailable", "onImageAvailable: 获取照片byte数组");
                buffer.get(bytes);//由缓冲区存入字节数组
                final Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
//                bitmap = Bitmap.createScaledBitmap(bitmap, 10, 10, true);
                if (bitmap != null) {//在主线程中展示照片
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            imageView.setImageBitmap(bitmap);
                            Log.d("onImageAvailable:", "onImageAvailable: 设置进imageview");
                        }
                    });
                }
            }
        }, childHandler);

        if (mCameraManager == null)
            mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        String cameraIds[] = {};
        try {
            cameraIds = mCameraManager.getCameraIdList();
        } catch (CameraAccessException e) {
            Log.e("tsb", "Cam access exception getting IDs", e);
        }
        if (cameraIds.length < 1) {
            Log.e("tsb", "No cameras found");
            return;
        }
        mCameraID = cameraIds[0];
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
//                MyUtil.setToast(MainActivity.this, "没有照相机权限", false);
                Toast.makeText(MainActivity.this,"没有相机权限",Toast.LENGTH_SHORT).show();
                Log.d("openCamera", "openCamera: 没有相机权限");
                return;
            }
            mCameraManager.openCamera(mCameraID, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //该类用于接收相机的连接状态的更新
    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            //打开摄像头
            mCameraDevice = camera;
            //拍照
            takePicture();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            //关闭摄像头
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mImageReader.close();
            }
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            mCameraDevice = null;
            //有错误
//            MyUtil.setToast(MainActivity.this, "摄像头开启失败", false);
            Toast.makeText(MainActivity.this,"摄像头开启失败",Toast.LENGTH_SHORT).show();
        }
    };

    private void takePicture(){
        try {
            Log.d("takePicture", "takePicture: 已经打开摄像头");
            final CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            builder.addTarget(mImageReader.getSurface());
            // 自动对焦
//            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            //   打开闪光灯
//            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);//注意下
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            // 根据设备方向计算设置照片的方向
            builder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
            //CameraCaptureSession 是一个事务，用来向相机设备发送获取图像的请求。
            //有关创建一个捕获会话
            mCameraDevice.createCaptureSession(Arrays.asList(mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    if (mCameraDevice == null)
                        return;
                    try {
                        CaptureRequest mCaptureRequest = builder.build();
                        session.capture(mCaptureRequest, null, childHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
//                    MyUtil.setToast(MainActivity.this,"配置错误",false);
                    Toast.makeText(MainActivity.this,"配置错误",Toast.LENGTH_SHORT).show();
                }
            },childHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
}
