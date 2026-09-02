package com.store.app.offline;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class OfflineBarController {
    private static final String DEFAULT_TEXT = "أنت تعمل حالياً دون اتصال", RESTORED_TEXT = "تم استعادة الاتصال بنجاح", WARNING_TEXT = "⚠️ لا يمكن التحميل، تحقق من الاتصال";
    private static final int GLASS_BG = Color.argb(209, 18, 20, 26), GLASS_EXPANDED_BG = Color.argb(240, 22, 25, 33), GLASS_BORDER = Color.argb(31, 255, 255, 255), OFFLINE_COLOR = Color.rgb(125, 146, 176), OFFLINE_BORDER = Color.argb(89, 125, 146, 176), ONLINE_COLOR = Color.rgb(0, 230, 118), ONLINE_BORDER = Color.argb(115, 0, 230, 118), TEXT_COLOR = Color.rgb(249, 249, 251);
    private static final int COLLAPSED_SIZE_DP = 50, EXPANDED_WIDTH_DP = 230, BADGE_BOTTOM_MARGIN_DP = 20, BADGE_HORIZONTAL_MARGIN_DP = 20;
    private static final long APPEAR_DURATION = 700L, EXPAND_DURATION = 650L, COLLAPSE_DURATION = 650L, TEXT_DURATION = 350L;
    private static final Interpolator SPRING_INTERPOLATOR = new PathInterpolator(0.34f, 1.56f, 0.64f, 1f), SMOOTH_INTERPOLATOR = new PathInterpolator(0.25f, 1f, 0.5f, 1f);
    private final Activity activity; private final Handler mainHandler; private FrameLayout overlayRoot, offlineBadge, iconContainer; private ImageView offlineIcon; private TextView offlineBar; private View auraView;
    private int navigationBottomInset = 0; private boolean gestureNavigation = false, initialized = false, expanded = false, onlineMode = false;
    private Runnable autoCollapseRunnable, autoHideRunnable; private VisibilityCallback callback;
    public interface VisibilityCallback { void onVisibilityChanged(boolean visible); }
    public OfflineBarController(Activity activity) { this.activity = activity; this.mainHandler = new Handler(Looper.getMainLooper()); }
    public void init() { if (activity == null || activity.isFinishing() || initialized) return; initialized = true; createOverlay(); installInsetsController(); overlayRoot.post(() -> { WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(overlayRoot); if (insets != null) { Insets navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars()); navigationBottomInset = navigation.bottom; gestureNavigation = navigationBottomInset == 0; applySafeAreaPosition(); } }); }
    private void createOverlay() { overlayRoot = new FrameLayout(activity); overlayRoot.setClipChildren(false); overlayRoot.setClipToPadding(false); FrameLayout.LayoutParams rootParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT); activity.addContentView(overlayRoot, rootParams); createBadge(); }

    // [تعديل أول] createBadge مع تغيير الجهة وضبط الأيقونة
    private void createBadge() {
        offlineBadge = new FrameLayout(activity);
        offlineBadge.setClipChildren(false); 
        offlineBadge.setClipToPadding(false); 
        offlineBadge.setClickable(true); 
        offlineBadge.setFocusable(true); 
        offlineBadge.setVisibility(View.GONE);
        
        // 1. تحويل جهة ظهور الشريط للجهة المقابلة (Gravity.BOTTOM | Gravity.START)
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(dp(COLLAPSED_SIZE_DP), dp(COLLAPSED_SIZE_DP)); 
        badgeParams.gravity = Gravity.BOTTOM | Gravity.START; // تغيير الجهة للجهة الأُخرى
        badgeParams.leftMargin = dp(BADGE_HORIZONTAL_MARGIN_DP); 
        badgeParams.bottomMargin = dp(BADGE_BOTTOM_MARGIN_DP);
        overlayRoot.addView(offlineBadge, badgeParams); 
        applyBadgeBackground(false);

        // تثبيت اتجاه LTR للحاوية لمنع الانقلاب العشوائي للأيقونة في الوضع العربي
        ViewCompat.setLayoutDirection(offlineBadge, ViewCompat.LAYOUT_DIRECTION_LTR);

        iconContainer = new FrameLayout(activity); 
        iconContainer.setClipChildren(false); 
        iconContainer.setClipToPadding(false);
        
        // 2. ضبط الأيقونة لتكون في منتصف الدائرة السوداء بالضبط (13dp من جميع الاتجاهات)
        FrameLayout.LayoutParams iconContainerParams = new FrameLayout.LayoutParams(dp(24), dp(24)); 
        iconContainerParams.gravity = Gravity.CENTER_VERTICAL | Gravity.END; 
        iconContainerParams.rightMargin = dp(13);
        iconContainerParams.leftMargin = dp(13);
        offlineBadge.addView(iconContainer, iconContainerParams);

        auraView = new View(activity); 
        auraView.setBackground(new AuraDrawable(OFFLINE_COLOR)); 
        auraView.setAlpha(0f); 
        auraView.setScaleX(0.7f); 
        auraView.setScaleY(0.7f);
        FrameLayout.LayoutParams auraParams = new FrameLayout.LayoutParams(dp(24), dp(24)); 
        auraParams.gravity = Gravity.CENTER; 
        iconContainer.addView(auraView, auraParams);

        offlineIcon = new ImageView(activity); 
        offlineIcon.setImageDrawable(new LuxuryCloudDrawable(OFFLINE_COLOR, true)); 
        offlineIcon.setScaleType(ImageView.ScaleType.CENTER); 
        offlineIcon.setPadding(0, 0, 0, 0);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(24), dp(24)); 
        iconParams.gravity = Gravity.CENTER; 
        iconContainer.addView(offlineIcon, iconParams);

        offlineBar = new TextView(activity); 
        offlineBar.setText(DEFAULT_TEXT); 
        offlineBar.setTextColor(TEXT_COLOR); 
        offlineBar.setTextSize(13.5f); 
        offlineBar.setSingleLine(true); 
        offlineBar.setIncludeFontPadding(false); 
        offlineBar.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL); 
        offlineBar.setTextDirection(View.TEXT_DIRECTION_RTL); 
        offlineBar.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL)); 
        offlineBar.setAlpha(0f); 
        offlineBar.setTranslationX(dp(12));
        
        FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT); 
        textParams.gravity = Gravity.CENTER_VERTICAL | Gravity.START; 
        textParams.leftMargin = dp(13); 
        textParams.rightMargin = dp(49);
        offlineBadge.addView(offlineBar, textParams);

        offlineBadge.setOnClickListener(v -> { 
            cancelAutoCollapse(); 
            if (expanded) collapseBadge(); 
            else expandBadge(); 
        });
    }

    // [تعديل ثاني] دالة حساب العرض المرن
    private int calculateExpandedWidth(String text) {
        if (offlineBar == null || text == null) return dp(EXPANDED_WIDTH_DP);
        float textWidth = offlineBar.getPaint().measureText(text);
        int calculatedWidth = (int) (textWidth + dp(75));
        return Math.max(dp(EXPANDED_WIDTH_DP), calculatedWidth);
    }

    private void applyBadgeBackground(boolean expandedState) { GradientDrawable bg = new GradientDrawable(); bg.setColor(expandedState ? GLASS_EXPANDED_BG : GLASS_BG); bg.setCornerRadius(dp(25)); int borderColor = onlineMode ? ONLINE_BORDER : (expandedState ? OFFLINE_BORDER : GLASS_BORDER); bg.setStroke(dp(1), borderColor); offlineBadge.setBackground(bg); offlineBadge.setElevation(dp(14)); }
    private void installInsetsController() { ViewCompat.setOnApplyWindowInsetsListener(overlayRoot, (view, insets) -> { Insets navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars()); navigationBottomInset = navigation.bottom; gestureNavigation = navigationBottomInset == 0; applySafeAreaPosition(); return insets; }); ViewCompat.requestApplyInsets(overlayRoot); }

    // [تعديل ثالث] applySafeAreaPosition مع تنعيم الانتقال
    private void applySafeAreaPosition() { 
        if (offlineBadge == null) return; 
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) offlineBadge.getLayoutParams(); 
        if (params == null) return; 
        
        params.gravity = Gravity.BOTTOM | Gravity.START; // تثبيت الجهة
        params.leftMargin = dp(BADGE_HORIZONTAL_MARGIN_DP); 
        
        int targetBottomMargin = navigationBottomInset + dp(BADGE_BOTTOM_MARGIN_DP);
        
        if (params.bottomMargin != targetBottomMargin && params.bottomMargin > 0) {
            ValueAnimator marginAnimator = ValueAnimator.ofInt(params.bottomMargin, targetBottomMargin);
            marginAnimator.setDuration(200L);
            marginAnimator.addUpdateListener(anim -> {
                if (offlineBadge != null && offlineBadge.getLayoutParams() != null) {
                    ((FrameLayout.LayoutParams) offlineBadge.getLayoutParams()).bottomMargin = (Integer) anim.getAnimatedValue();
                    offlineBadge.requestLayout();
                }
            });
            marginAnimator.start();
        } else {
            params.bottomMargin = targetBottomMargin; 
            offlineBadge.setLayoutParams(params); 
        }
    }

    private void expandBadge() { 
        if (offlineBadge == null || expanded) return; 
        expanded = true; 
        cancelAutoCollapse(); 
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) offlineBadge.getLayoutParams(); 
        int startWidth = offlineBadge.getWidth(); 
        if (startWidth <= 0) startWidth = dp(COLLAPSED_SIZE_DP); 
        // [تعديل ثاني] استخدام العرض المحسوب ديناميكياً
        final int targetWidth = calculateExpandedWidth(offlineBar.getText() != null ? offlineBar.getText().toString() : DEFAULT_TEXT);
        ValueAnimator animator = ValueAnimator.ofInt(startWidth, targetWidth); 
        animator.setDuration(EXPAND_DURATION); 
        animator.setInterpolator(SPRING_INTERPOLATOR); 
        animator.addUpdateListener(animation -> { if (offlineBadge == null) return; params.width = (Integer) animation.getAnimatedValue(); offlineBadge.setLayoutParams(params); }); 
        animator.start(); 
        applyBadgeBackground(true); 
        offlineBar.animate().cancel(); 
        offlineBar.setTranslationX(dp(12)); 
        offlineBar.setAlpha(0f); 
        offlineBar.animate().alpha(1f).translationX(0f).setStartDelay(120L).setDuration(TEXT_DURATION).setInterpolator(SMOOTH_INTERPOLATOR).start(); 
        if (!onlineMode && auraView != null) { 
            auraView.animate().cancel(); 
            auraView.setAlpha(0f); 
            auraView.setScaleX(0.7f); 
            auraView.setScaleY(0.7f); 
            auraView.animate().alpha(0.5f).scaleX(1.8f).scaleY(1.8f).setDuration(3000L).setInterpolator(SMOOTH_INTERPOLATOR).withEndAction(() -> { if (auraView == null) return; auraView.setAlpha(0f); auraView.setScaleX(0.7f); auraView.setScaleY(0.7f); }).start(); 
        } 
        scheduleAutoCollapse(3800L); 
    }

    private void collapseBadge() { if (offlineBadge == null || !expanded) return; expanded = false; cancelAutoCollapse(); offlineBar.animate().cancel(); offlineBar.animate().alpha(0f).translationX(dp(12)).setDuration(260L).setInterpolator(SMOOTH_INTERPOLATOR).start(); FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) offlineBadge.getLayoutParams(); int startWidth = offlineBadge.getWidth(); if (startWidth <= 0) startWidth = dp(EXPANDED_WIDTH_DP); ValueAnimator animator = ValueAnimator.ofInt(startWidth, dp(COLLAPSED_SIZE_DP)); animator.setDuration(COLLAPSE_DURATION); animator.setInterpolator(SPRING_INTERPOLATOR); animator.addUpdateListener(animation -> { if (offlineBadge == null) return; params.width = (Integer) animation.getAnimatedValue(); offlineBadge.setLayoutParams(params); }); animator.start(); applyBadgeBackground(false); }
    private void scheduleAutoCollapse(long delay) { cancelAutoCollapse(); autoCollapseRunnable = () -> { if (offlineBadge != null && expanded) collapseBadge(); }; mainHandler.postDelayed(autoCollapseRunnable, delay); }
    private void cancelAutoCollapse() { if (autoCollapseRunnable != null) { mainHandler.removeCallbacks(autoCollapseRunnable); autoCollapseRunnable = null; } }
    public void show() { if (offlineBadge == null) return; activity.runOnUiThread(() -> { if (activity.isFinishing()) return; cancelAutoCollapse(); if (autoHideRunnable != null) { mainHandler.removeCallbacks(autoHideRunnable); autoHideRunnable = null; } offlineBadge.animate().cancel(); offlineBar.animate().cancel(); onlineMode = false; expanded = false; offlineBar.setText(DEFAULT_TEXT); offlineIcon.setImageDrawable(new LuxuryCloudDrawable(OFFLINE_COLOR, true)); offlineBar.setAlpha(0f); offlineBar.setTranslationX(dp(12)); FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) offlineBadge.getLayoutParams(); params.width = dp(COLLAPSED_SIZE_DP); offlineBadge.setLayoutParams(params); applySafeAreaPosition(); applyBadgeBackground(false); offlineBadge.setScaleX(0.70f); offlineBadge.setScaleY(0.70f); offlineBadge.setTranslationY(dp(30)); offlineBadge.setAlpha(0f); offlineBadge.setVisibility(View.VISIBLE); offlineBadge.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(APPEAR_DURATION).setInterpolator(SPRING_INTERPOLATOR).withEndAction(() -> { if (offlineBadge == null) return; expandBadge(); }).start(); }); notifyVisibilityChanged(true); }
    public void hideWithAnimation() { if (offlineBadge == null) return; activity.runOnUiThread(() -> { if (activity.isFinishing()) return; cancelAutoCollapse(); if (autoHideRunnable != null) { mainHandler.removeCallbacks(autoHideRunnable); autoHideRunnable = null; } onlineMode = true; offlineBar.setText(RESTORED_TEXT); offlineIcon.setImageDrawable(new LuxuryCloudDrawable(ONLINE_COLOR, false)); if (auraView != null) { auraView.animate().cancel(); auraView.setAlpha(0f); } if (offlineBadge.getVisibility() != View.VISIBLE) { expanded = false; FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) offlineBadge.getLayoutParams(); params.width = dp(COLLAPSED_SIZE_DP); offlineBadge.setLayoutParams(params); applySafeAreaPosition(); applyBadgeBackground(false); offlineBar.setAlpha(0f); offlineBar.setTranslationX(dp(12)); offlineBadge.setScaleX(0.70f); offlineBadge.setScaleY(0.70f); offlineBadge.setTranslationY(dp(30)); offlineBadge.setAlpha(0f); offlineBadge.setVisibility(View.VISIBLE); offlineBadge.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(700L).setInterpolator(SPRING_INTERPOLATOR).withEndAction(this::expandBadge).start(); } else if (!expanded) { expandBadge(); } autoHideRunnable = () -> { if (offlineBadge == null) return; if (expanded) collapseBadge(); mainHandler.postDelayed(() -> { if (offlineBadge == null) return; offlineBadge.animate().alpha(0f).scaleX(0.70f).scaleY(0.70f).setDuration(550L).setInterpolator(SMOOTH_INTERPOLATOR).withEndAction(() -> { if (offlineBadge == null) return; offlineBadge.setVisibility(View.GONE); offlineBadge.setAlpha(1f); offlineBadge.setScaleX(1f); offlineBadge.setScaleY(1f); offlineBadge.setTranslationY(0f); onlineMode = false; expanded = false; applyBadgeBackground(false); }).start(); }, 550L); }; mainHandler.postDelayed(autoHideRunnable, 2800L); }); notifyVisibilityChanged(false); }
    public void showOnlineTransition() { if (offlineBadge == null) return; activity.runOnUiThread(() -> { if (activity.isFinishing()) return; cancelAutoCollapse(); if (autoHideRunnable != null) { mainHandler.removeCallbacks(autoHideRunnable); autoHideRunnable = null; } onlineMode = true; offlineBar.setText(RESTORED_TEXT); offlineIcon.setImageDrawable(new LuxuryCloudDrawable(ONLINE_COLOR, false)); if (auraView != null) { auraView.animate().cancel(); auraView.setAlpha(0f); } if (offlineBadge.getVisibility() != View.VISIBLE) { expanded = false; FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) offlineBadge.getLayoutParams(); params.width = dp(COLLAPSED_SIZE_DP); offlineBadge.setLayoutParams(params); applySafeAreaPosition(); applyBadgeBackground(false); offlineBar.setAlpha(0f); offlineBar.setTranslationX(dp(12)); offlineBadge.setScaleX(0.70f); offlineBadge.setScaleY(0.70f); offlineBadge.setTranslationY(dp(30)); offlineBadge.setAlpha(0f); offlineBadge.setVisibility(View.VISIBLE); offlineBadge.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(700L).setInterpolator(SPRING_INTERPOLATOR).withEndAction(() -> { if (offlineBadge != null) expandBadge(); }).start(); } else { if (!expanded) expandBadge(); } autoHideRunnable = () -> { if (offlineBadge == null) return; if (expanded) collapseBadge(); mainHandler.postDelayed(() -> { if (offlineBadge == null) return; offlineBadge.animate().alpha(0f).scaleX(0.70f).scaleY(0.70f).setDuration(550L).setInterpolator(SMOOTH_INTERPOLATOR).withEndAction(() -> { if (offlineBadge == null) return; offlineBadge.setVisibility(View.GONE); offlineBadge.setAlpha(1f); offlineBadge.setScaleX(1f); offlineBadge.setScaleY(1f); offlineBadge.setTranslationY(0f); onlineMode = false; expanded = false; applyBadgeBackground(false); }).start(); }, 550L); }; mainHandler.postDelayed(autoHideRunnable, 2800L); }); }
    public void hideImmediately() { if (offlineBadge == null) return; activity.runOnUiThread(() -> { cancelAutoCollapse(); if (autoHideRunnable != null) { mainHandler.removeCallbacks(autoHideRunnable); autoHideRunnable = null; } offlineBadge.animate().cancel(); offlineBar.animate().cancel(); if (auraView != null) { auraView.animate().cancel(); auraView.setAlpha(0f); } offlineBadge.setVisibility(View.GONE); offlineBadge.setAlpha(1f); offlineBadge.setScaleX(1f); offlineBadge.setScaleY(1f); offlineBadge.setTranslationY(0f); offlineBar.setAlpha(0f); offlineBar.setTranslationX(dp(12)); FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) offlineBadge.getLayoutParams(); params.width = dp(COLLAPSED_SIZE_DP); offlineBadge.setLayoutParams(params); expanded = false; onlineMode = false; applySafeAreaPosition(); applyBadgeBackground(false); }); notifyVisibilityChanged(false); }

    // [تعديل رابع] تعديل shake() لتوسيع الشريط ديناميكياً
    public void shake() { 
        if (offlineBadge == null) return; 
        if (offlineBadge.getVisibility() != View.VISIBLE) { 
            show(); 
            return; 
        } 
        activity.runOnUiThread(() -> { 
            if (offlineBadge == null) return; 
            String originalText = offlineBar.getText().toString(); 
            offlineBar.setText(WARNING_TEXT); 
            
            // إعادة توسيع الشريط ليتناسب مع نص التحذير الجديد
            if (expanded) {
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) offlineBadge.getLayoutParams();
                params.width = calculateExpandedWidth(WARNING_TEXT);
                offlineBadge.setLayoutParams(params);
            } else {
                expandBadge(); 
            }

            offlineBadge.animate().translationX(dp(5)).setDuration(55L).withEndAction(() -> 
                offlineBadge.animate().translationX(dp(-5)).setDuration(55L).withEndAction(() -> 
                    offlineBadge.animate().translationX(0).setDuration(70L).start()
                ).start()
            ).start(); 
            
            mainHandler.postDelayed(() -> { 
                if (offlineBar != null && offlineBadge != null && offlineBadge.getVisibility() == View.VISIBLE) { 
                    offlineBar.setText(originalText); 
                    if (expanded) {
                        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) offlineBadge.getLayoutParams();
                        params.width = calculateExpandedWidth(originalText);
                        offlineBadge.setLayoutParams(params);
                    }
                } 
            }, 1800L); 
        }); 
    }

    public boolean isVisible() { return offlineBadge != null && offlineBadge.getVisibility() == View.VISIBLE; }
    public TextView getView() { return offlineBar; }
    public View getVeilView() { return null; }
    public boolean isGestureNavigation() { return gestureNavigation; }
    public int getNavigationBottomInset() { return navigationBottomInset; }
    public void setCallback(VisibilityCallback callback) { this.callback = callback; }
    private void notifyVisibilityChanged(boolean visible) { if (callback != null) callback.onVisibilityChanged(visible); }
    public void destroy() { cancelAutoCollapse(); if (autoHideRunnable != null) { mainHandler.removeCallbacks(autoHideRunnable); autoHideRunnable = null; } if (offlineBadge != null) offlineBadge.animate().cancel(); if (offlineBar != null) offlineBar.animate().cancel(); if (auraView != null) auraView.animate().cancel(); if (overlayRoot != null) { ViewGroup parent = (ViewGroup) overlayRoot.getParent(); if (parent != null) parent.removeView(overlayRoot); } auraView = null; iconContainer = null; offlineBadge = null; offlineIcon = null; offlineBar = null; overlayRoot = null; callback = null; expanded = false; onlineMode = false; initialized = false; }
    private int dp(float value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }

    private static final class LuxuryCloudDrawable extends Drawable {
        private final Paint cloudPaint = new Paint(Paint.ANTI_ALIAS_FLAG), slashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path cloudPath = new Path(); private final int color; private final boolean showSlash;
        LuxuryCloudDrawable(int color, boolean showSlash) { this.color = color; this.showSlash = showSlash; cloudPaint.setStyle(Paint.Style.STROKE); cloudPaint.setStrokeWidth(2.15f); cloudPaint.setStrokeCap(Paint.Cap.ROUND); cloudPaint.setStrokeJoin(Paint.Join.ROUND); cloudPaint.setColor(color); slashPaint.setStyle(Paint.Style.STROKE); slashPaint.setStrokeWidth(2.15f); slashPaint.setStrokeCap(Paint.Cap.ROUND); slashPaint.setStrokeJoin(Paint.Join.ROUND); slashPaint.setColor(color); }
        @Override public void draw(Canvas canvas) { Rect bounds = getBounds(); float sx = bounds.width() / 24f, sy = bounds.height() / 24f; canvas.save(); canvas.translate(bounds.left, bounds.top); canvas.scale(sx, sy); cloudPath.reset(); cloudPath.moveTo(17.35f, 18.8f); cloudPath.cubicTo(19.85f, 18.8f, 21.65f, 17.05f, 21.65f, 14.7f); cloudPath.cubicTo(21.65f, 12.35f, 19.85f, 10.45f, 17.55f, 10.25f); cloudPath.cubicTo(17.05f, 6.75f, 14.25f, 4.35f, 10.85f, 4.35f); cloudPath.cubicTo(7.15f, 4.35f, 4.05f, 7.25f, 3.85f, 10.9f); cloudPath.cubicTo(2.15f, 11.35f, 1.0f, 12.85f, 1.0f, 14.65f); cloudPath.cubicTo(1.0f, 16.95f, 2.85f, 18.8f, 5.15f, 18.8f); cloudPath.lineTo(17.35f, 18.8f); canvas.drawPath(cloudPath, cloudPaint); if (showSlash) canvas.drawLine(4.5f, 4.5f, 19.5f, 19.5f, slashPaint); canvas.restore(); }
        @Override public void setAlpha(int alpha) { cloudPaint.setAlpha(alpha); slashPaint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { cloudPaint.setColorFilter(filter); slashPaint.setColorFilter(filter); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    private static final class AuraDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); private final int color;
        AuraDrawable(int color) { this.color = color; }
        @Override public void draw(Canvas canvas) { Rect bounds = getBounds(); float cx = bounds.exactCenterX(), cy = bounds.exactCenterY(), radius = Math.min(bounds.width(), bounds.height()) * 0.5f; RadialGradient gradient = new RadialGradient(cx, cy, radius, new int[]{Color.argb(64, Color.red(color), Color.green(color), Color.blue(color)), Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))}, new float[]{0f, 1f}, Shader.TileMode.CLAMP); paint.setShader(gradient); canvas.drawCircle(cx, cy, radius, paint); paint.setShader(null); }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
                                                                                                                                                }
