package com.store.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.auth.AuthTabCallback;
import androidx.browser.auth.AuthTabIntent;
import androidx.browser.customtabs.CustomTabsIntent;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 👑 RoyalAuthManager - وحدة إدارة المصادقة والدفع المركزية
 * 
 * تعتمد على Auth Tab (الحل الرسمي من Google) و Custom Tabs
 * كبديل آمن ومستقر عن SDKs المتعددة.
 * 
 * تدير:
 * - Auth Tab (للمصادقات OAuth مثل Google, GitHub, Microsoft, Auth0, إلخ)
 * - Custom Tabs (كـ fallback للمنصات غير المدعومة، بوابات الدفع، الروابط الخارجية)
 * 
 * ✅ مزايا Auth Tab:
 *   - يعيد النتيجة تلقائياً عبر AuthTabCallback
 *   - واجهة مبسطة ومخصصة للمصادقة
 *   - متوافق مع جميع مزودي OAuth
 *   - Auto-fallback إلى Custom Tabs في الأجهزة الأقدم (Chrome < 132)
 */
public final class RoyalAuthManager {

    private static final String TAG = "RoyalAuthManager";

    // ==========================================
    // 🔥 الواجهات العامة (Callbacks)
    // ==========================================

    /**
     * واجهة استدعاء المصادقة (Auth Callback)
     */
    public interface AuthCallback {
        /**
         * يتم استدعاؤها عند نجاح المصادقة
         * @param token رمز المصادقة (Authorization Code أو Access Token)
         * @param userId معرف المستخدم (إن وُجد)
         */
        void onAuthSuccess(String token, String userId);

        /**
         * يتم استدعاؤها عند فشل المصادقة
         * @param exception سبب الفشل
         */
        void onAuthError(Exception exception);

        /**
         * يتم استدعاؤها عند إلغاء المستخدم لعملية المصادقة
         */
        void onAuthCancel();
    }

    /**
     * واجهة استدعاء الدفع (Payment Callback)
     */
    public interface PaymentCallback {
        /**
         * يتم استدعاؤها عند نجاح عملية الدفع
         * @param transactionId معرف عملية الدفع
         */
        void onPaymentSuccess(String transactionId);

        /**
         * يتم استدعاؤها عند فشل الدفع
         * @param exception سبب الفشل
         */
        void onPaymentError(Exception exception);

        /**
         * يتم استدعاؤها عند إلغاء المستخدم لعملية الدفع
         */
        void onPaymentCancel();
    }

    // ==========================================
    // 🔥 متغيرات المدير
    // ==========================================

    private final Activity activity;
    private final Context context;

    // Auth Tab Intent (المصادقة)
    private final AuthTabIntent authTabIntent;

    // Custom Tabs Intent (للدفع والروابط الخارجية)
    private final CustomTabsIntent customTabsIntent;

    // مراجع للـ Callbacks المعلقة
    private final AtomicReference<AuthCallback> pendingAuthCallback = new AtomicReference<>();
    private final AtomicReference<PaymentCallback> pendingPaymentCallback = new AtomicReference<>();

    // ==========================================
    // 🚀 البناء والتهيئة
    // ==========================================

    public RoyalAuthManager(@NonNull Activity activity, @NonNull Context context) {
        this.activity = activity;
        this.context = context;

        // ==========================================
        // 1. تهيئة Auth Tab (للمصادقة)
        // ==========================================
        this.authTabIntent = new AuthTabIntent.Builder()
                .setAuthTabCallback(new AuthTabCallback() {
                    @Override
                    public void onAuthTabResult(int resultCode, @Nullable Intent data) {
                        Log.i(TAG, "🔐 Auth Tab result: " + resultCode);
                        handleAuthTabResult(resultCode, data);
                    }
                })
                .build();

        // ==========================================
        // 2. تهيئة Custom Tabs (للدفع والروابط الخارجية)
        // ==========================================
        this.customTabsIntent = new CustomTabsIntent.Builder()
                .setToolbarColor(android.graphics.Color.parseColor("#007AFF"))
                .enableUrlBarHiding()
                .setStartAnimations(context, android.R.anim.fade_in, android.R.anim.fade_out)
                .setExitAnimations(context, android.R.anim.fade_in, android.R.anim.fade_out)
                .addDefaultShareMenuItem()
                .build();

        Log.i(TAG, "✅ RoyalAuthManager initialized (Auth Tab + Custom Tabs)");
    }

    // ==========================================
    // 🔐 1. Auth Tab (للمصادقات الخارجية)
    // ==========================================

    /**
     * فتح Auth Tab لصفحة المصادقة
     * 
     * @param url      رابط المصادقة (مثل GitHub OAuth, Microsoft Entra ID, Auth0)
     * @param callback استدعاء النتيجة (يُستدعى عند النجاح/الفشل/الإلغاء)
     */
    public void launchAuthTab(@NonNull String url, @NonNull AuthCallback callback) {
        pendingAuthCallback.set(callback);
        try {
            Log.i(TAG, "🚀 Launching Auth Tab for: " + url);
            authTabIntent.launch(activity, Uri.parse(url));
        } catch (Exception e) {
            Log.e(TAG, "❌ Auth Tab launch failed", e);
            pendingAuthCallback.set(null);
            callback.onAuthError(e);
        }
    }

    /**
     * معالجة نتيجة Auth Tab
     * يجب استدعاؤها من onActivityResult (أو onNewIntent حسب التنفيذ)
     */
    private void handleAuthTabResult(int resultCode, @Nullable Intent data) {
        AuthCallback callback = pendingAuthCallback.getAndSet(null);
        if (callback == null) {
            Log.w(TAG, "⚠️ No pending AuthCallback for Auth Tab result");
            return;
        }

        if (resultCode == Activity.RESULT_OK) {
            if (data != null && data.getData() != null) {
                // استخراج بيانات المصادقة من URL
                Uri resultUri = data.getData();
                String token = resultUri.getQueryParameter("code");
                String userId = resultUri.getQueryParameter("user_id");
                if (token != null && !token.isEmpty()) {
                    Log.i(TAG, "✅ Auth Tab success: token=" + token);
                    callback.onAuthSuccess(token, userId);
                } else {
                    Log.e(TAG, "❌ Auth Tab: No token received");
                    callback.onAuthError(new Exception("No token received"));
                }
            } else {
                Log.e(TAG, "❌ Auth Tab: Invalid response (null data)");
                callback.onAuthError(new Exception("Invalid response from Auth Tab"));
            }
        } else if (resultCode == Activity.RESULT_CANCELED) {
            Log.w(TAG, "⚠️ Auth Tab cancelled by user");
            callback.onAuthCancel();
        } else {
            Log.e(TAG, "❌ Auth Tab failed with code: " + resultCode);
            callback.onAuthError(new Exception("Auth Tab failed with code: " + resultCode));
        }
    }

    // ==========================================
    // 🌐 2. Custom Tabs (للدفع والروابط الخارجية)
    // ==========================================

    /**
     * فتح Custom Tabs لعملية دفع أو صفحة خارجية
     * 
     * @param url      رابط الدفع أو الصفحة
     * @param callback استدعاء النتيجة (يُستدعى عند النجاح/الفشل/الإلغاء)
     */
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

    /**
     * معالجة نتيجة Custom Tabs
     * يجب استدعاؤها من onActivityResult (إذا تم استخدام startActivityForResult)
     * أو من onNewIntent (في حالة Deep Links)
     */
    public void handleCustomTabsResult(int resultCode, @Nullable Intent data) {
        PaymentCallback callback = pendingPaymentCallback.getAndSet(null);
        if (callback == null) {
            Log.w(TAG, "⚠️ No pending PaymentCallback for Custom Tabs result");
            return;
        }

        if (resultCode == Activity.RESULT_OK) {
            if (data != null && data.getData() != null) {
                String transactionId = data.getData().getQueryParameter("transaction_id");
                if (transactionId != null && !transactionId.isEmpty()) {
                    Log.i(TAG, "✅ Payment success: transaction_id=" + transactionId);
                    callback.onPaymentSuccess(transactionId);
                } else {
                    Log.e(TAG, "❌ Payment: No transaction_id received");
                    callback.onPaymentError(new Exception("No transaction_id received"));
                }
            } else {
                Log.e(TAG, "❌ Payment: Invalid response (null data)");
                callback.onPaymentError(new Exception("Invalid response from payment"));
            }
        } else if (resultCode == Activity.RESULT_CANCELED) {
            Log.w(TAG, "⚠️ Payment cancelled by user");
            callback.onPaymentCancel();
        } else {
            Log.e(TAG, "❌ Payment failed with code: " + resultCode);
            callback.onPaymentError(new Exception("Payment failed with code: " + resultCode));
        }
    }

    // ==========================================
    // 🔄 3. وحدة التحكم والتوجيه (Routing)
    // ==========================================

    /**
     * دالة توجيه عامة للمصادقة - تستخدم Auth Tab لكل المزودين
     * 
     * @param provider  اسم مزود المصادقة (google, github, microsoft, إلخ)
     * @param callback  استدعاء النتيجة
     */
    public void handleAuth(@NonNull String provider, @NonNull AuthCallback callback) {
        // بناء رابط المصادقة حسب المزود
        String authUrl = buildAuthUrl(provider);
        if (authUrl != null) {
            launchAuthTab(authUrl, callback);
        } else {
            callback.onAuthError(new Exception("Unsupported auth provider: " + provider));
        }
    }

    /**
     * بناء رابط المصادقة حسب المزود
     * يمكنك توسيع هذه الدالة لإضافة المزيد من المزودين
     */
    private String buildAuthUrl(@NonNull String provider) {
        switch (provider.toLowerCase()) {
            case "google":
                // مثال: رابط OAuth لـ Google
                return "https://accounts.google.com/o/oauth2/auth?client_id=YOUR_CLIENT_ID&redirect_uri=YOUR_REDIRECT_URI&response_type=code&scope=email%20profile";
            case "github":
                return "https://github.com/login/oauth/authorize?client_id=YOUR_CLIENT_ID&redirect_uri=YOUR_REDIRECT_URI&scope=user:email";
            case "microsoft":
            case "azure":
            case "entra":
                return "https://login.microsoftonline.com/common/oauth2/v2.0/authorize?client_id=YOUR_CLIENT_ID&redirect_uri=YOUR_REDIRECT_URI&response_type=code&scope=openid%20profile%20email";
            case "auth0":
                return "https://YOUR_DOMAIN.auth0.com/authorize?client_id=YOUR_CLIENT_ID&redirect_uri=YOUR_REDIRECT_URI&response_type=code&scope=openid%20profile%20email";
            default:
                return null;
        }
    }

    // ==========================================
    // 💳 4. Payment (للاستخدام المستقبلي)
    // ==========================================

    /**
     * فتح بوابة دفع عبر Custom Tabs
     * 
     * @param gateway  اسم بوابة الدفع (stripe, paypal, إلخ)
     * @param callback استدعاء النتيجة
     */
    public void launchPayment(@NonNull String gateway, @NonNull PaymentCallback callback) {
        String paymentUrl = buildPaymentUrl(gateway);
        if (paymentUrl != null) {
            launchCustomTabs(paymentUrl, callback);
        } else {
            callback.onPaymentError(new Exception("Unsupported payment gateway: " + gateway));
        }
    }

    /**
     * بناء رابط الدفع حسب البوابة
     * يمكنك توسيع هذه الدالة لإضافة المزيد من البوابات
     */
    private String buildPaymentUrl(@NonNull String gateway) {
        switch (gateway.toLowerCase()) {
            case "stripe":
                return "https://checkout.stripe.com/pay/YOUR_SESSION_ID";
            case "paypal":
                return "https://www.paypal.com/checkoutnow?token=YOUR_TOKEN";
            case "razorpay":
                return "https://checkout.razorpay.com/v1/checkout?order_id=YOUR_ORDER_ID";
            default:
                return null;
        }
    }

    // ==========================================
    // 🧹 5. تنظيف الموارد
    // ==========================================

    public void destroy() {
        pendingAuthCallback.set(null);
        pendingPaymentCallback.set(null);
        Log.i(TAG, "🧹 RoyalAuthManager destroyed");
    }
                           }
