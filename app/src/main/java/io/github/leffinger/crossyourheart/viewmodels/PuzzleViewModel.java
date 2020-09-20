package io.github.leffinger.crossyourheart.viewmodels;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Stack;

import io.github.leffinger.crossyourheart.io.AbstractPuzzleFile;

public class PuzzleViewModel extends ViewModel {
    private static final String TAG = "PuzzleViewModel";

    /**
     * Representation of on-disk puzzle file.
     */
    private final AbstractPuzzleFile mPuzzleFile;

    /**
     * Name of the file where the puzzle should be saved.
     */
    private final File mFile;

    /**
     * Grid of mutable CellViewModels. Black cells are null.
     */
    private final CellViewModel[][] mGrid;

    /**
     * True if the currently active clue is an Across, false if Down.
     */
    private final MutableLiveData<Boolean> mAcrossFocus;
    /**
     * Currently active clue.
     */
    private final MediatorLiveData<ClueViewModel> mCurrentClue = new MediatorLiveData<>();
    /**
     * History of actions. Enables "undo" functionality.
     */
    private final Stack<Action> mUndoStack = new Stack<>();
    /**
     * The view model for the currently focused square.
     */
    private final MutableLiveData<CellViewModel> mCurrentCell = new MutableLiveData<>();
    /**
     * Whether the puzzle's solution is currently correct.
     */
    private final MutableLiveData<Boolean> mIsSolved;

    // This can be called from a background thread, so it should not call setValue() on any
    // LiveData objects.
    public PuzzleViewModel(AbstractPuzzleFile puzzleFile, File file, boolean startWithDownClues) {
        mPuzzleFile = puzzleFile;
        mFile = file;
        mAcrossFocus = new MutableLiveData<>(!startWithDownClues);
        boolean isSolved = puzzleFile.isSolved();
        Log.i(TAG, "isSolved=" + isSolved);
        mIsSolved = new MutableLiveData<>(isSolved);

        StringBuilder solution = new StringBuilder();
        for (int row = 0; row < getNumRows(); row++) {
            for (int col = 0; col < getNumColumns(); col++) {
                solution.append(puzzleFile.getCellSolution(row, col));
            }
            solution.append('\n');
        }

        // Construct a structure of ClueViewModels linked to CellViewModels, and vice versa.
        ClueViewModel[] clues = new ClueViewModel[mPuzzleFile.getNumClues()];
        for (int i = 0; i < clues.length; i++) {
            AbstractPuzzleFile.Clue clue = mPuzzleFile.getClue(i);
            clues[i] = new ClueViewModel(clue.isAcross(), clue.getNumber(), clue.getText());
        }

        mGrid = new CellViewModel[getNumRows()][getNumColumns()];
        for (int row = 0; row < getNumRows(); row++) {
            for (int col = 0; col < getNumColumns(); col++) {
                if (mPuzzleFile.isBlack(row, col)) {
                    continue;
                }

                mGrid[row][col] =
                        new CellViewModel(this, row, col, mPuzzleFile.getCellContents(row, col),
                                          mPuzzleFile.isCircled(row, col));

                int acrossClueIndex = mPuzzleFile.getAcrossClueIndex(row, col);
                if (acrossClueIndex >= 0) {
                    mGrid[row][col].setAcrossClue(clues[acrossClueIndex]);
                    clues[acrossClueIndex].addCell(mGrid[row][col]);
                }

                int downClueIndex = mPuzzleFile.getDownClueIndex(row, col);
                if (downClueIndex >= 0) {
                    mGrid[row][col].setDownClue(clues[downClueIndex]);
                    clues[downClueIndex].addCell(mGrid[row][col]);
                }
            }
        }

        // Link Clue objects in a doubly-linked circular list.
        linkClues(clues);

        // When across/down focus changes, or the current cell changes, update the currently
        // selected clue.
        Observer<Object> observer = o -> {
            CellViewModel currentCell = mCurrentCell.getValue();
            if (currentCell == null) {
                return;
            }
            if (mAcrossFocus.getValue()) {
                if (currentCell.getAcrossClue() != null) {
                    mCurrentClue.setValue(currentCell.getAcrossClue());
                } else if (currentCell.getDownClue() != null) {
                    // switch directions if this cell only has a down clue
                    mAcrossFocus.setValue(false);  // should ttrigger another update
                }
            } else {
                if (currentCell.getDownClue() != null) {
                    mCurrentClue.setValue(currentCell.getDownClue());
                } else if (currentCell.getAcrossClue() != null) {
                    // switch directions if this cell only has an across clue
                    mAcrossFocus.setValue(true);  // should trigger another update
                }
            }
        };
        mCurrentClue.addSource(mAcrossFocus, observer);
        mCurrentClue.addSource(mCurrentCell, observer);
    }

    private static void linkClues(ClueViewModel[] clues) {
        ClueViewModel firstAcrossClue = clues[0];
        ClueViewModel firstDownClue = clues[1];
        ClueViewModel lastAcrossClue = linkClues(clues, true);
        ClueViewModel lastDownClue = linkClues(clues, false);

        firstAcrossClue.setPreviousClue(lastDownClue);
        lastDownClue.setNextClue(firstAcrossClue);
        firstDownClue.setPreviousClue(lastAcrossClue);
        lastAcrossClue.setNextClue(firstDownClue);
    }

    private static ClueViewModel linkClues(ClueViewModel[] clues, boolean across) {
        ClueViewModel firstClue = null;
        ClueViewModel previousClue = null;
        for (int i = 0; i < clues.length; i++) {
            ClueViewModel clue = clues[i];

            if (clue.isAcross() == across) {
                if (firstClue == null) {
                    firstClue = clue;
                }
                if (previousClue != null) {
                    clue.setPreviousClue(previousClue);
                    previousClue.setNextClue(clue);
                }
                previousClue = clue;
            }
        }

        // Now "previousClue" should be the last clue.
        return previousClue;
    }

    public CellViewModel getCellViewModel(int row, int col) {
        return mGrid[row][col];
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

    public String getNote() {
        return mPuzzleFile.getNote();
    }

    public int getNumRows() {
        return mPuzzleFile.getHeight();
    }

    public int getNumColumns() {
        return mPuzzleFile.getWidth();
    }

    public void onFocusChanged(int row, int col) {
        mCurrentCell.setValue(mGrid[row][col]);
    }

    public LiveData<ClueViewModel> getCurrentClue() {
        return mCurrentClue;
    }

    public LiveData<Integer> getCurrentClueNumber() {
        return Transformations.map(mCurrentClue, clue -> clue == null ? 0 : clue.getNumber());
    }

    public LiveData<String> getCurrentClueText() {
        return Transformations.map(mCurrentClue, clue -> clue == null ? null : clue.getText());
    }

    public LiveData<Boolean> getAcrossFocus() {
        return mAcrossFocus;
    }

    public LiveData<Boolean> isSolved() {
        return Transformations.distinctUntilChanged(mIsSolved);
    }

    public void toggleDirection() {
        mAcrossFocus.setValue(!mAcrossFocus.getValue());
    }

    private void moveToNextCell(boolean skipFilledClues, boolean skipFilledSquares) {
        // Find our location in the current clue.
        CellViewModel currentCell = mCurrentCell.getValue();
        List<CellViewModel> currentClueCells = mCurrentClue.getValue().getCells();
        int i = currentClueCells.indexOf(currentCell);
        if (i < 0) {
            Log.e(TAG, "CellViewModel should be in ClueViewModel but isn't!");
            return;
        }
        for (int j = 1; j < currentClueCells.size(); j++) {
            int index = (i + j) % currentClueCells.size();
            if (!skipFilledSquares ||
                    currentClueCells.get(index).getContents().getValue().isEmpty()) {
                currentClueCells.get(index).requestFocus();
                return;
            }
        }

        // No empty cell available; move to next clue.
        moveToNextClue(skipFilledClues, skipFilledSquares);
    }

    public void moveToPreviousClue(boolean skipFilledClues, boolean skipFilledSquares) {
        ClueViewModel prev = mCurrentClue.getValue().getPreviousClue();

        if (skipFilledClues) {
            // Find a clue with at least one empty square.
            while (prev != mCurrentClue.getValue() && prev.isFilled()) {
                prev = prev.getPreviousClue();
            }
            if (prev == mCurrentClue.getValue()) {
                // All clues are filled.
                prev = prev.getPreviousClue();
            }
        }

        CellViewModel cell = prev.getCells().get(0);
        if (skipFilledSquares) {
            for (int i = 0; i < prev.getCells().size(); i++) {
                if (prev.getCells().get(i).getContents().getValue().isEmpty()) {
                    cell = prev.getCells().get(i);
                    break;
                }
            }
        }

        Log.i(TAG,
              String.format("Selecting clue %d-%s", prev.getNumber(), prev.isAcross() ? "A" : "D"));
        mAcrossFocus.setValue(prev.isAcross());
        cell.requestFocus();
    }

    public void moveToNextClue(boolean skipFilledClues, boolean skipFilledSquares) {
        ClueViewModel next = mCurrentClue.getValue().getNextClue();

        if (skipFilledClues) {
            // Find a clue with at least one empty square.
            while (next != mCurrentClue.getValue() && next.isFilled()) {
                next = next.getNextClue();
            }
            if (next == mCurrentClue.getValue()) {
                // All clues are filled.
                next = next.getNextClue();
            }
        }

        CellViewModel cell = next.getCells().get(0);
        if (skipFilledSquares) {
            for (int i = 0; i < next.getCells().size(); i++) {
                if (next.getCells().get(i).getContents().getValue().isEmpty()) {
                    cell = next.getCells().get(i);
                    break;
                }
            }
        }

        Log.i(TAG,
              String.format("Selecting clue %d-%s", next.getNumber(), next.isAcross() ? "A" : "D"));
        mAcrossFocus.setValue(next.isAcross());
        cell.requestFocus();
    }

    public void setCurrentCellContents(String newContents, boolean skipFilledClues,
                                       boolean skipFilledSquares) {
        CellViewModel currentCell = mCurrentCell.getValue();
        String oldContents = currentCell.getContents().getValue();
        mUndoStack.push(new Action(currentCell, currentCell, oldContents, newContents));
        currentCell.getContents().setValue(newContents);
        moveToNextCell(skipFilledClues, skipFilledSquares);
    }

    public void doUndo() {
        if (mUndoStack.empty()) {
            return;
        }

        Action lastAction = mUndoStack.pop();
        lastAction.mModifiedCell.getContents().setValue(lastAction.mOldContents);
        lastAction.mFocusedCell.requestFocus();
    }

    public void doBackspace() {
        // If current cell is empty, move to the previous cell and delete its contents.
        CellViewModel currentCell = mCurrentCell.getValue();
        if (currentCell.getContents().getValue().isEmpty()) {
            int newRow, newCol;
            if (mAcrossFocus.getValue()) {
                newRow = currentCell.getRow();
                newCol = currentCell.getCol() - 1;
            } else {
                newRow = currentCell.getRow() - 1;
                newCol = currentCell.getCol();
            }

            if (newRow < 0 || newCol < 0) {
                // At the beginning of a row/column. Don't do anything.
                return;
            }

            CellViewModel newCell = mGrid[newRow][newCol];
            if (newCell == null) {
                // Previous square is black. Don't do anything.
                return;
            }

            // Delete previous cell contents and move to that cell.
            mUndoStack.push(new Action(newCell, currentCell, newCell.getContents().getValue(), ""));
            newCell.getContents().setValue("");
            newCell.requestFocus();
        } else {
            // Delete current cell's contents.
            mUndoStack
                    .push(new Action(currentCell, currentCell, currentCell.getContents().getValue(),
                                     ""));
            currentCell.getContents().setValue("");
        }
    }

    public void saveToFile() throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(mFile)) {
            mPuzzleFile.savePuzzleFile(outputStream);
        }
    }

    public void onContentsChanged(int row, int col, String value) {
        mPuzzleFile.setCellContents(row, col, value);
        mIsSolved.setValue(mPuzzleFile.isSolved());
    }

    public PuzzleInfoViewModel getPuzzleInfoViewModel() {
        return new PuzzleInfoViewModel(getTitle(), getAuthor(), getCopyright(), getNote());
    }

    public File getFile() {
        return mFile;
    }

    public AbstractPuzzleFile getPuzzleFile() {
        return mPuzzleFile;
    }

    private static class Action {
        CellViewModel mModifiedCell;
        CellViewModel mFocusedCell;
        String mOldContents;
        String mNewContents;

        public Action(CellViewModel modifiedCell, CellViewModel focusedCell, String oldContents,
                      String newContents) {
            mModifiedCell = modifiedCell;
            mFocusedCell = focusedCell;
            mOldContents = oldContents;
            mNewContents = newContents;
        }
    }
}

