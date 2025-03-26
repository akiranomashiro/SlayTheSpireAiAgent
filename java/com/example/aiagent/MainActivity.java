package com.example.aiagent;

import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity implements ScreenCaptureService.OnImageProcessedListener {

    // 定义游戏包名
    private static final String GAME_PACKAGE_NAME = "com.humble.SlayTheSpire";

    private ScreenCaptureService mService;

    private boolean mIsBound = false;

    private ActivityResultLauncher<Intent> mScreenCaptureLauncher;

    private Intent mScreenCaptureIntent; // 保存权限结果 Intent

    private ADBDeviceLogMonitor logMonitor;

    private GameCharacter gameCharacter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化游戏角色数据
        List<Integer> initialRelics = new ArrayList<>();
        for (int i = 0; i < 150; i++) initialRelics.add(0); // 假设最多150个遗物
        gameCharacter = new GameCharacter(
                80,       // 当前生命
                80,       //最大生命
                0,         //当前层数
                "",        //层底boss
                initialRelics,
                new ArrayList<>(),
                new ArrayList<>(),
                0          //当前钱财
        );

        logMonitor = new ADBDeviceLogMonitor();
        logMonitor.setGameCharacter(gameCharacter);

        // 开始游戏按钮
        findViewById(R.id.btnStartGame).setOnClickListener(v -> {
            if (isGameInstalled()) {
                requestScreenCapturePermission();
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    startGame();
                    startLogMonitoring();
                }, 3000);
            } else {
                showGameNotInstalledDialog();
            }
        });

        // 初始化权限请求回调
        mScreenCaptureLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        mScreenCaptureIntent = result.getData();
                        Log.d("MainActivity", "权限已授予，Intent 已保存");
                        startAICoreFunction(); // 权限授予后启动服务
                    } else {
                        Toast.makeText(this, "屏幕捕获权限被拒绝", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        // 其他按钮跳转
        setupNavigationButtons();
    }

    // 检测游戏是否安装
    private boolean isGameInstalled() {
        PackageManager pm = getPackageManager();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ 使用 ApplicationInfoFlags
                pm.getApplicationInfo(GAME_PACKAGE_NAME, PackageManager.ApplicationInfoFlags.of(0));
            }
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    // 启动游戏
    private void startGame() {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(GAME_PACKAGE_NAME);
        if (launchIntent == null) {
            Log.e("MainActivity", "未找到游戏启动 Intent");
            return;
        }
        Log.d("MainActivity", "游戏启动 Intent 已找到，准备启动游戏");
        if (launchIntent == null) {
            // 手动构造 Intent
            launchIntent = new Intent(Intent.ACTION_MAIN);
            launchIntent.setPackage(GAME_PACKAGE_NAME);
            launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        if (launchIntent != null) {
            launchIntent.putExtra("REQUEST_SCREEN_CAPTURE_PERMISSION", true);
            startActivity(launchIntent);
            Log.d("MainActivity", "游戏已启动");
        } else {
            Toast.makeText(this, "无法启动游戏：未找到启动 Activity", Toast.LENGTH_SHORT).show();
        }
    }

    // 显示未安装弹窗
    private void showGameNotInstalledDialog() {
        new AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage("未检测到《杀戮尖塔》游戏，请先安装游戏。")
                .setPositiveButton("确定", null)
                .show();
    }

    // 初始化其他按钮跳转
    private void setupNavigationButtons() {
        findViewById(R.id.btnSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        findViewById(R.id.btnHelp).setOnClickListener(v ->
                startActivity(new Intent(this, HelpActivity.class)));

        findViewById(R.id.btnLog).setOnClickListener(v ->
                startActivity(new Intent(this, LogActivity.class)));

        findViewById(R.id.btnAbout).setOnClickListener(v ->
                startActivity(new Intent(this, AboutActivity.class)));
    }

    // 请求屏幕捕获权限
    private void requestScreenCapturePermission() {
        MediaProjectionManager projectionManager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        Intent permissionIntent = projectionManager.createScreenCaptureIntent();
        mScreenCaptureLauncher.launch(permissionIntent);
    }

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            ScreenCaptureService.LocalBinder binder = (ScreenCaptureService.LocalBinder) service;
            mService = binder.getService();
            mService.setOnImageProcessedListener(MainActivity.this);
            mIsBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mIsBound = false;
        }
    };

    // 核心功能（需根据计划书补充）
    private void startAICoreFunction() {
        if (mIsBound) {
            Log.w(TAG, "服务已绑定，跳过重复启动");
            return;
        }

        if (mScreenCaptureIntent == null) {
            Toast.makeText(this, "请先授予屏幕捕获权限", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
        serviceIntent.putExtra("screen_capture_intent", mScreenCaptureIntent);

        // 使用 startForegroundService 启动（适配 Android 8.0+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        bindService(serviceIntent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onImageProcessed(Bitmap processedBitmap) {
        runOnUiThread(() -> extractVisualInformation(processedBitmap));
    }

    private void extractVisualInformation(Bitmap bitmap) {
        // 实现视觉信息提取逻辑
        Log.d(TAG, "接收到处理后的图像，开始视觉信息提取");
    }

    @Override
    protected void onDestroy() {
        if (mIsBound) {
            unbindService(mConnection);
            mIsBound = false;
        }
        stopLogMonitoring();
        super.onDestroy();
    }

    // 开始监听日志
    private void startLogMonitoring() {
        logMonitor.startMonitoring();
    }

    // 停止监听日志
    private void stopLogMonitoring() {
        logMonitor.stopMonitoring();
    }

    public GameCharacter getGameCharacter() { return this.gameCharacter; }
}