package io.github.leffinger.crossyourheart.activities;

import static java.util.Objects.requireNonNull;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executors;

import io.github.leffinger.crossyourheart.R;
import io.github.leffinger.crossyourheart.io.IOUtil;
import io.github.leffinger.crossyourheart.io.PuzFile;
import io.github.leffinger.crossyourheart.room.Database;
import io.github.leffinger.crossyourheart.room.PuzFileMetadata;
import io.github.leffinger.crossyourheart.room.Puzzle;
import io.github.leffinger.crossyourheart.room.PuzzleDao;
import io.github.leffinger.crossyourheart.viewmodels.PuzzleInfoViewModel;

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

    private static Puzzle findDuplicate(Database database, PuzFile puzzleLoader) {
        List<Puzzle> matchingPuzzles = database.puzzleDao()
                                               .getMatchingPuzFiles(puzzleLoader.getTitle(),
                                                       puzzleLoader.getAuthor(),
                                                       puzzleLoader.getHeaderChecksum());
        if (matchingPuzzles.isEmpty()) {
            return null;
        }
        return matchingPuzzles.get(0);
    }

    private static Puzzle loadUri(Context context, Database database, Uri uri)
            throws DuplicateFileException, IOException {
        try (InputStream inputStream = requireNonNull(
                context.getContentResolver().openInputStream(uri))) {
            return loadInputStream(context, database, inputStream);
        } catch (IOException e) {
            throw new IOException("Failed to open puzzle file", e);
        }
    }

    private static Puzzle loadInputStream(Context context, Database database,
                                          InputStream inputStream)
            throws DuplicateFileException, IOException {
        try {
            PuzFile puzzleLoader = PuzFile.verifyPuzFile(inputStream);

            // Check to see if we have already loaded this file.
            Puzzle duplicatePuzzle = findDuplicate(database, puzzleLoader);
            if (duplicatePuzzle != null) {
                throw new DuplicateFileException(duplicatePuzzle);
            }

            String date = FORMAT.format(Calendar.getInstance().getTime());
            String filename = String.format("%s-%s.puz", date, UUID.randomUUID());
            File puzzleFile = IOUtil.getPuzzleFile(context, filename);
            try {
                puzzleLoader.savePuzzleFile(puzzleFile);
                Puzzle puzzle =
                        new Puzzle(filename, puzzleLoader.getTitle(), puzzleLoader.getAuthor(),
                                puzzleLoader.getCopyright(), puzzleLoader.isSolved(), false,
                                !puzzleLoader.isEmpty(), puzzleLoader.getScrambleState(), false);
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
        SplashScreen.installSplashScreen(this);
        setContentView(R.layout.activity_container);

        if (IOUtil.getPuzzleDir(this).mkdir()) {
            Log.i(TAG, "Created puzzle dir: " + IOUtil.getPuzzleDir(this));
        }

        // Create or load database.
        mDatabase = Database.getInstance(getApplicationContext());

        // Perform first-start-up tasks.
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (!preferences.getBoolean(getString(R.string.preference_intro_puzzle_copied), false)) {
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    loadInputStream(MainActivity.this, mDatabase,
                            getAssets().open("intro_puzzle.puz"));
                } catch (Exception e) {
                    Log.w(TAG, "Failed to load intro puzzle", e);
                }
            });
            preferences.edit()
                       .putBoolean(getString(R.string.preference_intro_puzzle_copied), true)
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
            PuzzleListFragment fragment = PuzzleListFragment.newInstance();
            getSupportFragmentManager().beginTransaction()
                                       .replace(R.id.container, fragment)
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
    protected void onDestroy() {
        super.onDestroy();
        mDatabase.close();
    }

    @Override
    protected void onResume() {
        super.onResume();
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (fragmentManager.findFragmentById(R.id.container) == null) {
            PuzzleListFragment fragment = PuzzleListFragment.newInstance();
            fragmentManager.beginTransaction().replace(R.id.container, fragment).commitNow();
        }
    }

    @Override
    public void onPuzzleSelected(Puzzle puzzle) {
        mPuzzle = puzzle;

        if (mPuzzle.opened) {
            startActivity(PuzzleActivity.newIntent(MainActivity.this, mPuzzle));
            return;
        }

        String openInDownsOnlyMode = PreferenceManager.getDefaultSharedPreferences(this)
                                                      .getString(getString(
                                                                      R.string.preference_start_in_downs_only_mode_list),
                                                              "downsOnlyModeNever");

        if (openInDownsOnlyMode.equals("downsOnlyModeAlways")) {
            updatePuzzleAndStart(true);
        } else if (openInDownsOnlyMode.equals("downsOnlyModeAsk")) {
            AlertDialog dialog =
                    new AlertDialog.Builder(this).setTitle("Open puzzle in downs-only mode?")
                                                 .setPositiveButton(R.string.no,
                                                         (dialogInterface, i) -> updatePuzzleAndStart(
                                                                 false))
                                                 .setNegativeButton(R.string.yes,
                                                         (dialogInterface, i) -> updatePuzzleAndStart(
                                                                 true))
                                                 .setNeutralButton("Show Puzzle Info", null)
                                                 .setCancelable(true)
                                                 .create();
            // Don't dismiss the dialog when "Show Puzzle Info" is pressed.
            dialog.setOnShowListener(dialogInterface -> dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                                                              .setOnClickListener(view -> {
                                                                  PuzzleInfoViewModel
                                                                          puzzleInfoViewModel =
                                                                          new PuzzleInfoViewModel(
                                                                                  mPuzzle.title,
                                                                                  mPuzzle.author,
                                                                                  mPuzzle.copyright);
                                                                  PuzzleInfoFragment.newInstance(
                                                                                            puzzleInfoViewModel)
                                                                                    .show(getSupportFragmentManager(),
                                                                                            "PuzzleInfo");
                                                              }));
            dialog.show();
        } else {
            updatePuzzleAndStart(false);
        }
    }

    private void updatePuzzleAndStart(boolean downsOnlyMode) {
        Executors.newSingleThreadExecutor()
                 .execute(() -> mDatabase.puzzleDao()
                                         .updateDownsOnlyMode(
                                                 new PuzzleDao.DownsOnlyModeUpdate(mPuzzle.filename,
                                                         downsOnlyMode)));
        mPuzzle.downsOnlyMode = downsOnlyMode;
        startActivity(PuzzleActivity.newIntent(MainActivity.this, mPuzzle));
    }

    @Override
    public void onUriSelected(Uri uri) {
        onMultipleUrisSelected(Collections.singletonList(uri));
    }

    @Override
    public void onMultipleUrisSelected(List<Uri> uris) {
        Handler handler = new Handler(Looper.getMainLooper());
        Executors.newSingleThreadExecutor().execute(() -> {
            int successCount = 0;
            int dupeCount = 0;
            int failCount = 0;
            ArrayList<Puzzle> newPuzzles = new ArrayList<>();
            Puzzle dupePuzzle = null;
            for (int i = 0; i < uris.size(); i++) {
                try {
                    Puzzle puzzle = loadUri(MainActivity.this, mDatabase, uris.get(i));
                    Log.i(TAG, "Successfully loaded " + puzzle.filename);
                    successCount++;
                    newPuzzles.add(puzzle);
                } catch (DuplicateFileException e) {
                    Log.i(TAG, "Duplicate file " + e.getDuplicatePuzzle().filename);
                    dupeCount++;
                    dupePuzzle = e.getDuplicatePuzzle();
                } catch (IOException e) {
                    Log.e(TAG, "Failed to load puzzle file", e);
                    failCount++;
                }
            }

            // If the user tried to open exactly one file, go directly to that file.
            final Puzzle singlePuzzle;
            if (uris.size() == 1 && successCount == 1) {
                singlePuzzle = newPuzzles.get(0);
            } else if (uris.size() == 1 && dupeCount == 1) {
                singlePuzzle = dupePuzzle;
            } else {
                singlePuzzle = null;
            }

            final String message =
                    getString(R.string.multiple_uris_result, uris.size(), successCount, dupeCount,
                            failCount);
            handler.post(() -> {
                PuzzleListFragment.setNewPuzzlesFragmentResult(getSupportFragmentManager(),
                        newPuzzles.size());
                if (singlePuzzle != null) {
                    onPuzzleSelected(singlePuzzle);
                } else {
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    @Override
    public void onDownloadSelected() {
        startActivity(DownloadPuzzlesActivity.newIntent(this));
    }

}
