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

    // 3. المعالج الرياضي للألوان
    public static boolean isColorLight(int color) {
        double darkness = 1 - (0.299 * Color.red(color)
                + 0.587 * Color.green(color)
                + 0.114 * Color.blue(color)) / 255;
        return darkness < 0.5;
    }

    // =========================================================
    // 👑 التحديث الحريري لشريط الحالة والحاوية عبر أنيميشن متدرج (ValueAnimator)
    // =========================================================
    public static void updateStatusBarColor(android.app.Activity activity, int targetColor) {
        if (activity == null || activity.isFinishing()) return;

        activity.runOnUiThread(() -> {
            Window window = activity.getWindow();
            View contentView = activity.findViewById(android.R.id.content);
            
            int currentColor = window.getStatusBarColor();
            
            // عدم تكرار الأنيميشن إذا كان اللون الحالي هو نفس اللون المطلوب
            if (currentColor == targetColor) return;

            // إلغاء أي أنيميشن سابقة تعمل حالياً لمنع التداخل
            if (window.getDecorView().getTag() instanceof android.animation.ValueAnimator) {
                ((android.animation.ValueAnimator) window.getDecorView().getTag()).cancel();
            }

            // أنيميشن تدرج الألوان المترابط (Smooth 300ms Transition)
            android.animation.ValueAnimator colorAnimation = 
                android.animation.ValueAnimator.ofObject(new android.animation.ArgbEvaluator(), currentColor, targetColor);
            colorAnimation.setDuration(300);

            colorAnimation.addUpdateListener(animator -> {
                int animatedColor = (int) animator.getAnimatedValue();
                
                // تحديث لون شريط الحالة ولون الحاوية تدريجياً في كل إطار
                window.setStatusBarColor(animatedColor);
                if (contentView != null) {
                    contentView.setBackgroundColor(animatedColor);
                }
                
                // تحديث أيقونات النظام وفقاً للون الإطار الحالي
                setDynamicIcons(window, isColorLight(animatedColor));
            });

            window.getDecorView().setTag(colorAnimation);
            colorAnimation.start();
        });
    }

    // =========================================================
    // 👑 استخراج لون الموقع المتوافق مع برمجيات SPA والوضع الليلي/النهاري
    // =========================================================
    public static void syncStatusBarWithWeb(android.app.Activity activity, WebView webView) {
        if (activity == null || webView == null) return;

        String defaultHex = isDarkMode(activity) ? "#121212" : "#FFFFFF";

        String jsScript = 
            "(function() {" +
            "  function extractColor() {" +
            "    var meta = document.querySelector('meta[name=\"theme-color\"]');" +
            "    if (meta && meta.content) return meta.content;" +
            "    var el = document.elementFromPoint(window.innerWidth / 2, 20);" +
            "    if (!el) el = document.querySelector('header') || document.querySelector('nav') || document.body;" +
            "    while (el && el !== document.documentElement) {" +
            "      var st = window.getComputedStyle(el);" +
            "      var bg = st.backgroundColor;" +
            "      if (bg && bg !== 'transparent' && bg !== 'rgba(0, 0, 0, 0)') return bg;" +
            "      el = el.parentElement;" +
            "    }" +
            "    return window.getComputedStyle(document.body).backgroundColor || '" + defaultHex + "';" +
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
