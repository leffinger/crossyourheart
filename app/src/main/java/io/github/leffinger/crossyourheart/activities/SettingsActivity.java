package io.github.leffinger.crossyourheart.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;

import io.github.leffinger.crossyourheart.R;

public class SettingsActivity extends AppCompatActivity {
    private static final String KEY_PREFERENCES_RES_ID = "preferences";

    public static Intent newIntent(Context context, int key) {
        Intent intent = new Intent(context, SettingsActivity.class);
        intent.putExtra(KEY_PREFERENCES_RES_ID, key);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_container);
        if (savedInstanceState == null) {
            int preferences_res_id = getIntent().getIntExtra(KEY_PREFERENCES_RES_ID, 0);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, SettingsFragment.newInstance(preferences_res_id))
                    .commit();
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            onBackPressed();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        private static final String ARG_PREFERENCES_RES_ID = "PREFERENCES_RES_ID";

        public static SettingsFragment newInstance(int preferencesResId) {
            Bundle args = new Bundle();
            args.putInt(ARG_PREFERENCES_RES_ID, preferencesResId);
            SettingsFragment fragment = new SettingsFragment();
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            Bundle args = getArguments();
            int preferencesResId = args.getInt(ARG_PREFERENCES_RES_ID);
            setPreferencesFromResource(preferencesResId, rootKey);
        }
    }
}