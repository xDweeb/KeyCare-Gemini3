package com.keycare.ime;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StatusActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "keycare_prefs";
    private static final String KEY_SERVER_URL = "server_url";
    // Use ApiConfig for consistency
    private static final String DEFAULT_SERVER_URL = ApiConfig.BASE_URL;

    private TextView statusEnabled, statusDefault, statusSubtitle, serverStatus;
    private EditText inputServerUrl;
    private Button btnEnableKeyboard, btnSetDefault, btnSaveServer, btnTestServer;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_status);

        initViews();
        loadServerUrl();
        setupClickListeners();
    }

    private void initViews() {
        statusEnabled = findViewById(R.id.statusEnabled);
        statusDefault = findViewById(R.id.statusDefault);
        statusSubtitle = findViewById(R.id.statusSubtitle);
        serverStatus = findViewById(R.id.serverStatus);
        inputServerUrl = findViewById(R.id.inputServerUrl);
        btnEnableKeyboard = findViewById(R.id.btnEnableKeyboard);
        btnSetDefault = findViewById(R.id.btnSetDefault);
        btnSaveServer = findViewById(R.id.btnSaveServer);
        btnTestServer = findViewById(R.id.btnTestServer);
    }

    private void loadServerUrl() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String serverUrl = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL);
        inputServerUrl.setText(serverUrl);
    }

    private void setupClickListeners() {
        btnEnableKeyboard.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
            startActivity(intent);
        });

        btnSetDefault.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showInputMethodPicker();
            }
        });

        btnSaveServer.setOnClickListener(v -> saveServerUrl());
        btnTestServer.setOnClickListener(v -> testServerConnection());
    }

    private void saveServerUrl() {
        String url = inputServerUrl.getText().toString().trim();
        if (url.isEmpty()) {
            Toast.makeText(this, "Please enter a server URL", Toast.LENGTH_SHORT).show();
            return;
        }

        // Remove trailing slash if present
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString(KEY_SERVER_URL, url).apply();

        Toast.makeText(this, "Server URL saved", Toast.LENGTH_SHORT).show();
        
        // Update the input field with cleaned URL
        inputServerUrl.setText(url);
    }

    private void testServerConnection() {
        String url = inputServerUrl.getText().toString().trim();
        if (url.isEmpty()) {
            Toast.makeText(this, "Please enter a server URL", Toast.LENGTH_SHORT).show();
            return;
        }

        // Remove trailing slash if present
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        serverStatus.setVisibility(android.view.View.VISIBLE);
        serverStatus.setText("Testing connection...");
        serverStatus.setTextColor(getResources().getColor(R.color.textMuted, null));

        String testUrl = url + "/health";
        
        executor.execute(() -> {
            try {
                URL healthUrl = new URL(testUrl);
                HttpURLConnection conn = (HttpURLConnection) healthUrl.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("GET");

                int responseCode = conn.getResponseCode();
                
                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(response.toString());
                    String status = json.optString("status", "unknown");

                    mainHandler.post(() -> {
                        serverStatus.setText("✓ Connected - Status: " + status);
                        serverStatus.setTextColor(getResources().getColor(R.color.primary, null));
                    });
                } else {
                    mainHandler.post(() -> {
                        serverStatus.setText("✗ Server returned: " + responseCode);
                        serverStatus.setTextColor(getResources().getColor(R.color.danger, null));
                    });
                }

                conn.disconnect();
            } catch (Exception e) {
                mainHandler.post(() -> {
                    serverStatus.setText("✗ Connection failed: " + e.getMessage());
                    serverStatus.setTextColor(getResources().getColor(R.color.danger, null));
                });
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateStatus() {
        boolean isEnabled = isKeyboardEnabled();
        boolean isDefault = isKeyboardDefault();

        // Update enabled chip
        if (isEnabled) {
            statusEnabled.setText(R.string.chip_done);
            statusEnabled.setBackgroundResource(R.drawable.chip_enabled);
        } else {
            statusEnabled.setText(R.string.chip_pending);
            statusEnabled.setBackgroundResource(R.drawable.chip_disabled);
        }

        // Update default chip
        if (isDefault) {
            statusDefault.setText(R.string.chip_done);
            statusDefault.setBackgroundResource(R.drawable.chip_enabled);
        } else {
            statusDefault.setText(R.string.chip_pending);
            statusDefault.setBackgroundResource(R.drawable.chip_disabled);
        }

        // Update subtitle
        if (isEnabled && isDefault) {
            statusSubtitle.setText(R.string.status_ready);
            statusSubtitle.setTextColor(getResources().getColor(R.color.primary, null));
        } else {
            statusSubtitle.setText(R.string.status_setup_needed);
            statusSubtitle.setTextColor(getResources().getColor(R.color.warning, null));
        }
    }

    private boolean isKeyboardEnabled() {
        String enabledIMEs = Settings.Secure.getString(
            getContentResolver(),
            Settings.Secure.ENABLED_INPUT_METHODS
        );
        return enabledIMEs != null && enabledIMEs.contains("com.keycare.ime");
    }

    private boolean isKeyboardDefault() {
        String defaultIME = Settings.Secure.getString(
            getContentResolver(),
            Settings.Secure.DEFAULT_INPUT_METHOD
        );
        return defaultIME != null && defaultIME.contains("com.keycare.ime");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
