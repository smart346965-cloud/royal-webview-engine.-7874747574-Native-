package com.store.app;

import android.content.Intent;
import android.graphics.Color;
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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowCompat;

import com.store.app.offline.OfflineUIController;
import com.store.app.offline.OfflineStateManager;
import com.store.app.RoyalAuthManager;
import com.store.app.RoyalJsBridge;

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
    private boolean isPageLoaded = false; // لمنع إعادة تحميل الصفحة في onResume
    private boolean webViewReady = false;
    private boolean visualStateReady = false;

    private WebEngineManager engineManager;
    private RoyalCapabilitiesEngine capabilitiesEngine; // ✅ إضافة تعريف المحرك
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

        final SplashScreen splashScreen =
                SplashScreen.installSplashScreen(this);

        splashStartTime = System.currentTimeMillis();

        /*
         * 👑 Splash ثابت لمدة 5 ثوانٍ حقيقية.
         *
         * WebView / Chromium يعملان بالتوازي في الخلفية.
         * لا ننتظر WebView حتى يبدأ.
         */
        splashScreen.setKeepOnScreenCondition(
                () -> System.currentTimeMillis() - splashStartTime
                        < FIXED_SPLASH_TIME
        );

        splashScreen.setOnExitAnimationListener(
                splashScreenView -> {

                    splashScreenView.getView()
                            .animate()
                            .alpha(0f)
                            .setDuration(500L)
                            .withEndAction(
                                    splashScreenView::remove
                            )
                            .start();
                }
        );

        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        getWindow().setBackgroundDrawable(
                new ColorDrawable(
                        Color.parseColor("#F3F4F6")
                )
        );

        try {
            RoyalPanopticon.startAwareness();

            Log.i(
                    TAG,
                    "RoyalPanopticon Engine: Active and running in background."
            );

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Failed to initialize RoyalPanopticon.",
                    e
            );
        }

        rootContainer = new FrameLayout(this);

        rootContainer.setBackgroundColor(
                Color.parseColor("#F3F4F6")
        );

        setContentView(rootContainer);

        /*
         * Chromium startup barrier.
         *
         * RoyalApplication بدأ WebView startup
         * منذ لحظة إنشاء الـ process.
         */
        RoyalWebViewHost.whenStartupReady(
                () -> initializeWebView(savedInstanceState)
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

        // 👑 Edge-to-Edge مع إزاحة نصف شريط الحالة فقط
        activeWebView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
        activeWebView.setClipToPadding(false);

        ViewCompat.setOnApplyWindowInsetsListener(activeWebView, (v, insets) -> {
            int insetTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()
                    | WindowInsetsCompat.Type.displayCutout()).top;

            // نصف ارتفاع شريط الحالة فقط
            int appliedTopPadding = Math.max(0, insetTop / 2);

            v.setPadding(0, appliedTopPadding, 0, 0);

            return insets;
        });

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

        // ✅ ربط المحرك
        capabilitiesEngine = engineManager.getCapabilitiesHandler();

        // 🔗 ربط الـ Bridge بعد اكتمال WebEngineManager
        RoyalWebViewHost.bindEngineManager(engineManager);

        // 🔗 تأكيد ربط واجهة الجافاسكريبت بالـ WebView
        activeWebView.addJavascriptInterface(
                new RoyalJsBridge(activeWebView, engineManager),
                "RoyalJsBridge"
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

        // ✅ معالجة Intent الأولي للـ Auth
        handleInitialAuthIntent(getIntent());

        if (!NetworkMonitor.isInternetAvailable(this)) {

            offlineController.setOfflineUIVisibility(
                    true
            );
        }
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

        // ✅ إضافة التدمير للمحرك كأولوية
        if (capabilitiesEngine != null) {
            capabilitiesEngine.destroy();
        }

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

        // ✅ معالجة النتائج واختيار الملفات مباشرة عبر محرك القدرات
        if (capabilitiesEngine != null && capabilitiesEngine.handleActivityResult(requestCode, resultCode, data)) {
            return;
        }
    }

    // =========================================================
    // 🔗 معالجة الروابط العميقة (Deep Links / OAuth Callbacks)
    // =========================================================

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        setIntent(intent);

        if (intent == null) {
            return;
        }

        Uri data = intent.getData();

        if (data == null) {
            return;
        }

        Log.i(TAG, "🔗 Deep link received in onNewIntent: " + data.toString());

        if (royalAuthManager != null) {
            boolean handled = royalAuthManager.handleRedirectIntent(intent);
            if (!handled && activeWebView != null && RoyalAuthManager.isAuthCallback(data)) {
                dispatchAuthUrlToWebView(data.toString());
            }
        } else if (activeWebView != null && RoyalAuthManager.isAuthCallback(data)) {
            dispatchAuthUrlToWebView(data.toString());
        }
    }

    /**
     * معالجة Intent الأولي للـ Auth عند بدء التطبيق
     */
    private void handleInitialAuthIntent(Intent intent) {

        if (intent == null) {
            return;
        }

        Uri data = intent.getData();

        if (data == null) {
            return;
        }

        Log.i(TAG, "🔗 Initial Auth Intent received: " + data.toString());

        if (royalAuthManager != null) {
            royalAuthManager.handleRedirectIntent(intent);
        } else if (activeWebView != null && RoyalAuthManager.isAuthCallback(data)) {
            dispatchAuthUrlToWebView(data.toString());
        }
    }

    // =========================================================
    // 🔐 صلاحيات التطبيق (مدمجة مع محرك القدرات)
    // =========================================================

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        // ✅ التعديل الجراحي الموصى به
        if (capabilitiesEngine != null) {
            capabilitiesEngine.handlePermissionResult(requestCode, permissions, grantResults);
        }
    }

    // =========================================================
    // 👑 دالة التوزيع المباشر لرابط العودة واستلام الـ Session Cookie فوراً
    // =========================================================

    /**
     * 👑 دالة التوزيع المباشر لرابط العودة، إغلاق الـ Custom Tab وتأكيد الـ Session Cookie
     */
    public void dispatchAuthUrlToWebView(@NonNull String url) {
        runOnUiThread(() -> {
            // 1. تقديم MainActivity للواجهة فوراً لإغلاق الـ Custom Tab
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);

            // 2. معالجة وتمرير الرابط الحقيقي الحاوي على code=
            if (engineManager != null) {
                engineManager.handleAuthReturn(url);
            } else if (activeWebView != null) {
                Log.i(TAG, "🚀 Dispatching OAuth Callback URL directly to WebView: " + url);
                activeWebView.loadUrl(url);
                android.webkit.CookieManager.getInstance().flush();
            } else {
                Log.w(TAG, "⚠️ activeWebView and engineManager are null.");
            }
        });
    }
    }
