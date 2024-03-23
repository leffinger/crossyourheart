package io.github.leffinger.crossyourheart.io;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.widget.ProgressBar;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import io.github.leffinger.crossyourheart.room.Database;
import io.github.leffinger.crossyourheart.room.PuzFileMetadata;
import io.github.leffinger.crossyourheart.room.Puzzle;

/** Guards access to the puzzle directory. */
public class PuzzleDirectory {
    @SuppressLint("ConstantLocale")
    private static final SimpleDateFormat FORMAT =
            new SimpleDateFormat("yyMMddHHmmss", Locale.getDefault());
    private static final String TAG = "PuzzleDirectory";

    // Singleton instance
    private static final PuzzleDirectory mInstance = new PuzzleDirectory();

    public static PuzzleDirectory getInstance() {
        return mInstance;
    }

    /** Fetches the list of all puzzle files. */
    public synchronized List<Puzzle> getAllPuzzles(Context context) {
        Database database = Database.getInstance(context.getApplicationContext());
        return database.puzzleDao().getAll();
    }

    /** Copies the input stream into the puzzle directory and adds it to the database. */
    public synchronized Puzzle loadInputStream(Context context, InputStream inputStream)
            throws DuplicateFileException, IOException {
        Database database = Database.getInstance(context.getApplicationContext());
        try {
            PuzFile puzzleLoader = PuzFile.verifyPuzFile(inputStream);

            // Check to see if we have already loaded this file.
            List<Puzzle> matchingPuzzles = database.puzzleDao()
                                                   .getMatchingPuzFiles(puzzleLoader.getTitle(),
                                                           puzzleLoader.getAuthor(),
                                                           puzzleLoader.getHeaderChecksum());
            if (!matchingPuzzles.isEmpty()) {
                throw new DuplicateFileException(matchingPuzzles.get(0));
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

    /**
     * Compares the set of filenames in the puzzle directory to the set of filenames in the database
     * and returns true if they are the same.
     */
    public synchronized boolean databaseAndDirectoryHaveSameFiles(Context context) {
        Database database = Database.getInstance(context.getApplicationContext());
        Set<String> dbFilenames = new HashSet<>(database.puzzleDao().getFiles());
        String[] files = IOUtil.getPuzzleDir(context).list();
        assert files != null;
        Set<String> dirFilenames = new HashSet<>(Arrays.asList(files));
        return dbFilenames.equals(dirFilenames);
    }

    /** Rebuilds the puzzle database from the contents of the puzzle directory. */
    public synchronized List<File> reindexFiles(Context context, ProgressBar progressBar) {
        Database database = Database.getInstance(context.getApplicationContext());

        // Current list of files (existing files will be updated; missing files will be
        // deleted).
        Set<String> currentFiles = new HashSet<>(database.puzzleDao().getFiles());
        Set<String> foundFiles = new HashSet<>();

        // Scan and update puzzle files.
        File puzzleDir = IOUtil.getPuzzleDir(context);
        File[] files = puzzleDir.listFiles();
        assert files != null;
        progressBar.setMax(files.length);
        List<File> corruptFiles = new ArrayList<>();
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            foundFiles.add(file.getName());
            try (FileInputStream inputStream = new FileInputStream(file)) {
                PuzFile puzzleLoader = new PuzFile(inputStream);
                database.puzzleDao().insert(new Puzzle(file.getName(), puzzleLoader.getTitle(), puzzleLoader.getAuthor(), puzzleLoader.getCopyright(), puzzleLoader.isSolved(), false, !puzzleLoader.isEmpty(), puzzleLoader.getScrambleState(), false));
                database.puzFileMetadataDao().insert(new PuzFileMetadata(file.getName(), puzzleLoader.getHeaderChecksum()));
            } catch (IOException e) {
                Log.e(TAG, "Failed to load puzzle file " + file.getName(), e);
                corruptFiles.add(file);
            }
            progressBar.setProgress(i + 1);
        }

        // Delete DB rows for puzzles that were in the DB but not on disk.
        currentFiles.removeAll(foundFiles);
        Log.i(TAG, "Removing " + currentFiles.size() + " files from DB");
        List<Puzzle> toBeDeleted = new ArrayList<>();
        for (String missingFile : currentFiles) {
            toBeDeleted.add(new Puzzle(missingFile));
        }
        database.puzzleDao().deletePuzzles(toBeDeleted);
        return corruptFiles;
    }

    /** Deletes files from the puzzle directory. Returns true if successful. */
    public synchronized boolean deleteFiles(List<File> files) {
        boolean success = true;
        for (File file : files) {
            if (!file.delete()) {
                success = false;
            }
        }
        return success;
    }
}
