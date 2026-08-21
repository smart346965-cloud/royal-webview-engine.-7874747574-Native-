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

    // =========================================================
    // 👑 المحرك الذكي لتحديث لون شريط الحالة ولون الأيقونات ديناميكياً
    // =========================================================
    public static void updateStatusBarColor(android.app.Activity activity, int color) {
        if (activity == null || activity.isFinishing()) return;

        activity.runOnUiThread(() -> {
            Window window = activity.getWindow();
            
            // 1. تغيير لون خلفية شريط الحالة
            window.setStatusBarColor(color);

            // 2. فحص درجة سطوع اللون واختيار لون الأيقونات المناسب (أبيض أو أسود)
            boolean isLight = isColorLight(color);
            setDynamicIcons(window, isLight);
        });
    }

    // =========================================================
    // 👑 استخراج لون الموقع عبر الجافاسكريبت وتمريره للنظام (JS Bridge Injection)
    // =========================================================
    public static void syncStatusBarWithWeb(android.app.Activity activity, WebView webView) {
        if (activity == null || webView == null) return;

        String jsScript = 
            "(function() {" +
            "  var color = null;" +
            "  var meta = document.querySelector('meta[name=\"theme-color\"]');" +
            "  if (meta && meta.content) { color = meta.content; }" +
            "  else {" +
            "    var el = document.elementFromPoint(window.innerWidth / 2, 10);" +
            "    if (!el) el = document.querySelector('header') || document.querySelector('nav') || document.body;" +
            "    while (el && el !== document.documentElement) {" +
            "      var st = window.getComputedStyle(el);" +
            "      var bg = st.backgroundColor;" +
            "      if (bg && bg !== 'transparent' && bg !== 'rgba(0, 0, 0, 0)') { color = bg; break; }" +
            "      el = el.parentElement;" +
            "    }" +
            "  }" +
            "  return color || '#F3F4F6';" +
            "})();";

        webView.evaluateJavascript(jsScript, value -> {
            if (value != null && !value.equals("null") && !value.equals("\"null\"")) {
                int parsedColor = parseColorString(value);
                updateStatusBarColor(activity, parsedColor);
            }
        });
    }

    // =========================================================
    // 👑 محول ألوان الجافاسكريبت (HEX / RGB / RGBA) إلى Android Color
    // =========================================================
    public static int parseColorString(String colorStr) {
        if (colorStr == null) return Color.parseColor("#F3F4F6");
        colorStr = colorStr.replace("\"", "").trim();
        try {
            if (colorStr.startsWith("#")) {
                return Color.parseColor(colorStr);
            } else if (colorStr.startsWith("rgb")) {
                String[] parts = colorStr.substring(colorStr.indexOf("(") + 1, colorStr.indexOf(")")).split(",");
                int r = Integer.parseInt(parts[0].trim());
                int g = Integer.parseInt(parts[1].trim());
                int b = Integer.parseInt(parts[2].trim());
                return Color.rgb(r, g, b);
            }
        } catch (Exception e) {
            // Fallback لون افتراضي مريح في حال الفشل
        }
        return Color.parseColor("#F3F4F6");
    }
    }
