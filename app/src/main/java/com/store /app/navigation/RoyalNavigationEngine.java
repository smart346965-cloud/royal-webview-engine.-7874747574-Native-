package com.store.app.navigation;

import android.app.Activity;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ViewGroup;
import android.webkit.WebView;

/**
 * 👑 RoyalNavigationEngine
 *
 * محرك الانتقالات الداخلية.
 *
 * المسؤوليات:
 *
 * - تحديد بداية التنقل الداخلي.
 * - التقاط Snapshot قبل مغادرة الصفحة.
 * - تثبيت اللقطة أثناء تحميل الصفحة الجديدة.
 * - انتظار onPageCommitVisible.
 * - إزالة الـ Snapshot بأنيميشن.
 * - منع بقاء الـ Overlay عالقًا.
 * - حماية WebView من الوميض الأبيض أثناء الانتقال.
 *
 * لا يتدخل في:
 * - Router
 * - Fetch
 * - DOM replacement
 * - Barba.js
 * - SPA logic
 * - Business logic للموقع.
 */
public final class RoyalNavigationEngine {

    private static final String TAG = "RoyalNavigation";

    /**
     * أقصى مدة يسمح فيها للـ snapshot بالبقاء.
     *
     * مهم جدًا:
     * لا نريد أبدًا أن تبقى اللقطة فوق WebView
     * إذا فشل التحميل.
     */
    private static final long SNAPSHOT_TIMEOUT = 4500L;

    /**
     * مدة كشف الصفحة الجديدة.
     *
     * قصيرة بما يكفي للحفاظ على الإحساس الفوري.
     */
    private static final long REVEAL_DURATION = 180L;

    /**
     * أقل مدة بين عمليتي انتقال.
     *
     * تمنع تكرار إنشاء Snapshots بسرعة شديدة
     * عند النقر المتعدد.
     */
    private static final long MIN_TRANSITION_INTERVAL = 80L;

    private final Activity activity;
    private final WebView webView;
    private final RoyalNavigationSnapshot snapshot;

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private long transitionSequence = 0L;

    private long lastTransitionStart = 0L;

    private boolean transitionActive = false;

    private String pendingUrl = null;

    private final Runnable timeoutRunnable =
            this::handleTransitionTimeout;

    public RoyalNavigationEngine(
            Activity activity,
            WebView webView,
            ViewGroup root
    ) {

        this.activity = activity;
        this.webView = webView;

        this.snapshot =
                new RoyalNavigationSnapshot(root);
    }

    /**
     * استدعاؤها قبل تنفيذ التنقل الداخلي.
     *
     * @return true إذا تم تجهيز طبقة الانتقال.
     */
    public boolean beginInternalNavigation(Uri targetUri) {

        if (activity == null ||
                webView == null ||
                targetUri == null) {

            return false;
        }

        if (activity.isFinishing()) {
            return false;
        }

        if (android.os.Build.VERSION.SDK_INT >= 17 &&
                activity.isDestroyed()) {

            return false;
        }

        long now = System.currentTimeMillis();

        /*
         * حماية من Double Tap / Rapid Navigation.
         */
        if (transitionActive &&
                now - lastTransitionStart < MIN_TRANSITION_INTERVAL) {

            return true;
        }

        lastTransitionStart = now;

        transitionSequence++;

        pendingUrl = targetUri.toString();

        cancelTimeout();

        /*
         * إذا كانت هناك لقطة سابقة، نزيلها أولًا.
         */
        snapshot.removeImmediate();

        /*
         * التقاط آخر Frame مرئي.
         */
        Bitmap bitmap = snapshot.capture(webView);

        if (bitmap == null) {

            Log.w(
                    TAG,
                    "Snapshot capture failed. Navigation will continue normally."
            );

            transitionActive = false;
            pendingUrl = null;

            return false;
        }

        /*
         * وضع اللقطة فوق WebView.
         */
        boolean shown = snapshot.show(bitmap);

        if (!shown) {

            Log.w(
                    TAG,
                    "Snapshot overlay creation failed."
            );

            transitionActive = false;
            pendingUrl = null;

            return false;
        }

        transitionActive = true;

        /*
         * Failsafe:
         * حتى لو لم يصل onPageCommitVisible،
         * لا نسمح للطبقة بأن تبقى إلى الأبد.
         */
        scheduleTimeout(transitionSequence);

        Log.d(
                TAG,
                "🚀 Internal transition started: " + pendingUrl
        );

        return true;
    }

    /**
     * يتم استدعاؤها من onPageCommitVisible.
     *
     * هذه هي النقطة الصحيحة لكشف الصفحة الجديدة:
     *
     * old page snapshot
     *        ↓
     * new page committed
     *        ↓
     * reveal
     */
    public void onPageCommitVisible(String url) {

        if (!transitionActive) {
            return;
        }

        long currentSequence = transitionSequence;

        cancelTimeout();

        pendingUrl = url;

        snapshot.hideAnimated(
                REVEAL_DURATION,
                () -> {

                    /*
                     * لا ننفذ أي شيء إذا بدأ انتقال آخر
                     * أثناء عملية الإزالة.
                     */
                    if (currentSequence != transitionSequence) {
                        return;
                    }

                    transitionActive = false;

                    Log.d(
                            TAG,
                            "✨ Internal transition revealed: " + url
                    );
                }
        );
    }

    /**
     * في حال حدوث خطأ في الصفحة الجديدة.
     *
     * لا نريد أن نظل نعرض الصفحة القديمة.
     */
    public void onNavigationError() {

        if (!transitionActive) {
            return;
        }

        cancelTimeout();

        snapshot.hideAnimated(
                120L,
                () -> {

                    transitionActive = false;
                    pendingUrl = null;

                    Log.w(
                            TAG,
                            "⚠️ Transition released after navigation error."
                    );
                }
        );
    }

    /**
     * إلغاء آمن عند destroy / renderer crash.
     */
    public void destroy() {

        transitionSequence++;

        cancelTimeout();

        snapshot.removeImmediate();

        transitionActive = false;

        pendingUrl = null;
    }

    public boolean isTransitionActive() {
        return transitionActive;
    }

    public String getPendingUrl() {
        return pendingUrl;
    }

    private void scheduleTimeout(long sequence) {

        cancelTimeout();

        mainHandler.postDelayed(
                () -> {

                    if (sequence != transitionSequence) {
                        return;
                    }

                    handleTransitionTimeout();

                },
                SNAPSHOT_TIMEOUT
        );
    }

    private void handleTransitionTimeout() {

        if (!transitionActive) {
            return;
        }

        Log.w(
                TAG,
                "⏱️ Navigation transition timeout. Releasing snapshot."
        );

        transitionSequence++;

        snapshot.hideAnimated(
                100L,
                () -> {

                    transitionActive = false;
                    pendingUrl = null;
                }
        );
    }

    private void cancelTimeout() {

        mainHandler.removeCallbacks(timeoutRunnable);
    }
          }
