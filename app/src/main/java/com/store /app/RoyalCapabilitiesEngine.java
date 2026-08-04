package com.store.app;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class RoyalCapabilitiesEngine {

    private final Activity activity;
    
    // متغير حيوي لحفظ مسار الملف عندما يطلب الموقع رفع صورة/ملف
    public static ValueCallback<Uri[]> filePathCallback;
    public final static int FILECHOOSER_RESULTCODE = 101; // كود سري لتعريف العملية

    // 📸 الكاميرا والميكروفون (WebRTC)
    private PermissionRequest lastPermissionRequest;

    public RoyalCapabilitiesEngine(Activity activity) {
        this.activity = activity;
    }

    // 1️⃣ تفعيل مدير التحميلات (File Downloading)
    public void attachDownloadManager(WebView webView) {
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent,
                                        String contentDisposition, String mimetype,
                                        long contentLength) {
                try {
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                    request.setMimeType(mimetype);
                    
                    // جلب الكوكيز لتتمكن من تحميل الملفات من المواقع التي تتطلب تسجيل دخول
                    String cookies = CookieManager.getInstance().getCookie(url);
                    request.addRequestHeader("cookie", cookies);
                    request.addRequestHeader("User-Agent", userAgent);

                    request.setDescription("Downloading file...");
                    request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimetype));
                    request.allowScanningByMediaScanner();
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimetype));
                    
                    DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
                    dm.enqueue(request);
                    
                    Toast.makeText(activity.getApplicationContext(), "جاري التحميل...", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e("RoyalCapabilities", "❌ خطأ في تحميل الملف: " + e.getMessage());
                }
            }
        });
    }

    // 2️⃣ بناء العميل الخارق (WebChromeClient) الذي يدمج شريط التحميل مع قدرات العتاد
    public WebChromeClient buildChromeClient(ProgressBar progressBar) {
        return new WebChromeClient() {

            // [أ] التعامل مع شريط التحميل (تم نقله من الملف القديم)
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (progressBar != null) {
                    progressBar.setProgress(newProgress);
                    if (newProgress == 100) {
                        progressBar.animate()
                                .alpha(0f)
                                .setDuration(150)
                                .withEndAction(() -> progressBar.setVisibility(View.GONE))
                                .start();
                    } else {
                        progressBar.setVisibility(View.VISIBLE);
                        progressBar.setAlpha(1f);
                    }
                }
            }

            // [ب] نظام الملفات والاستوديو: استجابة لزر رفع الصور (File Upload)
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (RoyalCapabilitiesEngine.filePathCallback != null) {
                    RoyalCapabilitiesEngine.filePathCallback.onReceiveValue(null);
                }
                RoyalCapabilitiesEngine.filePathCallback = filePathCallback;
                
                Intent contentSelectionIntent = new Intent(Intent.ACTION_GET_CONTENT);
                contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
                contentSelectionIntent.setType("*/*"); // يسمح باختيار أي نوع ملف
                
                // السماح برفع ملفات متعددة إذا كان الموقع يطلب ذلك
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    contentSelectionIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, fileChooserParams.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                }

                Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
                chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent);
                chooserIntent.putExtra(Intent.EXTRA_TITLE, "اختر ملفاً أو صورة");
                
                try {
                    activity.startActivityForResult(chooserIntent, FILECHOOSER_RESULTCODE);
                    return true;
                } catch (Exception e) {
                    RoyalCapabilitiesEngine.filePathCallback = null;
                    return false;
                }
            }

            // [ج] تحديد الموقع الجغرافي (Geolocation) لخرائط التوصيل
            // [تعديل جراحي في RoyalCapabilitiesEngine.java]
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(activity, new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION, 
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    }, 102);
                    callback.invoke(origin, true, false);
                } else {
                    callback.invoke(origin, true, false);
                }
            }

            // [د] صلاحيات الويب الحديثة (WebRTC, Camera, Microphone, Bluetooth)
            // [تعديل جراحي في RoyalCapabilitiesEngine.java]
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                activity.runOnUiThread(() -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        String[] resources = request.getResources();
                        for (String resource : resources) {
                            // الكاميرا
                            if (resource.equals(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                                    ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA}, 103);
                                }
                            }
                            // الميكروفون (تمت الإضافة الآن)
                            if (resource.equals(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                    ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.RECORD_AUDIO}, 104);
                                }
                            }
                        }
                        // منح الإذن لكل ما طلبه المتصفح (الموافقة الفعلية تأتي من النظام لاحقاً)
                        request.grant(resources);
                    }
                });
            }
        };
    }

    // 3. طلب إذن الإشعارات لأندرويد 13+ فور تشغيل التطبيق (اختياري)
    public void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 105);
            }
        }
    }

    // دالة لمعالجة الرد القادم من MainActivity (سنستدعيها لاحقاً)
    public void handlePermissionResult(int requestCode, int[] grantResults) {
        if (requestCode == 103 && lastPermissionRequest != null) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                lastPermissionRequest.grant(lastPermissionRequest.getResources());
            } else {
                lastPermissionRequest.deny();
            }
            lastPermissionRequest = null;
        }
    }
            }
