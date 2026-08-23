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

public final class SystemUI {

    private static final String TAG = "ROYAL_UI_DIAG";

    private static final int WEB = 0;
    private static final int NATIVE = 1;

    private static int owner = WEB;
    private static int currentColor = Integer.MIN_VALUE;
    private static boolean darkIcons = false;
    private static long generation = 0L;

    private static final Handler HANDLER =
            new Handler(Looper.getMainLooper());

    private static Runnable syncTask;

    private SystemUI() {}

    // =========================================================
    // EDGE TO EDGE
    // =========================================================

    public static void applyKingMode(
            androidx.fragment.app.FragmentActivity activity,
            WebView webView,
            int initialColor) {

        if (activity == null) return;

        Window window = activity.getWindow();

        WindowCompat.setDecorFitsSystemWindows(
                window,
                false
        );

        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        if (android.os.Build.VERSION.SDK_INT >=
                android.os.Build.VERSION_CODES.Q) {

            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }

        log("KING", "Edge-to-edge enabled");
    }

    // =========================================================
    // ICONS
    // =========================================================

    public static void setStatusBarIcons(
            Window window,
            boolean lightBackground) {

        if (window == null) return;

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(
                        window,
                        window.getDecorView()
                );

        if (controller == null) {
            log("ICONS", "ERROR: controller=null");
            return;
        }

        controller.setAppearanceLightStatusBars(
                lightBackground
        );

        darkIcons = lightBackground;

        log(
                "ICONS",
                lightBackground
                        ? "DARK icons"
                        : "LIGHT icons"
        );
    }

    public static void setDynamicIcons(
            Window window,
            boolean lightBackground) {

        setStatusBarIcons(
                window,
                lightBackground
        );
    }

    // =========================================================
    // COLOR ANALYSIS
    // =========================================================

    public static boolean isColorLight(int color) {

        double r = Color.red(color) / 255.0;
        double g = Color.green(color) / 255.0;
        double b = Color.blue(color) / 255.0;

        r = r <= 0.04045
                ? r / 12.92
                : Math.pow((r + 0.055) / 1.055, 2.4);

        g = g <= 0.04045
                ? g / 12.92
                : Math.pow((g + 0.055) / 1.055, 2.4);

        b = b <= 0.04045
                ? b / 12.92
                : Math.pow((b + 0.055) / 1.055, 2.4);

        double luminance =
                0.2126 * r +
                0.7152 * g +
                0.0722 * b;

        double black =
                (luminance + 0.05) / 0.05;

        double white =
                1.05 / (luminance + 0.05);

        return black >= white;
    }

    // =========================================================
    // NATIVE OWNER
    // =========================================================

    public static void forceNativeStatusBar(
            Activity activity,
            int color) {

        if (activity == null ||
                activity.isFinishing()) {
            return;
        }

        activity.runOnUiThread(() -> {

            owner = NATIVE;
            generation++;

            cancelStatusBarSync();

            applyHeaderColorInternal(
                    activity,
                    color,
                    "NATIVE"
            );
        });
    }

    public static void syncWithNativeUI(
            Activity activity,
            int color) {

        forceNativeStatusBar(
                activity,
                color
        );
    }

    // =========================================================
    // WEB SYNC
    // =========================================================

    public static void scheduleStatusBarSync(
            Activity activity,
            WebView webView) {

        if (activity == null ||
                webView == null) {
            return;
        }

        cancelStatusBarSync();

        final long requestGeneration =
                generation;

        syncTask = () -> {

            if (activity.isFinishing()) return;

            if (webView.getVisibility()
                    != View.VISIBLE) {

                log(
                        "WEB",
                        "SKIP: WebView invisible"
                );

                return;
            }

            if (owner != WEB) {

                log(
                        "WEB",
                        "BLOCKED: Native owner"
                );

                return;
            }

            if (requestGeneration != generation) {

                log(
                        "WEB",
                        "BLOCKED: stale generation"
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
            HANDLER.removeCallbacks(syncTask);
            syncTask = null;
        }
    }

    // =========================================================
    // WEB COLOR
    // =========================================================

    public static void syncStatusBarWithWeb(
            Activity activity,
            WebView webView) {

        if (activity == null ||
                webView == null ||
                owner != WEB) {
            return;
        }

        final long requestGeneration =
                generation;

        final String fallback =
                isDarkMode(activity)
                        ? "#121212"
                        : "#FFFFFF";

        String script =
                "(function(){"

                + "function n(c){"
                + "try{"
                + "var x=document.createElement('canvas');"
                + "var y=x.getContext('2d');"
                + "y.fillStyle=c;"
                + "return y.fillStyle;"
                + "}catch(e){return null;}"
                + "}"

                + "var m=document.querySelector("
                + "'meta[name=\"theme-color\"]');"

                + "if(m&&m.content){"
                + "var c=n(m.content);"
                + "if(c)return c;"
                + "}"

                + "var e=document.elementFromPoint("
                + "innerWidth/2,20);"

                + "while(e&&e!==document.documentElement){"
                + "var s=getComputedStyle(e);"
                + "var b=s.backgroundColor;"

                + "if(b&&b!=='transparent'&&"
                + "b!=='rgba(0, 0, 0, 0)'){"
                + "var c=n(b);"
                + "if(c)return c;"
                + "}"

                + "e=e.parentElement;"
                + "}"

                + "return n(getComputedStyle(document.body)"
                + ".backgroundColor)||'"
                + fallback
                + "';"
                + "})();";

        webView.evaluateJavascript(
                script,
                value -> {

                    if (owner != WEB) {
                        log(
                                "RESULT",
                                "DISCARDED: Native owner"
                        );
                        return;
                    }

                    if (requestGeneration != generation) {
                        log(
                                "RESULT",
                                "DISCARDED: stale"
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
    // UNIFIED APPLY
    // =========================================================

    private static void applyHeaderColorInternal(
            Activity activity,
            int color,
            String source) {

        if (activity == null ||
                activity.isFinishing()) {
            return;
        }

        Window window =
                activity.getWindow();

        if (window == null) return;

        window.setStatusBarColor(
                Color.TRANSPARENT
        );

        boolean light =
                isColorLight(color);

        setStatusBarIcons(
                window,
                light
        );

        currentColor = color;

        diagnostic(
                source,
                activity
        );
    }

    public static void applyHeaderColor(
            Activity activity,
            int color) {

        if (activity == null) return;

        activity.runOnUiThread(() ->
                applyHeaderColorInternal(
                        activity,
                        color,
                        "DIRECT"
                )
        );
    }

    public static void updateStatusBarColor(
            Activity activity,
            int color) {

        applyHeaderColor(
                activity,
                color
        );
    }

    // =========================================================
    // RESUME
    // =========================================================

    public static void restoreHeaderOnResume(
            Activity activity) {

        if (activity == null) return;

        int color =
                currentColor != Integer.MIN_VALUE
                        ? currentColor
                        : getDefaultSystemColor(activity);

        applyHeaderColorInternal(
                activity,
                color,
                "RESUME"
        );
    }

    // =========================================================
    // TRANSPARENT
    // =========================================================

    public static void makeStatusBarTransparent(
            Activity activity,
            int underlyingColor) {

        if (activity == null ||
                activity.isFinishing()) {
            return;
        }

        activity.runOnUiThread(() -> {

            Window window =
                    activity.getWindow();

            if (window == null) return;

            window.setStatusBarColor(
                    Color.TRANSPARENT
            );

            setStatusBarIcons(
                    window,
                    isColorLight(
                            underlyingColor
                    )
            );

            currentColor =
                    underlyingColor;

            diagnostic(
                    "TRANSPARENT",
                    activity
            );
        });
    }

    // =========================================================
    // PARSER
    // =========================================================

    public static int parseColorString(
            Context context,
            String value) {

        int fallback =
                getDefaultSystemColor(context);

        if (value == null) {
            return fallback;
        }

        value =
                value
                        .replace("\"", "")
                        .trim();

        try {

            if (value.startsWith("#")) {
                return Color.parseColor(value);
            }

            if (value.startsWith("rgb")) {

                int start =
                        value.indexOf("(");

                int end =
                        value.indexOf(")");

                String[] parts =
                        value.substring(
                                start + 1,
                                end
                        ).split(",");

                if (parts.length < 3) {
                    return fallback;
                }

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

                return Color.rgb(r, g, b);
            }

        } catch (Throwable e) {

            log(
                    "PARSER",
                    "ERROR: " + e.getMessage()
            );
        }

        return fallback;
    }

    // =========================================================
    // DARK MODE
    // =========================================================

    public static boolean isDarkMode(
            Context context) {

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
            Context context) {

        return isDarkMode(context)
                ? Color.parseColor("#121212")
                : Color.WHITE;
    }

    // =========================================================
    // DIAGNOSTIC
    // =========================================================

    private static void log(
            String step,
            String message) {

        Log.i(
                TAG,
                "STEP[" + step + "] " + message
        );
    }

    private static void diagnostic(
            String step,
            Activity activity) {

        Window window =
                activity.getWindow();

        String color =
                currentColor == Integer.MIN_VALUE
                        ? "UNSET"
                        : String.format(
                                "#%08X",
                                currentColor
                        );

        log(
                step,
                "owner="
                        + (owner == NATIVE
                        ? "NATIVE"
                        : "WEB")
                        + " | color="
                        + color
                        + " | icons="
                        + (darkIcons
                        ? "DARK"
                        : "LIGHT")
                        + " | generation="
                        + generation
                        + " | statusBar="
                        + String.format(
                                "#%08X",
                                window.getStatusBarColor()
                        )
        );
    }

    public static void dumpDiagnostic(
            Activity activity) {

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
