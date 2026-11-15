package com.touchassist.app;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Accessibility Service that performs system gestures (Home, Back, Recents, etc.)
 * This runs as a background service and is controlled by the floating overlay.
 */
public class TouchAccessibilityService extends AccessibilityService {

    private static TouchAccessibilityService instance;

    public static TouchAccessibilityService getInstance() {
        return instance;
    }

    public static boolean isRunning() {
        return instance != null;
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // We don't need to process events — we only use this service
        // for performing global actions
    }

    @Override
    public void onInterrupt() {
        // Required override
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }

    // ── Public action methods called from FloatingService ──

    public void doBack() {
        performGlobalAction(GLOBAL_ACTION_BACK);
    }

    public void doHome() {
        performGlobalAction(GLOBAL_ACTION_HOME);
    }

    public void doRecents() {
        performGlobalAction(GLOBAL_ACTION_RECENTS);
    }

    public void doNotifications() {
        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
    }

    public void doQuickSettings() {
        performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);
    }

    public void doPowerDialog() {
        performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
    }

    public void doScreenshot() {
        // GLOBAL_ACTION_TAKE_SCREENSHOT requires API 28+ (Android 9) ✓
        performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT);
    }

    public void doLockScreen() {
        // GLOBAL_ACTION_LOCK_SCREEN requires API 28+ (Android 9) ✓
        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    public void doSplitScreen() {
        performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN);
    }
}
