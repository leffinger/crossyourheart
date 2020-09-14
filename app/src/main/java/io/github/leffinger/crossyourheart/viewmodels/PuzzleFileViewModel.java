package io.github.leffinger.crossyourheart.viewmodels;

import io.github.leffinger.crossyourheart.R;
import io.github.leffinger.crossyourheart.io.AbstractPuzzleFile;
import io.github.leffinger.crossyourheart.io.AbstractPuzzleFile.ScrambleState;

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

    public int getSolvedStateResId() {
        ScrambleState scrambleState = mPuzzleFile.getScrambleState();
        if (scrambleState == ScrambleState.LOCKED || scrambleState == ScrambleState.UNKNOWN) {
            return R.drawable.locked;
        }
        if (mPuzzleFile.isSolved()) {
            return R.drawable.solved;
        }
        return R.drawable.in_progress;
    }

    public int getSolvedMessageResId() {
        ScrambleState scrambleState = mPuzzleFile.getScrambleState();
        if (scrambleState == ScrambleState.LOCKED || scrambleState == ScrambleState.UNKNOWN) {
            return R.string.locked_message;
        }
        if (mPuzzleFile.isSolved()) {
            return R.string.solved_message;
        }
        return R.string.unsolved_message;
    }
}
