package com.example.aiagent;

import android.app.Instrumentation;
import android.os.SystemClock;
import android.view.MotionEvent;

import java.util.HashMap;
import java.util.Map;

public class ActionExecutor {
    // 定义动作与屏幕位置的映射
    private static final Map<Integer, ScreenPosition> ACTION_POSITION_MAP = new HashMap<>();
    static {

        ACTION_POSITION_MAP.put(0, new ScreenPosition(100, 200));

        ACTION_POSITION_MAP.put(1, new ScreenPosition(200, 300));

        ACTION_POSITION_MAP.put(2, new ScreenPosition(300, 400));

        ACTION_POSITION_MAP.put(3, new ScreenPosition(400, 500));

        ACTION_POSITION_MAP.put(4, new ScreenPosition(500, 600));

        ACTION_POSITION_MAP.put(5, new ScreenPosition(600, 700));
    }

    // 屏幕位置类
    private static class ScreenPosition {
        int x;
        int y;

        public ScreenPosition(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    // 执行动作的方法
    public void executeAction(int action) {
        ScreenPosition position = ACTION_POSITION_MAP.get(action);
        if (position != null) {
            performClick(position.x, position.y);
        }
    }

    // 模拟屏幕点击的方法
    private void performClick(int x, int y) {
        Instrumentation instrumentation = new Instrumentation();
        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis();

        MotionEvent eventDown = MotionEvent.obtain(
                downTime,
                eventTime,
                MotionEvent.ACTION_DOWN,
                x,
                y,
                0
        );
        MotionEvent eventUp = MotionEvent.obtain(
                downTime,
                eventTime + 100,
                MotionEvent.ACTION_UP,
                x,
                y,
                0
        );

        instrumentation.sendPointerSync(eventDown);
        instrumentation.sendPointerSync(eventUp);
    }
}
