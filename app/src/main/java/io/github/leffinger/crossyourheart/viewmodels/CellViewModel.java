package io.github.leffinger.crossyourheart.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

public class CellViewModel {
    private static final String TAG = "CellViewModel";
    private final PuzzleViewModel mPuzzleViewModel;
    private final int mRow;
    private final int mCol;
    private final boolean mIsCircled;
    private final MutableLiveData<String> mContents;
    private final MutableLiveData<Boolean> mMarkedIncorrect;
    private final MutableLiveData<Boolean> mMarkedCorrect;
    private final MutableLiveData<Boolean> mRevealed;
    private final MutableLiveData<Boolean> mPencil;
    private final MediatorLiveData<Boolean> mHighlighted;
    private final MediatorLiveData<Boolean> mSelected;
    private final MediatorLiveData<Boolean> mReferenced;
    private int mClueNumber;  // if this is the first cell in one or both directions
    private ClueViewModel mAcrossClue;
    private ClueViewModel mDownClue;

    /**
     * @param puzzleViewModel ViewModel for the whole puzzle
     * @param row             row for this cell (0-indexed)
     * @param col             column for this cell (0-indexed)
     * @param contents        initial contents of the cell
     * @param isCircled       whether the cell should be circled
     */
    public CellViewModel(PuzzleViewModel puzzleViewModel, int row, int col, String contents,
                         boolean isCircled) {
        mPuzzleViewModel = puzzleViewModel;
        mRow = row;
        mCol = col;
        mIsCircled = isCircled;

        mClueNumber = 0;

        mContents = new MutableLiveData<>(contents);
        mSelected = new MediatorLiveData<>();
        mMarkedIncorrect = new MutableLiveData<>(false);
        mMarkedCorrect = new MutableLiveData<>(false);
        mRevealed = new MutableLiveData<>(false);
        mPencil = new MutableLiveData<>(false);
        mHighlighted = new MediatorLiveData<>();
        mReferenced = new MediatorLiveData<>();

        // Set up LiveData observer for whether this cell is currently selected.
        mSelected.addSource(mPuzzleViewModel.getCurrentCell(),
                            selectedCell -> mSelected.setValue(selectedCell == this));

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

    private void arrangeReferencedLiveData() {
        if (mAcrossClue == null || mDownClue == null) {
            return;
        }

        // Set up LiveData observers for whether this cell is referenced by the current clue.
        Observer<Object> observer = o -> {
            Boolean acrossReferenced = mAcrossClue.isReferenced().getValue();
            boolean acrossReferencedUnboxed = acrossReferenced == null ? false : acrossReferenced;
            Boolean downReferenced = mDownClue.isReferenced().getValue();
            boolean downReferencedUnboxed = downReferenced == null ? false : downReferenced;
            mReferenced.setValue(acrossReferencedUnboxed || downReferencedUnboxed);
        };
        mReferenced.addSource(mAcrossClue.isReferenced(), observer);
        mReferenced.addSource(mDownClue.isReferenced(), observer);
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
        arrangeReferencedLiveData();
    }

    public ClueViewModel getDownClue() {
        return mDownClue;
    }

    public void setDownClue(ClueViewModel downClue) {
        mDownClue = downClue;
        arrangeReferencedLiveData();
    }

    public LiveData<String> getContents() {
        return mContents;
    }

    public String setContents(String newContents, boolean pencil) {
        String oldContents = mContents.getValue();
        if (mMarkedCorrect.getValue() || mRevealed.getValue()) {
            return oldContents;
        }
        mPencil.setValue(pencil);
        mMarkedIncorrect.setValue(false);
        mRevealed.setValue(false);

        // this triggers updates, e.g. autocheck, so do it last
        mContents.setValue(newContents);
        return oldContents;
    }

    public LiveData<Boolean> isHighlighted() {
        return mHighlighted;
    }

    public LiveData<Boolean> isRevealed() {return mRevealed; }

    public LiveData<Boolean> isMarkedIncorrect() {
        return mMarkedIncorrect;
    }

    public LiveData<Boolean> isMarkedCorrect() { return mMarkedCorrect; }

    public boolean isCircled() {
        return mIsCircled;
    }

    public void checkContents() {
        if (mContents.getValue().isEmpty()) {
            return;
        }
        if (mPuzzleViewModel.isCorrect(mRow, mCol)) {
            mMarkedCorrect.setValue(true);
        } else {
            mMarkedIncorrect.setValue(true);
        }
    }

    public void revealContents() {
        if (!mPuzzleViewModel.isCorrect(mRow, mCol)) {
            mMarkedIncorrect.setValue(true);
        }
        String solution = mPuzzleViewModel.getSolution(mRow, mCol);
        mContents.setValue(solution);
        mRevealed.setValue(true);
    }

    public LiveData<Boolean> getSelected() {
        return mSelected;
    }

    public MutableLiveData<Boolean> getPencil() {
        return mPencil;
    }

    public LiveData<Boolean> getReferenced() { return mReferenced; }

    public void reset() {
        mContents.setValue("");
        mMarkedCorrect.setValue(false);
        mMarkedIncorrect.setValue(false);
        mRevealed.setValue(false);
        mPencil.setValue(false);
    }

    @Override
    public String toString() {
        return "CellViewModel{" + "mRow=" + mRow + ", mCol=" + mCol + '}';
    }
}
