package io.github.leffinger.crossyourheart.activities;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.preference.PreferenceManager;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import io.github.leffinger.crossyourheart.R;
import io.github.leffinger.crossyourheart.databinding.AlertProgressBinding;
import io.github.leffinger.crossyourheart.io.IOUtil;
import io.github.leffinger.crossyourheart.io.PuzFile;
import io.github.leffinger.crossyourheart.room.Database;
import io.github.leffinger.crossyourheart.room.PuzFileMetadata;
import io.github.leffinger.crossyourheart.room.Puzzle;

import static java.util.Objects.requireNonNull;

/**
 * Base activity that handles implicit intents and displays the puzzle list if no intent is
 * specified.
 */
public class MainActivity extends AppCompatActivity implements PuzzleListFragment.Callbacks {
    public static final String TAG = "MainActivity";
    public static final String ARG_PUZZLE = "puzzle";
    private static final SimpleDateFormat FORMAT =
            new SimpleDateFormat("yyMMddHHmmss", Locale.getDefault());
    private Puzzle mPuzzle;
    private Database mDatabase;

    private static String findDuplicate(Database database, PuzFile puzzleLoader) {
        List<Puzzle> matchingPuzzles = database.puzzleDao()
                .getMatchingPuzFiles(puzzleLoader.getTitle(), puzzleLoader.getAuthor(),
                                     puzzleLoader.getHeaderChecksum());
        if (matchingPuzzles.isEmpty()) {
            return null;
        }
        return matchingPuzzles.get(0).filename;
    }


    private static Puzzle loadUri(Context context, Database database,
                                  Uri uri) throws DuplicateFileException, IOException {
        try (InputStream inputStream = requireNonNull(
                context.getContentResolver().openInputStream(uri))) {
            return loadInputStream(context, database, inputStream);
        } catch (IOException e) {
            throw new IOException("Failed to open puzzle file", e);
        }
    }

    private static Puzzle loadInputStream(Context context, Database database,
                                          InputStream inputStream) throws DuplicateFileException,
            IOException {
        try {
            PuzFile puzzleLoader = PuzFile.verifyPuzFile(inputStream);

            // Check to see if we have already loaded this file.
            String duplicateFilename = findDuplicate(database, puzzleLoader);
            if (duplicateFilename != null) {
                throw new DuplicateFileException(duplicateFilename);
            }

            String date = FORMAT.format(Calendar.getInstance().getTime());
            String filename = String.format("%s-%s.puz", date, UUID.randomUUID());
            try (FileOutputStream outputStream = new FileOutputStream(
                    IOUtil.getPuzzleFile(context, filename))) {
                puzzleLoader.savePuzzleFile(outputStream);
                Puzzle puzzle =
                        new Puzzle(filename, puzzleLoader.getTitle(), puzzleLoader.getAuthor(),
                                   puzzleLoader.getCopyright(), puzzleLoader.isSolved(), false,
                                   !puzzleLoader.isEmpty(), puzzleLoader.getScrambleState());
                database.puzzleDao().insert(puzzle);
                database.puzFileMetadataDao()
                        .insert(new PuzFileMetadata(filename, puzzleLoader.getHeaderChecksum()));
                return puzzle;
            } catch (IOException e) {
                throw new IOException("Failed to save puzzle file", e);
            }
        } catch (IOException e) {
            throw new IOException("Failed to parse puzzle file", e);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_container);

        if (IOUtil.getPuzzleDir(this).mkdir()) {
            Log.i(TAG, "Created puzzle dir: " + IOUtil.getPuzzleDir(this));
        }

        // Create or load database.
        mDatabase = Database.getInstance(getApplicationContext());

        // Perform first-start-up tasks.
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (!preferences.getBoolean(getString(R.string.preference_intro_puzzle_copied), false)) {
            AsyncTask.execute(() -> {
                try {
                    loadInputStream(MainActivity.this, mDatabase,
                                    getAssets().open("intro_puzzle.puz"));
                } catch (Exception e) {
                    Log.w(TAG, "Failed to load intro puzzle", e);
                }
            });
            preferences.edit().putBoolean(getString(R.string.preference_intro_puzzle_copied), true)
                    .apply();
        }

        if (preferences.getBoolean(getString(R.string.preference_start_tutorial_on_open), true)) {
            // Show tutorial the first time the app is opened.
            startActivity(new Intent(this, TutorialActivity.class));
            preferences.edit()
                    .putBoolean(getString(R.string.preference_start_tutorial_on_open), false)
                    .apply();
        } else if (savedInstanceState != null && savedInstanceState.containsKey(ARG_PUZZLE)) {
            mPuzzle = (Puzzle) savedInstanceState.getSerializable(ARG_PUZZLE);
            startActivity(PuzzleActivity.newIntent(this, mPuzzle));
        } else if (getIntent() == null || getIntent().getData() == null) {
            // No implicit intent; start list activity to select file.
            PuzzleListFragment fragment = PuzzleListFragment.newInstance();
            getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment)
                    .commitNow();
        } else {
            // Load the external file specified by the implicit intent.
            Uri uri = getIntent().getData();
            onMultipleUrisSelected(Collections.singletonList(uri));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (mPuzzle != null) {
            outState.putSerializable(ARG_PUZZLE, mPuzzle);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onResume() {
        super.onResume();

        // Display list of puzzles.
        PuzzleListFragment fragment = PuzzleListFragment.newInstance();
        getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment)
                .commitNow();
    }

    @Override
    public void onPuzzleSelected(Puzzle puzzle) {
        mPuzzle = puzzle;
        startActivity(PuzzleActivity.newIntent(MainActivity.this, mPuzzle));
    }

    @Override
    public void onUriSelected(Uri uri) {
        onMultipleUrisSelected(Collections.singletonList(uri));
    }

    @Override
    public void onMultipleUrisSelected(List<Uri> uris) {
        new LoadMultipleUrisTask(this, mDatabase, uris).execute();
    }

    @Override
    public void onDownloadSelected() {
        startActivity(DownloadPuzzlesActivity.newIntent(this));
    }

    private static class LoadMultipleUrisTask extends AsyncTask<Void, Integer, Void> {
        private final WeakReference<MainActivity> mActivity;
        private final Database mDatabase;
        private final List<Uri> mUris;

        /**
         * List of successfully loaded puzzles.
         */
        private final List<Puzzle> mPuzzles;

        private AlertDialog mAlertDialog;
        private AlertProgressBinding mAlertProgressBinding;

        private int mDupeCount;
        private int mFailCount;
        private int mSuccessCount;

        public LoadMultipleUrisTask(MainActivity mainActivity, Database database, List<Uri> uris) {
            mActivity = new WeakReference<>(mainActivity);
            mDatabase = database;
            mUris = uris;
            mPuzzles = new ArrayList<>();
            mDupeCount = 0;
            mFailCount = 0;
            mSuccessCount = 0;
        }

        @Override
        protected void onPreExecute() {
            Context context = mActivity.get();
            if (context == null) {
                return;
            }
            mAlertProgressBinding = DataBindingUtil
                    .inflate(LayoutInflater.from(context), R.layout.alert_progress, null, false);
            mAlertDialog = new AlertDialog.Builder(context).setView(mAlertProgressBinding.getRoot())
                    .setCancelable(false).setTitle("Loading files...").show();
            mAlertProgressBinding.progressBar.setMax(mUris.size());
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            mAlertProgressBinding.progressBar.setProgress(values[0]);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            MainActivity activity = mActivity.get();
            if (activity == null) {
                return null;
            }
            for (int i = 0; i < mUris.size(); i++) {
                try {
                    Puzzle puzzle = loadUri(activity, mDatabase, mUris.get(i));
                    Log.i(TAG, "Successfully loaded " + puzzle.filename);
                    mSuccessCount++;
                    mPuzzles.add(puzzle);
                } catch (DuplicateFileException e) {
                    Log.i(TAG, "Duplicate file");
                    mDupeCount++;
                } catch (IOException e) {
                    Log.e(TAG, "Failed to load puzzle file", e);
                    mFailCount++;
                }
                publishProgress(i + 1);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            if (mAlertDialog != null) {
                mAlertDialog.dismiss();
            }

            MainActivity activity = mActivity.get();
            if (activity == null) {
                return;
            }
            if (mUris.size() == 1 && mSuccessCount == 1) {
                activity.onPuzzleSelected(mPuzzles.get(0));
            } else {
                Toast.makeText(activity,
                               activity.getString(R.string.multiple_uris_result, mUris.size(),
                                                  mSuccessCount, mDupeCount, mFailCount),
                               Toast.LENGTH_LONG).show();
            }
        }
    }
}
