package com.keycare.ime;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class EnableKeyboardFragment extends Fragment {

    public interface OnFinishClickListener {
        void onFinishClicked();
    }

    private OnFinishClickListener listener;
    private TextView chipStep1, chipStep2, chipStep3;
    private Button btnEnableKeyboard, btnSetDefault, btnFinish;

    public void setOnFinishClickListener(OnFinishClickListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_enable, container, false);
        
        chipStep1 = view.findViewById(R.id.chipStep1);
        chipStep2 = view.findViewById(R.id.chipStep2);
        chipStep3 = view.findViewById(R.id.chipStep3);
        btnEnableKeyboard = view.findViewById(R.id.btnEnableKeyboard);
        btnSetDefault = view.findViewById(R.id.btnSetDefault);
        btnFinish = view.findViewById(R.id.btnFinish);

        btnEnableKeyboard.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
            startActivity(intent);
        });

        btnSetDefault.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showInputMethodPicker();
            }
        });

        btnFinish.setOnClickListener(v -> {
            if (listener != null) {
                listener.onFinishClicked();
            }
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateStatus() {
        boolean isEnabled = isKeyboardEnabled();
        boolean isDefault = isKeyboardDefault();

        // Update Step 1 chip
        if (isEnabled) {
            chipStep1.setText(R.string.chip_done);
            chipStep1.setBackgroundResource(R.drawable.chip_enabled);
        } else {
            chipStep1.setText(R.string.chip_pending);
            chipStep1.setBackgroundResource(R.drawable.chip_disabled);
        }

        // Update Step 2 chip
        if (isDefault) {
            chipStep2.setText(R.string.chip_done);
            chipStep2.setBackgroundResource(R.drawable.chip_enabled);
        } else {
            chipStep2.setText(R.string.chip_pending);
            chipStep2.setBackgroundResource(R.drawable.chip_disabled);
        }

        // Update Step 3 chip and button visibility
        if (isEnabled && isDefault) {
            chipStep3.setText(R.string.chip_done);
            chipStep3.setBackgroundResource(R.drawable.chip_enabled);
            btnEnableKeyboard.setVisibility(View.GONE);
            btnSetDefault.setVisibility(View.GONE);
            btnFinish.setVisibility(View.VISIBLE);
        } else {
            chipStep3.setText(R.string.chip_pending);
            chipStep3.setBackgroundResource(R.drawable.chip_disabled);
            btnEnableKeyboard.setVisibility(View.VISIBLE);
            btnSetDefault.setVisibility(View.VISIBLE);
            btnFinish.setVisibility(View.GONE);
        }
    }

    private boolean isKeyboardEnabled() {
        String enabledIMEs = Settings.Secure.getString(
            requireContext().getContentResolver(),
            Settings.Secure.ENABLED_INPUT_METHODS
        );
        return enabledIMEs != null && enabledIMEs.contains("com.keycare.ime");
    }

    private boolean isKeyboardDefault() {
        String defaultIME = Settings.Secure.getString(
            requireContext().getContentResolver(),
            Settings.Secure.DEFAULT_INPUT_METHOD
        );
        return defaultIME != null && defaultIME.contains("com.keycare.ime");
    }
}
