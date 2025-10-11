package io.github.leffinger.crossyourheart.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import io.github.leffinger.crossyourheart.R;

public class DownloadPuzzlesActivity extends BaseActivity {

    public static Intent newIntent(Context context) {
        Intent intent = new Intent(context, DownloadPuzzlesActivity.class);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, DownloadPuzzlesFragment.newInstance()).commitNow();
    }
}