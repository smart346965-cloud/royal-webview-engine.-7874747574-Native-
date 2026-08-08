package com.store.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsIntent;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 👑 RoyalAuthManager - وحدة إدارة المصادقة والدفع المركزية
 * 
 * تعتمد على Custom Tabs (مع دعم Auth Tab كـ fallback تلقائي)
 * كبديل آمن ومستقر عن SDKs المتعددة.
 * 
 * ✅ المزايا:
 *   - يعمل مع أي مزود OAuth
 *   - Auto-fallback إلى Auth Tab في الأجهزة المدعومة
 *   - تخصيص UI احترافي
 *   - واجهة موحدة للـ Callbacks
 */
public final class RoyalAuthManager {

    private static final String TAG = "RoyalAuthManager";

    // ==========================================
    // 🔥 الواجهات العامة (Callbacks)
    // ==========================================

    public interface AuthCallback {
        void onAuthSuccess(String token, String userId);
        void onAuthError(Exception exception);
        void onAuthCancel();
    }

    public interface PaymentCallback {
        void onPaymentSuccess(String transactionId);
        void onPaymentError(Exception exception);
        void onPaymentCancel();
    }

    // ==========================================
    // 🔥 المتغيرات
    // ==========================================

    private final Activity activity;
    private final Context context;

    // Custom Tabs Intent (يعمل مع Auth Tab تلقائياً)
    private final CustomTabsIntent customTabsIntent;

    // مراجع للـ Callbacks المعلقة
    private final AtomicReference<AuthCallback> pendingAuthCallback = new AtomicReference<>();
    private final AtomicReference<PaymentCallback> pendingPaymentCallback = new AtomicReference<>();

    // ==========================================
    // 🚀 البناء
    // ==========================================

    public RoyalAuthManager(@NonNull Activity activity, @NonNull Context context) {
        this.activity = activity;
        this.context = context;

        // تهيئة Custom Tabs (يدعم Auth Tab تلقائياً)
        this.customTabsIntent = new CustomTabsIntent.Builder()
                .setToolbarColor(android.graphics.Color.parseColor("#007AFF"))
                .enableUrlBarHiding()
                .setStartAnimations(context, android.R.anim.fade_in, android.R.anim.fade_out)
                .setExitAnimations(context, android.R.anim.fade_in, android.R.anim.fade_out)
                .addDefaultShareMenuItem()
                .build();

        Log.i(TAG, "✅ RoyalAuthManager initialized (Custom Tabs)");
    }

    // ==========================================
    // 🔐 1. Auth / Custom Tabs (للمصادقات)
    // ==========================================

    /**
     * فتح Custom Tabs للمصادقة (يدعم Auth Tab تلقائياً)
     * @param url رابط المصادقة
     * @param callback استدعاء النتيجة
     */
    public void launchAuthTab(@NonNull String url, @NonNull AuthCallback callback) {
        pendingAuthCallback.set(callback);
        try {
            Log.i(TAG, "🚀 Launching Custom Tabs (Auth) for: " + url);
            customTabsIntent.launchUrl(activity, Uri.parse(url));
        } catch (Exception e) {
            Log.e(TAG, "❌ Custom Tabs launch failed", e);
            pendingAuthCallback.set(null);
            callback.onAuthError(e);
        }
    }

    /**
     * معالجة نتيجة المصادقة - يجب استدعاؤها من onActivityResult أو onNewIntent
     */
    public void handleAuthResult(int resultCode, @Nullable Intent data) {
        AuthCallback callback = pendingAuthCallback.getAndSet(null);
        if (callback == null) {
            Log.w(TAG, "⚠️ No pending AuthCallback");
            return;
        }

        if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            Uri resultUri = data.getData();
            String token = resultUri.getQueryParameter("code");
            String userId = resultUri.getQueryParameter("user_id");
            if (token != null && !token.isEmpty()) {
                Log.i(TAG, "✅ Auth success: token=" + token);
                callback.onAuthSuccess(token, userId);
            } else {
                callback.onAuthError(new Exception("No token received"));
            }
        } else if (resultCode == Activity.RESULT_CANCELED) {
            callback.onAuthCancel();
        } else {
            callback.onAuthError(new Exception("Auth failed with code: " + resultCode));
        }
    }

    // ==========================================
    // 🌐 2. Custom Tabs (للدفع والروابط الخارجية)
    // ==========================================

    public void launchCustomTabs(@NonNull String url, @NonNull PaymentCallback callback) {
        pendingPaymentCallback.set(callback);
        try {
            Log.i(TAG, "🌐 Launching Custom Tabs for: " + url);
            customTabsIntent.launchUrl(activity, Uri.parse(url));
        } catch (Exception e) {
            Log.e(TAG, "❌ Custom Tabs launch failed", e);
            pendingPaymentCallback.set(null);
            callback.onPaymentError(e);
        }
    }

    public void handlePaymentResult(int resultCode, @Nullable Intent data) {
        PaymentCallback callback = pendingPaymentCallback.getAndSet(null);
        if (callback == null) {
            Log.w(TAG, "⚠️ No pending PaymentCallback");
            return;
        }

        if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            String transactionId = data.getData().getQueryParameter("transaction_id");
            if (transactionId != null && !transactionId.isEmpty()) {
                callback.onPaymentSuccess(transactionId);
            } else {
                callback.onPaymentError(new Exception("No transaction_id received"));
            }
        } else if (resultCode == Activity.RESULT_CANCELED) {
            callback.onPaymentCancel();
        } else {
            callback.onPaymentError(new Exception("Payment failed with code: " + resultCode));
        }
    }

    // ==========================================
    // 🔄 3. وحدة التحكم والتوجيه
    // ==========================================

    public void handleAuth(@NonNull String provider, @NonNull AuthCallback callback) {
        String authUrl = buildAuthUrl(provider);
        if (authUrl != null) {
            launchAuthTab(authUrl, callback);
        } else {
            callback.onAuthError(new Exception("Unsupported auth provider: " + provider));
        }
    }

    public void launchPayment(@NonNull String gateway, @NonNull PaymentCallback callback) {
        String paymentUrl = buildPaymentUrl(gateway);
        if (paymentUrl != null) {
            launchCustomTabs(paymentUrl, callback);
        } else {
            callback.onPaymentError(new Exception("Unsupported payment gateway: " + gateway));
        }
    }

    // ==========================================
    // 🧹 4. تنظيف الموارد
    // ==========================================

    public void destroy() {
        pendingAuthCallback.set(null);
        pendingPaymentCallback.set(null);
        Log.i(TAG, "🧹 RoyalAuthManager destroyed");
    }

    // ==========================================
    // 🔧 دوال مساعدة (بناء الروابط)
    // ==========================================

    private String buildAuthUrl(@NonNull String provider) {
        switch (provider.toLowerCase()) {
            case "google":
                return "https://accounts.google.com/o/oauth2/auth?client_id=YOUR_CLIENT_ID&redirect_uri=YOUR_REDIRECT_URI&response_type=code&scope=email%20profile";
            case "github":
                return "https://github.com/login/oauth/authorize?client_id=YOUR_CLIENT_ID&redirect_uri=YOUR_REDIRECT_URI&scope=user:email";
            case "microsoft":
            case "azure":
                return "https://login.microsoftonline.com/common/oauth2/v2.0/authorize?client_id=YOUR_CLIENT_ID&redirect_uri=YOUR_REDIRECT_URI&response_type=code&scope=openid%20profile%20email";
            default:
                return null;
        }
    }

    private String buildPaymentUrl(@NonNull String gateway) {
        switch (gateway.toLowerCase()) {
            case "stripe":
                return "https://checkout.stripe.com/pay/YOUR_SESSION_ID";
            case "paypal":
                return "https://www.paypal.com/checkoutnow?token=YOUR_TOKEN";
            default:
                return null;
        }
    }
                    }
