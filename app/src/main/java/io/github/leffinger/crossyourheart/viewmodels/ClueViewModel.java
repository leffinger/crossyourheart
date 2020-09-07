package io.github.leffinger.crossyourheart.viewmodels;

import java.util.ArrayList;
import java.util.List;

/**
 * An entire clue, including its clue number, clue text, and the cells in the clue.
 */
class ClueViewModel {
    private boolean mAcross;
    private int mNumber;
    private String mText;
    private ClueViewModel mNextClue;
    private ClueViewModel mPreviousClue;
    private List<CellViewModel> mCells = new ArrayList<>();

    public boolean isAcross() {
        return mAcross;
    }

    public void setAcross(boolean across) {
        mAcross = across;
    }

    public int getNumber() {
        return mNumber;
    }

    public void setNumber(int number) {
        mNumber = number;
    }

    public String getText() {
        return mText;
    }

    public void setText(String text) {
        mText = text;
    }

    public ClueViewModel getNextClue() {
        return mNextClue;
    }

    public void setNextClue(ClueViewModel nextClue) {
        mNextClue = nextClue;
    }

    public ClueViewModel getPreviousClue() {
        return mPreviousClue;
    }

    public void setPreviousClue(ClueViewModel previousClue) {
        mPreviousClue = previousClue;
    }

    public void addCell(CellViewModel cellViewModel) {
        mCells.add(cellViewModel);
    }

    public List<CellViewModel> getCells() {
        return mCells;
    }
}
