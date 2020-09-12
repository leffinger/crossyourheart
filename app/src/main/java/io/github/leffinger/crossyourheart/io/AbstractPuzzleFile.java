package io.github.leffinger.crossyourheart.io;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

/**
 * Generic API for an on-disk puzzle file.
 */
public abstract class AbstractPuzzleFile implements Serializable {
    public abstract int getNumClues();

    public abstract String getClue(int i);

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

    public abstract void setCellContents(int row, int col, String value);

    public abstract boolean isSolved();
}
