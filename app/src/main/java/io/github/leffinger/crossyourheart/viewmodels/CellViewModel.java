package io.github.leffinger.crossyourheart.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;


public class CellViewModel {
    private final PuzzleViewModel mPuzzleViewModel;
    private final int mRow;
    private final int mCol;
    private final boolean mIsCircled;

    private int mClueNumber;  // if this is the first cell in one or both directions
    private ClueViewModel mAcrossClue;
    private ClueViewModel mDownClue;

    private final MutableLiveData<String> mContents;
    private final MediatorLiveData<Boolean> mHighlighted;
    private Listener mListener;

    /**
     * @param puzzleViewModel ViewModel for the whole puzzle
     * @param row             row for this cell (0-indexed)
     * @param col             column for this cell (0-indexed)
     * @param contents        initial contents of the cell
     */
    public CellViewModel(PuzzleViewModel puzzleViewModel, int row, int col, String contents,
                         boolean isCircled) {
        mPuzzleViewModel = puzzleViewModel;
        mRow = row;
        mCol = col;
        mIsCircled = isCircled;

        mClueNumber = 0;

        mContents = new MutableLiveData<>(contents);

        mHighlighted = new MediatorLiveData<>();

        // Set up LiveData observers for whether this cell should be highlighted (is part of the
        // current clue).
        Observer<Object> observer = o -> {
            if (mPuzzleViewModel.getAcrossFocus().getValue()) {
                mHighlighted
                        .setValue(getAcrossClue() == mPuzzleViewModel.getCurrentClue().getValue());
            } else {
                mHighlighted
                        .setValue(getDownClue() == mPuzzleViewModel.getCurrentClue().getValue());
            }
        };
        mHighlighted.addSource(mPuzzleViewModel.getAcrossFocus(), observer);
        mHighlighted.addSource(mPuzzleViewModel.getCurrentClue(), observer);
    }

    public int getRow() {
        return mRow;
    }

    public int getCol() {
        return mCol;
    }

    public int getClueNumber() {
        return mClueNumber;
    }

    public void setClueNumber(int clueNumber) {
        mClueNumber = clueNumber;
    }

    public ClueViewModel getAcrossClue() {
        return mAcrossClue;
    }

    public void setAcrossClue(ClueViewModel acrossClue) {
        mAcrossClue = acrossClue;
    }

    public ClueViewModel getDownClue() {
        return mDownClue;
    }

    public void setDownClue(ClueViewModel downClue) {
        mDownClue = downClue;
    }

    public MutableLiveData<String> getContents() {
        return mContents;
    }

    public LiveData<Boolean> isHighlighted() {
        return mHighlighted;
    }

    public boolean isCircled() {
        return mIsCircled;
    }

    public void onFocus() {
        mPuzzleViewModel.onFocusChanged(mRow, mCol);
    }

    public void requestFocus() {
        if (mListener != null) {
            mListener.requestFocus();
        }
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void onContentsChanged() {
        mPuzzleViewModel.onContentsChanged(mRow, mCol, mContents.getValue());
    }

    public interface Listener {
        void requestFocus();
    }
}
