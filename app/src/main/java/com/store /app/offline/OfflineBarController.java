package com.store.app.offline;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
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
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class OfflineBarController {
    private static final String DEFAULT_TEXT = "أنت تعمل حالياً دون اتصال";
    private static final String RESTORED_TEXT = "تم استعادة الاتصال بنجاح";
    private static final String WARNING_TEXT = "⚠️ لا يمكن التحميل، تحقق من الاتصال";

    private static final int GLASS_BG = Color.argb(209, 18, 20, 26);
    private static final int GLASS_EXPANDED_BG = Color.argb(240, 22, 25, 33);
    private static final int GLASS_BORDER = Color.argb(31, 255, 255, 255);
    private static final int OFFLINE_COLOR = Color.rgb(125, 146, 176);
    private static final int OFFLINE_BORDER = Color.argb(89, 125, 146, 176);
    private static final int ONLINE_COLOR = Color.rgb(0, 230, 118);
    private static final int ONLINE_BORDER = Color.argb(115, 0, 230, 118);
    private static final int TEXT_COLOR = Color.rgb(249, 249, 251);

    private static final int COLLAPSED_SIZE_DP = 50;
    private static final int EXPANDED_WIDTH_DP = 230;
    private static final int BADGE_BOTTOM_MARGIN_DP = 28; // ارتفاع ثابت مريح فوق NavigationBar
    private static final int BADGE_HORIZONTAL_MARGIN_DP = 16; // تثبيت فاصل يمين الشاشة
    private static final int ICON_SIZE_DP = 24;

    private static final long APPEAR_DURATION = 700L;
    private static final long EXPAND_DURATION = 650L;
    private static final long COLLAPSE_DURATION = 580L;
    private static final long TEXT_DURATION = 350L;
    private static final long ONLINE_DURATION = 280L;

    private static final TimeInterpolator SPRING_INTERPOLATOR =
            new android.view.animation.PathInterpolator(0.34f, 1.56f, 0.64f, 1f);
    private static final TimeInterpolator SMOOTH_INTERPOLATOR =
            new android.view.animation.PathInterpolator(0.25f, 1f, 0.5f, 1f);

    private final Activity activity;
    private final Handler mainHandler;
    private FrameLayout overlayRoot;
    private FrameLayout offlineBadge;
    private ImageView offlineIcon;
    private TextView offlineBar;

    private boolean initialized = false;
    private boolean expanded = false;
    private boolean onlineMode = false;

    private Runnable autoCollapseRunnable;
    private Runnable autoHideRunnable;

    private VisibilityCallback callback;

    public interface VisibilityCallback {
        void onVisibilityChanged(boolean visible);
    }

    public OfflineBarController(Activity activity) {
        this.activity = activity;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void init() {
        if (activity == null || activity.isFinishing() || initialized) return;
        initialized = true;
        createOverlay();
        applySafeAreaPosition();
        // تم إلغاء تتبع انزلاق شريط التنقل بناءً على طلبك
    }

    private void createOverlay() {
        overlayRoot = new FrameLayout(activity);
        overlayRoot.setClipChildren(false);
        overlayRoot.setClipToPadding(false);
        FrameLayout.LayoutParams rootParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        activity.addContentView(overlayRoot, rootParams);
        createBadge();
    }

    private void createBadge() {
        offlineBadge = new FrameLayout(activity);
        offlineBadge.setClipChildren(false);
        offlineBadge.setClipToPadding(false);
        offlineBadge.setClickable(true);
        offlineBadge.setFocusable(true);
        offlineBadge.setVisibility(View.GONE);
        offlineBadge.setAlpha(0f);

        // 📍 التثبيت الصارم في الزاوية السفلية اليمنى (Gravity.BOTTOM | Gravity.END)
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(dp(COLLAPSED_SIZE_DP), dp(COLLAPSED_SIZE_DP));
        badgeParams.gravity = Gravity.BOTTOM | Gravity.END;
        badgeParams.rightMargin = dp(BADGE_HORIZONTAL_MARGIN_DP);
        badgeParams.bottomMargin = dp(BADGE_BOTTOM_MARGIN_DP);
        overlayRoot.addView(offlineBadge, badgeParams);
        applyBadgeBackground(false);

        // 📡 الأيقونة - تتوسط الكبسولة هندسياً عند الحالة المغلقة (Collapsed)
        offlineIcon = new ImageView(activity);
        offlineIcon.setImageDrawable(new LuxuryCloudDrawable(OFFLINE_COLOR));
        offlineIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(ICON_SIZE_DP), dp(ICON_SIZE_DP));
        iconParams.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
        // حساب الهامش الأيمن ليكون في المنتصف تماماً عند CollapsedSize (50dp - 24dp) / 2 = 13dp
        iconParams.rightMargin = dp((COLLAPSED_SIZE_DP - ICON_SIZE_DP) / 2f);
        offlineBadge.addView(offlineIcon, iconParams);

        // 📝 النص - يبدأ من يسار الأيقونة ويمتد لليسار بدقة مع اتجاه اللغة العربية
        offlineBar = new TextView(activity);
        offlineBar.setText(DEFAULT_TEXT);
        offlineBar.setTextColor(TEXT_COLOR);
        offlineBar.setTextSize(13f);
        offlineBar.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        offlineBar.setSingleLine(true);
        offlineBar.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        offlineBar.setAlpha(0f);
        offlineBar.setTranslationX(dp(-10)); // دخول من اليمين لليسار

        FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        textParams.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
        textParams.rightMargin = dp(COLLAPSED_SIZE_DP); // تبدأ بداية النص بعد مسافة الأيقونة الدائرية
        textParams.leftMargin = dp(16); // هامش أمان أيسر للنص
        offlineBadge.addView(offlineBar, textParams);

        offlineBadge.setOnClickListener(v -> {
            if (expanded) {
                collapseBadge();
            } else {
                expandBadge();
            }
        });
    }

    private void applyBadgeBackground(boolean expandedState) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(expandedState ? GLASS_EXPANDED_BG : GLASS_BG);
        bg.setCornerRadius(dp(25));
        bg.setStroke(dp(1), onlineMode ? ONLINE_BORDER : (expandedState ? OFFLINE_BORDER : GLASS_BORDER));
        offlineBadge.setBackground(bg);
        offlineBadge.setElevation(dp(14));
    }

    private void applySafeAreaPosition() {
        if (offlineBadge == null) return;
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) offlineBadge.getLayoutParams();
        if (params == null) return;
        params.gravity = Gravity.BOTTOM | Gravity.END;
        params.rightMargin = dp(BADGE_HORIZONTAL_MARGIN_DP);
        params.bottomMargin = dp(BADGE_BOTTOM_MARGIN_DP);
        offlineBadge.setLayoutParams(params);
    }

    private int calculateTargetWidth() {
        if (offlineBar == null) return dp(EXPANDED_WIDTH_DP);
        // 📐 قياس العرض الفعلي المطلوب للنص بدقة
        offlineBar.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        int textWidth = offlineBar.getMeasuredWidth();
        // العرض الكلي = عرض النص + عرض مساحة الأيقونة اليمنى (50dp) + الهامش الأيسر (18dp)
        int requiredWidth = textWidth + dp(COLLAPSED_SIZE_DP) + dp(18);
        return Math.max(requiredWidth, dp(EXPANDED_WIDTH_DP));
    }

    private void expandBadge() {
        if (offlineBadge == null || expanded) return;
        expanded = true;
        cancelAutoCollapse();

        int targetWidth = calculateTargetWidth();
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) offlineBadge.getLayoutParams();
        int startWidth = offlineBadge.getWidth() > 0 ? offlineBadge.getWidth() : dp(COLLAPSED_SIZE_DP);

        ValueAnimator widthAnimator = ValueAnimator.ofInt(startWidth, targetWidth);
        widthAnimator.setDuration(EXPAND_DURATION);
        widthAnimator.setInterpolator(SPRING_INTERPOLATOR);
        widthAnimator.addUpdateListener(animation -> {
            if (offlineBadge == null) return;
            params.width = (Integer) animation.getAnimatedValue();
            offlineBadge.setLayoutParams(params);
        });
        widthAnimator.start();

        applyBadgeBackground(true);

        // إظهار النص بانسيابية وانزلاق مطاطي متناسق مع اتجاه اللغة
        offlineBar.animate()
                .alpha(1f)
                .translationX(0f)
                .setStartDelay(100L)
                .setDuration(TEXT_DURATION)
                .setInterpolator(SMOOTH_INTERPOLATOR)
                .start();

        scheduleAutoCollapse(3800L);
    }

    private void collapseBadge() {
        if (offlineBadge == null || !expanded) return;
        expanded = false;
        cancelAutoCollapse();

        // إخفاء النص أولاً
        offlineBar.animate()
                .alpha(0f)
                .translationX(dp(-10))
                .setDuration(220L)
                .setInterpolator(SMOOTH_INTERPOLATOR)
                .start();

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) offlineBadge.getLayoutParams();
        int startWidth = offlineBadge.getWidth() > 0 ? offlineBadge.getWidth() : dp(EXPANDED_WIDTH_DP);

        ValueAnimator widthAnimator = ValueAnimator.ofInt(startWidth, dp(COLLAPSED_SIZE_DP));
        widthAnimator.setDuration(COLLAPSE_DURATION);
        widthAnimator.setInterpolator(SMOOTH_INTERPOLATOR);
        widthAnimator.addUpdateListener(animation -> {
            if (offlineBadge == null) return;
            params.width = (Integer) animation.getAnimatedValue();
            offlineBadge.setLayoutParams(params);
        });
        widthAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (offlineBadge != null) applyBadgeBackground(false);
            }
        });
        widthAnimator.start();
    }

    private void scheduleAutoCollapse(long delay) {
        cancelAutoCollapse();
        autoCollapseRunnable = () -> {
            if (offlineBadge != null && expanded) collapseBadge();
        };
        mainHandler.postDelayed(autoCollapseRunnable, delay);
    }

    private void cancelAutoCollapse() {
        if (autoCollapseRunnable != null) {
            mainHandler.removeCallbacks(autoCollapseRunnable);
            autoCollapseRunnable = null;
        }
    }

    private void scheduleOnlineDismiss() {
        if (autoHideRunnable != null) mainHandler.removeCallbacks(autoHideRunnable);
        autoHideRunnable = () -> {
            if (offlineBadge == null) return;
            collapseBadge();
            mainHandler.postDelayed(() -> {
                if (offlineBadge == null) return;
                offlineBadge.animate().alpha(0f).scaleX(0.70f).scaleY(0.70f).setDuration(550L).setInterpolator(SMOOTH_INTERPOLATOR).withEndAction(() -> {
                    if (offlineBadge == null) return;
                    offlineBadge.setVisibility(View.GONE);
                    offlineBadge.setAlpha(1f);
                    offlineBadge.setScaleX(1f);
                    offlineBadge.setScaleY(1f);
                    onlineMode = false;
                    expanded = false;
                    applyBadgeBackground(false);
                }).start();
            }, 550L);
        };
        mainHandler.postDelayed(autoHideRunnable, 2800L);
    }

    // =========================================================
    // PUBLIC API
    // =========================================================

    public void show() {
        if (offlineBadge == null) return;
        activity.runOnUiThread(() -> {
            if (activity.isFinishing()) return;
            cancelAutoCollapse();
            offlineBadge.animate().cancel();
            offlineBar.animate().cancel();
            onlineMode = false;
            expanded = false;

            offlineBar.setText(DEFAULT_TEXT);
            offlineIcon.setImageDrawable(new LuxuryCloudDrawable(OFFLINE_COLOR));
            offlineBar.setAlpha(0f);
            offlineBar.setTranslationX(dp(-10));

            // إعادة ضبط العرض كدائرة صغيرة أولاً
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) offlineBadge.getLayoutParams();
            params.width = dp(COLLAPSED_SIZE_DP);
            offlineBadge.setLayoutParams(params);

            applySafeAreaPosition();
            applyBadgeBackground(false);

            // مرحلة 1: ظهور كبسولة الأيقونة بالجهـة اليمنى عبر انكماش وبروز مطاطي
            offlineBadge.setScaleX(0.6f);
            offlineBadge.setScaleY(0.6f);
            offlineBadge.setAlpha(0f);
            offlineBadge.setVisibility(View.VISIBLE);

            offlineBadge.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(450L)
                    .setInterpolator(SPRING_INTERPOLATOR)
                    .withEndAction(() -> {
                        // مرحلة 2: انسدال وتمدد الشريط لإظهار النص
                        if (offlineBadge != null) expandBadge();
                    }).start();
        });
        notifyVisibilityChanged(true);
    }

    public void hideWithAnimation() {
        if (offlineBadge == null) return;
        activity.runOnUiThread(() -> {
            if (activity.isFinishing()) return;
            cancelAutoCollapse();
            onlineMode = true;
            offlineBar.setText(RESTORED_TEXT);
            offlineIcon.setImageDrawable(new LuxuryCloudDrawable(ONLINE_COLOR));
            applyBadgeBackground(expanded);
            offlineBar.animate().alpha(1f).translationX(0f).setDuration(ONLINE_DURATION).setInterpolator(SMOOTH_INTERPOLATOR).start();
            scheduleOnlineDismiss();
        });
        notifyVisibilityChanged(false);
    }

    public void hideImmediately() {
        if (offlineBadge == null) return;
        activity.runOnUiThread(() -> {
            cancelAutoCollapse();
            if (autoHideRunnable != null) {
                mainHandler.removeCallbacks(autoHideRunnable);
                autoHideRunnable = null;
            }
            offlineBadge.animate().cancel();
            offlineBar.animate().cancel();
            offlineBadge.setVisibility(View.GONE);
            offlineBadge.setAlpha(1f);
            offlineBadge.setScaleX(1f);
            offlineBadge.setScaleY(1f);
            offlineBar.setAlpha(0f);
            offlineBar.setTranslationX(dp(-10));
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) offlineBadge.getLayoutParams();
            params.width = dp(COLLAPSED_SIZE_DP);
            offlineBadge.setLayoutParams(params);
            expanded = false;
            onlineMode = false;
            applySafeAreaPosition();
            applyBadgeBackground(false);
        });
        notifyVisibilityChanged(false);
    }

    public void showOnlineTransition() {
        if (offlineBadge == null) return;
        activity.runOnUiThread(() -> {
            if (activity.isFinishing()) return;
            cancelAutoCollapse();
            onlineMode = true;
            offlineBar.setText(RESTORED_TEXT);
            offlineIcon.setImageDrawable(new LuxuryCloudDrawable(ONLINE_COLOR));
            if (offlineBadge.getVisibility() != View.VISIBLE) {
                expanded = false;
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) offlineBadge.getLayoutParams();
                params.width = dp(COLLAPSED_SIZE_DP);
                offlineBadge.setLayoutParams(params);
                applySafeAreaPosition();
                applyBadgeBackground(false);
                offlineBar.setAlpha(0f);
                offlineBar.setTranslationX(dp(-10));
                offlineBadge.setScaleX(0.70f);
                offlineBadge.setScaleY(0.70f);
                offlineBadge.setAlpha(0f);
                offlineBadge.setVisibility(View.VISIBLE);
                offlineBadge.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(ONLINE_DURATION).setInterpolator(SPRING_INTERPOLATOR).withEndAction(this::expandBadge).start();
            } else {
                expandBadge();
            }
            mainHandler.postDelayed(this::hideWithAnimation, 2800L);
        });
    }

    public void shake() {
        if (offlineBadge == null) return;
        activity.runOnUiThread(() -> {
            if (offlineBadge == null) return;
            if (offlineBadge.getVisibility() != View.VISIBLE) {
                show();
            } else {
                offlineBar.setText(WARNING_TEXT);

                // إعادة قياس وتوسيع العرض فوراً ليتناسب مع نص التنبيه الطويل
                if (expanded) {
                    expanded = false; // إعادة تعيين لتشغيل الانيميشن
                }
                expandBadge();

                // اهتزاز خفيف وانيق للتنبيه
                offlineBadge.animate().translationX(dp(-6)).setDuration(60L).withEndAction(() ->
                        offlineBadge.animate().translationX(dp(6)).setDuration(60L).withEndAction(() ->
                                offlineBadge.animate().translationX(0).setDuration(60L).start()
                        ).start()
                ).start();

                mainHandler.postDelayed(() -> {
                    if (offlineBar != null && offlineBadge != null && offlineBadge.getVisibility() == View.VISIBLE) {
                        offlineBar.setText(DEFAULT_TEXT);
                        if (expanded) {
                            expanded = false;
                            expandBadge();
                        }
                    }
                }, 2400L);
            }
        });
    }

    public boolean isVisible() {
        return offlineBadge != null && offlineBadge.getVisibility() == View.VISIBLE;
    }

    public TextView getView() {
        return offlineBar;
    }

    public View getVeilView() {
        return null;
    }

    public void setCallback(VisibilityCallback callback) {
        this.callback = callback;
    }

    private void notifyVisibilityChanged(boolean visible) {
        if (callback != null) callback.onVisibilityChanged(visible);
    }

    public void destroy() {
        cancelAutoCollapse();
        if (autoHideRunnable != null) {
            mainHandler.removeCallbacks(autoHideRunnable);
            autoHideRunnable = null;
        }
        if (offlineBadge != null) offlineBadge.animate().cancel();
        if (offlineBar != null) offlineBar.animate().cancel();
        if (overlayRoot != null) {
            ViewGroup parent = (ViewGroup) overlayRoot.getParent();
            if (parent != null) parent.removeView(overlayRoot);
        }
        offlineBadge = null;
        offlineIcon = null;
        offlineBar = null;
        overlayRoot = null;
        callback = null;
        expanded = false;
        onlineMode = false;
        initialized = false;
    }

    private int dp(float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    // =========================================================
    // ☁️ LUXURY CLOUD DRAWABLE
    // =========================================================

    private static final class LuxuryCloudDrawable extends Drawable {
        private final Paint cloudPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint slashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path cloudPath = new Path();
        private final int color;

        LuxuryCloudDrawable(int color) {
            this.color = color;
            cloudPaint.setStyle(Paint.Style.STROKE);
            cloudPaint.setStrokeWidth(2.05f);
            cloudPaint.setStrokeCap(Paint.Cap.ROUND);
            cloudPaint.setStrokeJoin(Paint.Join.ROUND);
            cloudPaint.setColor(color);
            slashPaint.setStyle(Paint.Style.STROKE);
            slashPaint.setStrokeWidth(2.05f);
            slashPaint.setStrokeCap(Paint.Cap.ROUND);
            slashPaint.setColor(color);
        }

        @Override
        public void draw(Canvas canvas) {
            RectF bounds = new RectF(getBounds());
            float w = bounds.width(), h = bounds.height();
            float sx = w / 24f, sy = h / 24f;
            canvas.save();
            canvas.translate(bounds.left, bounds.top);
            canvas.scale(sx, sy);

            cloudPath.reset();
            cloudPath.moveTo(17.35f, 18.8f);
            cloudPath.cubicTo(19.85f, 18.8f, 21.65f, 17.05f, 21.65f, 14.7f);
            cloudPath.cubicTo(21.65f, 12.35f, 19.85f, 10.45f, 17.55f, 10.25f);
            cloudPath.cubicTo(17.05f, 6.75f, 14.25f, 4.35f, 10.85f, 4.35f);
            cloudPath.cubicTo(7.15f, 4.35f, 4.05f, 7.25f, 3.85f, 10.9f);
            cloudPath.cubicTo(2.15f, 11.35f, 1.0f, 12.85f, 1.0f, 14.65f);
            cloudPath.cubicTo(1.0f, 16.95f, 2.85f, 18.8f, 5.15f, 18.8f);
            cloudPath.lineTo(17.35f, 18.8f);
            canvas.drawPath(cloudPath, cloudPaint);

            canvas.drawLine(5.0f, 4.7f, 19.25f, 19.0f, slashPaint);
            canvas.restore();
        }

        @Override
        public void setAlpha(int alpha) {
            cloudPaint.setAlpha(alpha);
            slashPaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter filter) {
            cloudPaint.setColorFilter(filter);
            slashPaint.setColorFilter(filter);
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }
    }
