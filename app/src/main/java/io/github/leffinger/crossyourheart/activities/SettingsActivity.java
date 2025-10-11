package io.github.leffinger.crossyourheart.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import java.util.Objects;

import io.github.leffinger.crossyourheart.R;

public class SettingsActivity extends BaseActivity {
    private static final String KEY_PREFERENCES_RES_ID = "preferences";

    private int mPreferencesResId;

    public static Intent newIntent(Context context, int key) {
        Intent intent = new Intent(context, SettingsActivity.class);
        intent.putExtra(KEY_PREFERENCES_RES_ID, key);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        if (savedInstanceState == null) {
            mPreferencesResId = getIntent().getIntExtra(KEY_PREFERENCES_RES_ID, 0);
        } else {
            mPreferencesResId = savedInstanceState.getInt(KEY_PREFERENCES_RES_ID);
        }

        getSupportFragmentManager().beginTransaction()
                                   .replace(R.id.container,
                                           SettingsFragment.newInstance(mPreferencesResId))
                                   .commit();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class SettingsFragment extends PreferenceFragmentCompat
            implements SharedPreferences.OnSharedPreferenceChangeListener {
        private static final String ARG_PREFERENCES_RES_ID = "PREFERENCES_RES_ID";

        public static SettingsFragment newInstance(int preferencesResId) {
            Bundle args = new Bundle();
            args.putInt(ARG_PREFERENCES_RES_ID, preferencesResId);
            SettingsFragment fragment = new SettingsFragment();
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void onResume() {
            super.onResume();
            Context context = getContext();
            if (context != null) {
                PreferenceManager.getDefaultSharedPreferences(context)
                                 .registerOnSharedPreferenceChangeListener(this);
            }
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            Bundle args = getArguments();
            if (args != null) {
                int preferencesResId = args.getInt(ARG_PREFERENCES_RES_ID);
                if (preferencesResId > 0) {
                    setPreferencesFromResource(preferencesResId, rootKey);
                }
            }
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                              @Nullable String key) {
            if (Objects.equals(key, getString(R.string.preference_theme_list))) {
                String newValue = sharedPreferences.getString(key, "themeSystemDefault");
                switch (newValue) {
                    case "themeLightMode": {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                        break;
                    }
                    case "themeDarkMode": {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                        break;
                    }
                    default: {
                        AppCompatDelegate.setDefaultNightMode(
                                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                        break;
                    }
                }
                requireActivity().recreate();
            }
        }

        @Override
        public void onPause() {
            super.onPause();
            Context context = getContext();
            if (context != null) {
                PreferenceManager.getDefaultSharedPreferences(context)
                                 .unregisterOnSharedPreferenceChangeListener(this);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(KEY_PREFERENCES_RES_ID, mPreferencesResId);
    }
}