package com.store.app.offline;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.store.app.BuildConfig;
import com.store.app.NetworkMonitor;
import com.store.app.R;
import com.store.app.RoyalNetworkEngine;
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
        // 🎨 ORIGINAL OFFLINE ILLUSTRATION
        // =====================================================

        ImageView illustration =
                new ImageView(activity);

        illustration.setImageResource(
                R.drawable.offline_illustration
        );

        illustration.setScaleType(
                ImageView.ScaleType.FIT_CENTER
        );

        illustration.setAdjustViewBounds(
                true
        );

        illustration.setBackgroundColor(
                Color.TRANSPARENT
        );

        FrameLayout.LayoutParams illustrationParams =
                new FrameLayout.LayoutParams(
                        dp(300),
                        dp(430),
                        Gravity.TOP | Gravity.CENTER_HORIZONTAL
                );

        illustrationParams.topMargin =
                dp(34);

        pureOfflineUI.addView(
                illustration,
                illustrationParams
        );

        // =====================================================
        // 💳 PROFESSIONAL FLOATING CARD
        // =====================================================

        LinearLayout bottomCard =
                new LinearLayout(activity);

        bottomCard.setOrientation(
                LinearLayout.VERTICAL
        );

        bottomCard.setGravity(
                Gravity.CENTER_HORIZONTAL
        );

        bottomCard.setBackground(
                createCardDrawable()
        );

        /*
         * HTML equivalent:
         *
         * padding: 26px 22px;
         * border-radius: 28px;
         * border: 1px solid rgba(0,0,0,.04);
         * box-shadow: ...
         */
        bottomCard.setPadding(
                dp(22),
                dp(26),
                dp(22),
                dp(26)
        );

        /*
         * ظل حقيقي Native.
         *
         * elevation = طبقة الظل الأساسية.
         * ويتم تحريكها لاحقاً في الـ ambient animation.
         */
        bottomCard.setElevation(
                dp(16)
        );

        bottomCard.setTranslationZ(
                dp(0)
        );

        // =====================================================
        // 📐 RESPONSIVE CARD POSITION
        // =====================================================

        FrameLayout.LayoutParams cardParams =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM
                );

        /*
         * HTML body:
         *
         * padding: 16px;
         *
         * لذلك الكرت لا يلامس حواف الشاشة.
         */
        cardParams.leftMargin =
                dp(16);

        cardParams.rightMargin =
                dp(16);

        cardParams.bottomMargin =
                dp(4);

        pureOfflineUI.addView(
                bottomCard,
                cardParams
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
                20f
        );

        titleMsg.setTypeface(
                android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD
        );

        titleMsg.setGravity(
                Gravity.CENTER
        );

        titleMsg.setIncludeFontPadding(
                true
        );

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        titleParams.bottomMargin =
                dp(8);

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
                14.72f
        );

        subMsg.setGravity(
                Gravity.CENTER
        );

        subMsg.setIncludeFontPadding(
                true
        );

        subMsg.setLineSpacing(
                0,
                1.6f
        );

        LinearLayout.LayoutParams subParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        subParams.bottomMargin =
                dp(22);

        bottomCard.addView(
                subMsg,
                subParams
        );

        // =====================================================
        // 🔘 PROFESSIONAL RETRY BUTTON
        // =====================================================

        FrameLayout btnContainer =
                new FrameLayout(activity);

        btnContainer.setBackground(
                createRetryButtonDrawable()
        );

        btnContainer.setClickable(
                true
        );

        btnContainer.setFocusable(
                true
        );

        btnContainer.setForeground(
                createRippleDrawable()
        );

        // =====================================================
        // 🔄 BUTTON CONTENT
        // =====================================================

        LinearLayout btnContent =
                new LinearLayout(activity);

        btnContent.setOrientation(
                LinearLayout.HORIZONTAL
        );

        btnContent.setGravity(
                Gravity.CENTER
        );

        btnContent.setLayoutDirection(
                View.LAYOUT_DIRECTION_RTL
        );

        // =====================================================
        // 🔄 RETRY ICON
        // =====================================================

        ImageView retryIcon =
                new ImageView(activity);

        retryIcon.setImageDrawable(
                new RetryIconDrawable(
                        Color.WHITE,
                        dp(18)
                )
        );

        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(
                        dp(18),
                        dp(18)
                );

        /*
         * HTML:
         * gap: 5px
         */
        iconParams.setMargins(
                0,
                0,
                0,
                0
        );

        btnContent.addView(
                retryIcon,
                iconParams
        );

        // =====================================================
        // 📝 RETRY TEXT
        // =====================================================

        TextView retryText =
                new TextView(activity);

        retryText.setText(
                "إعادة المحاولة"
        );

        retryText.setTextColor(
                Color.WHITE
        );

        retryText.setTextSize(
                15.68f
        );

        retryText.setTypeface(
                android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD
        );

        retryText.setGravity(
                Gravity.CENTER
        );

        LinearLayout.LayoutParams retryTextParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        retryTextParams.setMargins(
                dp(5),
                0,
                0,
                0
        );

        btnContent.addView(
                retryText,
                retryTextParams
        );

        // =====================================================
        // ⚪ THREE DOTS LOADER
        // =====================================================

        LinearLayout dotsLoader =
                createDotsLoader();

        FrameLayout.LayoutParams dotsParams =
                new FrameLayout.LayoutParams(
                        dp(30),
                        dp(24),
                        Gravity.CENTER
                );

        dotsLoader.setVisibility(
                View.INVISIBLE
        );

        btnContainer.addView(
                dotsLoader,
                dotsParams
        );

        // =====================================================
        // 🎯 BUTTON CONTENT CENTER
        // =====================================================

        FrameLayout.LayoutParams contentParams =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER
                );

        btnContainer.addView(
                btnContent,
                contentParams
        );

        // =====================================================
        // 👆 RETRY ACTION
        // =====================================================

        btnContainer.setOnClickListener(v -> {

            if (!btnContainer.isEnabled()) {
                return;
            }

            btnContainer.setEnabled(
                    false
            );

            /*
             * إخفاء محتوى الزر.
             */
            btnContent.animate()
                    .alpha(0f)
                    .setDuration(160)
                    .start();

            /*
             * إظهار اللودر.
             */
            dotsLoader.setVisibility(
                    View.VISIBLE
            );

            dotsLoader.setAlpha(
                    0f
            );

            dotsLoader.animate()
                    .alpha(1f)
                    .setDuration(180)
                    .start();

            /*
             * دوران احترافي مستمر.
             */
            ObjectAnimator loaderRotation =
                    ObjectAnimator.ofFloat(
                            dotsLoader,
                            View.ROTATION,
                            0f,
                            360f
                    );

            loaderRotation.setDuration(
                    1200
            );

            loaderRotation.setRepeatCount(
                    ObjectAnimator.INFINITE
            );

            loaderRotation.setInterpolator(
                    new android.view.animation.LinearInterpolator()
            );

            loaderRotation.start();

            /*
             * نفس منطق الانتظار السابق:
             * لا نغير منطق الشبكة.
             */
            new Handler(
                    Looper.getMainLooper()
            ).postDelayed(() -> {

                if (loaderRotation != null) {
                    loaderRotation.cancel();
                }

                if (NetworkMonitor.isInternetAvailable(activity)) {

                    hideOfflineUI();

                    if (webView != null) {
                        webView.reload();
                    }

                } else {

                    dotsLoader.animate()
                            .alpha(0f)
                            .setDuration(160)
                            .withEndAction(() -> {

                                dotsLoader.setVisibility(
                                        View.INVISIBLE
                                );

                                dotsLoader.setRotation(
                                        0f
                                );

                                btnContent.animate()
                                        .alpha(1f)
                                        .setDuration(180)
                                        .start();

                                btnContainer.setEnabled(
                                        true
                                );
                            })
                            .start();

                    /*
                     * نفس حركة التنبيه السابقة،
                     * لكن أنعم بصرياً.
                     */
                    v.animate()
                            .translationX(dp(8))
                            .setDuration(55)
                            .withEndAction(() ->
                                    v.animate()
                                            .translationX(dp(-8))
                                            .setDuration(55)
                                            .withEndAction(() ->
                                                    v.animate()
                                                            .translationX(0)
                                                            .setDuration(55)
                                                            .start()
                                            )
                                            .start()
                            )
                            .start();
                }

            }, 1000);
        });

        // =====================================================
        // 📏 BUTTON SIZE
        // =====================================================

        LinearLayout.LayoutParams btnLayoutParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(52)
                );

        bottomCard.addView(
                btnContainer,
                btnLayoutParams
        );

        // =====================================================
        // 🎬 CARD ENTRANCE ANIMATION
        // =====================================================

        bottomCard.setAlpha(
                0f
        );

        bottomCard.setTranslationY(
                dp(24)
        );

        bottomCard.setScaleX(
                0.98f
        );

        bottomCard.setScaleY(
                0.98f
        );

        activity.addContentView(
                pureOfflineUI,
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        );

        /*
         * تشغيل الدخول فقط عند ظهور الواجهة.
         * لا يتم تشغيله هنا حتى لا يغير سلوك النظام.
         */

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
                new GradientDrawable();

        gd.setColor(
                OFFLINE_CARD_TOP
        );

        /*
         * HTML:
         * border-radius: 28px;
         */
        gd.setCornerRadius(
                dp(28)
        );

        /*
         * HTML:
         * border: 1px solid rgba(0,0,0,0.04)
         *
         * نستخدم لوناً فعلياً ثابتاً حتى تكون النتيجة
         * متناسقة على جميع إصدارات Android.
         */
        gd.setStroke(
                dp(1),
                Color.parseColor("#0A000000")
        );

        return gd;
    }

    private Drawable createRetryButtonDrawable() {

        GradientDrawable gd =
                new GradientDrawable();

        gd.setColor(
                OFFLINE_ACCENT
        );

        /*
         * HTML:
         * border-radius: 16px;
         */
        gd.setCornerRadius(
                dp(16)
        );

        return gd;
    }

    private Drawable createRippleDrawable() {

        GradientDrawable mask =
                new GradientDrawable();

        mask.setColor(
                Color.WHITE
        );

        mask.setCornerRadius(
                dp(16)
        );

        return new RippleDrawable(
                android.content.res.ColorStateList.valueOf(
                        Color.parseColor("#30FFFFFF")
                ),
                null,
                mask
        );
    }

    private LinearLayout createDotsLoader() {

        LinearLayout loader =
                new LinearLayout(activity);

        loader.setOrientation(
                LinearLayout.HORIZONTAL
        );

        loader.setGravity(
                Gravity.CENTER
        );

        loader.setLayoutDirection(
                View.LAYOUT_DIRECTION_LTR
        );

        for (int i = 0; i < 3; i++) {

            View dot =
                    new View(activity);

            GradientDrawable dotDrawable =
                    new GradientDrawable();

            dotDrawable.setShape(
                    GradientDrawable.OVAL
            );

            dotDrawable.setColor(
                    Color.WHITE
            );

            dot.setBackground(
                    dotDrawable
            );

            LinearLayout.LayoutParams dotParams =
                    new LinearLayout.LayoutParams(
                            dp(6),
                            dp(6)
                    );

            if (i > 0) {
                dotParams.leftMargin =
                        dp(4);
            }

            loader.addView(
                    dot,
                    dotParams
            );
        }

        return loader;
    }

    private static class RetryIconDrawable
            extends Drawable {

        private final Paint paint =
                new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Path path =
                new Path();

        private final float size;

        RetryIconDrawable(
                int color,
                float size
        ) {

            this.size = size;

            paint.setColor(
                    color
            );

            paint.setStyle(
                    Paint.Style.STROKE
            );

            paint.setStrokeWidth(
                    size * 0.115f
            );

            paint.setStrokeCap(
                    Paint.Cap.ROUND
            );

            paint.setStrokeJoin(
                    Paint.Join.ROUND
            );
        }

        @Override
        public void draw(Canvas canvas) {

            RectF bounds =
                    getBounds();

            float left =
                    bounds.left;

            float top =
                    bounds.top;

            float right =
                    bounds.right;

            float bottom =
                    bounds.bottom;

            float cx =
                    (left + right) / 2f;

            float cy =
                    (top + bottom) / 2f;

            float radius =
                    Math.min(
                            right - left,
                            bottom - top
                    ) * 0.34f;

            /*
             * سهم دائري قريب جداً من
             * Material refresh icon الموجود في HTML.
             */
            RectF arcRect =
                    new RectF(
                            cx - radius,
                            cy - radius,
                            cx + radius,
                            cy + radius
                    );

            canvas.drawArc(
                    arcRect,
                    -55f,
                    285f,
                    false,
                    paint
            );

            path.reset();

            float arrowX =
                    cx + radius * 0.98f;

            float arrowY =
                    cy - radius * 0.88f;

            path.moveTo(
                    arrowX,
                    arrowY
            );

            path.lineTo(
                    arrowX - size * 0.28f,
                    arrowY
            );

            path.moveTo(
                    arrowX,
                    arrowY
            );

            path.lineTo(
                    arrowX,
                    arrowY + size * 0.28f
            );

            canvas.drawPath(
                    path,
                    paint
            );
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(
                    alpha
            );
        }

        @Override
        public void setColorFilter(
                android.graphics.ColorFilter colorFilter) {

            paint.setColorFilter(
                    colorFilter
            );
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return Math.round(size);
        }

        @Override
        public int getIntrinsicHeight() {
            return Math.round(size);
        }
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

            // 👑 تشغيل Animation الكرت عند ظهوره
            View card = pureOfflineUI.getChildAt(1);

            if (card != null) {

                card.setAlpha(0f);
                card.setTranslationY(dp(24));
                card.setScaleX(0.98f);
                card.setScaleY(0.98f);

                card.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(800)
                        .setInterpolator(
                                new android.view.animation.PathInterpolator(
                                        0.16f,
                                        1f,
                                        0.3f,
                                        1f
                                )
                        )
                        .withEndAction(() -> {

                            /*
                             * Ambient floating:
                             *
                             * HTML:
                             * translateY(-4px)
                             *
                             * بشكل مستمر وهادئ.
                             */
                            ObjectAnimator floating =
                                    ObjectAnimator.ofFloat(
                                            card,
                                            View.TRANSLATION_Y,
                                            0f,
                                            -dp(4),
                                            0f
                                    );

                            floating.setDuration(
                                    4000
                            );

                            floating.setInterpolator(
                                    new android.view.animation.AccelerateDecelerateInterpolator()
                            );

                            floating.setRepeatCount(
                                    ObjectAnimator.INFINITE
                            );

                            floating.start();

                            card.setTag(
                                    floating
                            );

                            /*
                             * محاكاة نبض الظل.
                             */
                            ObjectAnimator elevation =
                                    ObjectAnimator.ofFloat(
                                            card,
                                            View.TRANSLATION_Z,
                                            dp(16),
                                            dp(22),
                                            dp(16)
                                    );

                            elevation.setDuration(
                                    4000
                            );

                            elevation.setInterpolator(
                                    new android.view.animation.AccelerateDecelerateInterpolator()
                            );

                            elevation.setRepeatCount(
                                    ObjectAnimator.INFINITE
                            );

                            elevation.start();

                            card.setTag(
                                    "elevation_anim",
                                    elevation
                            );
                        })
                        .start();
            }

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

            // 👑 إلغاء الأنيميشن عند الإخفاء
            View card = pureOfflineUI.getChildAt(1);

            if (card != null) {

                card.animate().cancel();

                Object tag =
                        card.getTag();

                if (tag instanceof ObjectAnimator) {
                    ((ObjectAnimator) tag).cancel();
                }

                Object elevationTag =
                        card.getTag(
                                "elevation_anim"
                        );

                if (elevationTag instanceof ObjectAnimator) {
                    ((ObjectAnimator) elevationTag).cancel();
                }

                card.setTranslationY(
                        dp(0)
                );

                card.setTranslationZ(
                        dp(16)
                );
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

    private int dp(float value) {
        return Math.round(
                value * activity.getResources()
                        .getDisplayMetrics()
                        .density
        );
    }
            }
