package io.github.leffinger.crossyourheart.viewmodels;

import java.util.ArrayList;
import java.util.List;

/**
 * An entire clue, including its clue number, clue text, and the cells in the clue.
 */
public class ClueViewModel {
    private boolean mAcross;
    private int mNumber;
    private String mText;
    private ClueViewModel mNextClue;
    private ClueViewModel mPreviousClue;
    private List<CellViewModel> mCells = new ArrayList<>();

    public ClueViewModel(boolean across, int number, String text) {
        mAcross = across;
        mNumber = number;
        mText = text;
    }

    public boolean isAcross() {
        return mAcross;
    }

    public int getNumber() {
        return mNumber;
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

    public List<CellViewModel> getCells() {
        return mCells;
    }

    public void setClueInfo(boolean isAcross, List<CellViewModel> cellViewModels, int clueNumber) {
        mAcross = isAcross;
        mCells = cellViewModels;
        for (CellViewModel cell : cellViewModels) {
            if (mAcross) {
                cell.setAcrossClue(this);
            } else {
                cell.setDownClue(this);
            }
        }
        mNumber = clueNumber;
        getCells().get(0).setClueNumber(clueNumber);
    }

    public boolean isFilled() {
        for (CellViewModel cell : mCells) {
            if (cell.getContents().getValue().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public void addCell(CellViewModel cellViewModel) {
        if (mCells.isEmpty()) {
            cellViewModel.setClueNumber(mNumber);
        }
        mCells.add(cellViewModel);
    }
}
