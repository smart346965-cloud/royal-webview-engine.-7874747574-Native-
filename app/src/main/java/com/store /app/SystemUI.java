package com.store.app;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.FragmentActivity;

public class SystemUI {

    private static final String TAG = "SystemUI_Engine";

    // 👑 الحافظ الموحد للون الهيدر الناتيف الحالي
    private static int currentHeaderColor = Integer.MIN_VALUE;

    // 👑 رقم جيل المزامنة لحماية النوافذ المتزامنة من التضارب
    private static long syncGeneration = 0L;

    private static final Handler SYNC_HANDLER = new Handler(Looper.getMainLooper());
    private static Runnable syncTask;

    // =========================================================
    // 👑 Navigation Bar Intelligence Engine
    // 0 = 3 Buttons
    // 1 = 2 Buttons
    // 2 = Gestural
    // =========================================================
    private static final long NAVIGATION_BAR_HIDE_DELAY = 5000L;

    private static final Handler NAV_HANDLER =
            new Handler(Looper.getMainLooper());

    private static Runnable navigationHideTask;

    private static int detectedNavigationMode = -1;

    private static boolean navigationBarControllerReady = false;

    // =========================================================
    // 👑 Navigation Mode Detector
    // =========================================================

    private static int detectNavigationMode(android.content.Context context) {

        if (context == null) {
            return 2; // Fallback آمن: Gesture
        }

        try {

            int resourceId = context.getResources()
                    .getIdentifier(
                            "config_navBarInteractionMode",
                            "integer",
                            "android"
                    );

            if (resourceId != 0) {

                int mode = context.getResources()
                        .getInteger(resourceId);

                if (mode >= 0 && mode <= 2) {
                    return mode;
                }
            }

        } catch (Throwable t) {

            Log.w(
                    TAG,
                    "Navigation mode detection failed.",
                    t
            );
        }

        // 👑 الفشل = Gesture حتى لا نخفي شريط الإيماءات بالخطأ
        return 2;
    }

    // =========================================================
    // 👑 Navigation Bar Controller
    // Gesture = دائم الظهور
    // Buttons = إخفاء بعد 5 ثوانٍ
    // =========================================================

    public static void initializeNavigationBarController(
            android.app.Activity activity
    ) {

        if (activity == null || activity.isFinishing()) {
            return;
        }

        activity.runOnUiThread(() -> {

            Window window = activity.getWindow();

            if (window == null) {
                return;
            }

            detectedNavigationMode =
                    detectNavigationMode(activity);

            navigationBarControllerReady = true;

            WindowInsetsControllerCompat controller =
                    WindowCompat.getInsetsController(
                            window,
                            window.getDecorView()
                    );

            if (controller == null) {
                return;
            }

            // 👑 النظام لا يفرض خلفية على شريط التنقل
            window.setNavigationBarColor(Color.TRANSPARENT);

            if (android.os.Build.VERSION.SDK_INT >=
                    android.os.Build.VERSION_CODES.Q) {

                window.setNavigationBarContrastEnforced(false);
            }

            // =====================================================
            // 👑 GESTURE NAVIGATION
            // لا نلمس Navigation Bars إطلاقاً
            // =====================================================

            if (detectedNavigationMode == 2) {

                cancelNavigationBarHide();

                controller.show(
                        androidx.core.view.WindowInsetsCompat.Type.navigationBars()
                );

                controller.setSystemBarsBehavior(
                        WindowInsetsControllerCompat
                                .BEHAVIOR_SHOW_BARS_BY_SWIPE
                );

                return;
            }

            // =====================================================
            // 👑 BUTTON NAVIGATION
            // إظهار أولاً ثم بدء عداد 5 ثوانٍ
            // =====================================================

            controller.show(
                    androidx.core.view.WindowInsetsCompat.Type.navigationBars()
            );

            scheduleNavigationBarHide(activity);
        });
    }

    // =========================================================
    // 👑 5 Second Navigation Bar Auto-Hide
    // =========================================================

    private static void scheduleNavigationBarHide(
            android.app.Activity activity
    ) {

        cancelNavigationBarHide();

        if (activity == null ||
                activity.isFinishing() ||
                !navigationBarControllerReady) {
            return;
        }

        // Gesture Navigation لا يتم إخفاؤه أبداً
        if (detectedNavigationMode == 2) {
            return;
        }

        navigationHideTask = () -> {

            if (activity.isFinishing()) {
                return;
            }

            // 👑 حماية إضافية: إعادة التحقق قبل الإخفاء
            if (detectNavigationMode(activity) == 2) {
                return;
            }

            Window window = activity.getWindow();

            if (window == null) {
                return;
            }

            WindowInsetsControllerCompat controller =
                    WindowCompat.getInsetsController(
                            window,
                            window.getDecorView()
                    );

            if (controller != null) {

                // 👑 الإخفاء يتم بواسطة System UI نفسه
                // وبالتالي يأخذ Animation النظام الطبيعي
                controller.hide(
                        androidx.core.view.WindowInsetsCompat.Type.navigationBars()
                );
            }
        };

        NAV_HANDLER.postDelayed(
                navigationHideTask,
                NAVIGATION_BAR_HIDE_DELAY
        );
    }

    // =========================================================
    // 👑 Cancel Navigation Bar Hide
    // =========================================================

    public static void cancelNavigationBarHide() {

        if (navigationHideTask != null) {

            NAV_HANDLER.removeCallbacks(
                    navigationHideTask
            );

            navigationHideTask = null;
        }
    }

    // =========================================================
    // 👑 Stable Navigation Bar Policy
    // يمنع ظهور Navigation Bar كـ Transient Overlay
    // ويجعل ظهورها يمر عبر Insets النظام الطبيعية.
    // =========================================================

    private static void enforceStableNavigationBarPolicy(
            android.app.Activity activity
    ) {
        if (activity == null || activity.isFinishing()) {
            return;
        }

        Window window = activity.getWindow();

        if (window == null) {
            return;
        }

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(
                        window,
                        window.getDecorView()
                );

        if (controller == null) {
            return;
        }

        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_SWIPE
        );

        window.setNavigationBarColor(Color.TRANSPARENT);

        if (android.os.Build.VERSION.SDK_INT >=
                android.os.Build.VERSION_CODES.Q) {

            window.setNavigationBarContrastEnforced(false);
        }
    }

    // =========================================================
    // 👑 Navigation Bar Activity Refresh
    // =========================================================

    public static void refreshNavigationBar(
            android.app.Activity activity
    ) {

        if (activity == null ||
                activity.isFinishing()) {
            return;
        }

        activity.runOnUiThread(() -> {

            detectedNavigationMode =
                    detectNavigationMode(activity);

            Window window = activity.getWindow();

            if (window == null) {
                return;
            }

            WindowInsetsControllerCompat controller =
                    WindowCompat.getInsetsController(
                            window,
                            window.getDecorView()
                    );

            if (controller == null) {
                return;
            }

            // 👑 دائماً استخدم Navigation Bar مستقراً
            // وليس Transient Overlay
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_SWIPE
            );

            if (detectedNavigationMode == 2) {

                // 👑 Gesture = ظاهر دائماً
                cancelNavigationBarHide();

                controller.show(
                        androidx.core.view.WindowInsetsCompat.Type.navigationBars()
                );

            } else {

                // 👑 Buttons = يظهر ثم يبدأ 5 ثوانٍ
                controller.show(
                        androidx.core.view.WindowInsetsCompat.Type.navigationBars()
                );

                scheduleNavigationBarHide(activity);
            }
        });
    }

    // =========================================================
    // 👑 الدعم البرمجي للتوافقية (Stub Methods لمنع كسر الملفات الأخرى)
    // =========================================================
    public static void lockStatusBarIcons() {
        // تم إلغاء القفل لتجنب حظر تحديث الأيقونات
    }

    public static void unlockStatusBarIcons() {
        // تم التحرير دائماً
    }

    // =========================================================
    // 1. تفعيل وضع "الملك" الناتيف (Edge-to-Edge بصفاء تام)
    // =========================================================
    public static void applyKingMode(
            FragmentActivity activity,
            WebView webView,
            int initialColor
    ) {
        if (activity == null) return;

        Window window = activity.getWindow();

        // تمديد النافذة ملء الشاشة مع تثبيت الشفافية لأندرويد 15
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        // 👑 كسر تدخل النظام التلقائي وحظر التباين القسري
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
            window.setStatusBarContrastEnforced(false);
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        View content = activity.findViewById(android.R.id.content);
        if (content != null) {
            ViewCompat.setOnApplyWindowInsetsListener(content, null);
            content.setPadding(0, 0, 0, 0);
        }

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());

        if (controller != null) {
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_SWIPE
            );
        }

        // 👑 تشغيل نظام التنقل الذكي
        initializeNavigationBarController(activity);

        // 👑 منع الـ Transient Navigation Overlay
        // وجعل ظهور شريط النظام يمر عبر Insets الطبيعية
        enforceStableNavigationBarPolicy(activity);

        // تطبيق اللون الأولي المباشر
        applyHeaderColor(activity, initialColor);
    }

    // =========================================================
    // 👑 2. المالك والجهة الموحدة الحقيقية لتلوين الهيدر والأيقونات
    // =========================================================
    public static void applyHeaderColor(
            android.app.Activity activity,
            int targetColor
    ) {
        applyHeaderColorInternal(activity, targetColor, true);
    }

    /**
     * دالة داخلية تتيح تلوين الشريط الناتيف مع خيار التحكم في تحديث الأيقونات
     */
    private static void applyHeaderColorInternal(
            android.app.Activity activity,
            int targetColor,
            boolean updateIcons
    ) {
        if (activity == null || activity.isFinishing()) return;

        activity.runOnUiThread(() -> {
            Window window = activity.getWindow();
            if (window == null) return;

            window.setStatusBarColor(Color.TRANSPARENT);

            int defaultBg = getDefaultSystemColor(activity);
            int solidColor = compositeColorWithBackground(targetColor, defaultBg);

            View topSurface = activity.findViewById(android.R.id.content)
                    .findViewWithTag("TOP_VISUAL_SURFACE");

            if (topSurface != null) {
                topSurface.setBackgroundColor(solidColor);
            }

            // 👑 تحديث الأيقونات فقط إذا أُمرت الدالة بذلك (يُؤجل لما بعد الـ Splash)
            if (updateIcons) {

                boolean isLightHeader =
                        isColorLight(solidColor);

                // 👑 Status Bar Icons
                setStatusBarIconsInternal(
                        window,
                        isLightHeader
                );

                // 👑 Navigation Bar Icons / Gesture Handle
                WindowInsetsControllerCompat navigationController =
                        WindowCompat.getInsetsController(
                                window,
                                window.getDecorView()
                        );

                if (navigationController != null) {

                    navigationController.setAppearanceLightNavigationBars(
                            isLightHeader
                    );
                }
            }

            currentHeaderColor = solidColor;
        });
    }

    // =========================================================
    // 👑 3. إدارة أيقونات النظام الناتيفية النقية (بدون أقفال)
    // =========================================================
    private static void setStatusBarIconsInternal(
            Window window,
            boolean lightBackground
    ) {
        if (window == null) return;

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());

        if (controller != null) {
            // true = خلفية فاتحة (أيقونات سوداء)
            // false = خلفية داكنة (أيقونات بيضاء)
            controller.setAppearanceLightStatusBars(lightBackground);
        }
    }

    public static void setStatusBarIcons(
            Window window,
            boolean lightBackground
    ) {
        setStatusBarIconsInternal(window, lightBackground);
    }

    public static void setDynamicIcons(
            Window window,
            boolean isLightBackground
    ) {
        setStatusBarIconsInternal(window, isLightBackground);
    }

    // =========================================================
    // 👑 4. تحديث الأوفلاين والواجهات الناتيفية دون تعارض
    // =========================================================
    public static void forceNativeStatusBar(
            android.app.Activity activity,
            int color
    ) {
        if (activity == null || activity.isFinishing()) return;

        syncGeneration++;
        cancelStatusBarSync();

        applyHeaderColor(activity, color);
    }

    public static void syncWithNativeUI(
            android.app.Activity activity,
            int uiColor
    ) {
        forceNativeStatusBar(activity, uiColor);
    }

    public static void makeStatusBarTransparent(
            android.app.Activity activity,
            int underlyingColor
    ) {
        applyHeaderColor(activity, underlyingColor);
    }

    public static void updateStatusBarColor(
            android.app.Activity activity,
            int targetColor
    ) {
        applyHeaderColor(activity, targetColor);
    }

    public static void restoreHeaderOnResume(android.app.Activity activity) {
        if (currentHeaderColor != Integer.MIN_VALUE) {
            applyHeaderColor(activity, currentHeaderColor);
        } else {
            applyHeaderColor(activity, getDefaultSystemColor(activity));
        }
    }

    // =========================================================
    // 👑 5. المعادلة الرياضية المعيارية للتباين والسطوع (WCAG 2.1 Standard)
    // =========================================================
    public static boolean isColorLight(int color) {
        double red = Color.red(color) / 255.0;
        double green = Color.green(color) / 255.0;
        double blue = Color.blue(color) / 255.0;

        double r = (red <= 0.04045) ? red / 12.92 : Math.pow((red + 0.055) / 1.055, 2.4);
        double g = (green <= 0.04045) ? green / 12.92 : Math.pow((green + 0.055) / 1.055, 2.4);
        double b = (blue <= 0.04045) ? blue / 12.92 : Math.pow((blue + 0.055) / 1.055, 2.4);

        double luminance = (0.2126 * r) + (0.7152 * g) + (0.0722 * b);

        double blackContrast = (luminance + 0.05) / 0.05;
        double whiteContrast = 1.05 / (luminance + 0.05);

        // إذا كان التباين مع الأسود أفضل، تعتبر الخلفية فاتحة وتُطلب أيقونات سوداء (true)
        return blackContrast >= whiteContrast;
    }

    // =========================================================
    // 👑 6. محرك دمج الألوان لمنع الشفافية والتضارب التلقائي
    // =========================================================
    public static int compositeColorWithBackground(int foregroundColor, int backgroundColor) {
        int alpha = Color.alpha(foregroundColor);
        if (alpha == 255) return foregroundColor;
        if (alpha == 0) return backgroundColor;

        float a = alpha / 255.0f;
        int r = (int) (Color.red(foregroundColor) * a + Color.red(backgroundColor) * (1 - a));
        int g = (int) (Color.green(foregroundColor) * a + Color.green(backgroundColor) * (1 - a));
        int b = (int) (Color.blue(foregroundColor) * a + Color.blue(backgroundColor) * (1 - a));

        return Color.rgb(r, g, b);
    }

    // =========================================================
    // 👑 7. محرك مزامنة الويب مع محول Canvas Color Normalizer (محمي من الومضة السوداء)
    // =========================================================
    public static void syncStatusBarWithWeb(
            android.app.Activity activity,
            WebView webView
    ) {
        if (activity == null || webView == null) return;

        final long requestGeneration = syncGeneration;

        // 👑 لون النظام المبدئي كـ Fallback آمن لمنع إرجاع الأسود أثناء التهيئة
        String defaultHex = (currentHeaderColor != Integer.MIN_VALUE)
                ? String.format("#%06X", (0xFFFFFF & currentHeaderColor))
                : (isDarkMode(activity) ? "#12141C" : "#FFFFFF");

        String jsScript =
                "(function() {" +
                "  function normalizeColor(colorStr) {" +
                "    if (!colorStr) return null;" +
                "    try {" +
                "      var canvas = document.createElement('canvas');" +
                "      canvas.width = 1; canvas.height = 1;" +
                "      var ctx = canvas.getContext('2d');" +
                "      ctx.fillStyle = colorStr;" +
                "      return ctx.fillStyle;" +
                "    } catch(e) { return colorStr; }" +
                "  }" +
                "  function isBlackOrTransparent(colorStr) {" +
                "    if (!colorStr) return true;" +
                "    var c = colorStr.toLowerCase().replace(/\\s+/g, '');" +
                "    return c === 'transparent' || c === 'rgba(0,0,0,0)' || c === '#000000' || c === '#000' || c === 'rgb(0,0,0)';" +
                "  }" +
                "  function extractColor() {" +
                "    var metas = document.querySelectorAll('meta[name=\"theme-color\"]');" +
                "    for (var i = 0; i < metas.length; i++) {" +
                "      var m = metas[i];" +
                "      if (!m.media || window.matchMedia(m.media).matches) {" +
                "        if (m.content) {" +
                "          var normalized = normalizeColor(m.content);" +
                "          if (normalized && !isBlackOrTransparent(normalized)) return normalized;" +
                "        }" +
                "      }" +
                "    }" +
                "    var el = document.elementFromPoint(window.innerWidth / 2, 20);" +
                "    if (!el) el = document.querySelector('header') || document.querySelector('nav') || document.body;" +
                "    while (el && el !== document.documentElement) {" +
                "      var st = window.getComputedStyle(el);" +
                "      var bg = st.backgroundColor;" +
                "      if (bg && !isBlackOrTransparent(bg)) {" +
                "        return normalizeColor(bg);" +
                "      }" +
                "      el = el.parentElement;" +
                "    }" +
                "    var bodyBg = window.getComputedStyle(document.body).backgroundColor;" +
                "    if (bodyBg && !isBlackOrTransparent(bodyBg)) {" +
                "      return normalizeColor(bodyBg);" +
                "    }" +
                "    return '" + defaultHex + "';" +
                "  }" +
                "  return extractColor();" +
                "})();";

        webView.evaluateJavascript(jsScript, value -> {
            if (requestGeneration != syncGeneration) return;

            if (value == null || value.equals("null") || value.equals("\"null\"")) return;

            int parsedColor = parseColorString(activity, value);
            applyHeaderColor(activity, parsedColor);
        });
    }

    // =========================================================
    // 👑 7-B. المزامنة المبكرة الخفية (تصبغ الشريط تحت الـ Splash دون مساس بالأيقونات)
    // =========================================================
    public static void syncStatusBarWithWebEarly(
            android.app.Activity activity,
            WebView webView
    ) {
        if (activity == null || webView == null) return;

        final long requestGeneration = syncGeneration;
        String defaultHex = (currentHeaderColor != Integer.MIN_VALUE)
                ? String.format("#%06X", (0xFFFFFF & currentHeaderColor))
                : (isDarkMode(activity) ? "#12141C" : "#FFFFFF");

        String jsScript =
                "(function() {" +
                "  function normalizeColor(colorStr) {" +
                "    if (!colorStr) return null;" +
                "    try {" +
                "      var canvas = document.createElement('canvas');" +
                "      canvas.width = 1; canvas.height = 1;" +
                "      var ctx = canvas.getContext('2d');" +
                "      ctx.fillStyle = colorStr;" +
                "      return ctx.fillStyle;" +
                "    } catch(e) { return colorStr; }" +
                "  }" +
                "  function isBlackOrTransparent(colorStr) {" +
                "    if (!colorStr) return true;" +
                "    var c = colorStr.toLowerCase().replace(/\\s+/g, '');" +
                "    return c === 'transparent' || c === 'rgba(0,0,0,0)' || c === '#000000' || c === '#000' || c === 'rgb(0,0,0)';" +
                "  }" +
                "  function extractColor() {" +
                "    var metas = document.querySelectorAll('meta[name=\"theme-color\"]');" +
                "    for (var i = 0; i < metas.length; i++) {" +
                "      var m = metas[i];" +
                "      if (!m.media || window.matchMedia(m.media).matches) {" +
                "        if (m.content) {" +
                "          var normalized = normalizeColor(m.content);" +
                "          if (normalized && !isBlackOrTransparent(normalized)) return normalized;" +
                "        }" +
                "      }" +
                "    }" +
                "    var el = document.elementFromPoint(window.innerWidth / 2, 20);" +
                "    if (!el) el = document.querySelector('header') || document.querySelector('nav') || document.body;" +
                "    while (el && el !== document.documentElement) {" +
                "      var st = window.getComputedStyle(el);" +
                "      var bg = st.backgroundColor;" +
                "      if (bg && !isBlackOrTransparent(bg)) {" +
                "        return normalizeColor(bg);" +
                "      }" +
                "      el = el.parentElement;" +
                "    }" +
                "    var bodyBg = window.getComputedStyle(document.body).backgroundColor;" +
                "    if (bodyBg && !isBlackOrTransparent(bodyBg)) {" +
                "      return normalizeColor(bodyBg);" +
                "    }" +
                "    return '" + defaultHex + "';" +
                "  }" +
                "  return extractColor();" +
                "})();";

        webView.evaluateJavascript(jsScript, value -> {
            if (requestGeneration != syncGeneration) return;
            if (value == null || value.equals("null") || value.equals("\"null\"")) return;

            int parsedColor = parseColorString(activity, value);
            // 👑 تغيير لون الشريط الناتيف فقط دون اللمس بالأيقونات
            applyHeaderColorInternal(activity, parsedColor, false);
        });
    }

    public static void scheduleStatusBarSync(
            android.app.Activity activity,
            WebView webView
    ) {
        if (activity == null || webView == null) return;

        cancelStatusBarSync();
        syncGeneration++;

        final long scheduledGeneration = syncGeneration;

        syncTask = () -> {
            if (activity.isFinishing()) return;
            if (webView.getVisibility() != View.VISIBLE) return;
            if (scheduledGeneration != syncGeneration) return;

            syncStatusBarWithWeb(activity, webView);
        };

        SYNC_HANDLER.postDelayed(syncTask, 80L);
    }

    public static void cancelStatusBarSync() {
        if (syncTask != null) {
            SYNC_HANDLER.removeCallbacks(syncTask);
            syncTask = null;
        }
    }

    // =========================================================
    // 👑 8. تحويل الألوان والتعامل مع الشفافية RGBA
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

                if (parts.length >= 4) {
                    float a = Float.parseFloat(parts[3].trim());
                    int alphaInt = Math.round(a * 255);
                    return Color.argb(alphaInt, r, g, b);
                }
                return Color.rgb(r, g, b);
            }
        } catch (Exception e) {
            Log.w(TAG, "Color parsing fallback triggered for: " + colorStr, e);
        }
        return defaultColor;
    }

    // =========================================================
    // 👑 9. استشعار ثيم النظام الافتراضي (Dark / Light)
    // =========================================================
    public static boolean isDarkMode(android.content.Context context) {
        if (context == null) return false;
        int nightModeFlags = context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    public static int getDefaultSystemColor(android.content.Context context) {
        return isDarkMode(context) ? Color.parseColor("#12141C") : Color.WHITE;
    }

    // =========================================================
    // 👑 إعادة تشغيل مؤقت Navigation Bar عند تفاعل المستخدم
    // =========================================================
    public static void notifyNavigationUserInteraction(
            android.app.Activity activity
    ) {
        if (activity == null ||
                activity.isFinishing() ||
                !navigationBarControllerReady) {
            return;
        }

        if (detectedNavigationMode == 2) {
            cancelNavigationBarHide();
            return;
        }

        Window window = activity.getWindow();

        if (window == null) {
            return;
        }

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(
                        window,
                        window.getDecorView()
                );

        if (controller != null) {

            // 👑 منع ظهور Navigation Bar كطبقة Transient فوق Offline Bar
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_SWIPE
            );

            // إظهار الشريط فوراً
            controller.show(
                    androidx.core.view.WindowInsetsCompat.Type.navigationBars()
            );

            // 👑 إعادة عداد الـ 5 ثوانٍ من الصفر
            scheduleNavigationBarHide(activity);
        }
    }

    // =========================================================
    // 👑 Navigation Bar Visibility Monitor
    // =========================================================

    public static void onNavigationBarVisibilityChanged(
            android.app.Activity activity,
            boolean visible
    ) {

        if (activity == null ||
                activity.isFinishing()) {
            return;
        }

        if (!navigationBarControllerReady) {
            return;
        }

        if (detectedNavigationMode == 2) {

            // Gesture Navigation:
            // ممنوع تشغيل أي Hide Timer
            cancelNavigationBarHide();
            return;
        }

        if (visible) {

            // 👑 ظهرت أزرار النظام → أعد عداد الـ 5 ثوانٍ
            scheduleNavigationBarHide(activity);
        }
    }
        }
