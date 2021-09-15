package io.github.leffinger.crossyourheart.io;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

/**
 * Generic API for an on-disk puzzle file.
 */
public abstract class AbstractPuzzleFile implements Serializable {
    public abstract int getNumClues();

    public abstract boolean isBlack(int row, int col);

    public abstract String getCellSolution(int row, int col);

    public abstract String getCellContents(int row, int col);

    public abstract String getTitle();

    public abstract String getAuthor();

    public abstract String getCopyright();

    public abstract String getNote();

    public abstract int getHeight();

    public abstract int getWidth();

    public abstract void savePuzzleFile(OutputStream outputStream) throws IOException;

    public abstract void setCellContents(int row, int col, String contents);

    public abstract boolean isSolved();

    public abstract boolean isCorrect(int row, int col);

    public abstract ScrambleState getScrambleState();

    public abstract boolean isCircled(int row, int col);

    public abstract int getAcrossClueIndex(int row, int col);

    public abstract int getDownClueIndex(int row, int col);

    public abstract String getSolution(int row, int col);

    public abstract TimerInfo getTimerInfo();
    public abstract void setTimerInfo(TimerInfo timerInfo);

    public abstract boolean isEmpty();

    public abstract boolean[][] getClueReferences();

    public enum ScrambleState {
        UNSCRAMBLED, LOCKED, SCRAMBLED, UNKNOWN
    }

    public abstract Clue getClue(int i);

    public static class Clue implements Serializable {
        private final String mText;
        private boolean mIsAcross;
        private int mNumber;

        public Clue(String text) {
            mText = text;
        }

        public String getText() {
            return mText;
        }

        public void setAcross(boolean across) {
            mIsAcross = across;
        }

        public void setNumber(int number) {
            mNumber = number;
        }

        public boolean isAcross() {
            return mIsAcross;
        }

        public int getNumber() {
            return mNumber;
        }
    }

    public final static class TimerInfo implements Serializable {
        public final long elapsedTimeSecs;
        public final boolean isRunning;

        public TimerInfo(long elapsedTimeSecs, boolean isRunning) {
            this.elapsedTimeSecs = elapsedTimeSecs;
            this.isRunning = isRunning;
        }
    }
}
