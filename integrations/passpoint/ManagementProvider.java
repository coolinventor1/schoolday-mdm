package one.schoolday.passpoint.prototype;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.content.pm.PackageManager;
import java.security.MessageDigest;

/** Only the installed, SchoolDay-signed management agent can read health or request restart. */
public final class ManagementProvider extends ContentProvider {
    static volatile long uiHeartbeatAt, backendCheckedAt;
    static volatile boolean foreground, backendConnected;
    static volatile String lastError = "";
    static final long processStartedAt = System.currentTimeMillis();
    @Override public boolean onCreate() { return true; }
    private void authorize() {
        try {
            String caller = getCallingPackage();
            if (!"com.mdmesh.agent".equals(caller)) throw new SecurityException();
            android.content.pm.PackageInfo info = getContext().getPackageManager().getPackageInfo(caller, PackageManager.GET_SIGNING_CERTIFICATES);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(info.signingInfo.getApkContentsSigners()[0].toByteArray());
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b & 255));
            if (!"e02f616255efa50940dfa622bc41220b5bad079d491e576ae5fce8e08a4893e5".equals(hex.toString())) throw new SecurityException();
        } catch (Exception e) { throw new SecurityException("SchoolDay agent signature required"); }
    }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        authorize();
        Bundle out = new Bundle();
        if ("prepareRestart".equals(method)) {
            out.putBoolean("accepted", true);
            new Thread(() -> { try { Thread.sleep(300); } catch (InterruptedException ignored) {} android.os.Process.killProcess(android.os.Process.myPid()); }, "managed-restart").start();
            return out;
        }
        if (!"health".equals(method)) throw new IllegalArgumentException("Unknown management operation");
        out.putBoolean("responsive", uiHeartbeatAt > 0 && System.currentTimeMillis() - uiHeartbeatAt < 45000);
        out.putBoolean("foreground", foreground);
        out.putLong("uiHeartbeatAt", uiHeartbeatAt);
        out.putLong("backendCheckedAt", backendCheckedAt);
        out.putBoolean("backendConnected", backendConnected && System.currentTimeMillis() - backendCheckedAt < 120000);
        out.putString("lastError", lastError);
        out.putLong("processStartedAt", processStartedAt);
        if (Build.VERSION.SDK_INT >= 30) {
            org.json.JSONArray exits = new org.json.JSONArray();
            android.app.ActivityManager am = (android.app.ActivityManager)getContext().getSystemService(Context.ACTIVITY_SERVICE);
            for (android.app.ApplicationExitInfo info : am.getHistoricalProcessExitReasons(null, 0, 5)) {
                org.json.JSONObject item = new org.json.JSONObject();
                try { item.put("reason", info.getReason()); item.put("at", info.getTimestamp()); exits.put(item); } catch (Exception ignored) {}
            }
            out.putString("recentExits", exits.toString());
        }
        return out;
    }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { throw new SecurityException(); }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { throw new SecurityException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new SecurityException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new SecurityException(); }
}
