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
    private static final int BADGE_BOTTOM_MARGIN_DP = 20;
    private static final int BADGE_HORIZONTAL_MARGIN_DP = 20;

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

    private int navigationBottomInset = 0;
    private int gestureBottomInset = 0;
    private boolean gestureNavigation = false;
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
        installInsetsController();
        overlayRoot.post(() -> {
            WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(overlayRoot);
            if (insets != null) {
                Insets navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
                Insets gestures = insets.getInsets(WindowInsetsCompat.Type.systemGestures());
                navigationBottomInset = navigation.bottom;
                gestureBottomInset = gestures.bottom;
                gestureNavigation = navigationBottomInset == 0 && gestureBottomInset > 0;
                applySafeAreaPosition();
            }
        });
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

        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(dp(COLLAPSED_SIZE_DP), dp(COLLAPSED_SIZE_DP));
        badgeParams.gravity = Gravity.BOTTOM | Gravity.END;
        badgeParams.rightMargin = dp(BADGE_HORIZONTAL_MARGIN_DP);
        badgeParams.bottomMargin = dp(BADGE_BOTTOM_MARGIN_DP);
        overlayRoot.addView(offlineBadge, badgeParams);
        applyBadgeBackground(false);

        offlineIcon = new ImageView(activity);
        offlineIcon.setImageDrawable(new LuxuryCloudDrawable(OFFLINE_COLOR));
        offlineIcon.setScaleType(ImageView.ScaleType.CENTER);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(24), dp(24));
        iconParams.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
        iconParams.leftMargin = dp(13);
        offlineBadge.addView(offlineIcon, iconParams);

        offlineBar = new TextView(activity);
        offlineBar.setText(DEFAULT_TEXT);
        offlineBar.setTextColor(TEXT_COLOR);
        offlineBar.setTextSize(13.5f);
        offlineBar.setGravity(Gravity.CENTER_VERTICAL);
        offlineBar.setSingleLine(true);
        offlineBar.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
        offlineBar.setAlpha(0f);
        offlineBar.setTranslationX(dp(12));
        FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        textParams.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
        textParams.leftMargin = dp(49);
        textParams.rightMargin = dp(13);
        offlineBadge.addView(offlineBar, textParams);

        offlineBadge.setOnClickListener(v -> {
            if (expanded) collapseBadge();
            else expandBadge();
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

    private void installInsetsController() {
        ViewCompat.setOnApplyWindowInsetsListener(overlayRoot, (view, insets) -> {
            Insets navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            Insets gestures = insets.getInsets(WindowInsetsCompat.Type.systemGestures());
            navigationBottomInset = navigation.bottom;
            gestureBottomInset = gestures.bottom;
            gestureNavigation = navigationBottomInset == 0 && gestureBottomInset > 0;
            applySafeAreaPosition();
            return insets;
        });
        ViewCompat.requestApplyInsets(overlayRoot);
    }

    private void applySafeAreaPosition() {
        if (offlineBadge == null) return;
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) offlineBadge.getLayoutParams();
        if (params == null) return;
        params.gravity = Gravity.BOTTOM | Gravity.END;
        params.rightMargin = dp(BADGE_HORIZONTAL_MARGIN_DP);
        params.bottomMargin = navigationBottomInset + dp(BADGE_BOTTOM_MARGIN_DP);
        offlineBadge.setLayoutParams(params);
    }

    private void expandBadge() {
        if (offlineBadge == null || expanded) return;
        expanded = true;
        cancelAutoCollapse();
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) offlineBadge.getLayoutParams();
        int startWidth = offlineBadge.getWidth();
        if (startWidth <= 0) startWidth = dp(COLLAPSED_SIZE_DP);
        ValueAnimator widthAnimator = ValueAnimator.ofInt(startWidth, dp(EXPANDED_WIDTH_DP));
        widthAnimator.setDuration(EXPAND_DURATION);
        widthAnimator.setInterpolator(SPRING_INTERPOLATOR);
        widthAnimator.addUpdateListener(animation -> {
            if (offlineBadge == null) return;
            params.width = (Integer) animation.getAnimatedValue();
            offlineBadge.setLayoutParams(params);
        });
        widthAnimator.start();
        applyBadgeBackground(true);
        offlineBar.animate().alpha(1f).translationX(0f).setStartDelay(120L).setDuration(TEXT_DURATION).setInterpolator(SMOOTH_INTERPOLATOR).start();
        scheduleAutoCollapse(3800L);
    }

    private void collapseBadge() {
        if (offlineBadge == null || !expanded) return;
        expanded = false;
        cancelAutoCollapse();
        offlineBar.animate().alpha(0f).translationX(dp(12)).setDuration(260L).setInterpolator(SMOOTH_INTERPOLATOR).start();
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) offlineBadge.getLayoutParams();
        int startWidth = offlineBadge.getWidth();
        if (startWidth <= 0) startWidth = dp(EXPANDED_WIDTH_DP);
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
            offlineBar.setTranslationX(dp(12));
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) offlineBadge.getLayoutParams();
            params.width = dp(COLLAPSED_SIZE_DP);
            offlineBadge.setLayoutParams(params);
            applySafeAreaPosition();
            applyBadgeBackground(false);
            offlineBadge.setScaleX(0.70f);
            offlineBadge.setScaleY(0.70f);
            offlineBadge.setAlpha(0f);
            offlineBadge.setVisibility(View.VISIBLE);
            offlineBadge.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(APPEAR_DURATION).setInterpolator(SPRING_INTERPOLATOR).withEndAction(() -> {
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
            offlineBar.setTranslationX(dp(12));
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
                offlineBar.setTranslationX(dp(12));
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
        if (offlineBadge.getVisibility() != View.VISIBLE) show();
        activity.runOnUiThread(() -> {
            if (offlineBadge == null) return;
            String originalText = offlineBar.getText().toString();
            offlineBar.setText(WARNING_TEXT);
            offlineBadge.animate().translationX(dp(6)).setDuration(55L).withEndAction(() ->
                    offlineBadge.animate().translationX(dp(-6)).setDuration(55L).withEndAction(() ->
                            offlineBadge.animate().translationX(0).setDuration(65L).start()
                    ).start()
            ).start();
            mainHandler.postDelayed(() -> {
                if (offlineBar != null && offlineBadge != null && offlineBadge.getVisibility() == View.VISIBLE) {
                    offlineBar.setText(originalText);
                }
            }, 1800L);
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

    public boolean isGestureNavigation() {
        return gestureNavigation;
    }

    public int getNavigationBottomInset() {
        return navigationBottomInset;
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
