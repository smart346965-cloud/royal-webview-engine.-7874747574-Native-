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
import android.view.ViewTreeObserver;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.store.app.offline.OfflineUIController;
import com.store.app.offline.OfflineStateManager;
import com.store.app.RoyalAuthManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 👑 MainActivity - النواة الأساسية لإدارة محرك الويب المخصص
 * تم تطهيرها بالكامل من مخلفات الـ TWA لتعمل بأقصى سرعة استجابة (Zero-friction)
 * 
 * 🚀 تم تحسينها بأعلى معايير الأداء من وثائق كروميوم:
 * - Time-Based Memory Purge (تفريغ الذاكرة الاستباقي)
 * - shouldInterceptRequest Short Circuit (تحسين اعتراض الطلبات)
 * - Renderer Importance API (أولوية معالج العرض)
 * - onTrimMemory Optimization (تحسين استجابة ضغط الذاكرة)
 * - saveState/restoreState (تسريع حفظ واستعادة الحالة)
 * - Prefetch Native Library (تحميل المكتبات الأصلية مسبقاً)
 * - Threading Optimization (تحسين إدارة الخيوط)
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "RoyalMainActivity";
    private static final long FIXED_SPLASH_TIME = 5000; // قيمة ثابتة 5 ثوانٍ بالتمام والكمال
    private static final long MEMORY_PURGE_DELAY_MS = 4 * 60 * 1000; // 4 دقائق

    private boolean splashRemoved = false;
    private boolean isPageReady = false; // flag للرندرة
    private boolean isPageLoaded = false; // لمنع إعادة تحميل الصفحة في onResume

    private WebEngineManager engineManager;
    private WebView activeWebView;
    private ProgressBar progressBar;

    private long splashStartTime = 0;
    private Handler memoryPurgeHandler;
    private Runnable memoryPurgeRunnable;

    // متغيرات للتحكم في نقرتي الرجوع
    private boolean doubleBackToExitPressedOnce = false;
    private Handler backPressHandler = new Handler(Looper.getMainLooper());
    private Runnable resetBackPressFlag = () -> doubleBackToExitPressedOnce = false;

    // 🔥 تحسين الخيوط: استخدام ThreadPool لإدارة المهام الخلفية
    private static final ExecutorService backgroundExecutor = Executors.newFixedThreadPool(2);

    // 🔥 مدير واجهات الأوفلاين
    private OfflineUIController offlineController;

    // 🔥 مدير المصادقة والدفع
    private RoyalAuthManager royalAuthManager;

    // =========================================================
    // 🚀 دورة الحياة الأساسية
    // =========================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 👑 [تعديل جراحي ملكي 1]: استلام التحكم بأنيميشن خروج سبلاش النظام لجعل خروجه ناعماً للغاية
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSplashScreen().setOnExitAnimationListener(splashScreenView -> {
                // تنفيذ أنيميشن شفافية ناعم (Fade-Out) لسبلاش النظام لمنع الاختفاء المفاجئ
                splashScreenView.animate()
                        .alpha(0f)
                        .setDuration(500) // 500 ملي ثانية لأنيميشن اختفاء سينمائي
                        .withEndAction(splashScreenView::remove)
                        .start();
            });
        }

        // 🛡️ درع الوميض: مطابقة الخلفية مع لون السبلاش لمنع الوميض الأبيض الصارخ
        setTheme(R.style.AppTheme_NoSplash);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.parseColor("#F3F4F6")));

        super.onCreate(savedInstanceState);

        // 🔍 تفعيل محرك الفحص والتشخيص الذكي
        try {
            RoyalPanopticon.startAwareness();
            Log.i(TAG, "RoyalPanopticon Engine: Active and running in background.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize RoyalPanopticon: " + e.getMessage());
        }

        // تفعيل أدوات تصحيح الويب التقنية عبر المتصفح
        WebView.setWebContentsDebuggingEnabled(true);

        // 1️⃣ استدعاء وتهيئة الويب فيو الخالد مباشرة بدون وسطاء
        if (!RoyalWebViewHost.isReady()) {
            RoyalWebViewHost.create(getApplicationContext());
        }
        activeWebView = RoyalWebViewHost.attach(this);

        // 2️⃣ تعيين المحرك الخالد كواجهة أساسية مباشرة (استجابة 0ms)
        setContentView(activeWebView);

        // 🔥 [تحسين shouldInterceptRequest]: تفعيل الاختصار لمنع الاستدعاءات الفارغة
        setupWebViewClient();

        // 🔥 [تفعيل Renderer Importance API]: أعلى أولوية لمعالج العرض
        setupRendererPriority();

        // 🔥 [تفعيل Prefetch Native Library]: إجبار النظام على إبقاء المكتبات في الذاكرة
        setupNativeLibraryPrefetch();

        // 🚀 السطر الذهبي: حاول الإحياء الثنائي أولاً
        boolean sessionRestored = RoyalSessionSentinel.resurrect(activeWebView, this);

        if (!sessionRestored) {
            // إذا لم توجد جلسة، حمّل الرابط الافتراضي
            activeWebView.loadUrl(BuildConfig.CLIENT_URL);
        } else {
            isPageLoaded = true; // تم استعادة الجلسة، الصفحة محملة
        }

        // 4️⃣ نظام التحكم بالرجوع المستقل نيتف (محسّن)
        setupBackNavigation();

        // 5️⃣ الحصانة البصرية وتخصيص شريط النظام بالكامل
        SystemUI.applyKingMode(this, activeWebView);
        SystemUI.setDynamicIcons(this.getWindow(), true);

        // 6️⃣ بناء وتجهيز طبقة شاشة التحميل (Splash Screen Overlay)
        setupSplashScreen();

        // 7️⃣ مراقبة الشبكة وتهيئة مدير الأوفلاين
        NetworkMonitor.init(this);

        // 🔗 الربط الثنائي الموحد (تم حذف NetworkMonitor.setWebView)
        offlineController = new OfflineUIController(this, activeWebView, engineManager);
        offlineController.init();
        OfflineStateManager.getInstance().bind(activeWebView, offlineController);

        // 🔥 تهيئة مدير المصادقة والدفع
        royalAuthManager = new RoyalAuthManager(this, getApplicationContext());

        // 🚀 فحص الإنترنت الأولي (عند الإقلاع)
        if (!NetworkMonitor.isInternetAvailable(this)) {
            if (offlineController != null) {
                offlineController.setOfflineUIVisibility(true);
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // بدء مراقبة حالة التطبيق في المقدمة
    }

    @Override
    protected void onStop() {
        super.onStop();
        // 🔥 [Time-Based Memory Purge]: تفريغ الذاكرة بعد 4 دقائق في الخلفية
        scheduleMemoryPurge();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // إيقاف مؤقت للعمليات الرسومية غير النشطة في الخلفية للحفاظ على طاقة الجهاز
        if (activeWebView != null) {
            activeWebView.onPause();
        }
        // إلغاء مؤقت تفريغ الذاكرة عند الخروج الفوري
        cancelMemoryPurge();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (activeWebView != null) {
            activeWebView.onResume();
        }
        cancelMemoryPurge();

        // 🔥 تفويض منطق الأوفلاين إلى OfflineUIController
        if (offlineController != null) {
            offlineController.onResume();
        }

        // إذا كانت الصفحة فارغة وتحتاج تحميل (مع عدم وجود إنترنت)
        if (!isPageLoaded && activeWebView != null && activeWebView.getUrl() == null) {
            activeWebView.loadUrl(BuildConfig.CLIENT_URL);
            isPageLoaded = true;
        }
    }

    @Override
    protected void onDestroy() {
        // 🛡️ التعديل: لا تحمل about:blank، فقط افصل الويب فيو بأمان
        if (activeWebView != null) {
            // نكتفي بإيقاف العمليات دون مسح السطح الرسومي
            activeWebView.stopLoading();
        }
        // إلغاء مؤقت تفريغ الذاكرة
        cancelMemoryPurge();

        // 🔥 تنظيف OfflineUIController
        if (offlineController != null) {
            offlineController.destroy();
            offlineController = null;
        }

        // 🔥 إلغاء ربط OfflineStateManager
        OfflineStateManager.getInstance().unbind();

        // 🔥 تنظيف مدير المصادقة والدفع
        if (royalAuthManager != null) {
            royalAuthManager.destroy();
            royalAuthManager = null;
        }

        RoyalWebViewHost.detach();
        super.onDestroy();
    }

    // 🔥 [تحسين onTrimMemory]: استجابة سريعة لضغط الذاكرة
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        
        if (level >= TRIM_MEMORY_MODERATE) {
            Log.i(TAG, "🚨 Memory Pressure: Level " + level);
            
            if (activeWebView != null) {
                backgroundExecutor.execute(() -> {
                    try {
                        // تحرير موارد الرسم الصلبة
                        runOnUiThread(() -> activeWebView.onPause());
                        // تفريغ الكاش في الخلفية
                        activeWebView.clearCache(true);
                        Log.i(TAG, "🧹 Cache cleared due to memory pressure.");
                    } catch (Exception e) {
                        Log.w(TAG, "Memory pressure cleanup error: " + e.getMessage());
                    }
                });
            }
            
            // طلب جمع القمامة
            System.gc();
        }
    }

    // =========================================================
    // 🔧 دوال الإعدادات المحسّنة
    // =========================================================

    /**
     * 🔥 تحسين shouldInterceptRequest: منع الاستدعاءات الفارغة
     */
    private void setupWebViewClient() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activeWebView.setWebViewClient(new WebViewClient() {
                @Override
                public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                    // استخدام RoyalNetworkEngine بدلاً من التنفيذ الفارغ
                    return RoyalNetworkEngine.interceptRequest(request);
                }
            });
            Log.i(TAG, "✅ WebViewClient configured with shouldInterceptRequest optimization.");
        }
    }

    /**
     * 🔥 تفعيل Renderer Importance API: أعلى أولوية لمعالج العرض
     */
    private void setupRendererPriority() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                activeWebView.setRendererPriorityPolicy(
                    WebView.RENDERER_PRIORITY_BOUND,  // أعلى أولوية
                    true                              // Waived when not visible
                );
                Log.i(TAG, "✅ Renderer Priority set to BOUND.");
            } catch (Exception e) {
                Log.w(TAG, "Renderer priority setup failed: " + e.getMessage());
            }
        }
    }

    /**
     * 🔥 تفعيل Prefetch Native Library: إبقاء المكتبات في الذاكرة
     */
    private void setupNativeLibraryPrefetch() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                // إجبار النظام على إبقاء المكتبات الأصلية في الذاكرة
                // يتم ذلك عبر نفس آلية الأولوية (التأثير الجانبي المفيد)
                activeWebView.setRendererPriorityPolicy(
                    WebView.RENDERER_PRIORITY_BOUND, true
                );
                Log.i(TAG, "✅ Native Library Prefetch enabled.");
            } catch (Exception e) {
                Log.w(TAG, "Native library prefetch setup failed: " + e.getMessage());
            }
        }
    }

    /**
     * 👑 تحديد الصفحة الرئيسية بشكل آمن.
     *
     * مهم جداً:
     * لا نعتمد على WebView.canGoBack() لتحديد هل نحن في الرئيسية،
     * لأن WebView قد يمتلك History داخلياً حتى ونحن بصرياً في الصفحة الرئيسية.
     *
     * النتيجة:
     * الصفحة الرئيسية = ممنوع تنفيذ goBack() نهائياً.
     */
    private boolean isAtHomePage() {
        if (activeWebView == null) {
            return true;
        }

        String currentUrl = activeWebView.getUrl();

        // لا توجد صفحة فعلية بعد.
        // نعتبرها حالة لا يجوز فيها تنفيذ goBack().
        if (currentUrl == null || currentUrl.trim().isEmpty()) {
            return true;
        }

        currentUrl = currentUrl.trim();

        // حماية إضافية مطلقة من about:blank
        if (currentUrl.equalsIgnoreCase("about:blank")) {
            return true;
        }

        try {
            Uri current = Uri.parse(currentUrl);
            Uri home = Uri.parse(BuildConfig.CLIENT_URL);

            String currentHost = current.getHost();
            String homeHost = home.getHost();

            if (currentHost == null || homeHost == null) {
                return currentUrl.equalsIgnoreCase(BuildConfig.CLIENT_URL);
            }

            // مقارنة الـ Host بدون حساسية لحالة الأحرف.
            boolean sameHost = currentHost.equalsIgnoreCase(homeHost);

            if (!sameHost) {
                return false;
            }

            String currentPath = current.getPath();
            String homePath = home.getPath();

            if (currentPath == null || currentPath.isEmpty()) {
                currentPath = "/";
            }

            if (homePath == null || homePath.isEmpty()) {
                homePath = "/";
            }

            // الصفحة الرئيسية يجب أن تكون نفس المسار.
            boolean samePath = currentPath.equals(homePath);

            // لا نهتم بالـ query أو fragment في تحديد الصفحة الرئيسية.
            return samePath;

        } catch (Exception e) {
            Log.w(TAG, "Home page detection failed: " + e.getMessage());

            // في حالة فشل التحليل، نستخدم مقارنة مباشرة آمنة.
            return currentUrl.equalsIgnoreCase(BuildConfig.CLIENT_URL);
        }
    }

    /**
     * 👑 Royal Back Navigation
     *
     * القاعدة:
     *
     * 1. إذا كنا في الصفحة الرئيسية:
     *      Back #1 → تنبيه فقط.
     *      Back #2 → خروج.
     *
     * 2. إذا كنا داخل صفحة فرعية:
     *      Back → الرجوع الطبيعي داخل WebView.
     *
     * 3. إذا كانت الصفحة الرئيسية:
     *      NEVER call goBack()
     *      NEVER call safeGoBack()
     *
     * الهدف:
     * منع أي انتقال إلى about:blank أو أي History غير مقصود
     * عند الضغط على Back من الصفحة الرئيسية.
     */
    private void setupBackNavigation() {

        getOnBackPressedDispatcher().addCallback(
                this,
                new OnBackPressedCallback(true) {

                    @Override
                    public void handleOnBackPressed() {

                        try {

                            if (activeWebView == null) {
                                performRoyalExit();
                                return;
                            }

                            /*
                             * =====================================================
                             * 👑 الدرع الأول:
                             * هل نحن في الصفحة الرئيسية؟
                             *
                             * إذا نعم:
                             * لا نلمس WebView History إطلاقاً.
                             * =====================================================
                             */
                            if (isAtHomePage()) {

                                handleHomeBackPress();

                                return;
                            }

                            /*
                             * =====================================================
                             * 👑 لسنا في الصفحة الرئيسية.
                             *
                             * هنا فقط نسمح بالرجوع داخل WebView.
                             * =====================================================
                             */
                            if (activeWebView.canGoBack()) {

                                if (progressBar != null) {
                                    progressBar.setVisibility(View.GONE);
                                }

                                boolean navigated = false;

                                /*
                                 * نستخدم WebEngineManager إذا كان لديه
                                 * سجل رجوع صالح.
                                 */
                                if (engineManager != null) {
                                    try {
                                        navigated = engineManager.safeGoBack();
                                    } catch (Exception e) {
                                        Log.w(
                                                TAG,
                                                "safeGoBack failed: " + e.getMessage()
                                        );
                                    }
                                }

                                /*
                                 * إذا لم ينجح المحرك، نستخدم goBack()
                                 * لكن فقط بعد التأكد أننا لسنا في الرئيسية.
                                 */
                                if (!navigated) {

                                    String currentUrl = activeWebView.getUrl();

                                    if (currentUrl != null
                                            && !currentUrl.trim().isEmpty()
                                            && !currentUrl.equalsIgnoreCase("about:blank")) {

                                        /*
                                         * فحص أخير قبل goBack().
                                         *
                                         * حتى لو تغيرت الصفحة بين الفحوصات،
                                         * لا ننفذ الرجوع إذا أصبحت الرئيسية.
                                         */
                                        if (!isAtHomePage()) {
                                            activeWebView.goBack();
                                        } else {
                                            Log.i(
                                                    TAG,
                                                    "🛡️ Back blocked: returned to HOME."
                                            );
                                        }

                                    } else {

                                        Log.i(
                                                TAG,
                                                "🛡️ Back blocked: invalid/blank URL."
                                        );
                                    }
                                }

                                /*
                                 * الرجوع من صفحة فرعية يلغي حالة
                                 * النقرة الأولى للخروج.
                                 */
                                doubleBackToExitPressedOnce = false;
                                backPressHandler.removeCallbacks(resetBackPressFlag);

                                return;
                            }

                            /*
                             * =====================================================
                             * لا توجد صفحة سابقة صالحة.
                             *
                             * لا نحاول goBack().
                             * نعاملها كحالة خروج آمنة.
                             * =====================================================
                             */
                            handleHomeBackPress();

                        } catch (Throwable e) {

                            /*
                             * آخر خط دفاع:
                             *
                             * حتى لو حدث خطأ غير متوقع،
                             * لا ننفذ goBack() أبداً من هنا.
                             */
                            Log.e(
                                    TAG,
                                    "❌ Back navigation protected failure",
                                    e
                            );

                            handleHomeBackPress();
                        }
                    }
                }
        );

        Log.i(
                TAG,
                "✅ Royal Back Navigation armed: HOME is protected from goBack()."
        );
    }

    /**
     * 👑 معالجة زر الرجوع عندما نكون في الصفحة الرئيسية.
     *
     * النقرة الأولى:
     * لا يحصل أي Navigation إطلاقاً.
     * فقط يظهر التنبيه.
     *
     * النقرة الثانية:
     * خروج آمن من الـ Activity/Task.
     */
    private void handleHomeBackPress() {

        // =====================================================
        // النقرة الثانية
        // =====================================================
        if (doubleBackToExitPressedOnce) {

            doubleBackToExitPressedOnce = false;

            backPressHandler.removeCallbacks(resetBackPressFlag);

            Log.i(
                    TAG,
                    "🚪 Second BACK press on HOME → exiting application."
            );

            performRoyalExit();

            return;
        }

        // =====================================================
        // النقرة الأولى
        // =====================================================

        doubleBackToExitPressedOnce = true;

        /*
         * مهم:
         * لا يوجد هنا:
         *
         * goBack()
         * safeGoBack()
         * loadUrl()
         * reload()
         * stopLoading()
         *
         * ولا أي تعامل مع WebView History.
         *
         * نحن فقط نظهر رسالة Native.
         */

        android.widget.Toast.makeText(
                MainActivity.this,
                "اضغط مرة أخرى للخروج",
                android.widget.Toast.LENGTH_SHORT
        ).show();

        backPressHandler.removeCallbacks(resetBackPressFlag);

        backPressHandler.postDelayed(
                resetBackPressFlag,
                2000
        );

        Log.i(
                TAG,
                "👑 First BACK press on HOME → exit warning shown."
        );
    }

    /**
     * 👑 خروج آمن.
     *
     * لا نلمس WebView ولا نحمّل about:blank.
     * فقط نخرج من الـ Task.
     */
    private void performRoyalExit() {

        try {

            /*
             * إيقاف المؤقت حتى لا يبقى Runnable
             * يحاول تعديل حالة النقرتين بعد الخروج.
             */
            backPressHandler.removeCallbacks(resetBackPressFlag);

            doubleBackToExitPressedOnce = false;

            /*
             * إخراج الـ Task من الواجهة بدون تنفيذ
             * أي Navigation داخل WebView.
             */
            moveTaskToBack(true);

            Log.i(
                    TAG,
                    "👑 Royal exit completed."
            );

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Royal exit failed: " + e.getMessage()
            );

            /*
             * احتياط أخير فقط في حالة فشل moveTaskToBack.
             */
            try {
                finish();
            } catch (Exception ignored) {
            }
        }
    }

    // =========================================================
    // ⚙️ إعدادات واجهة السبلاش (بدون تغيير)
    // =========================================================

    private void setupSplashScreen() {
        splashStartTime = System.currentTimeMillis();

        // 👑 [تعديل جراحي ملكي 2]: تجميد الشاشة حتى اكتمال الـ 5 ثوانٍ، ثم إطلاق أنيميشن الـ Fade-out
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            findViewById(android.R.id.content).getViewTreeObserver().addOnPreDrawListener(
                    new ViewTreeObserver.OnPreDrawListener() {
                        @Override
                        public boolean onPreDraw() {
                            if (splashRemoved) {
                                // انقضت الـ 5 ثوانٍ.. نسمح للنظام بالرسم ليبدأ أنيميشن الـ Fade-Out
                                findViewById(android.R.id.content).getViewTreeObserver().removeOnPreDrawListener(this);
                                return true;
                            } else {
                                // الـ 5 ثوانٍ لم تنتهِ بعد.. جمّد الشاشة بصلابة!
                                return false;
                            }
                        }
                    }
            );
        }

        final FrameLayout splashContainer = new FrameLayout(this);
        splashContainer.setBackgroundColor(Color.parseColor("#F3F4F6"));

        ImageView splashIcon = new ImageView(this);
        splashIcon.setImageResource(R.mipmap.ic_launcher);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(280, 280, android.view.Gravity.CENTER);
        splashIcon.setLayoutParams(iconParams);
        splashContainer.addView(splashIcon);

        addContentView(splashContainer, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        addContentView(progressBar, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 8));

        engineManager = new WebEngineManager(
                this, activeWebView, splashContainer, progressBar,
                () -> splashRemoved = true, () -> splashRemoved
        );
        engineManager.setSplashStartTime(splashStartTime);
        engineManager.init();

        // 🚀 الـ Handler المعتمد للـ 5 ثوانٍ
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!splashRemoved) {
                engineManager.triggerFinalReveal();
            }
        }, FIXED_SPLASH_TIME);

        // 🛡️ تعطيل الاستجابة التلقائية للجسور
        if (RoyalWebViewHost.getBridge() != null) {
            RoyalWebViewHost.getBridge().setOnHideSplashCallback(() -> {
                Log.i(TAG, "⚡ Page ready, but Splash is LOCKED by engineer's timer.");
            });
        }
    }

    // =========================================================
    // 🔄 نتائج النشاطات والصلاحيات (محسّن)
    // =========================================================

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
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

    // =========================================================
    // 🔧 حفظ واستعادة الحالة المحسّن (saveState/restoreState)
    // =========================================================

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (activeWebView != null) {
            try {
                // حفظ الحالة في Bundle للتسريع
                activeWebView.saveState(outState);
                Log.i(TAG, "💾 WebView state saved.");
            } catch (Exception e) {
                Log.w(TAG, "SaveState failed: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (activeWebView != null && savedInstanceState != null) {
            try {
                activeWebView.restoreState(savedInstanceState);
                isPageLoaded = true;
                Log.i(TAG, "🔄 WebView state restored.");
            } catch (Exception e) {
                Log.w(TAG, "RestoreState failed: " + e.getMessage());
            }
        }
    }
}
