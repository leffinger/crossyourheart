package io.github.leffinger.crossyourheart.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.Observer;

import java.util.ArrayList;
import java.util.List;

/**
 * An entire clue, including its clue number, clue text, and the cells in the clue.
 */
public class ClueViewModel {
    private final boolean mAcross;
    private final int mNumber;
    private final String mText;
    private final String mExactClueNumber;

    private ClueViewModel mNextClue;
    private ClueViewModel mPreviousClue;
    private List<CellViewModel> mCells = new ArrayList<>();

    private MediatorLiveData<Boolean> mIsReferenced;

    public ClueViewModel(PuzzleViewModel puzzleViewModel, boolean across, int number, String text) {
        mAcross = across;
        mNumber = number;
        mText = text;
        mExactClueNumber = mNumber + "-" + (mAcross ? "Across" : "Down");

        mIsReferenced = new MediatorLiveData<>();
        mIsReferenced.addSource(puzzleViewModel.getCurrentClueText(), clueText -> {
            mIsReferenced.setValue(clueText.contains(mExactClueNumber));
        });
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

    public LiveData<Boolean> isReferenced() {
        return mIsReferenced;
    }
}
