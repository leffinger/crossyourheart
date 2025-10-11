package io.github.leffinger.crossyourheart.activities;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

import io.github.leffinger.crossyourheart.R;

public class CrossYourHeartApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        String theme = preferences.getString(getString(R.string.preference_theme_list), "");
        int mode = theme.equals("themeLightMode") ? AppCompatDelegate.MODE_NIGHT_NO :
                theme.equals("themeDarkMode") ? AppCompatDelegate.MODE_NIGHT_YES :
                        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        AppCompatDelegate.setDefaultNightMode(mode);
    }
}
