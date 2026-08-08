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

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 👑 RoyalAuthManager - وحدة إدارة المصادقة والدفع المركزية
 * 
 * تدير جميع عمليات المصادقة (OAuth, Social Login) والدفع (SDKs, Custom Tabs)
 * باستخدام أفضل الممارسات من Google (Auth Tab, SDKs, Custom Tabs)
 */
public final class RoyalAuthManager {

    private static final String TAG = "RoyalAuthManager";

    // ==========================================
    // 🔥 الواجهات العامة
    // ==========================================

    /**
     * واجهة استدعاء المصادقة
     */
    public interface AuthCallback {
        void onAuthSuccess(String token, String userId);
        void onAuthError(Exception exception);
        void onAuthCancel();
    }

    /**
     * واجهة استدعاء الدفع
     */
    public interface PaymentCallback {
        void onPaymentSuccess(String transactionId);
        void onPaymentError(Exception exception);
        void onPaymentCancel();
    }

    // ==========================================
    // 🔥 متغيرات Auth Tab
    // ==========================================

    private final Context context;
    private final Activity activity;
    private final AuthTabIntent authTabIntent;
    private final AtomicReference<AuthCallback> pendingAuthCallback = new AtomicReference<>();
    private final AtomicReference<PaymentCallback> pendingPaymentCallback = new AtomicReference<>();

    // ==========================================
    // 🔥 متغيرات Google Sign-In
    // ==========================================

    private static final int RC_GOOGLE_SIGN_IN = 9001;
    private static final int RC_FACEBOOK_SIGN_IN = 64206;
    private GoogleSignInClient googleSignInClient;

    // ==========================================
    // 🚀 البناء
    // ==========================================

    public RoyalAuthManager(@NonNull Activity activity, @NonNull Context context) {
        this.activity = activity;
        this.context = context;

        // تهيئة Auth Tab
        this.authTabIntent = new AuthTabIntent.Builder()
                .setAuthTabCallback(new AuthTabCallback() {
                    @Override
                    public void onAuthTabResult(int resultCode, @Nullable Intent data) {
                        Log.i(TAG, "Auth Tab result: " + resultCode);
                        handleAuthTabResult(resultCode, data);
                    }
                })
                .build();

        // تهيئة Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestIdToken("YOUR_WEB_CLIENT_ID") // أدخل client_id الخاص بـ Firebase
                .build();
        googleSignInClient = GoogleSignIn.getClient(activity, gso);
    }

    // ==========================================
    // 🔐 1. Auth Tab (للمصادقات الخارجية)
    // ==========================================

    /**
     * فتح Auth Tab لصفحة المصادقة
     * @param url رابط المصادقة (مثل GitHub OAuth, Microsoft Entra ID, Auth0)
     * @param callback استدعاء النتيجة
     */
    public void launchAuthTab(@NonNull String url, @NonNull AuthCallback callback) {
        pendingAuthCallback.set(callback);
        try {
            authTabIntent.launch(activity, Uri.parse(url));
        } catch (Exception e) {
            Log.e(TAG, "Auth Tab launch failed", e);
            pendingAuthCallback.set(null);
            callback.onAuthError(e);
        }
    }

    /**
     * معالجة نتيجة Auth Tab
     * يجب استدعاؤها من onNewIntent أو onActivityResult (حسب التنفيذ)
     */
    private void handleAuthTabResult(int resultCode, @Nullable Intent data) {
        AuthCallback callback = pendingAuthCallback.getAndSet(null);
        if (callback == null) {
            Log.w(TAG, "No pending AuthCallback for Auth Tab result");
            return;
        }

        if (resultCode == Activity.RESULT_OK) {
            // Auth Tab يرد عبر intent مع بيانات المصادقة
            if (data != null && data.getData() != null) {
                String token = data.getData().getQueryParameter("code");
                String userId = data.getData().getQueryParameter("user_id");
                if (token != null) {
                    callback.onAuthSuccess(token, userId);
                } else {
                    callback.onAuthError(new Exception("No token received"));
                }
            } else {
                callback.onAuthError(new Exception("Invalid response from Auth Tab"));
            }
        } else if (resultCode == Activity.RESULT_CANCELED) {
            callback.onAuthCancel();
        } else {
            callback.onAuthError(new Exception("Auth Tab failed with code: " + resultCode));
        }
    }

    // ==========================================
    // 🔐 2. Google Sign-In (SDK)
    // ==========================================

    public void signInWithGoogle() {
        Intent signInIntent = googleSignInClient.getSignInIntent();
        activity.startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN);
    }

    /**
     * معالجة نتيجة Google Sign-In
     * يجب استدعاؤها من onActivityResult
     */
    public boolean handleGoogleSignInResult(int requestCode, int resultCode, @Nullable Intent data, AuthCallback callback) {
        if (requestCode != RC_GOOGLE_SIGN_IN) return false;

        if (resultCode == Activity.RESULT_OK) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                if (account != null) {
                    String token = account.getIdToken();
                    String userId = account.getId();
                    callback.onAuthSuccess(token, userId);
                } else {
                    callback.onAuthError(new Exception("No account data"));
                }
            } catch (ApiException e) {
                callback.onAuthError(e);
            }
        } else if (resultCode == Activity.RESULT_CANCELED) {
            callback.onAuthCancel();
        } else {
            callback.onAuthError(new Exception("Google Sign-In failed"));
        }
        return true;
    }

    // ==========================================
    // 🔐 3. Facebook Sign-In (SDK)
    // ==========================================

    /**
     * ملاحظة: يتطلب Facebook SDK. يتم تفعيله عبر تسجيل LoginManager وتطبيق الكود المعتاد.
     * هذه دالة توضيحية.
     */
    public void signInWithFacebook() {
        // مثال بسيط، يُنفذ عبر LoginManager.getInstance().logInWithReadPermissions(...)
        // سيتم استدعاء onActivityResult مع RC_FACEBOOK_SIGN_IN
        Log.i(TAG, "Facebook Sign-In: استخدم LoginManager مع Firebase Auth");
    }

    // ==========================================
    // 🔐 4. Apple / Twitter / Microsoft عبر FirebaseUI أو Auth Tab
    // ==========================================

    /**
     * Apple و Twitter و Microsoft يمكن تفعيلها عبر Firebase Auth مباشرة أو عبر Auth Tab.
     */
    public void signInWithApple() {
        // Firebase Auth: OAuthProvider.newBuilder("apple.com")
        Log.i(TAG, "Apple Sign-In: استخدم Firebase Auth");
    }

    public void signInWithTwitter() {
        // Firebase Auth: OAuthProvider.newBuilder("twitter.com")
        Log.i(TAG, "Twitter Sign-In: استخدم Firebase Auth");
    }

    public void signInWithMicrosoft() {
        // Microsoft Entra ID: عبر Auth Tab أو MSAL SDK
        launchAuthTab("https://login.microsoftonline.com/...", new AuthCallback() {
            @Override public void onAuthSuccess(String token, String userId) {
                Log.i(TAG, "Microsoft Auth success");
            }
            @Override public void onAuthError(Exception e) {
                Log.e(TAG, "Microsoft Auth error", e);
            }
            @Override public void onAuthCancel() {
                Log.w(TAG, "Microsoft Auth cancelled");
            }
        });
    }

    // ==========================================
    // 💳 5. Payment Gateways (SDKs)
    // ==========================================

    /**
     * فتح بوابة دفع عبر SDK (Stripe, PayPal, Braintree...)
     * ملاحظة: يجب تهيئة SDKs بشكل منفصل في التطبيق.
     */
    public void launchPayment(@NonNull String gateway, @NonNull PaymentCallback callback) {
        pendingPaymentCallback.set(callback);
        switch (gateway.toLowerCase()) {
            case "stripe":
                // Stripe SDK: StripeApiRepository, PaymentSheet
                Log.i(TAG, "Stripe: استخدام PaymentSheet");
                // مثال: StripePaymentManager.getInstance().presentPaymentSheet(...);
                break;
            case "paypal":
                // PayPal SDK
                Log.i(TAG, "PayPal: استخدام PayPalCheckout");
                break;
            case "braintree":
                // Braintree SDK: DropInClient
                Log.i(TAG, "Braintree: استخدام DropInClient");
                break;
            case "razorpay":
                // Razorpay SDK
                Log.i(TAG, "Razorpay: استخدام RazorpayCheckout");
                break;
            case "googlepay":
                // Google Pay عبر PaymentsClient
                Log.i(TAG, "Google Pay: استخدام PaymentsClient");
                break;
            default:
                // إذا كانت غير معروفة، نستخدم Custom Tabs
                Log.w(TAG, "Gateway not recognized, falling back to Custom Tabs");
                launchCustomTabs("https://payments." + gateway + ".com", callback);
                break;
        }
    }

    // ==========================================
    // 🌐 6. Custom Tabs (للحالات التي لا تدعمها Auth Tab)
    // ==========================================

    /**
     * فتح Custom Tabs بشكل احترافي مع تخصيص UI
     */
    public void launchCustomTabs(@NonNull String url, @NonNull PaymentCallback callback) {
        // نستخدم Custom Tabs كـ fallback للمدفوعات أو للمنصات غير المدعومة
        pendingPaymentCallback.set(callback);
        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
        builder.setToolbarColor(android.graphics.Color.parseColor("#007AFF"));
        builder.enableUrlBarHiding();
        builder.setStartAnimations(context, android.R.anim.fade_in, android.R.anim.fade_out);
        builder.setExitAnimations(context, android.R.anim.fade_in, android.R.anim.fade_out);
        builder.addDefaultShareMenuItem();
        CustomTabsIntent customTabsIntent = builder.build();
        customTabsIntent.launchUrl(activity, Uri.parse(url));
    }

    // ==========================================
    // 🔄 7. وحدة التحكم والتوجيه
    // ==========================================

    /**
     * دالة توجيه عامة للمصادقة - تختار تلقائياً بين SDK أو Auth Tab
     */
    public void handleAuth(@NonNull String provider, @NonNull AuthCallback callback) {
        switch (provider.toLowerCase()) {
            case "google":
                signInWithGoogle();
                break;
            case "facebook":
                signInWithFacebook();
                break;
            case "apple":
                signInWithApple();
                break;
            case "twitter":
                signInWithTwitter();
                break;
            case "microsoft":
            case "azure":
            case "entra":
                signInWithMicrosoft();
                break;
            default:
                // استخدام Auth Tab للمزودين غير المدعومين بـ SDK
                launchAuthTab("https://" + provider + ".com/oauth", callback);
                break;
        }
    }

    // ==========================================
    // 🧹 8. تنظيف الموارد
    // ==========================================

    public void destroy() {
        pendingAuthCallback.set(null);
        pendingPaymentCallback.set(null);
    }
  }
