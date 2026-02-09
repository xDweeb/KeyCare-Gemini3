package com.keycare.ime;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

public class OnboardingActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "keycare_prefs";
    private static final String KEY_ONBOARDING_DONE = "onboarding_done";

    private ViewPager2 viewPager;
    private WelcomeFragment welcomeFragment;
    private CommunicateFragment communicateFragment;
    private EnableKeyboardFragment enableKeyboardFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check if onboarding is already completed
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_ONBOARDING_DONE, false)) {
            launchStatusActivity();
            return;
        }

        setContentView(R.layout.activity_onboarding);

        viewPager = findViewById(R.id.viewPager);
        viewPager.setUserInputEnabled(false); // Disable swipe, use buttons only

        setupFragments();
        setupViewPager();
    }

    private void setupFragments() {
        welcomeFragment = new WelcomeFragment();
        welcomeFragment.setOnNextClickListener(() -> viewPager.setCurrentItem(1, true));

        communicateFragment = new CommunicateFragment();
        communicateFragment.setOnNextClickListener(() -> viewPager.setCurrentItem(2, true));

        enableKeyboardFragment = new EnableKeyboardFragment();
        enableKeyboardFragment.setOnFinishClickListener(this::finishOnboarding);
    }

    private void setupViewPager() {
        viewPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                switch (position) {
                    case 0:
                        return welcomeFragment;
                    case 1:
                        return communicateFragment;
                    case 2:
                        return enableKeyboardFragment;
                    default:
                        return welcomeFragment;
                }
            }

            @Override
            public int getItemCount() {
                return 3;
            }
        });
    }

    private void finishOnboarding() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply();
        launchStatusActivity();
    }

    private void launchStatusActivity() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        if (viewPager.getCurrentItem() > 0) {
            viewPager.setCurrentItem(viewPager.getCurrentItem() - 1, true);
        } else {
            super.onBackPressed();
        }
    }
}
