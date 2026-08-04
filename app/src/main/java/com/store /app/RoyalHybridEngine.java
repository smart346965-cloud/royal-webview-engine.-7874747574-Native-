package com.store.app;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;

/**
 * =========================================================
 * 👑 ROYAL HYBRID ENGINE (V2 - Runtime Controller)
 * =========================================================
 * - Process Priority Enforcement (OOM Protection)
 * - Offscreen Pre-Rasterization
 * - Chromium Native Cache Delegation
 * - Capacitor-Safe (Zero Plugin Breakage)
 */
public final class RoyalHybridEngine {

    private static final String TAG = "RoyalHybridEngine";
    private static boolean isEnginePrimed = false;

    // 🔒 منع إنشاء كائنات (Singleton Utility)
    private RoyalHybridEngine() {}

    /**
     * 🚀 نقطة الإشعال (Prime The Engine)
     * يتم استدعاؤها مرة واحدة فقط لتسليح الـ WebView.
     */
    public static void prime(WebView webView, Context context) {
        if (webView == null || isEnginePrimed) return;

        Log.i(TAG, "⚙️ Priming Royal Hybrid Engine V2 (Runtime Controller)...");

        WebSettings settings = webView.getSettings();

        // 1️⃣ تعزيز أولوية الرندرة (Renderer Priority) - لمنع الوميض الناتج عن خمول المحرك
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, true);
        }

        // 2️⃣ الرسم المسبق خارج الشاشة (Offscreen Pre-Raster) - السر الحقيقي وراء اختفاء الوميض
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            settings.setOffscreenPreRaster(true);
        }

        // 3️⃣ تحسينات العرض المتقدمة (Viewport Tuning)
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkImage(false);
        
        // 4️⃣ قفل الكاش الموحد
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        // 5️⃣ منع التخزين المؤقت للرسم القديم (Hardware Only)
        webView.setDrawingCacheEnabled(false);

        // مطابقة الخلفية فوراً للون النظام لمنع التباين
        webView.setBackgroundColor(android.graphics.Color.parseColor("#F3F4F6"));

        // ==========================================
        // 6️⃣ Modern Native UX & Security (البدائل الحديثة والاحترافية)
        // ==========================================
        
        // 1. التثبيت البصري (Text Lock): يمنع إعدادات خط الهاتف من تخريب تصميم (CSS) المتجر
        settings.setTextZoom(100);

        // 2. سلوك التطبيقات الحقيقية: إخفاء خيار "البحث في الويب" عند قيام العميل بتحديد نص في المتجر
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            settings.setDisabledActionModeMenuItems(
                WebSettings.MENU_ITEM_WEB_SEARCH | WebSettings.MENU_ITEM_PROCESS_TEXT
            );
        }

        // 3. تأمين التصفح الصامت (Safe Browsing): تفعيل حماية جوجل المدمجة دون إزعاج المستخدم
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }

        // 💥 ربط الكاش بالجلسة: تفعيل الكوكيز لربط التخزين المؤقت بجلسة المستخدم
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }

        // ==========================================
        // 7️⃣ SPA Stability & Security (استقرار وأمان)
        // ==========================================
        settings.setJavaScriptEnabled(true);
        
        // 👑 كسر القيود الأمنية لمنع الحظر الصامت وتشغيل الـ WebAssembly المحلي بنجاح
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        // السماح بالصور
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkImage(false);

        // 🛍️ إعدادات التجارة الإلكترونية (E-Commerce Native Feel)
        // 1. السماح بتشغيل الفيديوهات الترويجية تلقائياً بدون تدخل المستخدم
        settings.setMediaPlaybackRequiresUserGesture(false);

        isEnginePrimed = true;
        Log.i(TAG, "✅ Royal Hybrid Engine V2: Process Priority & Pre-Raster Primed.");
    }

    /**
     * 🔄 إعادة ضبط المحرك (تُستدعى فقط عند الـ Soft Restart)
     */
    public static void reset() {
        isEnginePrimed = false;
    }
            }
