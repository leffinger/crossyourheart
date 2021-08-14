package io.github.leffinger.crossyourheart.activities;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import io.github.leffinger.crossyourheart.R;
import io.github.leffinger.crossyourheart.io.IOUtil;
import io.github.leffinger.crossyourheart.io.PuzFile;
import io.github.leffinger.crossyourheart.room.Puzzle;

public class PuzzleActivity extends AppCompatActivity {
    private static final String TAG = "PuzzleActivity";
    public static final String KEY_PUZZLE = "puzzle";
    private Puzzle mPuzzle;

    public static Intent newIntent(Context context, Puzzle puzzle) {
        Intent intent = new Intent(context, PuzzleActivity.class);
        intent.putExtra(KEY_PUZZLE, puzzle);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_container);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, PuzzleLoadingFragment.newInstance()).commitNow();

        if (savedInstanceState != null) {
            mPuzzle = (Puzzle) savedInstanceState.getSerializable(KEY_PUZZLE);
        } else {
            mPuzzle = (Puzzle) getIntent().getSerializableExtra(KEY_PUZZLE);
        }
        if (mPuzzle == null) {
            finish();
            return;
        }
        new LoadPuzzleFileTask().execute();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(KEY_PUZZLE, mPuzzle);
    }

    private class LoadPuzzleFileTask extends AsyncTask<Void, Void, PuzzleFragment> {

        @Override
        protected PuzzleFragment doInBackground(Void... voids) {
            File file = IOUtil.getPuzzleFile(PuzzleActivity.this, mPuzzle.filename);
            try (FileInputStream inputStream = new FileInputStream(file)) {
                PuzFile puzFile = PuzFile.loadPuzFile(inputStream);
                return PuzzleFragment.newInstance(mPuzzle.filename, puzFile, mPuzzle.usePencil);
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