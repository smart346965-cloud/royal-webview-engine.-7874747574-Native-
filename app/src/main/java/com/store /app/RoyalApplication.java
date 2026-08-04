package com.store.app;

import android.app.Application;
import android.util.Log;

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

        // 🔥 الخطوة الثانية: التسخين المباشر للمحرك (يتم بالتزامن مع الخطوة الأولى)
        // بما أن startEngineAsync يعمل على خيط خلفي، فهذا التسخين يضمن
        // أن WebView جاهز فوراً عند الحاجة إليه
        RoyalWebViewHost.create(this);

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
