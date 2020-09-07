package io.github.leffinger.crossyourheart.viewmodels;

import io.github.leffinger.crossyourheart.io.AbstractPuzzleFile;

public class PuzzleFileViewModel {

    private final String mFilename;
    private final AbstractPuzzleFile mPuzzleFile;

    public PuzzleFileViewModel(String filename, AbstractPuzzleFile puzzle) {
        mFilename = filename;
        mPuzzleFile = puzzle;
    }

    public String getFilename() {
        return mFilename;
    }

    public String getTitle() {
        return mPuzzleFile.getTitle();
    }

    public String getAuthor() {
        return mPuzzleFile.getAuthor();
    }

    public String getCopyright() {
        return mPuzzleFile.getCopyright();
    }
}
