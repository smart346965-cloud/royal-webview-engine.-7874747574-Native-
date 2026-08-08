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

    private void handleNetworkChange(boolean connected) {
        if (webView == null) return;

        if (connected) {
            // ✅ الإنترنت عاد: بروتوكول "الاستعادة الصارمة"
            mainHandler.postDelayed(() -> {
                if (NetworkMonitor.isInternetAvailable(webView.getContext())) {
                    
                    // 🛡️ إذا كان الدرع الكبير ظاهراً (بداية تشغيل أوفلاين)
                    if (uiController != null && uiController.isOfflineUIVisible()) {
                        Log.i(TAG, "🌐 Cold Start Recovery: Loading -> " + pendingUrl);
                        webView.loadUrl(pendingUrl);
                        // لا نخفي الدرع هنا، بل ننتظر إشارة onPageCommitVisible في الـ Manager لضمان السلاسة
                    } 
                    // 🛡️ إذا كان الإنترنت انقطع أثناء التصفح (الشريط النحيف كان ظاهراً)
                    else if (isOnErrorPage || !isPageValid) {
                        webView.reload();
                    } else {
                        webView.evaluateJavascript("window.dispatchEvent(new Event('online'));", null);
                    }

                    if (uiController != null) {
                        uiController.setOfflineBarVisibility(false);
                    }
                }
            }, 600); // مهلة 600ms لضمان استقرار الشبكة في نظام أندرويد
        } else {
            // ❌ الإنترنت انقطع
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
                }
