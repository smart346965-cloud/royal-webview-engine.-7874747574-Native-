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

    public void bind(WebView webView, OfflineUIController uiController) {
        this.webView = webView;
        this.uiController = uiController;

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

    // [تعديل جراحي في OfflineStateManager.java - دالة handleNetworkChange]
    private void handleNetworkChange(boolean connected) {
        if (webView == null) return;

        if (connected) {
            // ✅ الإنترنت عاد: تنفيذ بروتوكول الاستعادة
            mainHandler.postDelayed(() -> {
                // فحص إضافي للتأكد من استقرار الشبكة (Vitality Check)
                if (NetworkMonitor.isInternetAvailable(webView.getContext())) {
                    
                    if (isOnErrorPage || !isPageValid) {
                        Log.i(TAG, "🌐 Restoration: Loading original target URL...");
                        // إذا فشل التحميل سابقاً، نعيد المحاولة للرابط الأصلي
                        webView.reload(); 
                    } else {
                        // إرسال نبضة للجافا سكريبت لتحديث البيانات ديناميكياً
                        webView.evaluateJavascript("window.dispatchEvent(new Event('online'));", null);
                    }

                    // إخفاء كافة واجهات الأوفلاين بنعومة
                    if (uiController != null) {
                        uiController.setOfflineUIVisibility(false);
                        uiController.setOfflineBarVisibility(false);
                    }
                }
            }, 500); // تأخير بسيط لضمان استقرار الـ DNS بعد عودة الشبكة
        } else {
            // ❌ الإنترنت انقطع: تفعيل بروتوكول الحماية
            if (isPageValid) {
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
            this.lastFailedUrl = url;
        }
        Log.d(TAG, "🛡️ Error page set to: " + error + ", URL: " + url);
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
    }
