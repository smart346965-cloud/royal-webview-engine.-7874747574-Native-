package com.store.app;

import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.FragmentActivity;

public class SystemUI {

    // 1. تفعيل وضع "الملك" مع الاستجابة اللحظية لإيماءات الرجوع (Single Swipe Back)
    public static void applyKingMode(
            FragmentActivity activity,
            WebView webView
    ) {

        if (activity == null) return;

        Window window = activity.getWindow();

        // =========================================================
        // 👑 TRUE EDGE-TO-EDGE
        // =========================================================

        WindowCompat.setDecorFitsSystemWindows(
                window,
                false
        );

        // =========================================================
        // 👑 TRANSPARENT SYSTEM BARS
        // =========================================================

        window.setStatusBarColor(
                Color.TRANSPARENT
        );

        window.setNavigationBarColor(
                Color.TRANSPARENT
        );

        if (android.os.Build.VERSION.SDK_INT >=
                android.os.Build.VERSION_CODES.Q) {

            window.setNavigationBarContrastEnforced(
                    false
            );

            window.setStatusBarContrastEnforced(
                    false
            );
        }

        window.addFlags(
                WindowManager.LayoutParams
                        .FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
        );

        // =========================================================
        // 👑 DO NOT CONSUME WINDOW INSETS HERE
        // =========================================================

        View content =
                activity.findViewById(
                        android.R.id.content
                );

        if (content != null) {

            ViewCompat.setOnApplyWindowInsetsListener(
                    content,
                    null
            );

            content.setPadding(
                    0,
                    0,
                    0,
                    0
            );
        }

        // =========================================================
        // 👑 SYSTEM BAR BEHAVIOR
        // =========================================================

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(
                        window,
                        window.getDecorView()
                );

        if (controller != null) {

            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat
                            .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            );

            // لا نخفي Status Bar.
            // الموقع يمتد خلفه بينما الأيقونات تبقى ظاهرة.
        }
    }

    // 2. المحرك الذكي لتغيير لون الأيقونات (ساعة، بطارية) لتناسب الموقع
    public static void setDynamicIcons(android.view.Window window, boolean isLightBackground) {
        if (window == null) return;
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(isLightBackground);
            controller.setAppearanceLightNavigationBars(isLightBackground);
        }
    }

    // 3. المعالج الرياضي للألوان
    public static boolean isColorLight(int color) {
        double darkness = 1 - (0.299 * Color.red(color)
                + 0.587 * Color.green(color)
                + 0.114 * Color.blue(color)) / 255;
        return darkness < 0.5;
    }
                }
