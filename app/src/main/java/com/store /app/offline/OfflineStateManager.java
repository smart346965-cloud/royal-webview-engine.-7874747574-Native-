package com.store.app.offline;

import android.app.Activity;
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
    // 📡 معالجة تغيرات الشبكة
    // ==========================================

    private void handleNetworkChange(boolean connected) {
        if (webView == null) {
            Log.w(TAG, "⚠️ WebView is null, cannot handle network change");
            return;
        }

        if (connected) {
            // ✅ الإنترنت عاد
            if (isOnErrorPage || !isPageValid) {
                // صفحة خطأ أو غير صالحة → إعادة تحميل وإخفاء الدرع
                Log.i(TAG, "🌐 Network restored. Reloading invalid page...");
                webView.reload();
                isOnErrorPage = false;
                isPageValid = true;
                // إخفاء الواجهة الكبيرة
                if (uiController != null) {
                    uiController.setOfflineUIVisibility(false);
                }
            } else if (isPageValid) {
                // صفحة سليمة → إرسال حدث online للـ JS
                Log.i(TAG, "🌐 Network restored. Dispatching online event to JS.");
                webView.evaluateJavascript(
                    "window.dispatchEvent(new Event('online'));",
                    null
                );
                // إخفاء الشريط النحيف
                if (uiController != null) {
                    uiController.setOfflineBarVisibility(false);
                }
            }
        } else {
            // ❌ الإنترنت انقطع
            if (isPageValid) {
                // صفحة سليمة → إظهار الشريط النحيف
                Log.i(TAG, "📡 Network lost. Showing offline bar.");
                webView.evaluateJavascript(
                    "window.dispatchEvent(new Event('offline'));",
                    null
                );
                if (uiController != null) {
                    uiController.setOfflineBarVisibility(true);
                }
            } else {
                // صفحة غير سليمة → إظهار الواجهة الكبيرة
                Log.i(TAG, "📡 Network lost and page invalid. Showing native offline UI.");
                if (uiController != null) {
                    uiController.setOfflineUIVisibility(true);
                }
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
                }
