package com.store.app;

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
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.FragmentActivity;

public class SystemUI {

    private static int lastSyncedStatusBarColor =
            Integer.MIN_VALUE;

    // 👑 الحافظ المصدر الموحد للون الهيدر الحالي
    private static int currentHeaderColor =
            Integer.MIN_VALUE;

    /**
     * 👑 مصدر ملكية الـ Status Bar
     *
     * 0 = WebView
     * 1 = Native / Offline UI
     */
    private static int statusBarOwner = 0;

    /**
     * 👑 رقم جيل المزامنة
     *
     * يمنع نتيجة JavaScript قديمة من WebView
     * من الكتابة فوق Offline UI بعد ظهورها.
     */
    private static long syncGeneration = 0L;

    // =========================================================
    // 👑 إلغاء Animation قيد التشغيل
    // =========================================================
    private static void cancelStatusBarColorAnimation(Window window) {
        if (window == null) return;

        View decorView = window.getDecorView();

        Object animator = decorView.getTag();

        if (animator instanceof android.animation.ValueAnimator) {
            ((android.animation.ValueAnimator) animator).cancel();
        }

        decorView.setTag(null);
    }

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
        // 👑 شريط نظام شفاف دائماً (متوافق 100% مع Android 15 Edge-to-Edge)
        // =========================================================

        window.setStatusBarColor(Color.TRANSPARENT);

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

    // =========================================================
    // 👑 التحكم الاحترافي بأيقونات شريط الحالة
    // =========================================================
    public static void setDynamicIcons(
            android.view.Window window,
            boolean isLightBackground
    ) {
        if (window == null) return;

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(
                        window,
                        window.getDecorView()
                );

        if (controller != null) {

            controller.setAppearanceLightStatusBars(
                    isLightBackground
            );
        }

        /*
         * 👑 Android Legacy / Compatibility Layer
         *
         * بعض إصدارات Android / بعض التركيبات مع Edge-to-Edge
         * قد لا تطبق controller وحده بالشكل المتوقع.
         *
         * لذلك نطبق نفس القرار أيضًا عبر systemUiVisibility.
         */
        if (android.os.Build.VERSION.SDK_INT >=
                android.os.Build.VERSION_CODES.M) {

            View decorView = window.getDecorView();

            int flags = decorView.getSystemUiVisibility();

            if (isLightBackground) {

                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;

            } else {

                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }

            decorView.setSystemUiVisibility(flags);
        }
    }

    // =========================================================
    // 👑 STATUS BAR ONLY — إدارة أيقونات شريط الحالة فقط
    // =========================================================
    public static void setStatusBarIcons(
            android.view.Window window,
            boolean lightBackground
    ) {

        if (window == null) return;

        android.util.Log.i(
                "ROYAL_UI_DIAG",
                "STEP[ICON_REQUEST] "
                        + "decision="
                        + (lightBackground ? "DARK" : "LIGHT")
                        + " | caller=SystemUI"
        );

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(
                        window,
                        window.getDecorView()
                );

        if (controller != null) {
            controller.setAppearanceLightStatusBars(
                    lightBackground
            );
        }

        if (android.os.Build.VERSION.SDK_INT >=
                android.os.Build.VERSION_CODES.M) {

            View decorView = window.getDecorView();

            int flags = decorView.getSystemUiVisibility();

            if (lightBackground) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }

            decorView.setSystemUiVisibility(flags);
        }
    }

    // =========================================================
    // 👑 Force Native Status Bar (الأوفلاين والواجهات الناتيفية عبر الدالة الموحدة)
    // =========================================================
    public static void forceNativeStatusBar(
            android.app.Activity activity,
            int color
    ) {
        if (activity == null || activity.isFinishing()) return;

        Runnable apply = () -> {
            if (activity.isFinishing()) return;

            statusBarOwner = 1;
            cancelStatusBarSync();
            syncGeneration++;

            // تطبيق اللون والأيقونات عبر المالك الموحد المباشر
            applyHeaderColor(activity, color);
        };

        if (Looper.myLooper() == Looper.getMainLooper()) {
            apply.run();
        } else {
            activity.runOnUiThread(apply);
        }
    }

    // =========================================================
    // 👑 تحديد أفضل لون لأيقونات Status Bar
    // بناءً على أعلى Contrast Ratio
    // =========================================================
    public static boolean isColorLight(int color) {

        double red =
                Color.red(color) / 255.0;

        double green =
                Color.green(color) / 255.0;

        double blue =
                Color.blue(color) / 255.0;

        double r =
                (red <= 0.04045)
                        ? red / 12.92
                        : Math.pow(
                                (red + 0.055) / 1.055,
                                2.4
                        );

        double g =
                (green <= 0.04045)
                        ? green / 12.92
                        : Math.pow(
                                (green + 0.055) / 1.055,
                                2.4
                        );

        double b =
                (blue <= 0.04045)
                        ? blue / 12.92
                        : Math.pow(
                                (blue + 0.055) / 1.055,
                                2.4
                        );

        double luminance =
                (0.2126 * r)
                        + (0.7152 * g)
                        + (0.0722 * b);

        // Contrast مع الأسود
        double blackContrast =
                (luminance + 0.05) / 0.05;

        // Contrast مع الأبيض
        double whiteContrast =
                1.05 / (luminance + 0.05);

        /*
         * إذا كان الأسود يعطي Contrast أفضل:
         *
         * return true
         *
         * لأن true تعني:
         * Light Background → Dark Icons
         */
        return blackContrast >= whiteContrast;
    }

    // =========================================================
    // 👑 المالك والدالة الموحدة الوحيدة لتلوين الهيدر وضبط الأيقونات
    // =========================================================
    public static void applyHeaderColor(
            android.app.Activity activity,
            int targetColor
    ) {
        if (activity == null || activity.isFinishing()) return;

        activity.runOnUiThread(() -> {
            Window window = activity.getWindow();
            if (window == null) return;

            cancelStatusBarColorAnimation(window);

            // 1. تثبيت الشفافية لمنع الومضات والتوافق التام مع أندرويد 15
            window.setStatusBarColor(Color.TRANSPARENT);

            // 2. استهداف مباشر وصريح لعنصر Top Visual Surface عبر الوسم (Tag)
            View topSurface = activity.findViewById(android.R.id.content)
                    .findViewWithTag("TOP_VISUAL_SURFACE");

            if (topSurface != null) {
                topSurface.setBackgroundColor(targetColor);
            } else {
                // البحث الاحتياطي في حال عدم إيجاد الوسم مباشرة
                View contentView = activity.findViewById(android.R.id.content);
                if (contentView instanceof android.view.ViewGroup) {
                    android.view.ViewGroup contentGroup = (android.view.ViewGroup) contentView;
                    if (contentGroup.getChildCount() > 0 && contentGroup.getChildAt(0) instanceof android.view.ViewGroup) {
                        android.view.ViewGroup rootGroup = (android.view.ViewGroup) contentGroup.getChildAt(0);
                        for (int i = 0; i < rootGroup.getChildCount(); i++) {
                            View child = rootGroup.getChildAt(i);
                            if (child.getLayoutParams().height > 0 && !(child instanceof WebView)) {
                                child.setBackgroundColor(targetColor);
                            }
                        }
                    }
                }
            }

            // 3. حساب التباين من اللون الناتيف المصبوغ (الهيدر خلف الشريط)
            android.util.Log.i(
                    "ROYAL_UI_DIAG",
                    "STEP[ICON_INPUT] "
                            + "color="
                            + String.format(
                                    "#%08X",
                                    targetColor
                            )
                            + " | source=applyHeaderColor"
            );

            boolean lightBackground = isColorLight(targetColor);

            android.util.Log.i(
                    "ROYAL_UI_DIAG",
                    "STEP[ICON_DECISION] "
                            + "background="
                            + String.format(
                                    "#%08X",
                                    targetColor
                            )
                            + " -> icons="
                            + (lightBackground
                            ? "DARK"
                            : "LIGHT")
            );

            // إذا كان اللون فاتح (أبيض أو ألوان فاتحة) → أيقونات سوداء
            // إذا كان اللون داكن (أسود أو ألوان داكنة) → أيقونات بيضاء
            setStatusBarIcons(window, lightBackground);

            currentHeaderColor = targetColor;
            lastSyncedStatusBarColor = targetColor;
        });
    }

    public static void updateStatusBarColor(
            android.app.Activity activity,
            int targetColor
    ) {
        applyHeaderColor(activity, targetColor);
    }

    // 👑 استعادة اللون والأيقونات المحفوظة فوراً عند العودة لمنع ظاهرة (أبيض على أبيض)
    public static void restoreHeaderOnResume(android.app.Activity activity) {
        if (currentHeaderColor != Integer.MIN_VALUE) {
            applyHeaderColor(activity, currentHeaderColor);
        } else {
            applyHeaderColor(activity, getDefaultSystemColor(activity));
        }
    }

    // =========================================================
    // 👑 ROYAL UI DIAGNOSTIC — WEB COLOR SOURCE
    // =========================================================

    private static void diagnoseWebColorSource(
            android.app.Activity activity,
            WebView webView
    ) {

        if (activity == null || webView == null) {
            return;
        }

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
                        + "}catch(e){return 'ERROR';}"
                        + "}"

                        + "function info(el,source){"
                        + "if(!el)return null;"
                        + "var s=getComputedStyle(el);"
                        + "return {"
                        + "source:source,"
                        + "tag:el.tagName,"
                        + "id:el.id||'',"
                        + "className:typeof el.className==='string'?el.className:'',"
                        + "background:s.backgroundColor,"
                        + "normalized:norm(s.backgroundColor),"
                        + "x:Math.round(window.innerWidth/2),"
                        + "y:20"
                        + "};"
                        + "}"

                        + "var result={};"

                        // 1. theme-color
                        + "var metas=document.querySelectorAll("
                        + "'meta[name=\"theme-color\"]');"

                        + "result.themeColors=[];"

                        + "for(var i=0;i<metas.length;i++){"
                        + "var m=metas[i];"
                        + "result.themeColors.push({"
                        + "content:m.content||'',"
                        + "media:m.media||'',"
                        + "active:!m.media||window.matchMedia(m.media).matches"
                        + "});"
                        + "}"

                        // 2. العنصر الحقيقي عند Y=20
                        + "var pointEl=document.elementFromPoint("
                        + "window.innerWidth/2,20);"

                        + "result.point=info(pointEl,'ELEMENT_FROM_POINT');"

                        // 3. header
                        + "var header=document.querySelector('header');"
                        + "result.header=info(header,'HEADER');"

                        // 4. nav
                        + "var nav=document.querySelector('nav');"
                        + "result.nav=info(nav,'NAV');"

                        // 5. body
                        + "result.body=info(document.body,'BODY');"

                        // 6. اللون الفعلي الذي سيختاره المحرك الحالي
                        + "result.selected='';"

                        + "var activeMeta=null;"

                        + "for(var j=0;j<metas.length;j++){"
                        + "var mm=metas[j];"
                        + "if(!mm.media||window.matchMedia(mm.media).matches){"
                        + "if(mm.content){"
                        + "activeMeta=norm(mm.content);"
                        + "break;"
                        + "}"
                        + "}"
                        + "}"

                        + "if(activeMeta){"
                        + "result.selected=activeMeta;"
                        + "result.selectedSource='THEME_COLOR';"
                        + "}else if(pointEl){"

                        + "var cur=pointEl;"

                        + "while(cur&&cur!==document.documentElement){"
                        + "var cs=getComputedStyle(cur);"
                        + "var bg=cs.backgroundColor;"

                        + "if(bg&&bg!=='transparent'&&"
                        + "bg!=='rgba(0, 0, 0, 0)'){"

                        + "result.selected=norm(bg);"
                        + "result.selectedSource='ELEMENT_FROM_POINT';"
                        + "result.selectedTag=cur.tagName;"
                        + "result.selectedId=cur.id||'';"
                        + "result.selectedClass="
                        + "typeof cur.className==='string'"
                        + "?cur.className:'';"

                        + "break;"
                        + "}"

                        + "cur=cur.parentElement;"
                        + "}"

                        + "}else{"

                        + "result.selected=norm("
                        + "getComputedStyle(document.body).backgroundColor"
                        + ");"

                        + "result.selectedSource='BODY';"

                        + "}"

                        + "return JSON.stringify(result);"

                        + "})();";

        webView.evaluateJavascript(
                script,
                value -> {

                    Log.i(
                            "ROYAL_UI_DIAG",
                            "STEP[WEB_SOURCE] " + value
                    );
                }
        );
    }

    // =========================================================
    // 👑 محرك الويب المطور لاستخراج الألوان مع Canvas Color Normalizer
    // =========================================================
    public static void syncStatusBarWithWeb(
            android.app.Activity activity,
            WebView webView
    ) {

        if (activity == null ||
                webView == null) {
            return;
        }

        /*
         * 👑 WebView يأخذ ملكية Status Bar
         * فقط إذا لم تكن Native UI مسيطرة عليها.
         */
        if (statusBarOwner != 0) {
            return;
        }

        diagnoseWebColorSource(
                activity,
                webView
        );

        final long requestGeneration =
                syncGeneration;

        String defaultHex =
                isDarkMode(activity)
                        ? "#121212"
                        : "#FFFFFF";

        String jsScript =
                "(function() {" +

                        "function normalizeColor(colorStr) {" +
                        "  if (!colorStr) return null;" +
                        "  try {" +
                        "    var canvas = document.createElement('canvas');" +
                        "    canvas.width = 1;" +
                        "    canvas.height = 1;" +
                        "    var ctx = canvas.getContext('2d');" +
                        "    ctx.fillStyle = colorStr;" +
                        "    return ctx.fillStyle;" +
                        "  } catch(e) {" +
                        "    return colorStr;" +
                        "  }" +
                        "}" +

                        "function extractColor() {" +

                        "  var metas = document.querySelectorAll(" +
                        "'meta[name=\"theme-color\"]');" +

                        "  for (var i = 0; i < metas.length; i++) {" +

                        "    var m = metas[i];" +

                        "    if (!m.media || window.matchMedia(m.media).matches) {" +

                        "      if (m.content) {" +
                        "        var normalized = normalizeColor(m.content);" +
                        "        if (normalized) return normalized;" +
                        "      }" +
                        "    }" +
                        "  }" +

                        "  var el = document.elementFromPoint(" +
                        "window.innerWidth / 2, 20);" +

                        "  if (!el) {" +
                        "    el = document.querySelector('header')" +
                        "      || document.querySelector('nav')" +
                        "      || document.body;" +
                        "  }" +

                        "  while (el && el !== document.documentElement) {" +

                        "    var st = window.getComputedStyle(el);" +
                        "    var bg = st.backgroundColor;" +

                        "    if (bg &&" +
                        "        bg !== 'transparent' &&" +
                        "        bg !== 'rgba(0, 0, 0, 0)') {" +

                        "      return normalizeColor(bg);" +
                        "    }" +

                        "    el = el.parentElement;" +
                        "  }" +

                        "  var bodyBg =" +
                        "window.getComputedStyle(document.body).backgroundColor;" +

                        "  return normalizeColor(bodyBg) ||" +
                        "'" + defaultHex + "';" +

                        "}" +

                        "return extractColor();" +

                        "})();";

        webView.evaluateJavascript(
                jsScript,
                value -> {

                    Log.i(
                            "ROYAL_UI_DIAG",
                            "STEP[WEB_RETURN] raw=" + value
                    );

                    /*
                     * 👑 حماية من Race Condition
                     *
                     * قد ترجع نتيجة JavaScript بعد أن أصبح
                     * Offline UI هو المالك.
                     */
                    if (statusBarOwner != 0) {
                        return;
                    }

                    if (requestGeneration != syncGeneration) {
                        return;
                    }

                    if (value == null ||
                            value.equals("null") ||
                            value.equals("\"null\"")) {
                        return;
                    }

                    int parsedColor =
                            parseColorString(
                                    activity,
                                    value
                            );

                    // تمرير اللون المستخرج لتلوين الجزء الناتيف + الأيقونات معًا
                    applyHeaderColor(
                            activity,
                            parsedColor
                    );
                }
        );
    }

    // =========================================================
    // 👑 Royal Visual Synchronization
    // =========================================================

    private static final Handler SYNC_HANDLER =
            new Handler(Looper.getMainLooper());

    private static Runnable syncTask;

    public static void scheduleStatusBarSync(
            android.app.Activity activity,
            WebView webView
    ) {

        if (activity == null ||
                webView == null) {
            return;
        }

        cancelStatusBarSync();

        statusBarOwner = 0;
        syncGeneration++;

        final long scheduledGeneration =
                syncGeneration;

        syncTask = () -> {

            if (activity.isFinishing()) {
                return;
            }

            if (webView.getVisibility()
                    != View.VISIBLE) {
                return;
            }

            if (statusBarOwner != 0) {
                return;
            }

            if (scheduledGeneration != syncGeneration) {
                return;
            }

            syncStatusBarWithWeb(
                    activity,
                    webView
            );
        };

        /*
         * تأخير صغير جدًا للسماح للـ WebView
         * بإكمال تثبيت الـ DOM / theme-color.
         */
        SYNC_HANDLER.postDelayed(
                syncTask,
                80L
        );
    }

    public static void cancelStatusBarSync() {

        if (syncTask != null) {

            SYNC_HANDLER.removeCallbacks(
                    syncTask
            );

            syncTask = null;
        }
    }

    // =========================================================
    // 👑 Offline UI → Status Bar Synchronization
    // يسمح لأي واجهة Native بإخبار SystemUI بلونها الحالي.
    // =========================================================
    public static void syncWithNativeUI(
            android.app.Activity activity,
            int uiColor
    ) {

        forceNativeStatusBar(
                activity,
                uiColor
        );
    }

    // =========================================================
    // 👑 Offline UI → Transparent Status Bar
    // يستخدم عندما تكون واجهة الأوفلاين مصممة Edge-to-Edge
    // وتريد أن يظهر محتوى الواجهة خلف Status Bar.
    // =========================================================
    public static void makeStatusBarTransparent(
            android.app.Activity activity,
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

            cancelStatusBarColorAnimation(window);

            boolean lightBackground =
                    isColorLight(underlyingColor);

            /*
             * الأيقونات أولاً.
             */
            setStatusBarIcons(
                    window,
                    lightBackground
            );

            /*
             * ثم الشفافية.
             */
            window.setStatusBarColor(
                    Color.TRANSPARENT
            );

            if (android.os.Build.VERSION.SDK_INT >=
                    android.os.Build.VERSION_CODES.Q) {

                window.setStatusBarContrastEnforced(
                        false
                );
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
