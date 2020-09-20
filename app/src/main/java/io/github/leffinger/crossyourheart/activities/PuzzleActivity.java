package io.github.leffinger.crossyourheart.activities;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import io.github.leffinger.crossyourheart.R;
import io.github.leffinger.crossyourheart.io.AbstractPuzzleFile;
import io.github.leffinger.crossyourheart.io.IOUtil;
import io.github.leffinger.crossyourheart.io.PuzFile;

public class PuzzleActivity extends AppCompatActivity {

    private static final String TAG = "PuzzleActivity";
    private String mFilename;

    public static Intent newIntent(Context context, String filename) {
        Intent intent = new Intent(context, PuzzleActivity.class);
        intent.putExtra("filename", filename);
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
            mFilename = savedInstanceState.getString("filename");
        } else {
            mFilename = getIntent().getStringExtra("filename");
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
        outState.putString("filename", mFilename);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        Log.i(TAG, "onRestoreInstanceState");
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.i(TAG, "onRestart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.i(TAG, "onActivityResult");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");
    }

    private class LoadPuzzleFileTask extends AsyncTask<Void, Void, AbstractPuzzleFile> {

        @Override
        protected AbstractPuzzleFile doInBackground(Void... voids) {
            File file = IOUtil.getPuzzleFile(PuzzleActivity.this, mFilename);
            try (FileInputStream inputStream = new FileInputStream(file)) {
                return PuzFile.loadPuzFile(inputStream);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        protected void onPostExecute(AbstractPuzzleFile abstractPuzzleFile) {
            PuzzleFragment fragment = PuzzleFragment.newInstance(mFilename, abstractPuzzleFile);
            getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment)
                    .commitNow();
        }
    }


}