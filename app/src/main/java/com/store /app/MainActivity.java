package com.store.app;

import android.content.Intent;
importandroid.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

import com.store.app.offline.OfflineUIController;
import com.store.app.offline.OfflineStateManager;
import com.store.app.RoyalAuthManager;

/**
 * 👑 MainActivity - النواة الأساسية لإدارة محرك الويب المخصص
 * تم تطهيرها بالكامل من مخلفات الـ TWA لتعمل بأقصى سرعة استجابة (Zero-friction)
 * 
 * 🚀 تم تحسينها بأعلى معايير الأداء من وثائق كروميوم:
 * - Time-Based Memory Purge (تفريغ الذاكرة الاستباقي)
 * - shouldInterceptRequest Short Circuit (تحسين اعتراض الطلبات)
 * - Renderer Importance API (أولوية معالج العرض)
 * - onTrimMemory Optimization (تحسين استجابة ضغط الذ memory)
 * - saveState/restoreState (تسريع حفظ واستعادة الحالة)
 * - Prefetch Native Library (تحميل المكتبات الأصلية مسبقاً)
 * - Threading Optimization (تحسين إدارة الخيوط)
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "RoyalMainActivity";
    private static final long FIXED_SPLASH_TIME = 5000; // قيمة ثابتة 5 ثوانٍ بالتمام والكمال

    private boolean splashRemoved = false;
    private boolean isPageLoaded = false;
    private boolean webViewReady = false;
    private boolean visualStateReady = false;

    private WebEngineManager engineManager;
    private WebView activeWebView;
    private ProgressBar progressBar;

    private long splashStartTime = 0;

    // 🔥 مدير واجهات الأوفلاين
    private OfflineUIController offlineController;

    // 🔥 مدير المصادقة والدفع
    private RoyalAuthManager royalAuthManager;

    private FrameLayout rootContainer;

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    // =========================================================
    // 🚀 دورة الحياة الأساسية
    // =========================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 👑 System Splash هو الـ Splash الوحيد للتطبيق
        // التعديل: إزالة setKeepOnScreenCondition (غير مدعومة) واستخدام splashScreenView مباشرة
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSplashScreen().setOnExitAnimationListener(
                    splashScreenView -> {
                        splashScreenView.animate()
                                .alpha(0f)
                                .setDuration(500)
                                .withEndAction(
                                        splashScreenView::remove
                                )
                                .start();
                    }
            );
        }

        super.onCreate(savedInstanceState);

        getWindow().setBackgroundDrawable(
                new ColorDrawable(Color.parseColor("#F3F4F6"))
        );

        // 🔍 تفعيل محرك الفحص والتشخيص الذكي
        try {
            RoyalPanopticon.startAwareness();
            Log.i(TAG, "RoyalPanopticon Engine: Active and running in background.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize RoyalPanopticon: " + e.getMessage());
        }

        // =========================================================
        // 👑 Native root — يظهر فوراً ولا ينتظر Chromium
        // =========================================================

        rootContainer = new FrameLayout(this);
        rootContainer.setBackgroundColor(
                Color.parseColor("#F3F4F6")
        );

        setContentView(rootContainer);

        splashStartTime = System.currentTimeMillis();

        /*
         * =========================================================
         * Chromium barrier
         * =========================================================
         *
         * إذا كان Chromium لم ينته بعد:
         * لا ننشئ WebView قبل callback.
         *
         * لكن Splash موجود بالفعل.
         */
        RoyalWebViewHost.whenStartupReady(
                () -> initializeWebView(savedInstanceState)
        );

        /*
         * =========================================================
         * Splash timer
         * =========================================================
         *
         * 5000ms ثابتة.
         *
         * لا علاقة له بوقت تحميل Chromium.
         * لا علاقة له بـ FCP.
         * لا علاقة له بـ onPreDraw.
         */
        mainHandler.postDelayed(
                this::releaseSplash,
                FIXED_SPLASH_TIME
        );
    }

    private void initializeWebView(Bundle savedInstanceState) {

        if (isFinishing() ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                        && isDestroyed())) {
            return;
        }

        /*
         * الآن فقط يسمح لـ Host بإنشاء WebView.
         */
        RoyalWebViewHost.create(this);

        activeWebView =
                RoyalWebViewHost.attach(this);

        /*
         * WebView يدخل خلف الـ Splash.
         *
         * index 0 = خلفية
         * Splash = فوقه (لكننا لا نستخدم splashContainer الآن)
         */
        rootContainer.addView(
                activeWebView,
                0,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        );

        /*
         * إعداد WebView قبل أي navigation.
         */
        setupWebViewClient();

        /*
         * System UI بعد وجود WebView.
         */
        SystemUI.applyKingMode(
                this,
                activeWebView
        );

        SystemUI.setDynamicIcons(
                getWindow(),
                true
        );

        /*
         * WebEngineManager الآن فقط.
         * لأنه يحتاج WebView حقيقي.
         *
         * تمرير null للـ splashOverlay و progressBar لأننا نعتمد على System Splash.
         */
        engineManager = new WebEngineManager(
                this,
                activeWebView,
                null,   // splashOverlay
                null,   // progressBar
                () -> splashRemoved = true,
                () -> splashRemoved
        );

        engineManager.setSplashStartTime(
                splashStartTime
        );

        engineManager.init();

        /*
         * Navigation manager كان يأخذ engineManager = null
         * في النسخة القديمة.
         *
         * الآن يأخذ object حقيقي.
         */
        new com.store.app.navigation.RoyalBackNavigation(
                this,
                activeWebView,
                engineManager,
                null // progressBar غير مستخدم
        ).setupBackNavigation();

        /*
         * =====================================================
         * استعادة / تحميل الصفحة
         * =====================================================
         *
         * يوجد مالك واحد فقط للـ navigation.
         */

        boolean restored = false;

        if (savedInstanceState != null) {

            try {

                activeWebView.restoreState(savedInstanceState);

                restored = true;
                isPageLoaded = true;

                Log.i(
                        TAG,
                        "🔄 WebView restored from Activity state."
                );

            } catch (Throwable t) {

                Log.w(
                        TAG,
                        "WebView restoreState failed.",
                        t
                );
            }
        }

        if (!restored) {

            restored = RoyalSessionSentinel.resurrect(
                    activeWebView,
                    this
            );

            if (restored) {

                isPageLoaded = true;

                Log.i(
                        TAG,
                        "🧊 WebView session resurrected."
                );
            }
        }

        if (!restored) {

            activeWebView.loadUrl(
                    BuildConfig.CLIENT_URL
            );

            isPageLoaded = true;

            Log.i(
                    TAG,
                    "🌐 Initial CLIENT_URL navigation started."
            );
        }

        waitForVisualReady();

        /*
         * Offline
         */
        NetworkMonitor.init(this);

        offlineController =
                new OfflineUIController(
                        this,
                        activeWebView,
                        engineManager
                );

        offlineController.init();

        OfflineStateManager
                .getInstance()
                .bind(
                        activeWebView,
                        offlineController
                );

        /*
         * Auth / Payment
         */
        royalAuthManager =
                new RoyalAuthManager(
                        this,
                        getApplicationContext()
                );

        if (!NetworkMonitor.isInternetAvailable(this)) {

            offlineController.setOfflineUIVisibility(
                    true
            );
        }

        /*
         * إذا انتهت الـ 5 ثواني قبل انتهاء WebView startup،
         * نكشف Splash الآن بعد أن أصبح WebView موجودًا.
         */
        if (splashRemoved) {
            // إذا تم طلب الإصدار بالفعل، نحرره فوراً
            if (engineManager != null) {
                engineManager.triggerFinalReveal();
            }
        }
    }

    private void waitForVisualReady() {

        if (activeWebView == null) {
            return;
        }

        activeWebView.postVisualStateCallback(
                System.nanoTime(),
                new WebView.VisualStateCallback() {

                    @Override
                    public void onComplete(long requestId) {

                        visualStateReady = true;

                        Log.i(
                                TAG,
                                "🎨 WebView visual state ready."
                        );

                        revealWebViewIfReady();
                    }
                }
        );
    }

    private void revealWebViewIfReady() {

        if (!splashRemoved) {
            return;
        }

        if (!visualStateReady) {
            return;
        }

        if (activeWebView == null) {
            return;
        }

        Log.i(
                TAG,
                "👑 Visual state ready. Content can be exposed."
        );
    }

    private void releaseSplash() {

        splashRemoved = true;

        Log.i(TAG, "👑 System Splash release requested.");

        revealWebViewIfReady();
    }

    // =========================================================
    // 🔧 دوال الإعدادات المحسّنة
    // =========================================================

    /**
     * 🔥 تحسين shouldInterceptRequest: منع الاستدعاءات الفارغة
     */
    private void setupWebViewClient() {

        activeWebView.setWebViewClient(
                new WebViewClient() {

                    @Override
                    public WebResourceResponse shouldInterceptRequest(
                            WebView view,
                            WebResourceRequest request) {

                        /*
                         * يجب أن يكون هذا Short-Circuit فعليًا.
                         *
                         * إذا RoyalNetworkEngine لا يريد الطلب:
                         * return null فوراً.
                         *
                         * لا Networking blocking هنا.
                         */
                        return RoyalNetworkEngine
                                .interceptRequest(request);
                    }

                    @Override
                    public boolean onRenderProcessGone(
                            WebView view,
                            android.webkit.RenderProcessGoneDetail detail) {

                        if (detail.didCrash()) {

                            Log.e(
                                    TAG,
                                    "💥 Chromium renderer crashed."
                            );

                        } else {

                            Log.e(
                                    TAG,
                                    "⚠️ Chromium renderer was killed by system."
                            );
                        }

                        /*
                         * WebView الذي فقد Renderer انتهى.
                         *
                         * لا نحاول إعادة استخدامه.
                         */
                        if (view.getParent() instanceof ViewGroup) {

                            ((ViewGroup) view.getParent())
                                    .removeView(view);
                        }

                        RoyalWebViewHost.destroy();

                        activeWebView = null;

                        splashRemoved = false;
                        visualStateReady = false;
                        isPageLoaded = false;

                        if (!isFinishing()) {
                            recreate();
                        }

                        return true;
                    }
                }
        );
    }

    // =========================================================
    // 🔄 دورة الحياة المحدّثة
    // =========================================================

    @Override
    protected void onPause() {

        if (activeWebView != null) {
            activeWebView.onPause();
        }

        super.onPause();
    }

    @Override
    protected void onResume() {

        super.onResume();

        if (activeWebView != null) {
            activeWebView.onResume();
        }

        if (offlineController != null) {
            offlineController.onResume();
        }

        if (!isPageLoaded
                && activeWebView != null
                && activeWebView.getUrl() == null) {

            activeWebView.loadUrl(
                    BuildConfig.CLIENT_URL
            );

            isPageLoaded = true;
        }
    }

    @Override
    protected void onDestroy() {

        mainHandler.removeCallbacksAndMessages(null);

        if (activeWebView != null) {
            activeWebView.stopLoading();
        }

        if (offlineController != null) {
            offlineController.destroy();
            offlineController = null;
        }

        OfflineStateManager
                .getInstance()
                .unbind();

        if (royalAuthManager != null) {
            royalAuthManager.destroy();
            royalAuthManager = null;
        }

        if (!isChangingConfigurations()) {
            RoyalWebViewHost.detach();
        }

        activeWebView = null;

        super.onDestroy();
    }

    // =========================================================
    // 💾 حفظ واستعادة الحالة
    // =========================================================

    @Override
    protected void onSaveInstanceState(Bundle outState) {

        if (activeWebView != null) {

            try {

                if (androidx.webkit.WebViewFeature.isFeatureSupported(
                        androidx.webkit.WebViewFeature.SAVE_STATE)) {

                    androidx.webkit.WebViewCompat.saveState(
                            activeWebView,
                            outState,
                            1024 * 1024,
                            false
                    );

                } else {

                    activeWebView.saveState(
                            outState
                    );
                }

            } catch (Throwable t) {

                Log.w(
                        TAG,
                        "WebView state save failed.",
                        t
                );
            }
        }

        super.onSaveInstanceState(outState);
    }

    // لا نستخدم onRestoreInstanceState، لأن الاستعادة تتم في initializeWebView.

    // =========================================================
    // 🔄 نتائج النشاطات والصلاحيات (محسّن)
    // =========================================================

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // 🔥 معالجة اختيار الملفات (رفع الصور)
        if (requestCode == RoyalCapabilitiesEngine.FILECHOOSER_RESULTCODE) {
            if (RoyalCapabilitiesEngine.filePathCallback == null) return;

            Uri[] results = null;

            if (resultCode == android.app.Activity.RESULT_OK) {
                if (data != null) {
                    String dataString = data.getDataString();
                    android.content.ClipData clipData = data.getClipData();

                    if (clipData != null) {
                        results = new Uri[clipData.getItemCount()];
                        for (int i = 0; i < clipData.getItemCount(); i++) {
                            results[i] = clipData.getItemAt(i).getUri();
                        }
                    } else if (dataString != null) {
                        results = new Uri[]{Uri.parse(dataString)};
                    }
                }
            }

            RoyalCapabilitiesEngine.filePathCallback.onReceiveValue(results);
            RoyalCapabilitiesEngine.filePathCallback = null;
            return;
        }

        // 🔥 معالجة نتائج المصادقة والدفع
        if (royalAuthManager != null) {
            royalAuthManager.handleAuthResult(resultCode, data);
            royalAuthManager.handlePaymentResult(resultCode, data);
        }
    }

    // =========================================================
    // 🔗 معالجة الروابط العميقة (Deep Links)
    // =========================================================

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // إذا كان intent يحتوي على بيانات من Auth Tab أو Custom Tabs
        if (intent != null && intent.getData() != null) {
            Uri data = intent.getData();
            Log.i(TAG, "🔗 Deep link received: " + data.toString());
            // يمكن معالجتها حسب الحاجة
        }
    }

    // =========================================================
    // 🔐 صلاحيات التطبيق (مدمجة مع محرك القدرات)
    // =========================================================

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // تمرير النتيجة إلى محرك القدرات (لرفع الملفات)
        if (engineManager != null && engineManager.getCapabilitiesHandler() != null) {
            engineManager.getCapabilitiesHandler().handlePermissionResult(requestCode, grantResults);
        }
        // هنا يمكن تمرير النتائج إلى RoyalAuthManager إذا لزم الأمر
        // حالياً لا يوجد استخدام مباشر
    }
                }
