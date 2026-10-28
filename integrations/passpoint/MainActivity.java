package one.schoolday.passpoint.prototype;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.*;
import java.io.*;
import java.util.Map;

/** Bundled kiosk UI with an exact-origin API allowlist. No JavaScript-native bridge. */
public final class MainActivity extends Activity {
    private WebView web;
    private final android.os.Handler healthHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable healthTick = new Runnable() {
        @Override public void run() {
            if (web != null) web.evaluateJavascript("JSON.stringify(window.PassPointAPI?.managementHealth?.() || {})", value -> {
                ManagementProvider.uiHeartbeatAt = System.currentTimeMillis();
                try {
                    String decoded = (String)new org.json.JSONTokener(value).nextValue();
                    org.json.JSONObject state = new org.json.JSONObject(decoded);
                    ManagementProvider.backendCheckedAt = state.optLong("checkedAt", 0);
                    ManagementProvider.backendConnected = state.optBoolean("connected", false);
                    ManagementProvider.lastError = state.optString("error", "");
                } catch (Exception ignored) { ManagementProvider.lastError = "Health response unavailable"; }
            });
            healthHandler.postDelayed(this, 15000);
        }
    };
    private KioskController kiosk;
    private int maintenanceTaps;
    private long lastMaintenanceTap;
    private static final String ORIGIN = "https://schoolday.one/passpoint-tablet/";
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        kiosk = new KioskController(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        web = new WebView(this);
        web.setBackgroundColor(android.graphics.Color.WHITE);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.getSettings().setMediaPlaybackRequiresUserGesture(false);
        web.getSettings().setTextZoom(100);
        WebView.setWebContentsDebuggingEnabled(false);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !request.getUrl().toString().equals(ORIGIN + "index.html");
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (allowedApi(request)) return null;
                if (!url.startsWith(ORIGIN) || !request.getMethod().equals("GET")) return missing();
                String path = request.getUrl().getPath().substring("/passpoint-tablet/".length());
                if (path.contains("..") || path.contains("\\")) return missing();
                String extension = path.substring(path.lastIndexOf('.') + 1);
                String mime = switch(extension) {
                    case "html" -> "text/html"; case "css" -> "text/css";
                    case "js" -> "application/javascript"; case "svg" -> "image/svg+xml";
                    case "png" -> "image/png"; case "ttf" -> "font/ttf";
                    case "mp4" -> "video/mp4"; default -> "application/octet-stream";
                };
                try { return new WebResourceResponse(mime, null, 200, "OK", Map.of("Cache-Control", "no-store", "Content-Security-Policy", "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; connect-src 'self'; img-src 'self'; media-src 'self'; font-src 'self'; object-src 'none'; frame-src 'none'; base-uri 'none'; form-action 'none'"), getAssets().open("web/" + path)); }
                catch (IOException e) { return missing(); }
            }
        });
        setContentView(web);
        web.loadUrl(ORIGIN + "index.html");
        healthHandler.post(healthTick);
    }
    private static boolean allowedApi(WebResourceRequest request) {
        android.net.Uri uri = request.getUrl();
        if (!"https".equals(uri.getScheme()) || !"schoolday.one".equals(uri.getHost()) || uri.getPort() != -1 || uri.getUserInfo() != null) return false;
        String path = uri.getPath(), method = request.getMethod();
        if ("GET".equals(method)) return path.equals("/v1/portal/kiosk/classes") || path.matches("/v1/kiosk/enrollments/[a-f0-9]{32}");
        if (!"POST".equals(method)) return false;
        return path.equals("/v1/kiosk/device/session") || path.equals("/v1/kiosk/enrollments")
            || path.matches("/v1/kiosk/enrollments/[a-f0-9]{32}/claim")
            || path.matches("/v1/portal/kiosk/tablet/(student|faculty)")
            || path.matches("/v1/portal/teacher/classes/[a-zA-Z0-9_-]+/(attendance/check-in|hall-passes|hall-passes/[a-f0-9]{32}/return)");
    }
    private static WebResourceResponse missing() {
        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", Map.of(), new ByteArrayInputStream(new byte[0]));
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused && android.os.Build.VERSION.SDK_INT >= 30) {
            getWindow().getInsetsController().hide(WindowInsets.Type.systemBars());
            getWindow().getInsetsController().setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }
    @Override public void onBackPressed() {
        web.evaluateJavascript("document.documentElement.dataset.view", value -> {
            if ("\"home\"".equals(value)) { if (!kiosk.enabled()) finish(); }
            else web.evaluateJavascript("document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}))", null);
        });
    }
    @Override protected void onPause() { ManagementProvider.foreground = false; CookieManager.getInstance().flush(); web.onPause(); super.onPause(); }
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
            float zone = 64 * getResources().getDisplayMetrics().density;
            if (event.getX() > getWindow().getDecorView().getWidth() - zone && event.getY() < zone) {
                long now = android.os.SystemClock.elapsedRealtime();
                maintenanceTaps = now - lastMaintenanceTap < 1000 ? maintenanceTaps + 1 : 1;
                lastMaintenanceTap = now;
                if (maintenanceTaps >= 7) { maintenanceTaps = 0; kiosk.requestMaintenance(); }
            } else maintenanceTaps = 0;
        }
        return super.dispatchTouchEvent(event);
    }
    @Override protected void onResume() { ManagementProvider.foreground = true; super.onResume(); if(web != null) web.onResume(); if(kiosk != null) kiosk.resume(); }
    @Override protected void onDestroy() { healthHandler.removeCallbacks(healthTick); web.destroy(); super.onDestroy(); }
}
