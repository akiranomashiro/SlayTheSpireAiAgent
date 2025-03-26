// ScreenCaptureService.java
package com.example.aiagent;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class ScreenCaptureService extends Service {
    private static final String TAG = "ScreenCapture";
    private MediaProjection mMediaProjection;
    private ImageReader mImageReader;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private ExecutorService mProcessingThread = Executors.newFixedThreadPool(4);
    private int mScreenWidth, mScreenHeight;
    private final AtomicLong lastCaptureTime = new AtomicLong(0);
    private static final long CAPTURE_INTERVAL_MS = 5000; // 1秒间隔（可调整）
    private final IBinder mBinder = new LocalBinder();
    public class LocalBinder extends Binder {
        ScreenCaptureService getService() {
            return ScreenCaptureService.this;
        }
    }

    public interface OnImageProcessedListener {
        void onImageProcessed(Bitmap processedBitmap);
    }

    private OnImageProcessedListener listener;

    public void setOnImageProcessedListener(OnImageProcessedListener listener) {
        this.listener = listener;
    }

    @SuppressLint("WrongConstant")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundServiceWithNotification();

        if (mHandler == null) {
            Log.e(TAG, "Handler 初始化失败");
        } else {
            Log.d(TAG, "Handler 初始化成功");
        }

        if (mImageReader != null || mMediaProjection != null) {
            Log.w(TAG, "服务已在运行，跳过重复初始化");
            return START_STICKY;
        }

        // 初始化 OpenCV
        if (!OpenCVLoader.initLocal()) {
            Log.e(TAG, "OpenCV 初始化失败");
            stopSelf();
            return START_NOT_STICKY;
        }

        if (intent == null) {
            Log.e(TAG, "Service 启动 Intent 为 null");
            stopSelf();
            return START_NOT_STICKY;
        }

        Intent screenCaptureIntent = intent.getParcelableExtra("screen_capture_intent");
        if (screenCaptureIntent == null) {
            Log.e(TAG, "未找到 screen_capture_intent Extra");
            stopSelf();
            return START_NOT_STICKY;
        }

        // 获取 MediaProjection 权限令牌
        MediaProjectionManager projectionManager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mMediaProjection = projectionManager.getMediaProjection(Activity.RESULT_OK, screenCaptureIntent);

        if (mMediaProjection == null) {
            Log.e(TAG, "MediaProjection 初始化失败");
            stopSelf();
            return START_NOT_STICKY;
        }

        // 获取屏幕分辨率
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(metrics);
        mScreenWidth = metrics.widthPixels;
        mScreenHeight = metrics.heightPixels;

        // 配置 ImageReader
        mImageReader = ImageReader.newInstance(
                mScreenWidth, mScreenHeight, PixelFormat.RGBA_8888, 20
        );

        // 创建 VirtualDisplay
        VirtualDisplay virtualDisplay = mMediaProjection.createVirtualDisplay(
                "SlayTheSpire_Capture",
                mScreenWidth, mScreenHeight, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mImageReader.getSurface(), null, mHandler
        );

        // 设置图像监听
        mImageReader.setOnImageAvailableListener(reader -> {
            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image == null) return;

                long currentTime = System.currentTimeMillis();
                long lastTime = lastCaptureTime.get();

                // 时间间隔检查（原子操作保证线程安全）
                if (currentTime - lastTime >= CAPTURE_INTERVAL_MS) {
                    if (lastCaptureTime.compareAndSet(lastTime, currentTime)) {
                        // 提交处理任务
                        final Image finalImage = image;
                        mProcessingThread.execute(() -> processImage(finalImage));
                        image = null; // 标记已提交，避免重复关闭
                    }
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "获取图像失败: " + e.getMessage());
            } finally {
                // 未提交任务的图像立即关闭
                if (image != null) {
                    image.close();
                }
            }
        }, mHandler);

        return START_STICKY;
    }

    private void processImage(Image image) {
        try {
            // 将 Image 转换为 Bitmap
            Bitmap bitmap = imageToBitmap(image);

            // 图像预处理
            Bitmap processedBitmap = preprocessImage(bitmap);

            // 保存处理后的图像（测试用）
            saveBitmap(processedBitmap);

            callPythonFunction(processedBitmap);

            // 将处理后的图像传递给监听器
            if (listener != null) {
                listener.onImageProcessed(processedBitmap);
            }

        } catch (Exception e) {
            Log.e(TAG, "图像处理失败", e);
        } finally {
            image.close();
        }
    }

    // 将 Image 转换为 Bitmap
    private Bitmap imageToBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int width = image.getWidth();
        int height = image.getHeight();
        int pixelStride = planes[0].getPixelStride(); // 每个像素的字节数
        int rowStride = planes[0].getRowStride(); // 每行的字节数

        // 创建 ARGB_8888 格式的 Bitmap
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        // 确保缓冲区数据正确复制到 Bitmap
        if (pixelStride == 4) { // RGBA_8888 格式
            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int index = y * width + x;
                    int pixel = buffer.getInt(index * 4); // 每个像素占 4 字节
                    pixels[index] = pixel; // 直接复制 RGBA 数据
                }
            }
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        } else {
            Log.e(TAG, "不支持的像素格式: pixelStride=" + pixelStride);
        }

        return bitmap;
    }

    // 图像预处理（降噪）
    private Bitmap preprocessImage(Bitmap inputBitmap) {
        Mat srcMat = new Mat();
        Mat denoised = new Mat();
        try {
            // 1. 将 Bitmap 转换为 OpenCV Mat
            Utils.bitmapToMat(inputBitmap, srcMat);

            // 2. 仅执行高斯模糊（降噪）
            Imgproc.GaussianBlur(srcMat, denoised, new Size(3, 3), 0);

            // 3. 创建与原图相同大小的 Bitmap
            Bitmap outputBitmap = Bitmap.createBitmap(
                    inputBitmap.getWidth(),
                    inputBitmap.getHeight(),
                    Bitmap.Config.ARGB_8888
            );

            // 4. 将处理后的 Mat 转换回 Bitmap
            Utils.matToBitmap(denoised, outputBitmap);

            return outputBitmap;
        } finally {
            // 5. 释放所有 Mat 内存（即使发生异常）
            srcMat.release();
            denoised.release();
        }
    }

    // 保存 Bitmap 到文件（测试用）
    private void saveBitmap(Bitmap bitmap) {
        try {
            // 获取应用的私有目录
            File externalDir = getExternalFilesDir(null);
            if (externalDir == null) {
                Log.e(TAG, "外部存储目录不可用");
                return;
            }

            // 创建子目录
            File dir = new File(externalDir, "processed_frames");
            if (!dir.exists()) {
                boolean dirCreated = dir.mkdirs();
                Log.d(TAG, "目录创建结果: " + dirCreated);
            }

            // 创建文件
            File file = new File(dir, "frame_" + System.currentTimeMillis() + ".png");
            Log.d(TAG, "保存文件路径: " + file.getAbsolutePath());

            // 保存图片
            try (FileOutputStream fos = new FileOutputStream(file)) {
                boolean success = bitmap.compress(Bitmap.CompressFormat.PNG, 90, fos);
                Log.d(TAG, "图片保存结果: " + success);
            }
        } catch (IOException e) {
            Log.e(TAG, "保存失败: " + e.getMessage(), e);
        }
    }

    private void callPythonFunction(Bitmap bitmap) {
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }
        Python py = Python.getInstance();
        PyObject pyModule = py.getModule("main");

        // 将 Bitmap 转换为字节数组
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream);
        byte[] byteArray = stream.toByteArray();

        // 调用 Python 函数 t
        PyObject pyFunction = pyModule.callAttr("t", byteArray);
        // 处理 Python 函数的返回结果
        if (pyFunction != null) {
            // 这里可以根据返回结果进行相应的处理
            Log.d(TAG, "Python 函数返回结果: " + pyFunction.toString());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // 释放 MediaProjection
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
            Log.d(TAG, "MediaProjection 已释放");
        }

        // 强制释放所有残留图像
        if (mImageReader != null) {
            try {
                // 使用 acquireNextImage 替代 acquireLatestImage，避免跳过未处理图像
                Image image;
                while ((image = mImageReader.acquireNextImage()) != null) {
                    try {
                        image.close();
                        Log.d(TAG, "强制释放残留图像");
                    } catch (Exception e) {
                        Log.e(TAG, "关闭图像失败: " + e.getMessage());
                    }
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "ImageReader 已关闭，无法获取残留图像");
            } finally {
                mImageReader.close();
                mImageReader = null;
            }
        }

        // 关闭线程池
        if (mProcessingThread != null && !mProcessingThread.isShutdown()) {
            mProcessingThread.shutdownNow();
            Log.d(TAG, "线程池已关闭");
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private void startForegroundServiceWithNotification() {
        // 创建通知渠道（Android 8.0+ 必需）
        createNotificationChannel();

        // 构建通知
        Notification notification = new NotificationCompat.Builder(this, "screen_capture_channel")
                .setContentTitle("屏幕捕获服务运行中")
                .setContentText("正在捕获屏幕内容...")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        // 启动前台服务（ID 不可为0，类型需匹配）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(1, notification);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "screen_capture_channel",
                    "屏幕捕获服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("用于持续屏幕捕获的通知");
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }
}