package io.github.leffinger.crossyourheart.activities;

import android.graphics.Color;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import com.github.appintro.AppIntro;
import com.github.appintro.AppIntroFragment;

import org.jetbrains.annotations.Nullable;

import io.github.leffinger.crossyourheart.R;

public class AppIntroActivity extends AppIntro {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppIntroFragment slide1 = AppIntroFragment
                .newInstance("Welcome to Cross Your Heart!", "This is the tutorial",
                             R.drawable.ic_slide1, Color.WHITE, Color.BLACK, Color.BLACK);
        slide1.setBackgroundColor(Color.BLUE);
        addSlide(slide1);
    }

    @Override
    protected void onSkipPressed(@Nullable Fragment currentFragment) {
        super.onSkipPressed(currentFragment);
        finish();
    }

    @Override
    protected void onDonePressed(@Nullable Fragment currentFragment) {
        super.onDonePressed(currentFragment);
        finish();
    }
}