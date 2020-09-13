package io.github.leffinger.crossyourheart.io;

import android.content.Context;

import java.io.File;

public class IOUtil {
    private static final String PUZZLE_DIR_NAME = "puzzles";

    public static File getPuzzleDir(Context context) {
        return new File(context.getFilesDir(), PUZZLE_DIR_NAME);
    }

    public static File getPuzzleFile(Context context, String filename) {
        return new File(getPuzzleDir(context), filename);
    }
}
