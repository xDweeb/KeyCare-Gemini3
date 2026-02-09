package com.keycare.ime;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class WelcomeFragment extends Fragment {

    public interface OnNextClickListener {
        void onNextClicked();
    }

    private OnNextClickListener listener;

    public void setOnNextClickListener(OnNextClickListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_welcome, container, false);
        
        view.findViewById(R.id.btnNext).setOnClickListener(v -> {
            if (listener != null) {
                listener.onNextClicked();
            }
        });

        return view;
    }
}
