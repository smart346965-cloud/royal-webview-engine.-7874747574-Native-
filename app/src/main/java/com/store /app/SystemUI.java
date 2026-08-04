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

    private static boolean isApplied = false;

    // 1. تفعيل وضع "الملك" مع الاستجابة اللحظية لإيماءات الرجوع (Single Swipe Back)
    public static void applyKingMode(FragmentActivity activity, WebView webView) {

        if (isApplied) return;
        isApplied = true;

        Window window = activity.getWindow();

        // 🚀 الخطوة 1: تمديد المحتوى خلف الأشرطة تماماً (Edge-to-Edge)
        // هذا يضمن أن الموقع يحتل الشاشة من الحافة إلى الحافة
        WindowCompat.setDecorFitsSystemWindows(window, false);

        // 🚀 الخطوة 2: جعل الأشرطة شفافة تماماً
        // السر هنا أننا لا "نحذف" شريط التنقل بل نجعله شفافاً ليبقى الرجوع شغالاً من أول سحبة
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
            window.setStatusBarContrastEnforced(false);
        }

        // 🚀 الخطوة 3: إزالة القيود البرمجية على الحواف
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        View content = activity.findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(content, (view, insets) -> {
            // تصفير الحواف لضمان تمدد الويب فيو 100%
            view.setPadding(0, 0, 0, 0); 
            return WindowInsetsCompat.CONSUMED;
        });

        // تشغيل محرك الاختفاء الذكي لشريط الحالة فقط لضمان حرية الرجوع
        enablePureImmersiveExperience(window);
    }

    private static void enablePureImmersiveExperience(Window window) {
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            // 🛡️ السلوك العابر: تظهر الأشرطة عند السحب من الأعلى أو الأسفل وتختفي تلقائياً
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

            // تأخير زمني (4.5 ثانية) ثم إخفاء شريط الحالة العلوي "فقط"
            // ملاحظة: ترك شريط التنقل السفلي "موجوداً وشفافاً" هو ما يحل مشكلة السحبتين
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                // إخفاء شريط الحالة (الساعة والبطارية)
                controller.hide(WindowInsetsCompat.Type.statusBars());
                
                // 💡 هندسة ذكية: لا نقوم بعمل hide لـ navigationBars() 
                // لأن ذلك هو ما يسبب قفل إيماءة الرجوع ويجعلها تحتاج سحبتين.
                // بما أننا جعلنا لونه TRANSPARENT في الأعلى، فهو غير مرئي والموقع خلفه.
            }, 4500);
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
