package com.store.app.offline;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebView;

import com.store.app.MainActivity;
import com.store.app.NetworkMonitor;
import com.store.app.RoyalNetworkEngine;

/**
 * 👑 OfflineStateManager - يدير حالة الأوفلاين بشكل مركزي
 * 
 * - يحتفظ بحالة الصفحة (isPageValid, isOnErrorPage)
 * - يستمع لتغيرات الشبكة وينفذ منطق NetErrorAutoReloader
 * - يتحكم في واجهات الأوفلاين عبر OfflineUIController
 */
public class OfflineStateManager {

    private static final String TAG = "OfflineStateManager";

    // ==========================================
    // 🔥 المتغيرات
    // ==========================================

    private static OfflineStateManager instance;

    // حالة الأوفلاين
    private boolean isOnErrorPage = false;
    private boolean isPageValid = false;
    private String lastFailedUrl = null;
    private boolean isNetworkAvailable = true;
    private boolean isOfflineBarVisible = false;

    // 👑 الرابط الذي فشل تحميله وسنفتحه عند عودة الشبكة
    private String pendingUrl = null;

    // مراجع للتحكم
    private WebView webView;
    private OfflineUIController uiController;

    // معالج الخيط الرئيسي
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ==========================================
    // 🔒 Singleton
    // ==========================================

    public static synchronized OfflineStateManager getInstance() {
        if (instance == null) {
            instance = new OfflineStateManager();
        }
        return instance;
    }

    private OfflineStateManager() {}

    // ==========================================
    // 🔗 الربط مع المكونات الأخرى
    // ==========================================

    // [تعديل جراحي في OfflineStateManager.java]
    public void bind(WebView webView, OfflineUIController uiController) {
        this.webView = webView;
        this.uiController = uiController;
        
        // 🚀 [إضافة]: تعيين الرابط الافتراضي كـ pendingUrl منذ البداية لضمان عدم الضياع
        if (this.pendingUrl == null) {
            this.pendingUrl = com.store.app.BuildConfig.CLIENT_URL;
        }

        // تسجيل مستمع الشبكة
        NetworkMonitor.setListener(connected -> {
            isNetworkAvailable = connected;
            handleNetworkChange(connected);
        });

        Log.i(TAG, "🔗 Bound to WebView and UIController");
    }

    // ==========================================
    // 📡 معالجة تغيرات الشبكة (معدلة)
    // ==========================================

    // [تعديل جراحي في OfflineStateManager.java - الإحياء المصدق]
    private void handleNetworkChange(boolean connected) {
        if (webView == null) return;

        if (connected) {
            // ✅ الإنترنت حقيقي ومصدق (Validated): تنفيذ بروتوكول الاستعادة
            mainHandler.postDelayed(() -> {
                // فحص الرمق الأخير: التأكد أن الحالة ما زالت Validated
                if (NetworkMonitor.isInternetAvailable(webView.getContext())) {
                    
                    Log.i(TAG, "🌐 Restoration: Verified Internet access detected.");

                    if (isOnErrorPage || !isPageValid || webView.getUrl() == null) {
                        // إذا كنا في حالة خطأ، نقوم بالتحميل القسري للرابط المطلوب
                        String urlToLoad = (pendingUrl != null) ? pendingUrl : webView.getUrl();
                        if (urlToLoad == null) urlToLoad = com.store.app.BuildConfig.CLIENT_URL;

                        webView.loadUrl(urlToLoad); 
                    } else {
                        // الموقع شغال؟ نكتفي بإبلاغ الـ JS ليعيد تفعيل وظائفه
                        webView.evaluateJavascript("window.dispatchEvent(new Event('online'));", null);
                    }

                    // ❌ تم حذف السطرين التاليين لمنع إزالة الستار قبل اكتمال التحميل
                    // uiController.setOfflineUIVisibility(false);
                    // uiController.setOfflineBarVisibility(false);
                    
                    isOnErrorPage = false;
                }
            }, 1000); // زيادة المهلة لـ 1000ms لضمان استقرار جلسة الـ SSL بعد التصديق
        } else {
            // ❌ الإنترنت انقطع أو "وهمي" (No Validation)
            Log.w(TAG, "📡 Pulse Lost: Connection is either dead or fake.");
            if (isPageValid && !isOnErrorPage) {
                if (uiController != null) uiController.setOfflineBarVisibility(true);
                webView.evaluateJavascript("window.dispatchEvent(new Event('offline'));", null);
            } else {
                if (uiController != null) uiController.setOfflineUIVisibility(true);
            }
        }
    }

    // ==========================================
    // 🔧 دوال تحديث حالة الصفحة
    // ==========================================

    public void setPageValid(boolean valid) {
        this.isPageValid = valid;
        if (valid) {
            this.isOnErrorPage = false;
        }
        Log.d(TAG, "📄 Page valid set to: " + valid);
    }

    public void setErrorPage(boolean error, String url) {
        this.isOnErrorPage = error;
        this.isPageValid = !error;
        if (error) {
            this.pendingUrl = url; // حفظ الرابط فوراً لإعادة المحاولة لاحقاً
        }
        Log.d(TAG, "🛡️ Error tracking: " + error + " for URL: " + url);
    }

    public void setOfflineBarVisible(boolean visible) {
        this.isOfflineBarVisible = visible;
    }

    // ==========================================
    // 🔍 دوال الاستعلام عن الحالة
    // ==========================================

    public boolean isOnErrorPage() {
        return isOnErrorPage;
    }

    public boolean isPageValid() {
        return isPageValid;
    }

    public String getLastFailedUrl() {
        return lastFailedUrl;
    }

    public boolean isNetworkAvailable() {
        return isNetworkAvailable;
    }

    public boolean isOfflineBarVisible() {
        return isOfflineBarVisible;
    }

    // ==========================================
    // 🔓 إلغاء الربط
    // ==========================================

    public void unbind() {
        this.webView = null;
        this.uiController = null;
        // إزالة المستمع من NetworkMonitor
        NetworkMonitor.setListener(null);
        Log.i(TAG, "🔓 Unbound from WebView and UIController");
    }

    // ==========================================
    // 💥 إشعار محاولة النقر الفاشلة
    // ==========================================

    /**
     * [إضافة جراحية في OfflineStateManager.java]
     * يُستدعى عند محاولة النقر على رابط أثناء انقطاع الشبكة
     * لإعلام المستخدم بهز الشريط السفلي
     */
    public void notifyOfflineClickAttempt() {
        if (uiController != null) {
            // هز الشريط السفلي لجذب انتباه العميل دون تجميد الصفحة
            uiController.shakeOfflineBar();
        }
    }

    // ==========================================
    // 🚀 إخطار جاهزية الصفحة (الإضافة الجديدة)
    // ==========================================

    /**
     * 🎯 تُستدعى من WebEngineManager عند اكتمال تحميل الصفحة بنجاح
     * لإخفاء واجهات الأوفلاين بشكل آمن
     */
    public void notifyPageReadyToHide() {
        mainHandler.post(() -> {
            if (uiController != null) {
                uiController.forceHideAllInternal(); // 🚀 الآن فقط نزيح الستار بأمان
                isOnErrorPage = false;
                Log.i(TAG, "✅ Page ready, offline UI hidden.");
            }
        });
    }
            }
