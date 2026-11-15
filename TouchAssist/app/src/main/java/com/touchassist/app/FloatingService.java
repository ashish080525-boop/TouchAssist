package com.touchassist.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;

import androidx.core.app.NotificationCompat;

/**
 * The core floating overlay service.
 * Draws a draggable AssistiveTouch-style button and radial action menu.
 */
public class FloatingService extends Service {

    private static final String CHANNEL_ID = "touch_assist_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final String PREFS_NAME = "touch_assist_prefs";

    private WindowManager windowManager;
    private View floatingButton;
    private View menuView;
    private WindowManager.LayoutParams buttonParams;
    private WindowManager.LayoutParams menuParams;

    private boolean isMenuOpen = false;
    private int screenWidth, screenHeight;
    private Handler handler = new Handler(Looper.getMainLooper());

    // Touch tracking
    private float initialTouchX, initialTouchY;
    private int initialX, initialY;
    private boolean isDragging = false;
    private long touchStartTime;
    private static final int CLICK_THRESHOLD = 200; // ms
    private static final int MOVE_THRESHOLD = 15;   // px

    // Auto-hide timer
    private Runnable fadeRunnable;
    private static final long FADE_DELAY = 4000; // 4 seconds idle -> semi-transparent

    private Vibrator vibrator;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        MainActivity.FloatingServiceTracker.isRunning = true;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        createFloatingButton();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // Restart if killed
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        MainActivity.FloatingServiceTracker.isRunning = false;
        if (floatingButton != null) {
            try { windowManager.removeView(floatingButton); } catch (Exception ignored) {}
        }
        if (menuView != null) {
            try { windowManager.removeView(menuView); } catch (Exception ignored) {}
        }
    }

    // ═══════════════════════════════════════════
    //  NOTIFICATION (required for foreground service)
    // ═══════════════════════════════════════════

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "TouchAssist Service",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Keeps the floating touch button active");
        channel.setShowBadge(false);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("TouchAssist Active")
                .setContentText("Tap to open settings")
                .setSmallIcon(R.drawable.ic_touch)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    // ═══════════════════════════════════════════
    //  FLOATING BUTTON
    // ═══════════════════════════════════════════

    private void createFloatingButton() {
        floatingButton = LayoutInflater.from(this).inflate(R.layout.floating_button, null);

        int overlayType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

        buttonParams = new WindowManager.LayoutParams(
                dpToPx(52),
                dpToPx(52),
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        buttonParams.gravity = Gravity.TOP | Gravity.START;

        // Restore saved position or default to right-center
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        buttonParams.x = prefs.getInt("btn_x", screenWidth - dpToPx(64));
        buttonParams.y = prefs.getInt("btn_y", screenHeight / 2);

        windowManager.addView(floatingButton, buttonParams);

        // Touch listener for drag + tap
        floatingButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        isDragging = false;
                        touchStartTime = System.currentTimeMillis();
                        initialX = buttonParams.x;
                        initialY = buttonParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();

                        // Full opacity on touch
                        floatingButton.setAlpha(1f);
                        floatingButton.setScaleX(0.9f);
                        floatingButton.setScaleY(0.9f);
                        cancelFadeTimer();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - initialTouchX;
                        float dy = event.getRawY() - initialTouchY;

                        if (Math.abs(dx) > MOVE_THRESHOLD || Math.abs(dy) > MOVE_THRESHOLD) {
                            isDragging = true;
                        }

                        if (isDragging) {
                            buttonParams.x = initialX + (int) dx;
                            buttonParams.y = initialY + (int) dy;
                            windowManager.updateViewLayout(floatingButton, buttonParams);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        floatingButton.setScaleX(1f);
                        floatingButton.setScaleY(1f);

                        if (isDragging) {
                            // Snap to nearest edge
                            snapToEdge();
                            savePosition();
                        } else {
                            long elapsed = System.currentTimeMillis() - touchStartTime;
                            if (elapsed < CLICK_THRESHOLD) {
                                // It's a tap!
                                hapticTap();
                                toggleMenu();
                            }
                        }

                        startFadeTimer();
                        return true;
                }
                return false;
            }
        });

        startFadeTimer();
    }

    // ═══════════════════════════════════════════
    //  EDGE SNAPPING (like iPhone)
    // ═══════════════════════════════════════════

    private void snapToEdge() {
        int centerX = buttonParams.x + dpToPx(26);
        int targetX;
        if (centerX < screenWidth / 2) {
            targetX = dpToPx(4); // Snap left
        } else {
            targetX = screenWidth - dpToPx(56); // Snap right
        }

        // Clamp Y
        int targetY = Math.max(dpToPx(24), Math.min(buttonParams.y, screenHeight - dpToPx(80)));

        ValueAnimator animX = ValueAnimator.ofInt(buttonParams.x, targetX);
        ValueAnimator animY = ValueAnimator.ofInt(buttonParams.y, targetY);

        ValueAnimator.AnimatorUpdateListener listener = animation -> {
            // Updated in each animator individually
        };

        animX.setDuration(250);
        animX.setInterpolator(new AccelerateDecelerateInterpolator());
        animX.addUpdateListener(a -> {
            buttonParams.x = (int) a.getAnimatedValue();
            try {
                windowManager.updateViewLayout(floatingButton, buttonParams);
            } catch (Exception ignored) {}
        });

        animY.setDuration(250);
        animY.setInterpolator(new AccelerateDecelerateInterpolator());
        animY.addUpdateListener(a -> {
            buttonParams.y = (int) a.getAnimatedValue();
            try {
                windowManager.updateViewLayout(floatingButton, buttonParams);
            } catch (Exception ignored) {}
        });

        animX.start();
        animY.start();
    }

    private void savePosition() {
        handler.postDelayed(() -> {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            prefs.edit()
                    .putInt("btn_x", buttonParams.x)
                    .putInt("btn_y", buttonParams.y)
                    .apply();
        }, 300);
    }

    // ═══════════════════════════════════════════
    //  RADIAL ACTION MENU
    // ═══════════════════════════════════════════

    private void toggleMenu() {
        if (isMenuOpen) {
            closeMenu();
        } else {
            openMenu();
        }
    }

    private void openMenu() {
        if (menuView != null) return;

        menuView = LayoutInflater.from(this).inflate(R.layout.menu_layout, null);

        int overlayType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

        menuParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        menuParams.gravity = Gravity.TOP | Gravity.START;

        windowManager.addView(menuView, menuParams);

        // Dimmed background tap to close
        View dimBg = menuView.findViewById(R.id.dimBackground);
        dimBg.setOnClickListener(v -> closeMenu());

        // Setup action buttons
        setupMenuButton(R.id.btnHome, () -> performAction("home"));
        setupMenuButton(R.id.btnBack, () -> performAction("back"));
        setupMenuButton(R.id.btnRecents, () -> performAction("recents"));
        setupMenuButton(R.id.btnScreenshot, () -> performAction("screenshot"));
        setupMenuButton(R.id.btnNotifications, () -> performAction("notifications"));
        setupMenuButton(R.id.btnVolumeUp, () -> performAction("volume_up"));
        setupMenuButton(R.id.btnVolumeDown, () -> performAction("volume_down"));
        setupMenuButton(R.id.btnLock, () -> performAction("lock"));
        setupMenuButton(R.id.btnPower, () -> performAction("power"));

        // Animate menu in
        View menuContainer = menuView.findViewById(R.id.menuContainer);
        menuContainer.setScaleX(0.3f);
        menuContainer.setScaleY(0.3f);
        menuContainer.setAlpha(0f);

        // Position menu container near the button
        positionMenuNearButton(menuContainer);

        menuContainer.animate()
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(200)
                .setInterpolator(new OvershootInterpolator(1.2f))
                .start();

        dimBg.setAlpha(0f);
        dimBg.animate().alpha(1f).setDuration(200).start();

        isMenuOpen = true;
    }

    private void positionMenuNearButton(View menuContainer) {
        menuContainer.post(() -> {
            // Center the menu grid on screen — it's fixed layout
            // The dim background covers everything so tapping outside closes it
        });
    }

    private void closeMenu() {
        if (menuView == null) return;

        View menuContainer = menuView.findViewById(R.id.menuContainer);
        View dimBg = menuView.findViewById(R.id.dimBackground);

        menuContainer.animate()
                .scaleX(0.3f).scaleY(0.3f).alpha(0f)
                .setDuration(150)
                .start();

        dimBg.animate().alpha(0f).setDuration(150)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        try {
                            windowManager.removeView(menuView);
                        } catch (Exception ignored) {}
                        menuView = null;
                        isMenuOpen = false;
                    }
                }).start();
    }

    private void setupMenuButton(int id, Runnable action) {
        View btn = menuView.findViewById(id);
        if (btn == null) return;
        btn.setOnClickListener(v -> {
            hapticTap();
            action.run();
            // Close menu after a small delay so user sees the press
            handler.postDelayed(this::closeMenu, 120);
        });
    }

    // ═══════════════════════════════════════════
    //  ACTION EXECUTION
    // ═══════════════════════════════════════════

    private void performAction(String action) {
        TouchAccessibilityService service = TouchAccessibilityService.getInstance();
        AudioManager audio;

        switch (action) {
            case "home":
                if (service != null) service.doHome();
                break;
            case "back":
                if (service != null) service.doBack();
                break;
            case "recents":
                if (service != null) service.doRecents();
                break;
            case "screenshot":
                // Small delay so the menu is gone before screenshot
                handler.postDelayed(() -> {
                    if (service != null) service.doScreenshot();
                }, 300);
                break;
            case "notifications":
                if (service != null) service.doNotifications();
                break;
            case "lock":
                if (service != null) service.doLockScreen();
                break;
            case "power":
                if (service != null) service.doPowerDialog();
                break;
            case "volume_up":
                audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (audio != null) {
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                            AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                }
                break;
            case "volume_down":
                audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (audio != null) {
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                            AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                }
                break;
        }
    }

    // ═══════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════

    private void hapticTap() {
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }

    private void startFadeTimer() {
        cancelFadeTimer();
        fadeRunnable = () -> {
            if (floatingButton != null && !isMenuOpen) {
                floatingButton.animate().alpha(0.35f).setDuration(600).start();
            }
        };
        handler.postDelayed(fadeRunnable, FADE_DELAY);
    }

    private void cancelFadeTimer() {
        if (fadeRunnable != null) {
            handler.removeCallbacks(fadeRunnable);
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
