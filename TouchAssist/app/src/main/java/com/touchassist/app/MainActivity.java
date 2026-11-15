package com.touchassist.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

/**
 * Main setup activity.
 * Guides user through permissions, then launches/stops the floating service.
 */
public class MainActivity extends AppCompatActivity {

    private static final int OVERLAY_PERMISSION_CODE = 100;
    private static final String PREFS_NAME = "touch_assist_prefs";

    private Button btnToggle;
    private Button btnOverlayPermission;
    private Button btnAccessibilityPermission;
    private Switch switchAutoStart;
    private TextView statusText;
    private View overlayStatus;
    private View accessibilityStatus;

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Make status bar match our dark theme
        Window window = getWindow();
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.bg_dark));

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        btnToggle = findViewById(R.id.btnToggle);
        btnOverlayPermission = findViewById(R.id.btnOverlayPermission);
        btnAccessibilityPermission = findViewById(R.id.btnAccessibilityPermission);
        switchAutoStart = findViewById(R.id.switchAutoStart);
        statusText = findViewById(R.id.statusText);
        overlayStatus = findViewById(R.id.overlayStatus);
        accessibilityStatus = findViewById(R.id.accessibilityStatus);

        switchAutoStart.setChecked(prefs.getBoolean("auto_start", true));
        switchAutoStart.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("auto_start", isChecked).apply();
        });

        btnOverlayPermission.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        });

        btnAccessibilityPermission.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            Toast.makeText(this,
                    "Find \"TouchAssist\" in the list and enable it",
                    Toast.LENGTH_LONG).show();
        });

        btnToggle.setOnClickListener(v -> {
            if (!hasOverlayPermission()) {
                Toast.makeText(this, "Please grant overlay permission first",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (!TouchAccessibilityService.isRunning()) {
                Toast.makeText(this, "Please enable Accessibility Service first",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            toggleService();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    private void updateUI() {
        boolean overlayOk = hasOverlayPermission();
        boolean accessOk = TouchAccessibilityService.isRunning();
        boolean serviceRunning = isServiceRunning();

        // Update permission indicators
        overlayStatus.setBackgroundResource(overlayOk ?
                R.drawable.status_dot_green : R.drawable.status_dot_red);
        accessibilityStatus.setBackgroundResource(accessOk ?
                R.drawable.status_dot_green : R.drawable.status_dot_red);

        btnOverlayPermission.setEnabled(!overlayOk);
        btnOverlayPermission.setText(overlayOk ? "✓ Granted" : "Grant Permission");

        btnAccessibilityPermission.setEnabled(!accessOk);
        btnAccessibilityPermission.setText(accessOk ? "✓ Enabled" : "Enable Service");

        // Main toggle
        boolean canRun = overlayOk && accessOk;
        btnToggle.setEnabled(canRun);

        if (serviceRunning) {
            btnToggle.setText("⏹  Stop TouchAssist");
            btnToggle.setBackgroundResource(R.drawable.btn_stop);
            statusText.setText("● Active — floating button is on screen");
            statusText.setTextColor(ContextCompat.getColor(this, R.color.green_accent));
        } else {
            btnToggle.setText("▶  Start TouchAssist");
            btnToggle.setBackgroundResource(R.drawable.btn_start);
            if (canRun) {
                statusText.setText("Ready to start");
                statusText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            } else {
                statusText.setText("Grant all permissions above first");
                statusText.setTextColor(ContextCompat.getColor(this, R.color.orange_accent));
            }
        }
    }

    private void toggleService() {
        if (isServiceRunning()) {
            stopService(new Intent(this, FloatingService.class));
        } else {
            Intent intent = new Intent(this, FloatingService.class);
            startForegroundService(intent);
        }
        // Delay UI update to let service start/stop
        btnToggle.postDelayed(this::updateUI, 500);
    }

    private boolean hasOverlayPermission() {
        return Settings.canDrawOverlays(this);
    }

    private boolean isServiceRunning() {
        // Simple check: if our floating service notification channel is active
        // More reliable: use a static flag
        return FloatingServiceTracker.isRunning;
    }

    // ── Static tracker (simplest approach) ──
    public static class FloatingServiceTracker {
        public static boolean isRunning = false;
    }
}
