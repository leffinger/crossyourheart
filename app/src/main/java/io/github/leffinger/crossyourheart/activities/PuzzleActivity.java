package io.github.leffinger.crossyourheart.activities;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import io.github.leffinger.crossyourheart.R;
import io.github.leffinger.crossyourheart.io.IOUtil;
import io.github.leffinger.crossyourheart.io.PuzFile;

public class PuzzleActivity extends AppCompatActivity {
    private static final String TAG = "PuzzleActivity";
    public static final String KEY_FILENAME = "filename";
    private String mFilename;

    public static Intent newIntent(Context context, String filename) {
        Intent intent = new Intent(context, PuzzleActivity.class);
        intent.putExtra(KEY_FILENAME, filename);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        setContentView(R.layout.activity_container);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, PuzzleLoadingFragment.newInstance()).commitNow();

        if (savedInstanceState != null) {
            mFilename = savedInstanceState.getString(KEY_FILENAME);
        } else {
            mFilename = getIntent().getStringExtra(KEY_FILENAME);
        }
        if (mFilename == null) {
            finish();
            return;
        }
        new LoadPuzzleFileTask().execute();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.i(TAG, "onSaveInstanceState");
        outState.putString(KEY_FILENAME, mFilename);
    }

    private class LoadPuzzleFileTask extends AsyncTask<Void, Void, PuzzleFragment> {

        @Override
        protected PuzzleFragment doInBackground(Void... voids) {
            File file = IOUtil.getPuzzleFile(PuzzleActivity.this, mFilename);
            try (FileInputStream inputStream = new FileInputStream(file)) {
                PuzFile puzFile = PuzFile.loadPuzFile(inputStream);
                return PuzzleFragment.newInstance(mFilename, puzFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        protected void onPostExecute(PuzzleFragment fragment) {
            getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment)
                    .commitNow();
        }
    }


}