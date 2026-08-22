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

    // 1. تفعيل وضع "الملك" مع منع الوميض الأسود عبر تعيين اللون الأولي فوراً
    public static void applyKingMode(
            FragmentActivity activity,
            WebView webView,
            int initialColor
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
        // 👑 منع الوميض الأسود: تعيين لون المظهر المبدئي بدلاً من الشفافية المفاجئة
        // =========================================================

        window.setStatusBarColor(initialColor);

        window.setNavigationBarColor(Color.TRANSPARENT);

        if (android.os.Build.VERSION.SDK_INT >=
                android.os.Build.VERSION_CODES.Q) {

            window.setNavigationBarContrastEnforced(false);
            window.setStatusBarContrastEnforced(false);
        }

        window.addFlags(
                WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
        );

        // =========================================================
        // 👑 DO NOT CONSUME WINDOW INSETS HERE
        // =========================================================

        View content = activity.findViewById(android.R.id.content);

        if (content != null) {

            ViewCompat.setOnApplyWindowInsetsListener(
                    content,
                    null
            );

            content.setPadding(0, 0, 0, 0);
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

    // =========================================================
    // 👑 المعادلة المعيارية العالمية للسطوع التبايني (WCAG 2.1 Standard)
    // =========================================================
    public static boolean isColorLight(int color) {
        double red = Color.red(color) / 255.0;
        double green = Color.green(color) / 255.0;
        double blue = Color.blue(color) / 255.0;

        // حساب السطوع النسبي المعياري وفق معايير W3C / WCAG 2.1
        double r = (red <= 0.03928) ? red / 12.92 : Math.pow((red + 0.055) / 1.055, 2.4);
        double g = (green <= 0.03928) ? green / 12.92 : Math.pow((green + 0.055) / 1.055, 2.4);
        double b = (blue <= 0.03928) ? blue / 12.92 : Math.pow((blue + 0.055) / 1.055, 2.4);

        double luminance = (0.2126 * r) + (0.7152 * g) + (0.0722 * b);
        
        // إذا كان السطوع أكبر من العتبة المعيارية 0.179، يعتبر اللون فاتحاً وتكون الأيقونات سوداء
        return luminance > 0.179;
    }

    // =========================================================
    // 👑 التحديث الحريري لشريط الحالة مع الضبط الفوري الموحد للأيقونات
    // =========================================================
    public static void updateStatusBarColor(android.app.Activity activity, int targetColor) {
        if (activity == null || activity.isFinishing()) return;

        activity.runOnUiThread(() -> {
            Window window = activity.getWindow();
            View contentView = activity.findViewById(android.R.id.content);
            
            int currentColor = window.getStatusBarColor();
            
            if (currentColor == targetColor) return;

            if (window.getDecorView().getTag() instanceof android.animation.ValueAnimator) {
                ((android.animation.ValueAnimator) window.getDecorView().getTag()).cancel();
            }

            // 🛡️ 1. تحديد نمط الأيقونات (أسود/أبيض) مرة واحدة فقط للون الهدف منعاً لتعليق WindowInsetsController
            boolean isTargetLight = isColorLight(targetColor);
            setDynamicIcons(window, isTargetLight);

            // 🛡️ 2. تشغيل أنيميشن التدرج اللوني لخلفية شريط الحالة والحاوية فقط (300ms)
            android.animation.ValueAnimator colorAnimation = 
                android.animation.ValueAnimator.ofObject(new android.animation.ArgbEvaluator(), currentColor, targetColor);
            colorAnimation.setDuration(300);

            colorAnimation.addUpdateListener(animator -> {
                int animatedColor = (int) animator.getAnimatedValue();
                window.setStatusBarColor(animatedColor);
                if (contentView != null) {
                    contentView.setBackgroundColor(animatedColor);
                }
            });

            window.getDecorView().setTag(colorAnimation);
            colorAnimation.start();
        });
    }

    // =========================================================
    // 👑 محرك الويب المطور لاستخراج الألوان مع Canvas Color Normalizer
    // =========================================================
    public static void syncStatusBarWithWeb(android.app.Activity activity, WebView webView) {
        if (activity == null || webView == null) return;

        String defaultHex = isDarkMode(activity) ? "#121212" : "#FFFFFF";

        String jsScript = 
            "(function() {" +
            "  function normalizeColor(colorStr) {" +
            "    if (!colorStr) return null;" +
            "    try {" +
            "      var canvas = document.createElement('canvas');" +
            "      canvas.width = 1; canvas.height = 1;" +
            "      var ctx = canvas.getContext('2d');" +
            "      ctx.fillStyle = colorStr;" +
            "      return ctx.fillStyle;" + // تحويل تلقائي لجميع الصيغ (HSL, OKLCH, CSS Vars) إلى #rrggbb عبر محرك Chromium
            "    } catch(e) {" +
            "      return colorStr;" +
            "    }" +
            "  }" +
            "  function extractColor() {" +
            "    var metas = document.querySelectorAll('meta[name=\"theme-color\"]');" +
            "    for (var i = 0; i < metas.length; i++) {" +
            "      var m = metas[i];" +
            "      if (!m.media || window.matchMedia(m.media).matches) {" +
            "        if (m.content) return normalizeColor(m.content);" +
            "      }" +
            "    }" +
            "    var el = document.elementFromPoint(window.innerWidth / 2, 20);" +
            "    if (!el) el = document.querySelector('header') || document.querySelector('nav') || document.body;" +
            "    while (el && el !== document.documentElement) {" +
            "      var st = window.getComputedStyle(el);" +
            "      var bg = st.backgroundColor;" +
            "      if (bg && bg !== 'transparent' && bg !== 'rgba(0, 0, 0, 0)') return normalizeColor(bg);" +
            "      el = el.parentElement;" +
            "    }" +
            "    var bodyBg = window.getComputedStyle(document.body).backgroundColor;" +
            "    return normalizeColor(bodyBg) || '" + defaultHex + "';" +
            "  }" +
            "  return extractColor();" +
            "})();";

        webView.evaluateJavascript(jsScript, value -> {
            if (value != null && !value.equals("null") && !value.equals("\"null\"")) {
                int parsedColor = parseColorString(activity, value);
                updateStatusBarColor(activity, parsedColor);
            }
        });
    }

    // =========================================================
    // 👑 محول ألوان الجافاسكريبت مع فحص قناة الشفافية (Alpha Channel)
    // =========================================================
    public static int parseColorString(android.content.Context context, String colorStr) {
        int defaultColor = getDefaultSystemColor(context);
        if (colorStr == null) return defaultColor;
        colorStr = colorStr.replace("\"", "").trim();
        try {
            if (colorStr.startsWith("#")) {
                return Color.parseColor(colorStr);
            } else if (colorStr.startsWith("rgb")) {
                String[] parts = colorStr.substring(colorStr.indexOf("(") + 1, colorStr.indexOf(")")).split(",");
                int r = Integer.parseInt(parts[0].trim());
                int g = Integer.parseInt(parts[1].trim());
                int b = Integer.parseInt(parts[2].trim());

                // 🛡️ فحص قناة الشفافية Alpha لو كانت الصيغة rgba
                if (parts.length >= 4) {
                    float a = Float.parseFloat(parts[3].trim());
                    // إذا كانت الشفافية أقل من 10% نعتبرها شفافة ونرجع لون النظام المبدئي بدلاً من الأسود #000000
                    if (a < 0.1f) {
                        return defaultColor;
                    }
                }
                return Color.rgb(r, g, b);
            }
        } catch (Exception e) {
            // Fallback متجاوب تلقائياً مع مظهر النظام عند الفشل
        }
        return defaultColor;
    }

    // =========================================================
    // 👑 استشعار الوضع الليلي/النهاري للنظام وتحديد اللون الافتراضي
    // =========================================================
    public static boolean isDarkMode(android.content.Context context) {
        if (context == null) return false;
        int nightModeFlags = context.getResources().getConfiguration().uiMode 
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    public static int getDefaultSystemColor(android.content.Context context) {
        return isDarkMode(context) ? Color.parseColor("#121212") : Color.WHITE;
    }
                }
