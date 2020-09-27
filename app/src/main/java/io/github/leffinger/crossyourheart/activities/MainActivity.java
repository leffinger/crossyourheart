package io.github.leffinger.crossyourheart.activities;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.UUID;

import io.github.leffinger.crossyourheart.R;
import io.github.leffinger.crossyourheart.io.IOUtil;
import io.github.leffinger.crossyourheart.io.PuzFile;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_container);

        if (IOUtil.getPuzzleDir(this).mkdir()) {
            Log.i(TAG, "Created puzzle dir: " + IOUtil.getPuzzleDir(this));
        }

        mFilename = null;
        if (savedInstanceState != null && savedInstanceState.containsKey(ARG_FILENAME)) {
            mFilename = requireNonNull(savedInstanceState.getString(ARG_FILENAME));
        } else if (getIntent() != null && getIntent().getData() != null) {
            mFilename = loadUri(getIntent().getData());
        }

        if (mFilename == null) {
            // No filename found; start list activity to select file.
            PuzzleListFragment fragment = PuzzleListFragment.newInstance();
            getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment)
                    .commitNow();
            return;
        }

        // Start puzzle activity.
        startActivity(PuzzleActivity.newIntent(this, mFilename));
    }

    @Override
    public void onResume() {
        super.onResume();

        // Display list of puzzles.
        PuzzleListFragment fragment = PuzzleListFragment.newInstance();
        getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment)
                .commitNow();
    }

    /**
     * Open the input stream, try to parse it as a puzzle, and save it to a local file.
     * <p>
     * If any of these steps fail, log/show an error and return null.
     *
     * @param uri data source
     * @return name of the local file to which the puzzle was saved, or null.
     */
    private String loadUri(Uri uri) {
        try (InputStream inputStream = requireNonNull(getContentResolver().openInputStream(uri))) {
            try {
                PuzFile puzzleLoader = PuzFile.verifyPuzFile(inputStream);

                // Check to see if we have already loaded this file.
                String duplicateFilename = findDuplicate(puzzleLoader);
                if (duplicateFilename == null) {
                    String date = FORMAT.format(Calendar.getInstance().getTime());
                    String filename = String.format("%s-%s.puz", date, UUID.randomUUID());
                    try (FileOutputStream outputStream = new FileOutputStream(
                            IOUtil.getPuzzleFile(this, filename))) {
                        puzzleLoader.savePuzzleFile(outputStream);
                        return filename;
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to save puzzle file", e);
                        Toast.makeText(this, "Failed to save puzzle file", Toast.LENGTH_LONG)
                                .show();
                    }
                } else {
                    Toast.makeText(this, "Reusing existing file", Toast.LENGTH_LONG).show();
                    return duplicateFilename;
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to parse puzzle file", e);
                Toast.makeText(this, "Failed to parse puzzle file", Toast.LENGTH_LONG).show();
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to open puzzle file", e);
            Toast.makeText(this, "Failed to open puzzle file", Toast.LENGTH_LONG).show();
        }
        return null;
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

    @Override
    public void onUriSelected(Uri uri) {
        String filename = loadUri(uri);
        if (filename != null) {
            mFilename = filename;
            startActivity(PuzzleActivity.newIntent(this, mFilename));
        }
    }

    @Override
    public void onDownloadSelected() {
        startActivity(DownloadPuzzlesActivity.newIntent(this));
    }
}
