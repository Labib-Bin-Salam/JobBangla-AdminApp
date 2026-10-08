package bd.jobbangla.admin;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * In-app updater for the sideloaded Job Bangla Control APK.
 *
 * check()   asks GitHub for the latest release of JobBangla-AdminApp and compares its tag
 *           (v1.2) with this app's versionName.
 * install() downloads that release's .apk into the app cache and hands it to Android's
 *           package installer. Android always shows its own "Update" confirmation, and only
 *           accepts the file if it is signed with the same key as the installed app, so a
 *           tampered download simply fails to install.
 */
@CapacitorPlugin(name = "AppUpdater")
public class AppUpdaterPlugin extends Plugin {
    private static final String REPO = "Labib-Bin-Salam/JobBangla-AdminApp";
    private static final String LATEST_API = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final String DOWNLOAD_PREFIX = "https://github.com/" + REPO + "/releases/download/";

    private volatile boolean downloading = false;

    @PluginMethod
    public void check(final PluginCall call) {
        new Thread(() -> {
            try {
                PackageInfo info = getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0);
                String current = info.versionName == null ? "0" : info.versionName;

                JSONObject release = new JSONObject(readText(LATEST_API));
                String latest = release.optString("tag_name", "");
                String apkUrl = null;
                JSONArray assets = release.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        String url = asset.optString("browser_download_url", "");
                        if (asset.optString("name", "").endsWith(".apk") && url.startsWith(DOWNLOAD_PREFIX)) {
                            apkUrl = url;
                            break;
                        }
                    }
                }

                JSObject result = new JSObject();
                result.put("current", current);
                result.put("latest", latest.startsWith("v") ? latest.substring(1) : latest);
                result.put("notes", release.optString("body", ""));
                result.put("apkUrl", apkUrl == null ? "" : apkUrl);
                result.put("updateAvailable", apkUrl != null && compareVersions(latest, current) > 0);
                call.resolve(result);
            } catch (Exception e) {
                // Offline / rate limited: just report "nothing to update".
                JSObject result = new JSObject();
                result.put("updateAvailable", false);
                call.resolve(result);
            }
        }).start();
    }

    @PluginMethod
    public void install(final PluginCall call) {
        final String url = call.getString("url", "");
        if (url == null || !url.startsWith(DOWNLOAD_PREFIX) || !url.endsWith(".apk")) {
            call.reject("Not an official update file.");
            return;
        }

        // Android 8+: the user must allow this app to install packages once.
        if (!getContext().getPackageManager().canRequestPackageInstalls()) {
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + getContext().getPackageName()));
            settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(settings);
            JSObject result = new JSObject();
            result.put("status", "needsPermission");
            call.resolve(result);
            return;
        }

        if (downloading) {
            JSObject result = new JSObject();
            result.put("status", "busy");
            call.resolve(result);
            return;
        }
        downloading = true;

        new Thread(() -> {
            try {
                File dir = new File(getContext().getCacheDir(), "updates");
                if (!dir.exists() && !dir.mkdirs()) throw new Exception("Cannot create the download folder.");
                File apk = new File(dir, "JobBangla-Control-update.apk");
                if (apk.exists()) apk.delete();

                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setInstanceFollowRedirects(true);
                if (conn.getResponseCode() != 200) throw new Exception("Download failed (" + conn.getResponseCode() + ").");
                long total = conn.getContentLengthLong();

                try (InputStream in = new BufferedInputStream(conn.getInputStream());
                     FileOutputStream out = new FileOutputStream(apk)) {
                    byte[] buf = new byte[32 * 1024];
                    long done = 0;
                    int lastPercent = -1;
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        out.write(buf, 0, n);
                        done += n;
                        if (total > 0) {
                            int percent = (int) (done * 100 / total);
                            if (percent != lastPercent) {
                                lastPercent = percent;
                                JSObject progress = new JSObject();
                                progress.put("percent", percent);
                                notifyListeners("progress", progress);
                            }
                        }
                    }
                }
                if (apk.length() < 1_000_000) throw new Exception("The downloaded file looks incomplete.");

                final Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", apk);
                new Handler(Looper.getMainLooper()).post(() -> {
                    Intent open = new Intent(Intent.ACTION_VIEW);
                    open.setDataAndType(uri, "application/vnd.android.package-archive");
                    open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    getContext().startActivity(open);
                });
                JSObject result = new JSObject();
                result.put("status", "started");
                call.resolve(result);
            } catch (Exception e) {
                call.reject(e.getMessage() == null ? "Update failed." : e.getMessage());
            } finally {
                downloading = false;
            }
        }).start();
    }

    private static String readText(String address) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        if (conn.getResponseCode() != 200) throw new Exception("HTTP " + conn.getResponseCode());
        try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }

    /** Numeric dotted compare, tolerant of a leading "v" and trailing text: 1.10 > 1.9. */
    static int compareVersions(String a, String b) {
        int[] x = parts(a);
        int[] y = parts(b);
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int xi = i < x.length ? x[i] : 0;
            int yi = i < y.length ? y[i] : 0;
            if (xi != yi) return xi > yi ? 1 : -1;
        }
        return 0;
    }

    private static int[] parts(String v) {
        String[] raw = v.replaceAll("^[^0-9]*", "").split("[^0-9]+");
        int[] out = new int[raw.length];
        for (int i = 0; i < raw.length; i++) {
            try {
                out[i] = raw[i].isEmpty() ? 0 : Integer.parseInt(raw[i]);
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }
}
