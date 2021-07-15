package io.github.leffinger.crossyourheart.activities;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Room;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import io.github.leffinger.crossyourheart.R;
import io.github.leffinger.crossyourheart.io.IOUtil;
import io.github.leffinger.crossyourheart.io.PuzFile;
import io.github.leffinger.crossyourheart.room.Database;
import io.github.leffinger.crossyourheart.room.Puzzle;

import static java.util.Objects.requireNonNull;

/**
 * Base activity that handles implicit intents and displays the puzzle list if no intent is
 * specified.
 */
public class MainActivity extends AppCompatActivity implements PuzzleListFragment.Callbacks {
    public static final String TAG = "MainActivity";
    public static final String ARG_FILENAME = "filename";
    private static final SimpleDateFormat FORMAT =
            new SimpleDateFormat("yyMMddHHmmss", Locale.getDefault());
    private String mFilename;
    private Database mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_container);

        if (IOUtil.getPuzzleDir(this).mkdir()) {
            Log.i(TAG, "Created puzzle dir: " + IOUtil.getPuzzleDir(this));
        }

        // Create or load database.
        mDatabase =
                Room.databaseBuilder(getApplicationContext(), Database.class, "puzzles").build();
        mFilename = null;
        new LoadPuzzleTask().execute(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();

        // Display list of puzzles.
        PuzzleListFragment fragment = PuzzleListFragment.newInstance();
        getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment)
                .commitNow();
    }

    private class LoadPuzzleTask extends AsyncTask<Bundle, Void, String> {
        private String error = null;
        private Throwable exception = null;

        @Override
        protected String doInBackground(Bundle... bundles) {
            Bundle savedInstanceState = bundles[0];
            if (savedInstanceState != null && savedInstanceState.containsKey(ARG_FILENAME)) {
                 return requireNonNull(savedInstanceState.getString(ARG_FILENAME));
            }
            if (getIntent() == null || getIntent().getData() == null) {
                // No file supplied.
                return null;
            }

            // Try to load the puzzle file.
            Uri uri = getIntent().getData();
            try {
                return loadUri(uri);
            } catch (IOException e) {
                error = e.getMessage();
                exception = e.getCause();
                return null;
            } catch (DuplicateFileException e) {
                error = "Reusing existing file";
                return e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String filename) {
            if (error != null) {
                Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                if (exception != null) {
                    Log.e(TAG, error, exception);
                }
            }

            mFilename = filename;
            if (mFilename == null) {
                // No filename found; start list activity to select file.
                PuzzleListFragment fragment = PuzzleListFragment.newInstance();
                getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment)
                        .commitNow();
                return;
            }

            // Start puzzle activity.
            startActivity(PuzzleActivity.newIntent(MainActivity.this, mFilename));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (mFilename != null) {
            outState.putString(ARG_FILENAME, mFilename);
        }
        super.onSaveInstanceState(outState);
    }

    private String findDuplicate(PuzFile puzzleLoader) {
        File puzzleDir = IOUtil.getPuzzleDir(this);
        String[] files = puzzleDir.list();
        for (String filename : files) {
            try (FileInputStream inputStream = new FileInputStream(
                    IOUtil.getPuzzleFile(this, filename))) {
                PuzFile existingPuzzle = PuzFile.loadPuzFile(inputStream);
                if (existingPuzzle.checkDuplicate(puzzleLoader)) {
                    return filename;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    @Override
    public void onFileSelected(String filename) {
        try (InputStream inputStream = new FileInputStream(IOUtil.getPuzzleFile(this, filename))) {
            PuzFile.verifyPuzFile(inputStream);
            mFilename = filename;
            startActivity(PuzzleActivity.newIntent(this, mFilename));
        } catch (IOException e) {
            Log.e(TAG, "Parse failed: ", e);
            Toast.makeText(this, "Loading puzzle failed: " + e.getMessage(), Toast.LENGTH_LONG)
                    .show();
        }
    }

    private String loadUri(Uri uri) throws DuplicateFileException, IOException {
        try (InputStream inputStream = requireNonNull(getContentResolver().openInputStream(uri))) {
            try {
                PuzFile puzzleLoader = PuzFile.verifyPuzFile(inputStream);

                // Check to see if we have already loaded this file.
                String duplicateFilename = findDuplicate(puzzleLoader);
                if (duplicateFilename != null) {
                    throw new DuplicateFileException(duplicateFilename);
                }

                String date = FORMAT.format(Calendar.getInstance().getTime());
                String filename = String.format("%s-%s.puz", date, UUID.randomUUID());
                try (FileOutputStream outputStream = new FileOutputStream(
                        IOUtil.getPuzzleFile(MainActivity.this, filename))) {
                    puzzleLoader.savePuzzleFile(outputStream);
                    mDatabase.puzzleDao().insert(Puzzle.fromPuzzleFile(filename, puzzleLoader));
                    return filename;
                } catch (IOException e) {
                    throw new IOException("Failed to save puzzle file", e);
                }
            } catch (IOException e) {
                throw new IOException("Failed to parse puzzle file", e);
            }
        } catch (IOException e) {
            throw new IOException("Failed to open puzzle file", e);
        }
    }

    @Override
    @SuppressWarnings("StaticFieldLeak")
    public void onUriSelected(Uri uri) {
        new LoadPuzzleTask().execute((Bundle)null);
    }

    @Override
    public void onMultipleUrisSelected(List<Uri> uris) {
        for (Uri uri : uris) {
            try {
                loadUri(uri);
            } catch (DuplicateFileException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDownloadSelected() {
        startActivity(DownloadPuzzlesActivity.newIntent(this));
    }
}
