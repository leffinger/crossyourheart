package io.github.leffinger.crossyourheart.viewmodels;

import android.util.ArrayMap;
import android.util.Log;

import androidx.arch.core.util.Function;
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
import java.util.Map;
import java.util.Stack;

import io.github.leffinger.crossyourheart.io.AbstractPuzzleFile;
import io.github.leffinger.crossyourheart.io.PuzFile;

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
    private final MutableLiveData<Boolean> mAcrossFocus = new MutableLiveData<>();
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
    private MutableLiveData<CellViewModel> mCurrentCell = new MutableLiveData<>();

    /**
     * Whether the puzzle's solution is currently correct.
     */
    private MutableLiveData<Boolean> mIsSolved = new MutableLiveData<>();

    public PuzzleViewModel(AbstractPuzzleFile puzzleFile, File file) {
        mPuzzleFile = puzzleFile;
        mFile = file;

        mAcrossFocus.setValue(false);

        Log.i(TAG, "Puzzle width: " + getNumColumns());
        Log.i(TAG, "Puzzle height: " + getNumRows());

        mGrid = new CellViewModel[getNumRows()][getNumColumns()];

        ClueViewModel[] clues = new ClueViewModel[mPuzzleFile.getNumClues()];
        for (int i = 0; i < clues.length; i++) {
            clues[i] = new ClueViewModel();
            clues[i].setText(mPuzzleFile.getClue(i));
        }

        // Temporarily maps down clue numbers -> clue, for down clues only.
        Map<Integer, ClueViewModel> downClues = new ArrayMap<>();

        // Assign clue numbers and across clues.
        int nextClueNumber = 1;
        int currentClueIndex = 0;
        for (int row = 0; row < getNumRows(); row++) {
            ClueViewModel currentAcrossClue = null;
            for (int col = 0; col < getNumColumns(); col++) {
                if (mPuzzleFile.isBlack(row, col)) {
                    continue;
                }

                int clueNumber = 0;
                if (col == 0 || isBlack(row, col - 1) || row == 0 || isBlack(row - 1, col)) {
                    // New clue number
                    clueNumber = nextClueNumber++;

                    if (col == 0 || isBlack(row, col - 1)) {
                        // New across clue.
                        currentAcrossClue = clues[currentClueIndex++];
                        currentAcrossClue.setAcross(true);
                        currentAcrossClue.setNumber(clueNumber);
                    }

                    if (row == 0 || isBlack(row - 1, col)) {
                        // New down clue.
                        ClueViewModel clue = clues[currentClueIndex++];
                        clue.setAcross(false);
                        clue.setNumber(clueNumber);
                        downClues.put(clueNumber, clue);
                    }
                }

                // Create a ViewModel for the cell and add it to the current across clue.
                mGrid[row][col] = new CellViewModel(this, row, col);
                CellViewModel cellViewModel = mGrid[row][col];
                cellViewModel.setClueNumber(clueNumber);
                cellViewModel.setAcrossClue(currentAcrossClue);
                cellViewModel.getContents().setValue(mPuzzleFile.getCellContents(row, col));
                currentAcrossClue.addCell(cellViewModel);
            }
        }

        // Assign a down clue to each cell, and a list of cells to each down clue.
        for (int col = 0; col < getNumColumns(); col++) {
            ClueViewModel currentDownClue = null;
            for (int row = 0; row < getNumRows(); row++) {
                if (isBlack(row, col)) {
                    currentDownClue = null;
                    continue;
                }

                CellViewModel cellViewModel = mGrid[row][col];
                if (currentDownClue == null) {
                    // New down clue.
                    int clueNumber = cellViewModel.getClueNumber();
                    currentDownClue = downClues.get(clueNumber);
                }

                cellViewModel.setDownClue(currentDownClue);
                currentDownClue.addCell(cellViewModel);
            }
        }

        // Link Clue objects in two circular lists (across clues & down clues).
        linkClues(clues);

        // When across/down focus changes, or the current cell changes, update the currently
        // selected clue.
        Observer<Object> observer = new Observer<Object>() {
            @Override
            public void onChanged(Object o) {
                CellViewModel currentCell = mCurrentCell.getValue();
                if (currentCell == null) {
                    return;  // Shouldn't happen?
                }
                if (mAcrossFocus.getValue()) {
                    mCurrentClue.setValue(currentCell.getAcrossClue());
                } else {
                    mCurrentClue.setValue(currentCell.getDownClue());
                }
            }
        };
        mCurrentClue.addSource(mAcrossFocus, observer);
        mCurrentClue.addSource(mCurrentCell, observer);
    }

    private static void linkClues(ClueViewModel[] clues) {
        linkClues(clues, true);
        linkClues(clues, false);
    }

    private static void linkClues(ClueViewModel[] clues, boolean across) {
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
        firstClue.setPreviousClue(previousClue);
        previousClue.setNextClue(firstClue);
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

    public boolean isBlack(int row, int col) {
        return mPuzzleFile.isBlack(row, col);
    }

    public void onFocusChanged(int row, int col) {
        mCurrentCell.setValue(mGrid[row][col]);
    }

    public LiveData<ClueViewModel> getCurrentClue() {
        return mCurrentClue;
    }

    public LiveData<Integer> getCurrentClueNumber() {
        return Transformations.map(mCurrentClue, new Function<ClueViewModel, Integer>() {
            @Override
            public Integer apply(ClueViewModel clue) {
                return clue.getNumber();
            }
        });
    }

    public LiveData<String> getCurrentClueText() {
        return Transformations.map(mCurrentClue, new Function<ClueViewModel, String>() {
            @Override
            public String apply(ClueViewModel clue) {
                return clue.getText();
            }
        });
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

    private void moveToNextCell() {
        // Find our location in the current clue.
        CellViewModel currentCell = mCurrentCell.getValue();
        List<CellViewModel> currentClueCells = mCurrentClue.getValue().getCells();
        int i = currentClueCells.indexOf(currentCell);
        if (i < 0) {
            return;
        }
        for (int j = 1; j < currentClueCells.size(); j++) {
            int index = (i + j) % currentClueCells.size();
            if (currentClueCells.get(index).getContents().getValue().isEmpty()) {
                currentClueCells.get(index).requestFocus();
                return;
            }
        }

        // No empty cell available; move to next clue.
        moveToNextClue();
    }

    public void moveToPreviousClue() {
        ClueViewModel prev = mCurrentClue.getValue().getPreviousClue();
        while (prev != mCurrentClue.getValue()) {
            // Find empty cell.
            for (CellViewModel cell : prev.getCells()) {
                if (cell.getContents().getValue().isEmpty()) {
                    cell.requestFocus();
                    return;
                }
            }

            // No empty cell available.
            prev = prev.getPreviousClue();
        }

        // No empty clue available. Request first cell in previous clue.
        prev.getPreviousClue().getCells().get(0).requestFocus();
    }

    public void moveToNextClue() {
        ClueViewModel next = mCurrentClue.getValue().getNextClue();
        while (next != mCurrentClue.getValue()) {
            // Find empty cell.
            for (CellViewModel cell : next.getCells()) {
                if (cell.getContents().getValue().isEmpty()) {
                    mAcrossFocus.setValue(next.isAcross());
                    cell.requestFocus();
                    return;
                }
            }

            // No empty cell available.
            next = next.getNextClue();
        }

        // No empty clue available. Request first cell in next clue.
        next.getNextClue().getCells().get(0).requestFocus();
    }

    public void setCurrentCellContents(String newContents) {
        CellViewModel currentCell = mCurrentCell.getValue();
        String oldContents = currentCell.getContents().getValue();
        mUndoStack.push(new Action(currentCell, currentCell, oldContents, newContents));
        currentCell.getContents().setValue(newContents);
        moveToNextCell();
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

    public File getFile() {
        return mFile;
    }

    public AbstractPuzzleFile getPuzzleFile() {
        return mPuzzleFile;
    }
}

