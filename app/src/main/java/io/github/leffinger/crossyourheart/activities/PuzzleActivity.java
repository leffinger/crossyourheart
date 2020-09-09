package io.github.leffinger.crossyourheart.activities;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import io.github.leffinger.crossyourheart.R;
import io.github.leffinger.crossyourheart.io.AbstractPuzzleFile;
import io.github.leffinger.crossyourheart.io.PuzFile;

public class PuzzleActivity extends AppCompatActivity {

    private String mFilename;

    public static Intent newIntent(Context context, String filename) {
        Intent intent = new Intent(context, PuzzleActivity.class);
        intent.putExtra("filename", filename);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_container);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, PuzzleLoadingFragment.newInstance()).commitNow();

        mFilename = getIntent().getStringExtra("filename");
        new LoadPuzzleFileTask().execute();
    }

    private class LoadPuzzleFileTask extends AsyncTask<Void, Void, AbstractPuzzleFile> {

        @Override
        protected AbstractPuzzleFile doInBackground(Void... voids) {
            File file = new File(getFilesDir(), mFilename);
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