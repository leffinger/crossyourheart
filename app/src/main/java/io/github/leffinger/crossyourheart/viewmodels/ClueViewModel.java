package io.github.leffinger.crossyourheart.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import java.util.ArrayList;
import java.util.List;

/**
 * An entire clue, including its clue number, clue text, and the cells in the clue.
 */
public class ClueViewModel {
    private static final String TAG = "ClueViewModel";
    private final boolean mAcross;
    private final int mNumber;
    private final String mText;
    private final List<CellViewModel> mCells = new ArrayList<>();
    private final List<ClueViewModel> mReferences = new ArrayList<>();
    private final MediatorLiveData<Boolean> mIsReferenced;
    private ClueViewModel mNextClue;
    private ClueViewModel mPreviousClue;

    public ClueViewModel(PuzzleViewModel puzzleViewModel, boolean across, int number, String text) {
        mAcross = across;
        mNumber = number;
        mText = text;

        mIsReferenced = new MediatorLiveData<>();
        mIsReferenced.addSource(puzzleViewModel.getCurrentClue(), currentClue -> mIsReferenced
                .setValue(mReferences.contains(currentClue)));
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

    public void addReference(ClueViewModel otherClue) {
        mReferences.add(otherClue);
    }

    public LiveData<Boolean> isReferenced() {
        return mIsReferenced;
    }

    @Override
    public String toString() {
        return "ClueViewModel{" + "mAcross=" + mAcross + ", mNumber=" + mNumber + '}';
    }
}
