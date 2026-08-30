package com.store.app.offline;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.store.app.BuildConfig;
import com.store.app.NetworkMonitor;
import com.store.app.WebEngineManager;
import com.store.app.SystemUI;
import com.store.app.offline.OfflineStateManager;

/**
 * 👑 OfflineUIController - المسؤول عن إدارة واجهات الأوفلاين
 * 
 * تم نقل جميع منطق الأوفلاين من MainActivity إلى هنا
 * - إدارة واجهة الأوفلاين الكبيرة (pureOfflineUI)
 * - إدارة شريط الأوفلاين النحيف (offlineBar)
 * - التعامل مع تغيرات حالة الشبكة
 * - التبديل السلس بين WebView والواجهات الناتيف
 */
public class OfflineUIController {

    private static final String TAG = "OfflineUIController";

    // ==========================================
    // 🔥 المتغيرات
    // ==========================================

    private final Activity activity;
    private final WebView webView;
    private final WebEngineManager engineManager;

    // عناصر الواجهة
    private FrameLayout pureOfflineUI;
    private TextView offlineBar;
    private ProgressBar progressBar;

    // حالة الأوفلاين
    private boolean isOfflineUIVisible = false;
    private boolean isPageLoaded = false;

    // 👑 ألوان واجهة الأوفلاين المركزية
    private static final int OFFLINE_BACKGROUND =
            Color.parseColor("#F3F4F6");

    private static final int OFFLINE_CARD_TOP =
            Color.parseColor("#FFFFFF");

    private static final int OFFLINE_CARD_BOTTOM =
            Color.parseColor("#F5F6F9");

    private static final int OFFLINE_PRIMARY_TEXT =
            Color.parseColor("#17181C");

    private static final int OFFLINE_SECONDARY_TEXT =
            Color.parseColor("#6B707A");

    private static final int OFFLINE_ACCENT =
            Color.parseColor("#6674E8");

    // مراجع للعناصر الأخرى (للوصول إليها من MainActivity)
    public interface OfflineUICallback {
        void onOfflineUIVisibilityChanged(boolean visible);
        void onOfflineBarVisibilityChanged(boolean visible);
    }
    private OfflineUICallback callback;

    // ==========================================
    // 🚀 دورة الحياة
    // ==========================================

    public OfflineUIController(Activity activity, WebView webView, WebEngineManager engineManager) {
        this.activity = activity;
        this.webView = webView;
        this.engineManager = engineManager;
    }

    /**
     * 🔥 تهيئة وحدة الأوفلاين
     * - إنشاء الواجهات
     * - تسجيل مستمع الشبكة
     */
    public void init() {
        Log.i(TAG, "🚀 Initializing OfflineUIController...");

        // 1. إنشاء الواجهات
        createOfflineBar();
        createPureOfflineUI();

        // 2. إنشاء شريط التقدم (progressBar) مرة واحدة
        progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 6, android.view.Gravity.TOP);
        activity.addContentView(progressBar, p);
        progressBar.setVisibility(View.GONE);

        // 3. تسجيل مستمع الشبكة
        NetworkMonitor.setListener(connected -> {
            Log.i(TAG, "📡 Network state changed: " + connected);
            handleNetworkChange(connected);
        });

        // 🔥 ربط OfflineStateManager
        OfflineStateManager.getInstance().bind(webView, this);

        Log.i(TAG, "✅ OfflineUIController initialized.");
    }

    /**
     * 🔥 يُستدعى من MainActivity.onResume()
     */
    public void onResume() {
        Log.d(TAG, "🔄 onResume called");

        if (activity == null || activity.isFinishing()) return;

        // تحقق من حالة الشبكة عند العودة للتطبيق
        if (!NetworkMonitor.isInternetAvailable(activity)) {

            SystemUI.applyHeaderColor(
                    activity,
                    OFFLINE_BACKGROUND
            );

            // الإنترنت مقطوع
            handleOfflineState();
        }
        // ✅ لا تستدعِ handleOnlineState هنا مباشرة
        // اتركها لـ OfflineStateManager عند تغير الشبكة
    }

    /**
     * 🔥 يُستدعى من MainActivity.onDestroy()
     */
    public void destroy() {
        Log.i(TAG, "🧹 Destroying OfflineUIController...");
        // تنظيف المستمعين
        NetworkMonitor.setListener(null);
        // تنظيف المراجع
        pureOfflineUI = null;
        offlineBar = null;
        callback = null;
    }

    // ==========================================
    // 📡 معالجة تغيرات الشبكة
    // ==========================================

    private void handleNetworkChange(boolean connected) {
        if (connected) {
            // ✅ الإنترنت عاد
            handleOnlineState();
        } else {
            // ❌ الإنترنت انقطع
            handleOfflineState();
        }
    }

    private void handleOfflineState() {
        Log.i(TAG, "📡 Network lost. Handling offline state...");

        if (webView == null) return;

        // إذا كانت الصفحة فارغة أو غير صالحة
        if (webView.getUrl() == null || webView.getUrl().equals("about:blank")) {
            showOfflineUI();
        } else if (engineManager != null && !engineManager.isPageValid()) {
            // الصفحة غير صالحة (خطأ)
            showOfflineUI();
        } else {
            // صفحة موجودة وصالحة → إظهار الشريط النحيف
            showOfflineBar();
        }
    }

    private void handleOnlineState() {
        Log.i(TAG, "🌐 Network restored. Synchronizing UI...");

        if (webView == null) return;

        // ❌ لا نخفي الواجهة الكبيرة هنا فوراً، سنتركها حتى يكتمل التحميل
        // لكي لا يرى المستخدم صفحة بيضاء أثناء انتظار رد السيرفر
        
        if (offlineBar != null && offlineBar.getVisibility() == View.VISIBLE) {
            hideOfflineBarWithAnimation();
        }

        // [تعديل]: لا تعمل reload إذا الصفحة صالحة
        if (webView.getUrl() == null || webView.getUrl().equals("about:blank")) {
            // نستخدم loadUrl بدلاً من reload لضمان كسر حالة الأوفلاين
            webView.loadUrl(BuildConfig.CLIENT_URL);
        } else if (!OfflineStateManager.getInstance().isPageValid()) {
            // فقط إذا الصفحة غير صالحة
            webView.reload();
        }
        // else: الصفحة صالحة، لا تفعل شيئاً
    }

    // أضف هذه الدالة ليتم استدعاؤها من WebEngineManager عند نجاح التحميل
    public void forceHideAllInternal() {
        activity.runOnUiThread(() -> {
            if (isOfflineUIVisible) hideOfflineUI();
            setOfflineBarVisibility(false);
        });
    }

    // استدعاء عند عودة الشبكة بينما الصفحة صالحة
    public void showOnlineBarTransition() {
        if (offlineBar == null) return;
        activity.runOnUiThread(() -> {
            offlineBar.setBackgroundColor(Color.parseColor("#1A237E")); // لون الاستعادة
            offlineBar.setText("🔄 تم استعادة الاتصال، جاري التحديث...");
            if (offlineBar.getVisibility() != View.VISIBLE) {
                offlineBar.setVisibility(View.VISIBLE);
                offlineBar.setAlpha(0f);
                offlineBar.animate().alpha(1f).setDuration(220).start();
            } else {
                offlineBar.animate().scaleX(1.02f).scaleY(1.02f).setDuration(110)
                    .withEndAction(() -> offlineBar.animate().scaleX(1f).scaleY(1f).setDuration(110).start()).start();
            }
            // إخفاء تلقائي بعد مهلة قصيرة إذا لم يتم إخفاؤه من notifyPageReadyToHide
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (offlineBar != null && offlineBar.getVisibility() == View.VISIBLE) {
                    hideOfflineBarWithAnimation();
                }
            }, 900);
        });
    }

    // عرض overlay تحميل فوق الويب فيو (بدون إخفاء الويب فيو)
    public void showLoadingOverlay() {
        activity.runOnUiThread(() -> {
            if (progressBar != null) {
                if (progressBar.getVisibility() != View.VISIBLE) {
                    progressBar.setAlpha(0f);
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.animate().alpha(1f).setDuration(180).start();
                }
            }
        });
    }

    public void hideLoadingOverlay() {
        activity.runOnUiThread(() -> {
            if (progressBar != null && progressBar.getVisibility() == View.VISIBLE) {
                progressBar.animate().alpha(0f).setDuration(180)
                    .withEndAction(() -> progressBar.setVisibility(View.GONE)).start();
            }
        });
    }

    // ==========================================
    // 🎨 إنشاء الواجهات
    // ==========================================

    /**
     * 📡 شريط الأوفلاين النحيف
     */
    private void createOfflineBar() {
        if (activity == null) return;

        offlineBar = new TextView(activity);
        offlineBar.setText("لا يتوفر اتصال بالإنترنت");
        offlineBar.setTextColor(Color.WHITE);
        offlineBar.setBackgroundColor(Color.parseColor("#323232"));
        offlineBar.setGravity(android.view.Gravity.CENTER);
        offlineBar.setPadding(0, 12, 0, 12);
        offlineBar.setTextSize(14f);
        offlineBar.setVisibility(View.GONE);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 80, android.view.Gravity.BOTTOM);
        params.bottomMargin = 0;
        activity.addContentView(offlineBar, params);

        Log.d(TAG, "📡 Offline bar created.");
    }

    /**
     * 🍏 واجهة الأوفلاين الناتيف الكبيرة
     */
    private void createPureOfflineUI() {

        if (activity == null) return;

        // =====================================================
        // 👑 ROOT OFFLINE SURFACE
        // =====================================================

        pureOfflineUI = new FrameLayout(activity);

        pureOfflineUI.setBackgroundColor(
                OFFLINE_BACKGROUND
        );

        pureOfflineUI.setVisibility(
                View.GONE
        );

        // =====================================================
        // 🎨 NATIVE OFFLINE ILLUSTRATION
        // =====================================================

        OfflineIllustrationView illustration =
                new OfflineIllustrationView(activity);

        FrameLayout.LayoutParams illustrationParams =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(430),
                        Gravity.TOP | Gravity.CENTER_HORIZONTAL
                );

        illustrationParams.leftMargin = dp(20);
        illustrationParams.rightMargin = dp(20);
        illustrationParams.topMargin = dp(24);

        pureOfflineUI.addView(
                illustration,
                illustrationParams
        );

        // =====================================================
        // 💳 BOTTOM CARD
        // =====================================================

        LinearLayout bottomCard =
                new LinearLayout(activity);

        bottomCard.setOrientation(
                LinearLayout.VERTICAL
        );

        bottomCard.setBackground(
                createCardDrawable()
        );

        bottomCard.setPadding(
                dp(32),
                dp(38),
                dp(32),
                dp(46)
        );

        bottomCard.setGravity(
                Gravity.CENTER_HORIZONTAL
        );

        // =====================================================
        // 📝 TITLE
        // =====================================================

        TextView titleMsg =
                new TextView(activity);

        titleMsg.setText(
                "لا يوجد اتصال بالإنترنت"
        );

        titleMsg.setTextColor(
                OFFLINE_PRIMARY_TEXT
        );

        titleMsg.setTextSize(
                18f
        );

        titleMsg.setTypeface(
                android.graphics.Typeface.DEFAULT_BOLD
        );

        titleMsg.setGravity(
                Gravity.CENTER
        );

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        titleParams.bottomMargin =
                dp(20);

        bottomCard.addView(
                titleMsg,
                titleParams
        );

        // =====================================================
        // 📝 DESCRIPTION
        // =====================================================

        TextView subMsg =
                new TextView(activity);

        subMsg.setText(
                "يبدو أنك غير متصل بالشبكة. يرجى التحقق من الواي فاي أو بيانات الهاتف والمحاولة مجدداً."
        );

        subMsg.setTextColor(
                OFFLINE_SECONDARY_TEXT
        );

        subMsg.setTextSize(
                14f
        );

        subMsg.setGravity(
                Gravity.CENTER
        );

        subMsg.setLineSpacing(
                dp(10),
                1.1f
        );

        LinearLayout.LayoutParams subParams =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        subParams.bottomMargin =
                dp(40);

        bottomCard.addView(
                subMsg,
                subParams
        );

        // =====================================================
        // 🔘 RETRY BUTTON
        // =====================================================

        FrameLayout btnContainer =
                new FrameLayout(activity);

        GradientDrawable btnBg =
                new GradientDrawable();

        btnBg.setColor(
                OFFLINE_ACCENT
        );

        btnBg.setCornerRadius(
                dp(36)
        );

        btnContainer.setBackground(
                btnBg
        );

        btnContainer.setPadding(
                0,
                dp(18),
                0,
                dp(18)
        );

        LinearLayout btnContent =
                new LinearLayout(activity);

        btnContent.setOrientation(
                LinearLayout.HORIZONTAL
        );

        btnContent.setGravity(
                Gravity.CENTER
        );

        TextView retryText =
                new TextView(activity);

        retryText.setText(
                "🔄  إعادة المحاولة"
        );

        retryText.setTextColor(
                Color.WHITE
        );

        retryText.setTextSize(
                15f
        );

        retryText.setTypeface(
                android.graphics.Typeface.DEFAULT_BOLD
        );

        ProgressBar btnSpinner =
                new ProgressBar(
                        activity,
                        null,
                        android.R.attr.progressBarStyleSmall
                );

        btnSpinner.setVisibility(
                View.GONE
        );

        btnSpinner
                .getIndeterminateDrawable()
                .setColorFilter(
                        Color.WHITE,
                        android.graphics.PorterDuff.Mode.SRC_IN
                );

        btnContent.addView(
                retryText
        );

        btnContent.addView(
                btnSpinner
        );

        FrameLayout.LayoutParams contentParams =
                new FrameLayout.LayoutParams(
                        -2,
                        -2,
                        Gravity.CENTER
                );

        btnContainer.addView(
                btnContent,
                contentParams
        );

        // =====================================================
        // 👆 BUTTON ACTION
        // =====================================================

        btnContainer.setOnClickListener(v -> {

            retryText.setVisibility(
                    View.GONE
            );

            btnSpinner.setVisibility(
                    View.VISIBLE
            );

            btnContainer.setEnabled(
                    false
            );

            new Handler(
                    Looper.getMainLooper()
            ).postDelayed(() -> {

                if (NetworkMonitor.isInternetAvailable(activity)) {

                    hideOfflineUI();

                    if (webView != null) {
                        webView.reload();
                    }

                } else {

                    btnSpinner.setVisibility(
                            View.GONE
                    );

                    retryText.setVisibility(
                            View.VISIBLE
                    );

                    btnContainer.setEnabled(
                            true
                    );

                    v.animate()
                            .translationX(12)
                            .setDuration(50)
                            .withEndAction(() ->
                                    v.animate()
                                            .translationX(-12)
                                            .setDuration(50)
                                            .withEndAction(() ->
                                                    v.setTranslationX(0)
                                            )
                                            .start()
                            )
                            .start();
                }

            }, 1000);
        });

        LinearLayout.LayoutParams btnLayoutParams =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        btnLayoutParams.setMargins(
                dp(8),
                0,
                dp(8),
                0
        );

        bottomCard.addView(
                btnContainer,
                btnLayoutParams
        );

        // =====================================================
        // 📐 CARD
        // =====================================================

        FrameLayout.LayoutParams cardParams =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM
                );

        pureOfflineUI.addView(
                bottomCard,
                cardParams
        );

        activity.addContentView(
                pureOfflineUI,
                new ViewGroup.LayoutParams(
                        -1,
                        -1
                )
        );

        Log.d(
                TAG,
                "🍏 Professional Offline UI created."
        );
    }

    /**
     * 🔲 خلفية الكرت المنحنية
     */
    private Drawable createCardDrawable() {

        GradientDrawable gd =
                new GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{
                                OFFLINE_CARD_TOP,
                                OFFLINE_CARD_BOTTOM
                        }
                );

        gd.setCornerRadii(
                new float[]{
                        dp(34), dp(34),
                        dp(34), dp(34),
                        0, 0,
                        0, 0
                }
        );

        return gd;
    }

    private int dp(float value) {
        return Math.round(
                value * activity.getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    // =========================================================
    // 👑 Offline Surface → System UI Synchronization
    // =========================================================
    private void syncOfflineSystemUI() {

        if (activity == null ||
                activity.isFinishing()) {
            return;
        }

        SystemUI.syncWithNativeUI(
                activity,
                OFFLINE_BACKGROUND
        );
    }

    // ==========================================
    // 🎯 التحكم بالواجهات (public لاستدعائها من WebEngineManager)
    // ==========================================

    /**
     * 🔥 إظهار/إخفاء الواجهة الكبيرة
     */
    public void setOfflineUIVisibility(boolean show) {
        if (show && !isOfflineUIVisible) {
            showOfflineUI();
        } else if (!show && isOfflineUIVisible) {
            hideOfflineUI();
        }
    }

    /**
     * 🔥 إظهار/إخفاء الشريط النحيف
     */
    public void setOfflineBarVisibility(boolean show) {
        if (offlineBar == null) return;

        if (show) {
            showOfflineBar();
        } else {
            hideOfflineBarWithAnimation();
        }
    }

    // ==========================================
    // 🔧 الدوال الداخلية للتحكم بالواجهات
    // ==========================================

    private void showOfflineUI() {

        if (pureOfflineUI == null) {
            return;
        }

        isOfflineUIVisible = true;

        activity.runOnUiThread(() -> {

            if (activity.isFinishing()) {
                return;
            }

            /*
             * 👑 IMPORTANT:
             *
             * يجب أن يصبح Offline UI مالك الـ Status Bar
             * قبل أن نجعل الواجهة مرئية للمستخدم.
             *
             * لا يوجد هنا Handler
             * لا يوجد postDelayed
             * لا يوجد Animation قبل المزامنة.
             */
            SystemUI.forceNativeStatusBar(
                    activity,
                    OFFLINE_BACKGROUND
            );

            /*
             * إخفاء الشريط النحيف فورًا.
             */
            if (offlineBar != null) {

                offlineBar.animate().cancel();

                offlineBar.setVisibility(
                        View.GONE
                );
            }

            /*
             * 👑 الآن فقط نُظهر Offline UI.
             *
             * في هذه اللحظة الـ Status Bar
             * تم ضبطه بالفعل.
             */
            pureOfflineUI.setVisibility(
                    View.VISIBLE
            );

            // ❌ تم حذف الاستدعاء الثاني لـ forceNativeStatusBar هنا

            pureOfflineUI.setAlpha(1f);

            /*
             * WebView يختفي بعد أن أصبحت
             * الواجهة الأصلية جاهزة.
             */
            if (webView != null) {

                webView.setVisibility(
                        View.GONE
                );
            }

            if (callback != null) {

                callback.onOfflineUIVisibilityChanged(
                        true
                );
            }
        });

        Log.d(
                TAG,
                "🟠 Offline UI shown with synchronized System UI."
        );
    }

    private void hideOfflineUI() {

        if (pureOfflineUI == null) {
            return;
        }

        isOfflineUIVisible = false;

        activity.runOnUiThread(() -> {

            if (activity.isFinishing()) {
                return;
            }

            pureOfflineUI.animate()
                    .alpha(0f)
                    .setDuration(220)
                    .withEndAction(() -> {

                        pureOfflineUI.setVisibility(
                                View.GONE
                        );

                        pureOfflineUI.setAlpha(1f);

                        /*
                         * 👑 WebView يستعيد ملكية
                         * Status Bar بعد اختفاء Native UI.
                         */
                        SystemUI.scheduleStatusBarSync(
                                activity,
                                webView
                        );
                    })
                    .start();

            if (webView != null) {

                webView.setVisibility(
                        View.VISIBLE
                );
            }
        });

        if (callback != null) {

            callback.onOfflineUIVisibilityChanged(
                    false
            );
        }

        Log.d(
                TAG,
                "🟢 Offline UI hidden."
        );
    }

    private void showOfflineBar() {
        if (offlineBar == null) return;

        activity.runOnUiThread(() -> {
            offlineBar.setBackgroundColor(Color.parseColor("#323232"));
            offlineBar.setText("لا يتوفر اتصال بالإنترنت");
            offlineBar.setVisibility(View.VISIBLE);
            offlineBar.animate().translationY(0).setDuration(400).start();
        });

        if (callback != null) {
            callback.onOfflineBarVisibilityChanged(true);
        }

        Log.d(TAG, "📡 Offline bar shown.");
    }

    private void hideOfflineBarWithAnimation() {
        if (offlineBar == null) return;

        activity.runOnUiThread(() -> {
            offlineBar.setBackgroundColor(Color.parseColor("#1A237E"));
            offlineBar.setText("🔄 تم استعادة الاتصال، جاري التحديث...");

            offlineBar.animate().translationY(100).setDuration(400)
                    .withEndAction(() -> {
                        offlineBar.setVisibility(View.GONE);
                        // إعادة اللون الأصلي للاستخدام المستقبلي
                        offlineBar.setBackgroundColor(Color.parseColor("#323232"));
                        offlineBar.setText("لا يتوفر اتصال بالإنترنت");
                    }).start();
        });

        // إخفاء أي overlay تحميل إن وُجد
        hideLoadingOverlay();

        if (callback != null) {
            callback.onOfflineBarVisibilityChanged(false);
        }

        Log.d(TAG, "📡 Offline bar hidden with animation.");
    }

    // [إضافة جراحية في OfflineUIController.java]
    public void shakeOfflineBar() {
        if (offlineBar == null || offlineBar.getVisibility() != View.VISIBLE) {
            showOfflineBar();
        }

        activity.runOnUiThread(() -> {
            offlineBar.animate()
                    .translationX(12f).setDuration(60)
                    .withEndAction(() -> offlineBar.animate().translationX(-12f).setDuration(60)
                    .withEndAction(() -> offlineBar.animate().translationX(0f).setDuration(60).start())
                    .start()).start();

            String originalText = offlineBar.getText().toString();
            offlineBar.setText("⚠️ لا يمكن التحميل، تحقق من الاتصال");
            new Handler(Looper.getMainLooper()).postDelayed(() -> offlineBar.setText(originalText), 1800);
        });
    }

    // ==========================================
    // 🔗 الدوال العامة للاستعلام عن الحالة
    // ==========================================

    public boolean isOfflineUIVisible() {
        return isOfflineUIVisible;
    }

    public boolean isPageLoaded() {
        return isPageLoaded;
    }

    public void setPageLoaded(boolean loaded) {
        this.isPageLoaded = loaded;
    }

    public void setCallback(OfflineUICallback callback) {
        this.callback = callback;
    }

    /**
     * =========================================================
     * 👑 Native Offline Illustration
     * =========================================================
     *
     * رسم Native بالكامل باستخدام Canvas.
     *
     * المميزات:
     *
     * - لا يحتاج PNG
     * - لا يحتاج Drawable
     * - خلفية شفافة
     * - Responsive على مختلف أحجام الشاشات
     * - يحافظ على النسب
     * - Glow خفيف
     * - حدود أوضح وأغمق قليلًا
     * - لا يعتمد على Density معينة
     */
    private static class OfflineIllustrationView extends View {

        private final Paint paint =
                new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Path path =
                new Path();

        private final RectF rect =
                new RectF();

        private final float density;

        OfflineIllustrationView(Activity activity) {

            super(activity);

            density =
                    activity.getResources()
                            .getDisplayMetrics()
                            .density;

            setBackgroundColor(Color.TRANSPARENT);

            /*
             * نستخدم Software فقط لأننا نريد
             * Shadow / Glow ناعم ومستقر على
             * الأجهزة القديمة والجديدة.
             */
            setLayerType(
                    View.LAYER_TYPE_SOFTWARE,
                    null
            );
        }

        private float d(float value) {
            return value * density;
        }

        @Override
        protected void onDraw(Canvas canvas) {

            super.onDraw(canvas);

            if (getWidth() <= 0 || getHeight() <= 0) {
                return;
            }

            /*
             * =====================================================
             * DESIGN SPACE
             * =====================================================
             *
             * الرسم الأصلي مبني على:
             *
             * 300 × 430
             *
             * ثم نقوم بعمل Scale تلقائي حسب
             * مساحة الـ View الفعلية.
             */

            final float DESIGN_W = d(300);
            final float DESIGN_H = d(430);

            float availableW = getWidth();
            float availableH = getHeight();

            float scale =
                    Math.min(
                            availableW / DESIGN_W,
                            availableH / DESIGN_H
                    );

            float offsetX =
                    (availableW - DESIGN_W * scale) / 2f;

            float offsetY =
                    (availableH - DESIGN_H * scale) / 2f;

            canvas.save();

            canvas.translate(
                    offsetX,
                    offsetY
            );

            canvas.scale(
                    scale,
                    scale
            );

            /*
             * بعد هذه النقطة كل الإحداثيات
             * تعمل داخل مساحة 300×430.
             */

            drawCloudGlow(canvas);

            drawCloud(canvas);

            drawBrokenCable(canvas);

            drawRouter(canvas);

            drawSignal(canvas);

            drawGroundShadow(canvas);

            canvas.restore();
        }

        // =========================================================
        // ✨ CLOUD GLOW
        // =========================================================

        private void drawCloudGlow(Canvas canvas) {

            paint.reset();

            paint.setAntiAlias(true);

            paint.setStyle(
                    Paint.Style.FILL
            );

            paint.setShader(
                    new android.graphics.RadialGradient(
                            d(150),
                            d(92),
                            d(88),
                            new int[]{
                                    Color.argb(48, 255, 214, 135),
                                    Color.argb(22, 255, 226, 166),
                                    Color.argb(0, 255, 235, 190)
                            },
                            new float[]{
                                    0f,
                                    0.45f,
                                    1f
                            },
                            android.graphics.Shader.TileMode.CLAMP
                    )
            );

            canvas.drawCircle(
                    d(150),
                    d(92),
                    d(88),
                    paint
            );

            paint.setShader(null);
        }

        // =========================================================
        // ☁️ CLOUD
        // =========================================================

        private void drawCloud(Canvas canvas) {

            path.reset();

            /*
             * قاعدة السحابة
             */
            path.moveTo(
                    d(69),
                    d(112)
            );

            /*
             * الجانب الأيسر
             */
            path.cubicTo(
                    d(61),
                    d(111),
                    d(55),
                    d(105),
                    d(55),
                    d(97)
            );

            path.cubicTo(
                    d(55),
                    d(89),
                    d(61),
                    d(83),
                    d(70),
                    d(83)
            );

            /*
             * السحابة الصغيرة اليسرى
             */
            path.cubicTo(
                    d(71),
                    d(74),
                    d(78),
                    d(68),
                    d(87),
                    d(68)
            );

            /*
             * قمة السحابة
             */
            path.cubicTo(
                    d(87),
                    d(48),
                    d(102),
                    d(35),
                    d(120),
                    d(35)
            );

            path.cubicTo(
                    d(137),
                    d(35),
                    d(149),
                    d(44),
                    d(153),
                    d(57)
            );

            /*
             * السحابة الصغيرة اليمنى
             */
            path.cubicTo(
                    d(159),
                    d(54),
                    d(165),
                    d(53),
                    d(171),
                    d(53)
            );

            path.cubicTo(
                    d(185),
                    d(53),
                    d(195),
                    d(63),
                    d(195),
                    d(76)
            );

            /*
             * الجانب الأيمن
             */
            path.cubicTo(
                    d(207),
                    d(77),
                    d(216),
                    d(87),
                    d(216),
                    d(99)
            );

            path.cubicTo(
                    d(216),
                    d(107),
                    d(210),
                    d(112),
                    d(201),
                    d(112)
            );

            /*
             * قاعدة السحابة
             */
            path.lineTo(
                    d(69),
                    d(112)
            );

            path.close();

            /*
             * -----------------------------------------------------
             * Fill
             * -----------------------------------------------------
             */

            paint.reset();

            paint.setAntiAlias(true);

            paint.setStyle(
                    Paint.Style.FILL
            );

            paint.setShader(
                    new android.graphics.LinearGradient(
                            d(150),
                            d(35),
                            d(150),
                            d(112),
                            new int[]{
                                    Color.rgb(250, 250, 249),
                                    Color.rgb(232, 236, 242)
                            },
                            null,
                            android.graphics.Shader.TileMode.CLAMP
                    )
            );

            canvas.drawPath(
                    path,
                    paint
            );

            paint.setShader(null);

            /*
             * -----------------------------------------------------
             * Border
             * -----------------------------------------------------
             *
             * أغمق قليلاً من الصورة الأصلية
             * حتى تكون واضحة فوق الخلفية.
             */

            paint.setStyle(
                    Paint.Style.STROKE
            );

            paint.setStrokeWidth(
                    d(2.4f)
            );

            paint.setStrokeCap(
                    Paint.Cap.ROUND
            );

            paint.setStrokeJoin(
                    Paint.Join.ROUND
            );

            paint.setColor(
                    Color.rgb(145, 153, 164)
            );

            canvas.drawPath(
                    path,
                    paint
            );

            /*
             * -----------------------------------------------------
             * Cloud Face
             * -----------------------------------------------------
             */

            paint.setStyle(
                    Paint.Style.FILL
            );

            paint.setColor(
                    Color.rgb(126, 135, 147)
            );

            // العين اليسرى
            canvas.drawCircle(
                    d(112),
                    d(79),
                    d(4.1f),
                    paint
            );

            // العين اليمنى
            canvas.drawCircle(
                    d(166),
                    d(79),
                    d(4.1f),
                    paint
            );

            /*
             * الخدان
             */

            paint.setColor(
                    Color.argb(
                            125,
                            232,
                            199,
                            172
                    )
            );

            rect.set(
                    d(101),
                    d(86),
                    d(121),
                    d(96)
            );

            canvas.drawOval(
                    rect,
                    paint
            );

            rect.set(
                    d(157),
                    d(86),
                    d(177),
                    d(96)
            );

            canvas.drawOval(
                    rect,
                    paint
            );

            /*
             * الابتسامة
             */

            paint.setStyle(
                    Paint.Style.STROKE
            );

            paint.setStrokeWidth(
                    d(2.2f)
            );

            paint.setStrokeCap(
                    Paint.Cap.ROUND
            );

            paint.setColor(
                    Color.rgb(116, 125, 137)
            );

            path.reset();

            path.moveTo(
                    d(128),
                    d(88)
            );

            path.cubicTo(
                    d(134),
                    d(95),
                    d(144),
                    d(95),
                    d(150),
                    d(88)
            );

            canvas.drawPath(
                    path,
                    paint
            );
        }

        // =========================================================
        // 〰️ BROKEN CONNECTION
        // =========================================================

        private void drawBrokenCable(Canvas canvas) {

            paint.reset();

            paint.setAntiAlias(true);

            paint.setStyle(
                    Paint.Style.STROKE
            );

            paint.setStrokeWidth(
                    d(2.8f)
            );

            paint.setStrokeCap(
                    Paint.Cap.ROUND
            );

            paint.setStrokeJoin(
                    Paint.Join.ROUND
            );

            paint.setColor(
                    Color.rgb(145, 153, 164)
            );

            /*
             * الجزء الأول من السلك
             */

            path.reset();

            path.moveTo(
                    d(150),
                    d(112)
            );

            path.cubicTo(
                    d(150),
                    d(125),
                    d(147),
                    d(132),
                    d(141),
                    d(137)
            );

            path.cubicTo(
                    d(134),
                    d(143),
                    d(134),
                    d(150),
                    d(145),
                    d(153)
            );

            path.cubicTo(
                    d(157),
                    d(157),
                    d(161),
                    d(161),
                    d(159),
                    d(167)
            );

            canvas.drawPath(
                    path,
                    paint
            );

            /*
             * الجزء الثاني
             */

            path.reset();

            path.moveTo(
                    d(159),
                    d(167)
            );

            path.cubicTo(
                    d(156),
                    d(174),
                    d(147),
                    d(177),
                    d(146),
                    d(184)
            );

            path.cubicTo(
                    d(145),
                    d(191),
                    d(154),
                    d(193),
                    d(155),
                    d(201)
            );

            path.cubicTo(
                    d(156),
                    d(209),
                    d(148),
                    d(214),
                    d(149),
                    d(222)
            );

            canvas.drawPath(
                    path,
                    paint
            );

            /*
             * الجزء الثالث
             */

            path.reset();

            path.moveTo(
                    d(149),
                    d(222)
            );

            path.cubicTo(
                    d(151),
                    d(231),
                    d(160),
                    d(233),
                    d(158),
                    d(242)
            );

            path.cubicTo(
                    d(157),
                    d(250),
                    d(151),
                    d(255),
                    d(150),
                    d(265)
            );

            canvas.drawPath(
                    path,
                    paint
            );

            /*
             * -----------------------------------------------------
             * ✕ Disconnection
             * -----------------------------------------------------
             */

            paint.setStrokeWidth(
                    d(4.2f)
            );

            paint.setColor(
                    Color.rgb(139, 148, 160)
            );

            canvas.drawLine(
                    d(140),
                    d(232),
                    d(160),
                    d(252),
                    paint
            );

            canvas.drawLine(
                    d(160),
                    d(232),
                    d(140),
                    d(252),
                    paint
            );
        }

        // =========================================================
        // 📡 ROUTER
        // =========================================================

        private void drawRouter(Canvas canvas) {

            /*
             * -----------------------------------------------------
             * Antennas
             * -----------------------------------------------------
             */

            paint.reset();

            paint.setAntiAlias(true);

            paint.setStyle(
                    Paint.Style.STROKE
            );

            paint.setStrokeWidth(
                    d(2.8f)
            );

            paint.setStrokeCap(
                    Paint.Cap.ROUND
            );

            paint.setColor(
                    Color.rgb(139, 147, 158)
            );

            path.reset();

            path.moveTo(
                    d(121),
                    d(288)
            );

            path.lineTo(
                    d(113),
                    d(254)
            );

            canvas.drawPath(
                    path,
                    paint
            );

            path.reset();

            path.moveTo(
                    d(179),
                    d(288)
            );

            path.lineTo(
                    d(187),
                    d(254)
            );

            canvas.drawPath(
                    path,
                    paint
            );

            /*
             * -----------------------------------------------------
             * Main Router Body
             * -----------------------------------------------------
             */

            paint.setStyle(
                    Paint.Style.FILL
            );

            paint.setShader(
                    new android.graphics.LinearGradient(
                            d(150),
                            d(280),
                            d(150),
                            d(365),
                            new int[]{
                                    Color.rgb(250, 250, 248),
                                    Color.rgb(220, 225, 232)
                            },
                            null,
                            android.graphics.Shader.TileMode.CLAMP
                    )
            );

            rect.set(
                    d(116),
                    d(284),
                    d(184),
                    d(365)
            );

            canvas.drawRoundRect(
                    rect,
                    d(7),
                    d(7),
                    paint
            );

            paint.setShader(null);

            /*
             * Router border
             */

            paint.setStyle(
                    Paint.Style.STROKE
            );

            paint.setStrokeWidth(
                    d(2.2f)
            );

            paint.setColor(
                    Color.rgb(139, 147, 158)
            );

            canvas.drawRoundRect(
                    rect,
                    d(7),
                    d(7),
                    paint
            );

            /*
             * -----------------------------------------------------
             * Top Router
             * -----------------------------------------------------
             */

            paint.setStyle(
                    Paint.Style.FILL
            );

            paint.setShader(
                    new android.graphics.LinearGradient(
                            d(150),
                            d(272),
                            d(150),
                            d(307),
                            new int[]{
                                    Color.rgb(253, 252, 247),
                                    Color.rgb(226, 229, 226)
                            },
                            null,
                            android.graphics.Shader.TileMode.CLAMP
                    )
            );

            rect.set(
                    d(106),
                    d(272),
                    d(194),
                    d(307)
            );

            canvas.drawRoundRect(
                    rect,
                    d(7),
                    d(7),
                    paint
            );

            paint.setShader(null);

            /*
             * Top router border
             */

            paint.setStyle(
                    Paint.Style.STROKE
            );

            paint.setStrokeWidth(
                    d(2.3f)
            );

            paint.setColor(
                    Color.rgb(137, 145, 156)
            );

            canvas.drawRoundRect(
                    rect,
                    d(7),
                    d(7),
                    paint
            );

            /*
             * -----------------------------------------------------
             * Wi-Fi icon on router
             * -----------------------------------------------------
             */

            paint.setStrokeWidth(
                    d(2.3f)
            );

            paint.setColor(
                    Color.rgb(145, 153, 164)
            );

            rect.set(
                    d(138),
                    d(283),
                    d(162),
                    d(299)
            );

            canvas.drawArc(
                    rect,
                    220,
                    100,
                    false,
                    paint
            );

            rect.set(
                    d(142),
                    d(287),
                    d(158),
                    d(299)
            );

            canvas.drawArc(
                    rect,
                    220,
                    100,
                    false,
                    paint
            );

            paint.setStyle(
                    Paint.Style.FILL
            );

            canvas.drawCircle(
                    d(150),
                    d(299),
                    d(2.3f),
                    paint
            );

            /*
             * -----------------------------------------------------
             * Router indicator lights
             * -----------------------------------------------------
             */

            paint.setColor(
                    Color.rgb(153, 160, 169)
            );

            canvas.drawCircle(
                    d(178),
                    d(293),
                    d(2.1f),
                    paint
            );

            canvas.drawCircle(
                    d(185),
                    d(293),
                    d(2.1f),
                    paint
            );

            /*
             * -----------------------------------------------------
             * Main body Wi-Fi
             * -----------------------------------------------------
             */

            paint.setStyle(
                    Paint.Style.STROKE
            );

            paint.setStrokeWidth(
                    d(3)
            );

            paint.setStrokeCap(
                    Paint.Cap.ROUND
            );

            rect.set(
                    d(132),
                    d(315),
                    d(168),
                    d(341)
            );

            canvas.drawArc(
                    rect,
                    220,
                    100,
                    false,
                    paint
            );

            rect.set(
                    d(138),
                    d(321),
                    d(162),
                    d(341)
            );

            canvas.drawArc(
                    rect,
                    220,
                    100,
                    false,
                    paint
            );

            paint.setStyle(
                    Paint.Style.FILL
            );

            canvas.drawCircle(
                    d(150),
                    d(341),
                    d(3),
                    paint
            );

            /*
             * -----------------------------------------------------
             * Router bottom base
             * -----------------------------------------------------
             */

            paint.setStyle(
                    Paint.Style.FILL
            );

            paint.setShader(
                    new android.graphics.LinearGradient(
                            d(150),
                            d(350),
                            d(150),
                            d(370),
                            new int[]{
                                    Color.rgb(242, 244, 246),
                                    Color.rgb(205, 211, 219)
                            },
                            null,
                            android.graphics.Shader.TileMode.CLAMP
                    )
            );

            rect.set(
                    d(100),
                    d(350),
                    d(200),
                    d(371)
            );

            canvas.drawRoundRect(
                    rect,
                    d(8),
                    d(8),
                    paint
            );

            paint.setShader(null);

            paint.setStyle(
                    Paint.Style.STROKE
            );

            paint.setStrokeWidth(
                    d(2.3f)
            );

            paint.setColor(
                    Color.rgb(132, 141, 152)
            );

            canvas.drawRoundRect(
                    rect,
                    d(8),
                    d(8),
                    paint
            );
        }

        // =========================================================
        // 📶 SIGNAL BARS
        // =========================================================

        private void drawSignal(Canvas canvas) {

            paint.reset();

            paint.setAntiAlias(true);

            paint.setStyle(
                    Paint.Style.STROKE
            );

            paint.setStrokeWidth(
                    d(2.4f)
            );

            paint.setStrokeCap(
                    Paint.Cap.ROUND
            );

            paint.setColor(
                    Color.rgb(145, 153, 164)
            );

            /*
             * إشارة الشبكة الصغيرة
             */

            canvas.drawLine(
                    d(196),
                    d(391),
                    d(196),
                    d(401),
                    paint
            );

            canvas.drawLine(
                    d(205),
                    d(385),
                    d(205),
                    d(401),
                    paint
            );

            canvas.drawLine(
                    d(214),
                    d(377),
                    d(214),
                    d(401),
                    paint
            );

            canvas.drawLine(
                    d(223),
                    d(369),
                    d(223),
                    d(401),
                    paint
            );
        }

        // =========================================================
        // 🌫️ GROUND SHADOW
        // =========================================================

        private void drawGroundShadow(Canvas canvas) {

            paint.reset();

            paint.setAntiAlias(true);

            paint.setStyle(
                    Paint.Style.FILL
            );

            paint.setShader(
                    new android.graphics.RadialGradient(
                            d(150),
                            d(405),
                            d(80),
                            new int[]{
                                    Color.argb(55, 126, 135, 147),
                                    Color.argb(20, 126, 135, 147),
                                    Color.argb(0, 126, 135, 147)
                            },
                            new float[]{
                                    0f,
                                    0.55f,
                                    1f
                            },
                            android.graphics.Shader.TileMode.CLAMP
                    )
            );

            canvas.drawOval(
                    new RectF(
                            d(72),
                            d(397),
                            d(228),
                            d(416)
                    ),
                    paint
            );

            paint.setShader(null);
        }
    }
    }
