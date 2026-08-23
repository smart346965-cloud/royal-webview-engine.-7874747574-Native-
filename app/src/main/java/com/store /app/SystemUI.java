package com.store.app;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.webkit.WebView;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * 👑 SystemUI
 *
 * المالك الوحيد لحالة Status Bar.
 *
 * المسؤوليات:
 * 1. Edge-to-edge.
 * 2. شفافية Status Bar.
 * 3. اختيار لون الأيقونات.
 * 4. إدارة مالك الحالة:
 *      WEB / NATIVE
 * 5. تشخيص تضارب الكتابة.
 *
 * لا يقوم هذا الملف بتلوين Views عشوائية.
 * ولا يستخدم systemUiVisibility كمسار ثانٍ.
 */
public final class SystemUI {

    private static final String TAG = "ROYAL_UI_DIAG";

    private static final int OWNER_WEB = 0;
    private static final int OWNER_NATIVE = 1;

    private static int owner = OWNER_WEB;

    private static int currentBackground =
            Integer.MIN_VALUE;

    private static boolean currentLightIcons = false;

    private static long generation = 0L;

    private static final Handler HANDLER =
            new Handler(Looper.getMainLooper());

    private static Runnable syncTask;

    private SystemUI() {
        // Utility class.
    }

    // =========================================================
    // 👑 EDGE TO EDGE
    // =========================================================

    public static void applyKingMode(
            androidx.fragment.app.FragmentActivity activity,
            WebView webView,
            int initialColor
    ) {

        if (activity == null) {
            return;
        }

        Window window = activity.getWindow();

        WindowCompat.setDecorFitsSystemWindows(
                window,
                false
        );

        /*
         * Android 15:
         * Status Bar يجب أن تكون شفافة.
         */
        window.setStatusBarColor(
                Color.TRANSPARENT
        );

        window.setNavigationBarColor(
                Color.TRANSPARENT
        );

        if (android.os.Build.VERSION.SDK_INT >=
                android.os.Build.VERSION_CODES.Q) {

            window.setStatusBarContrastEnforced(
                    false
            );

            window.setNavigationBarContrastEnforced(
                    false
            );
        }

        log(
                "KING_MODE",
                "Edge-to-edge enabled | API="
                        + android.os.Build.VERSION.SDK_INT
        );
    }

    // =========================================================
    // 👑 ICONS
    // =========================================================

    public static void setStatusBarIcons(
            Window window,
            boolean lightBackground
    ) {

        if (window == null) {
            return;
        }

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(
                        window,
                        window.getDecorView()
                );

        if (controller == null) {
            log(
                    "ICON_CONTROLLER",
                    "ERROR: WindowInsetsControllerCompat = null"
            );
            return;
        }

        /*
         * true  = خلفية فاتحة → أيقونات سوداء
         * false = خلفية داكنة → أيقونات بيضاء
         */
        controller.setAppearanceLightStatusBars(
                lightBackground
        );

        currentLightIcons =
                lightBackground;

        log(
                "ICON_APPLY",
                "Icons="
                        + (lightBackground
                        ? "DARK"
                        : "LIGHT")
        );
    }

    public static void setDynamicIcons(
            Window window,
            boolean isLightBackground
    ) {

        setStatusBarIcons(
                window,
                isLightBackground
        );
    }

    // =========================================================
    // 👑 COLOR ANALYSIS
    // =========================================================

    public static boolean isColorLight(int color) {

        double red =
                Color.red(color) / 255.0;

        double green =
                Color.green(color) / 255.0;

        double blue =
                Color.blue(color) / 255.0;

        double r =
                red <= 0.04045
                        ? red / 12.92
                        : Math.pow(
                        (red + 0.055) / 1.055,
                        2.4
                );

        double g =
                green <= 0.04045
                        ? green / 12.92
                        : Math.pow(
                        (green + 0.055) / 1.055,
                        2.4
                );

        double b =
                blue <= 0.04045
                        ? blue / 12.92
                        : Math.pow(
                        (blue + 0.055) / 1.055,
                        2.4
                );

        double luminance =
                (0.2126 * r)
                        + (0.7152 * g)
                        + (0.0722 * b);

        double blackContrast =
                (luminance + 0.05) / 0.05;

        double whiteContrast =
                1.05 / (luminance + 0.05);

        return blackContrast >= whiteContrast;
    }

    // =========================================================
    // 👑 NATIVE OWNER
    // =========================================================

    public static void forceNativeStatusBar(
            Activity activity,
            int color
    ) {

        if (activity == null ||
                activity.isFinishing()) {
            return;
        }

        activity.runOnUiThread(() -> {

            owner = OWNER_NATIVE;

            generation++;

            cancelStatusBarSync();

            applyHeaderColorInternal(
                    activity,
                    color,
                    "NATIVE"
            );

            diagnostic(
                    "NATIVE_OWNER",
                    activity
            );
        });
    }

    public static void syncWithNativeUI(
            Activity activity,
            int uiColor
    ) {

        forceNativeStatusBar(
                activity,
                uiColor
        );
    }

    // =========================================================
    // 👑 WEB OWNER
    // =========================================================

    public static void scheduleStatusBarSync(
            Activity activity,
            WebView webView
    ) {

        if (activity == null ||
                webView == null) {
            return;
        }

        cancelStatusBarSync();

        /*
         * مهم جدًا:
         *
         * لا نغير owner هنا.
         *
         * لأن Offline UI قد تكون مالكة للـ Status Bar.
         */
        final long requestedGeneration =
                generation;

        syncTask = () -> {

            if (activity.isFinishing()) {
                return;
            }

            if (webView.getVisibility()
                    != View.VISIBLE) {

                log(
                        "WEB_SYNC",
                        "SKIP: WebView not visible"
                );

                return;
            }

            if (owner != OWNER_WEB) {

                log(
                        "WEB_SYNC",
                        "BLOCKED: Native UI owns Status Bar"
                );

                return;
            }

            if (requestedGeneration != generation) {

                log(
                        "WEB_SYNC",
                        "BLOCKED: generation changed"
                );

                return;
            }

            syncStatusBarWithWeb(
                    activity,
                    webView
            );
        };

        HANDLER.postDelayed(
                syncTask,
                80L
        );
    }

    public static void cancelStatusBarSync() {

        if (syncTask != null) {

            HANDLER.removeCallbacks(
                    syncTask
            );

            syncTask = null;
        }
    }

    // =========================================================
    // 👑 WEB COLOR EXTRACTION
    // =========================================================

    public static void syncStatusBarWithWeb(
            Activity activity,
            WebView webView
    ) {

        if (activity == null ||
                webView == null) {
            return;
        }

        if (owner != OWNER_WEB) {

            log(
                    "WEB_COLOR",
                    "BLOCKED: Native owner"
            );

            return;
        }

        final long requestGeneration =
                generation;

        final String defaultHex =
                isDarkMode(activity)
                        ? "#121212"
                        : "#FFFFFF";

        String script =
                "(function(){"

                + "function norm(c){"
                + "try{"
                + "var x=document.createElement('canvas');"
                + "x.width=1;"
                + "x.height=1;"
                + "var ctx=x.getContext('2d');"
                + "ctx.fillStyle=c;"
                + "return ctx.fillStyle;"
                + "}catch(e){return null;}"
                + "}"

                + "var m=document.querySelector("
                + "'meta[name=\"theme-color\"]');"

                + "if(m&&m.content){"
                + "var c=norm(m.content);"
                + "if(c)return c;"
                + "}"

                + "var el=document.elementFromPoint("
                + "window.innerWidth/2,20);"

                + "while(el&&el!==document.documentElement){"

                + "var s=getComputedStyle(el);"
                + "var bg=s.backgroundColor;"

                + "if(bg&&bg!=='transparent'&&"
                + "bg!=='rgba(0, 0, 0, 0)'){"
                + "var n=norm(bg);"
                + "if(n)return n;"
                + "}"

                + "el=el.parentElement;"
                + "}"

                + "return norm(getComputedStyle(document.body)"
                + ".backgroundColor)||'"
                + defaultHex
                + "';"

                + "})();";

        webView.evaluateJavascript(
                script,
                value -> {

                    if (owner != OWNER_WEB) {

                        log(
                                "WEB_RESULT",
                                "DISCARDED: Native owner"
                        );

                        return;
                    }

                    if (requestGeneration !=
                            generation) {

                        log(
                                "WEB_RESULT",
                                "DISCARDED: stale generation"
                        );

                        return;
                    }

                    int color =
                            parseColorString(
                                    activity,
                                    value
                            );

                    applyHeaderColorInternal(
                            activity,
                            color,
                            "WEB"
                    );
                }
        );
    }

    // =========================================================
    // 👑 UNIFIED COLOR APPLICATION
    // =========================================================

    private static void applyHeaderColorInternal(
            Activity activity,
            int color,
            String source
    ) {

        if (activity == null ||
                activity.isFinishing()) {
            return;
        }

        Window window =
                activity.getWindow();

        if (window == null) {
            return;
        }

        /*
         * Status Bar شفافة دائمًا.
         *
         * لا نحاول تلوين Window نفسها.
         */
        window.setStatusBarColor(
                Color.TRANSPARENT
        );

        boolean light =
                isColorLight(color);

        setStatusBarIcons(
                window,
                light
        );

        currentBackground = color;

        diagnostic(
                "APPLY[" + source + "]",
                activity
        );
    }

    public static void applyHeaderColor(
            Activity activity,
            int targetColor
    ) {

        if (activity == null) {
            return;
        }

        activity.runOnUiThread(() ->
                applyHeaderColorInternal(
                        activity,
                        targetColor,
                        "DIRECT"
                )
        );
    }

    public static void updateStatusBarColor(
            Activity activity,
            int targetColor
    ) {

        applyHeaderColor(
                activity,
                targetColor
        );
    }

    // =========================================================
    // 👑 RESUME
    // =========================================================

    public static void restoreHeaderOnResume(
            Activity activity
    ) {

        if (activity == null) {
            return;
        }

        int color =
                currentBackground !=
                        Integer.MIN_VALUE
                        ? currentBackground
                        : getDefaultSystemColor(activity);

        applyHeaderColorInternal(
                activity,
                color,
                "RESUME"
        );

        diagnostic(
                "RESUME",
                activity
        );
    }

    // =========================================================
    // 👑 TRANSPARENT
    // =========================================================

    public static void makeStatusBarTransparent(
            Activity activity,
            int underlyingColor
    ) {

        if (activity == null ||
                activity.isFinishing()) {
            return;
        }

        activity.runOnUiThread(() -> {

            Window window =
                    activity.getWindow();

            if (window == null) {
                return;
            }

            window.setStatusBarColor(
                    Color.TRANSPARENT
            );

            setStatusBarIcons(
                    window,
                    isColorLight(
                            underlyingColor
                    )
            );

            currentBackground =
                    underlyingColor;

            diagnostic(
                    "TRANSPARENT",
                    activity
            );
        });
    }

    // =========================================================
    // 👑 COLOR PARSER
    // =========================================================

    public static int parseColorString(
            Context context,
            String colorStr
    ) {

        int fallback =
                getDefaultSystemColor(context);

        if (colorStr == null) {
            return fallback;
        }

        colorStr =
                colorStr
                        .replace("\"", "")
                        .trim();

        try {

            if (colorStr.startsWith("#")) {
                return Color.parseColor(
                        colorStr
                );
            }

            if (colorStr.startsWith("rgb")) {

                int start =
                        colorStr.indexOf("(");

                int end =
                        colorStr.indexOf(")");

                String[] parts =
                        colorStr
                                .substring(
                                        start + 1,
                                        end
                                )
                                .split(",");

                int r =
                        Integer.parseInt(
                                parts[0].trim()
                        );

                int g =
                        Integer.parseInt(
                                parts[1].trim()
                        );

                int b =
                        Integer.parseInt(
                                parts[2].trim()
                        );

                return Color.rgb(
                        r,
                        g,
                        b
                );
            }

        } catch (Throwable ignored) {
        }

        return fallback;
    }

    // =========================================================
    // 👑 DARK MODE
    // =========================================================

    public static boolean isDarkMode(
            Context context
    ) {

        if (context == null) {
            return false;
        }

        int mode =
                context.getResources()
                        .getConfiguration()
                        .uiMode
                        & Configuration.UI_MODE_NIGHT_MASK;

        return mode ==
                Configuration.UI_MODE_NIGHT_YES;
    }

    public static int getDefaultSystemColor(
            Context context
    ) {

        return isDarkMode(context)
                ? Color.parseColor("#121212")
                : Color.WHITE;
    }

    // =========================================================
    // 👑 DIAGNOSTIC
    // =========================================================

    private static void log(
            String step,
            String message
    ) {

        Log.i(
                TAG,
                "STEP[" + step + "] " + message
        );
    }

    private static void diagnostic(
            String step,
            Activity activity
    ) {

        Window window =
                activity.getWindow();

        String color =
                currentBackground ==
                        Integer.MIN_VALUE
                        ? "UNSET"
                        : String.format(
                        "#%08X",
                        currentBackground
                );

        log(
                step,
                "owner="
                        + (owner == OWNER_NATIVE
                        ? "NATIVE"
                        : "WEB")
                        + " | color="
                        + color
                        + " | icons="
                        + (currentLightIcons
                        ? "DARK"
                        : "LIGHT")
                        + " | generation="
                        + generation
                        + " | statusBar="
                        + String.format(
                        "#%08X",
                        window.getStatusBarColor()
                );
    }

    /**
     * 👑 اطبع التشخيص الكامل يدويًا عند الحاجة.
     */
    public static void dumpDiagnostic(
            Activity activity
    ) {

        if (activity == null) {
            Log.e(
                    TAG,
                    "STEP[DUMP] Activity=null"
            );
            return;
        }

        diagnostic(
                "DUMP",
                activity
        );

        log(
                "DUMP",
                "API="
                        + android.os.Build.VERSION.SDK_INT
                        + " | darkMode="
                        + isDarkMode(activity)
        );
    }
                }
