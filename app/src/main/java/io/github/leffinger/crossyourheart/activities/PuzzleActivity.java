package io.github.leffinger.crossyourheart.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import io.github.leffinger.crossyourheart.R;

public class PuzzleActivity extends AppCompatActivity {

    public static Intent newIntent(Context context, String filename) {
        Intent intent = new Intent(context, PuzzleActivity.class);
        intent.putExtra("filename", filename);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_container);

        String filename = getIntent().getStringExtra("filename");

        PuzzleFragment fragment = PuzzleFragment.newInstance(filename);
        getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment)
                .commitNow();
    }
}