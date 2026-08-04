package com.store.app;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.ImageView;

import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 👑 ROYAL SESSION SENTINEL (The Immortal Session Core V2)
 * =========================================================
 * المعمارية الحتمية: استعادة الحالة الثنائية + الحصانة البصرية.
 */
public final class RoyalSessionSentinel {

    private static final String TAG = "RoyalSentinel";
    private static final String STATE_FILE = "royal_web_state.bin";
    private static final String SNAPSHOT_FILE = "ghost_snapshot.webp";
    private static final String META_FILE = "session_meta.properties";

    private static final ExecutorService diskExecutor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static ImageView ghostOverlay;
    private static boolean isResurrecting = false;

    private RoyalSessionSentinel() {}

    // ==========================================
    // ❄️ FREEZE: تجميد حقيقي (Binary Serialization)
    // ==========================================
    public static void freeze(WebView webView) {
        if (webView == null || webView.getUrl() == null || webView.getWidth() <= 0) return;

        // 1. التقاط الحالة الثنائية للـ WebView (التاريخ + السجل + البيانات)
        final Bundle webState = new Bundle();
        webView.saveState(webState);

        // 2. لقطة بصرية للجودة العالية
        final Bitmap snapshot = captureWebView(webView);
        final String url = webView.getUrl();

        diskExecutor.execute(() -> {
            try {
                File dir = webView.getContext().getCacheDir();

                // 💾 أ. حفظ الـ Bundle كملف ثنائي (السر الذي فات الخبير السابق)
                saveBundleToDisk(webState, new File(dir, STATE_FILE));

                // 💾 ب. حفظ اللقطة البصرية (WebP)
                if (snapshot != null) {
                    try (FileOutputStream fos = new FileOutputStream(new File(dir, SNAPSHOT_FILE))) {
                        snapshot.compress(Bitmap.CompressFormat.WEBP, 80, fos);
                    }
                    snapshot.recycle();
                }

                // 💾 ج. حفظ الإحداثيات
                saveMetadata(dir, url, webView.getScrollX(), webView.getScrollY());

                Log.i(TAG, "❄️ Core Frozen: State & Visuals stored.");
            } catch (Exception e) {
                Log.e(TAG, "❌ Freeze critical error: " + e.getMessage());
            }
        });
    }

    // ==========================================
    // ⚡ RESURRECT: إحياء حتمي (Binary Restore)
    // ==========================================
    public static boolean resurrect(WebView webView, Activity activity) {
        if (webView == null || activity == null) return false;

        File dir = activity.getCacheDir();
        File stateFile = new File(dir, STATE_FILE);
        File snapFile = new File(dir, SNAPSHOT_FILE);

        if (!stateFile.exists()) return false;

        isResurrecting = true;

        // 1. ارفع القناع البصري فوراً (0ms)
        if (snapFile.exists()) {
            showGhostOverlay(activity, snapFile);
        }

        diskExecutor.execute(() -> {
            try {
                // 📖 قراءة الحالة الثنائية
                final Bundle restoredBundle = loadBundleFromDisk(stateFile);
                
                mainHandler.post(() -> {
                    if (restoredBundle != null) {
                        Log.i(TAG, "⚡ Injecting Binary State into Chromium Kernel...");
                        webView.restoreState(restoredBundle);
                    } else {
                        isResurrecting = false;
                        hideGhostOverlay();
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> { isResurrecting = false; hideGhostOverlay(); });
            }
        });

        return true;
    }

    // ==========================================
    // 🛡️ THE SAFE SWITCH: التبديل الآمن عند جاهزية البكسلات
    // ==========================================
    /**
     * تُستدعى هذه الدالة حصراً من WebEngineManager.onPageCommitVisible
     */
    public static void notifyPageReady() {
        if (isResurrecting) {
            // نمهله 100ms إضافية للتأكد من رندرة الخطوط (Fonts)
            mainHandler.postDelayed(() -> {
                Log.i(TAG, "🎯 Visual Swap: WebView is live, removing ghost.");
                hideGhostOverlay();
                isResurrecting = false;
            }, 100);
        }
    }

    // ==========================================
    // 🔧 INTERNAL: هندسة الملفات الثنائية
    // ==========================================

    private static void saveBundleToDisk(Bundle bundle, File file) throws IOException {
        Parcel parcel = Parcel.obtain();
        bundle.writeToParcel(parcel, 0);
        byte[] bytes = parcel.marshall();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(bytes);
        } finally {
            parcel.recycle();
        }
    }

    private static Bundle loadBundleFromDisk(File file) throws IOException {
        byte[] bytes = new byte[(int) file.length()];
        try (FileInputStream fis = new FileInputStream(file)) {
            fis.read(bytes);
        }
        Parcel parcel = Parcel.obtain();
        parcel.unmarshall(bytes, 0, bytes.length);
        parcel.setDataPosition(0);
        Bundle bundle = new Bundle();
        bundle.readFromParcel(parcel);
        parcel.recycle();
        return bundle;
    }

    private static void saveMetadata(File dir, String url, int x, int y) throws IOException {
        File mFile = new File(dir, META_FILE);
        java.util.Properties p = new java.util.Properties();
        p.setProperty("url", url);
        p.setProperty("x", String.valueOf(x));
        p.setProperty("y", String.valueOf(y));
        p.setProperty("time", String.valueOf(System.currentTimeMillis()));
        try (FileOutputStream fos = new FileOutputStream(mFile)) {
            p.store(fos, null);
        }
    }

    private static Bitmap captureWebView(WebView webView) {
        try {
            Bitmap bitmap = Bitmap.createBitmap(webView.getWidth(), webView.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            webView.draw(canvas);
            return bitmap;
        } catch (Exception e) { return null; }
    }

    private static void showGhostOverlay(Activity activity, File file) {
        mainHandler.post(() -> {
            try {
                if (ghostOverlay == null) {
                    ghostOverlay = new ImageView(activity);
                    ghostOverlay.setScaleType(ImageView.ScaleType.FIT_XY);
                    ghostOverlay.setBackgroundColor(Color.WHITE);
                    ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
                    decor.addView(ghostOverlay, new ViewGroup.LayoutParams(-1, -1));
                }
                ghostOverlay.setImageURI(Uri.fromFile(file));
                ghostOverlay.setAlpha(1f);
                ghostOverlay.setVisibility(View.VISIBLE);
            } catch (Exception ignored) {}
        });
    }

    public static void hideGhostOverlay() {
        if (ghostOverlay != null && ghostOverlay.getVisibility() == View.VISIBLE) {
            ghostOverlay.animate().alpha(0f).setDuration(300)
                    .withEndAction(() -> ghostOverlay.setVisibility(View.GONE)).start();
        }
    }
                    }
