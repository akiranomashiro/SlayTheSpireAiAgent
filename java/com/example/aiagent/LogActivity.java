package com.example.aiagent;

import android.content.Context;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class LogActivity extends AppCompatActivity {
    private TextView tvLog;
    private Button btnClearLog;
    private File logFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log);

        // 返回按钮
        Button btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> {
            onBackPressed(); // 退出设置界面
        });

        // 初始化控件
        tvLog = findViewById(R.id.tvLog);
        btnClearLog = findViewById(R.id.btnClearLog);

        // 初始化日志文件
        logFile = new File(getFilesDir(), "game_log.txt");

        // 加载日志
        loadLog();

        // 清理日志按钮点击事件
        btnClearLog.setOnClickListener(v -> clearLog());
    }

    // 加载日志
    private void loadLog() {
        StringBuilder logContent = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logContent.append(line).append("\n");
            }
        } catch (IOException e) {
            logContent.append("暂无日志");
        }
        tvLog.setText(logContent.toString());
    }

    // 清理日志
    private void clearLog() {
        try (FileWriter writer = new FileWriter(logFile)) {
            writer.write(""); // 清空文件内容
            tvLog.setText("日志已清理");
        } catch (IOException e) {
            tvLog.setText("清理日志失败");
        }
    }

    // 添加日志（供外部调用）
    public static void addLog(Context context, String message) {
        File logFile = new File(context.getFilesDir(), "game_log.txt");
        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.append(message).append("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}