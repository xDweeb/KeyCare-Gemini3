package com.keycare.ime;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SetupActivity extends AppCompatActivity {

    private static final String BASE_URL = "https://keycare-gemini3-api-2587283546dc.herokuapp.com";

    private Button btnEnableKeyboard;
    private Button btnSetDefault;
    private Button btnCheckConnection;
    private View connectionIndicator;
    private TextView connectionStatus;
    private TextView serverUrl;

    private ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        btnEnableKeyboard = findViewById(R.id.btnEnableKeyboard);
        btnSetDefault = findViewById(R.id.btnSetDefault);
        btnCheckConnection = findViewById(R.id.btnCheckConnection);
        connectionIndicator = findViewById(R.id.connectionIndicator);
        connectionStatus = findViewById(R.id.connectionStatus);
        serverUrl = findViewById(R.id.serverUrl);

        serverUrl.setText("Server: " + BASE_URL);

        // Enable keyboard in system settings
        btnEnableKeyboard.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
            startActivity(intent);
        });

        // Set as default keyboard
        btnSetDefault.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showInputMethodPicker();
            }
        });

        // Test connection
        btnCheckConnection.setOnClickListener(v -> {
            connectionStatus.setText("Checking...");
            connectionIndicator.setBackgroundResource(R.drawable.badge_offensive);
            checkConnection();
        });

        // Auto-check on start
        checkConnection();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-check connection when returning to app
        checkConnection();
    }

    private void checkConnection() {
        executor.execute(() -> {
            boolean connected = false;
            try {
                URL url = new URL(BASE_URL + "/health");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);

                int responseCode = conn.getResponseCode();
                connected = (responseCode == 200);
                conn.disconnect();
            } catch (Exception e) {
                connected = false;
            }

            final boolean isConnected = connected;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (isConnected) {
                    connectionIndicator.setBackgroundResource(R.drawable.badge_safe);
                    connectionStatus.setText("Connected to server");
                } else {
                    connectionIndicator.setBackgroundResource(R.drawable.badge_dangerous);
                    connectionStatus.setText("Cannot reach server");
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
