package com.store.app.navigation;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 👑 RoyalNavigationSnapshot
 *
 * مسؤول فقط عن:
 *
 * 1. التقاط الحالة البصرية الحالية للـ WebView.
 * 2. إنشاء Native Overlay فوق الـ WebView.
 * 3. تثبيت اللقطة أثناء انتقال الصفحة.
 * 4. إزالة اللقطة بأنيميشن نظيف.
 *
 * لا يحتوي على منطق التنقل نفسه.
 */
public final class RoyalNavigationSnapshot {

    private static final long DEFAULT_FADE_DURATION = 180L;

    private final ViewGroup root;

    private ImageView overlay;
    private Bitmap snapshot;

    private final AtomicBoolean active = new AtomicBoolean(false);

    public RoyalNavigationSnapshot(ViewGroup root) {
        this.root = root;
    }

    /**
     * التقاط لقطة فورية من WebView.
     *
     * هذه العملية متعمدة أن تكون مباشرة حتى تكون اللقطة
     * هي آخر Frame مرئي قبل بدء التنقل.
     */
    public Bitmap capture(WebView webView) {

        if (webView == null) {
            return null;
        }

        if (!webView.isAttachedToWindow()) {
            return null;
        }

        int width = webView.getWidth();
        int height = webView.getHeight();

        if (width <= 0 || height <= 0) {
            return null;
        }

        try {

            Bitmap bitmap = Bitmap.createBitmap(
                    width,
                    height,
                    Bitmap.Config.ARGB_8888
            );

            Canvas canvas = new Canvas(bitmap);

            webView.draw(canvas);

            return bitmap;

        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * تثبيت Snapshot فوق WebView.
     *
     * لا نستخدم fade-in هنا.
     *
     * السبب:
     * اللقطة يجب أن تظهر فورًا وبلا أي Frame مكشوف.
     */
    public boolean show(Bitmap bitmap) {

        if (bitmap == null || root == null) {
            return false;
        }

        removeImmediate();

        try {

            snapshot = bitmap;

            overlay = new ImageView(root.getContext());

            overlay.setImageBitmap(snapshot);

            overlay.setScaleType(ImageView.ScaleType.FIT_XY);

            overlay.setClickable(true);
            overlay.setFocusable(false);

            overlay.setAlpha(1f);

            FrameLayout.LayoutParams params =
                    new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                    );

            root.addView(overlay, params);

            active.set(true);

            return true;

        } catch (Throwable ignored) {

            cleanupBitmap();

            return false;
        }
    }

    /**
     * هل الـ Snapshot نشط؟
     */
    public boolean isActive() {
        return active.get();
    }

    /**
     * إزالة اللقطة بأنيميشن.
     *
     * الأنيميشن قصير جدًا حتى لا يشعر المستخدم
     * أن التطبيق ينتظر الصفحة.
     */
    public void hideAnimated(long duration, Runnable endAction) {

        final ImageView currentOverlay = overlay;

        if (currentOverlay == null) {

            active.set(false);

            if (endAction != null) {
                endAction.run();
            }

            cleanupBitmap();

            return;
        }

        long safeDuration =
                duration > 0
                        ? duration
                        : DEFAULT_FADE_DURATION;

        currentOverlay.animate()
                .alpha(0f)
                .setDuration(safeDuration)
                .withEndAction(() -> {

                    removeView(currentOverlay);

                    active.set(false);

                    cleanupBitmap();

                    if (endAction != null) {
                        endAction.run();
                    }
                })
                .start();
    }

    /**
     * إزالة فورية في الحالات الاستثنائية:
     *
     * - renderer crash
     * - activity destroy
     * - timeout
     * - إعادة استخدام المحرك
     */
    public void removeImmediate() {

        ImageView currentOverlay = overlay;

        if (currentOverlay != null) {

            currentOverlay.animate().cancel();

            removeView(currentOverlay);
        }

        overlay = null;

        active.set(false);

        cleanupBitmap();
    }

    private void removeView(View view) {

        if (view == null) {
            return;
        }

        ViewGroup parent = root;

        if (view.getParent() instanceof ViewGroup) {
            parent = (ViewGroup) view.getParent();
        }

        try {
            parent.removeView(view);
        } catch (Throwable ignored) {
        }

        if (view instanceof ImageView) {
            ((ImageView) view).setImageDrawable(null);
        }
    }

    private void cleanupBitmap() {

        Bitmap old = snapshot;

        snapshot = null;

        if (old != null && !old.isRecycled()) {

            try {
                old.recycle();
            } catch (Throwable ignored) {
            }
        }
    }
  }
