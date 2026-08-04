package com.store.app;

import android.app.Application;
import android.util.Log;

public class RoyalApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        
        Log.i("RoyalEngine", "🚀 Royal Application Ignite! Pre-warming WebView...");
        
        // 🌐 تشغيل رادار مراقبة الشبكة فوراً لخدمة الـ WebEngineManager والكاش
        NetworkMonitor.init(this);
        
        // 👁️ تشغيل عقل الفحص الملكي وبدء مراقبة خيط الواجهة الرئيسي (Main Looper)
        RoyalPanopticon.startAwareness();
        
        // هنا تكمن الخدعة الصاروخية: 
        // نقوم بتسخين وخلق محركك (RoyalWebViewHost) في الذاكرة في الجزء من الثانية 
        // الذي يلمس فيه المستخدم أيقونة التطبيق، قبل حتى أن تظهر شاشة السبلاش!
        RoyalWebViewHost.create(this);
    }

    @Override
    public void onTerminate() {
        // إيقاف المحرك وتنظيف الذاكرة عند إغلاق التطبيق كاملاً لمنع تلمظ الرام (Memory Leaks)
        RoyalPanopticon.stopAwareness();
        super.onTerminate();
    }
}
