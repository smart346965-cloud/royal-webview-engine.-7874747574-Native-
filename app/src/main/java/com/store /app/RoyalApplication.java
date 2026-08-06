package com.store.app;

import android.app.Application;
import android.os.Looper;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebView;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

public class RoyalApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i("RoyalEngine", "🚀 Royal Application Ignite!");

        // 🌐 تشغيل رادار مراقبة الشبكة فوراً
        NetworkMonitor.init(this);

        // 👁️ تشغيل عقل الفحص الملكي
        RoyalPanopticon.startAwareness();

        // 🔥 الخطوة الأولى: تسخين نواة كروميوم بشكل غير متزامن
        // هذا يقلل وقت التهيئة بشكل كبير ويوزع الحمل على خيط خلفي
        RoyalWebViewHost.startEngineAsync(this);

        // 🔥 [التحسين 2]: تسخين خدمة الشبكة مبكراً (جديد)
        // هذا يقلل وقت التهيئة عند أول طلب شبكي
        RoyalNetworkEngine.warmupNetworkService(this);

        // 🔥 [التحسين 3]: تسخين Renderer (جديد)
        // يقوم بإنشاء Spare Renderer في الخلفية
        RoyalWebViewHost.prepareSpareRenderer(this);

        // 🔥 الخطوة الثانية: التسخين المباشر للمحرك (يتم بالتزامن مع الخطوة الأولى)
        // بما أن startEngineAsync يعمل على خيط خلفي، فهذا التسخين يضمن
        // أن WebView جاهز فوراً عند الحاجة إليه
        RoyalWebViewHost.create(this);

        // 🔥 [التحسين 4]: IdleHandler لتهيئة المهام الخفيفة أثناء خمول المعالج
        Looper.myQueue().addIdleHandler(() -> {
            try {
                // تهيئة CookieManager في الخلفية
                CookieManager.getInstance().setAcceptCookie(true);
                Log.i("RoyalEngine", "🍪 CookieManager initialized via IdleHandler.");

                // تسخين SafeBrowsing (إن كان مدعوماً)
                if (WebViewFeature.isFeatureSupported(WebViewFeature.START_SAFE_BROWSING)) {
                    WebViewCompat.startSafeBrowsing(this, value ->
                        Log.i("RoyalEngine", "🛡️ SafeBrowsing warmed up.")
                    );
                }
            } catch (Exception e) {
                Log.w("RoyalEngine", "IdleHandler task failed: " + e.getMessage());
            }
            return false; // تنفيذ مرة واحدة فقط
        });

        Log.i("RoyalEngine", "✅ All systems initialized. Ready for action.");
    }

    @Override
    public void onTerminate() {
        // إيقاف العقل الملكي وتنظيف الذاكرة
        RoyalPanopticon.stopAwareness();

        // تنظيف المحرك لمنع تسريب الذاكرة
        RoyalWebViewHost.destroy();

        super.onTerminate();
    }
}
