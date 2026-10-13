package bd.jobbangla.admin;

import android.os.Bundle;
import android.view.View;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // In-app updater (see AppUpdaterPlugin): a local plugin, so it must be registered by hand.
        registerPlugin(AppUpdaterPlugin.class);
        super.onCreate(savedInstanceState);

        // Edge-to-edge (Android 15) no longer resizes the window for the keyboard, so the chat box was
        // hidden behind it. Pad the WebView's parent by the keyboard height (minus the nav bar, which
        // Capacitor already applies as a margin) so the page shrinks and the focused field stays visible.
        View parent = (View) getBridge().getWebView().getParent();
        ViewCompat.setOnApplyWindowInsetsListener(parent, (v, insets) -> {
            int ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            int nav = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            v.setPadding(0, 0, 0, Math.max(0, ime - nav));
            return insets;
        });
    }
}
