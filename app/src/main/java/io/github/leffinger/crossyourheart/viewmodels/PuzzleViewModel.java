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
     * The view model for the currently selected square.
     */
    private final MutableLiveData<CellViewModel> mCurrentCell = new MutableLiveData<>(null);

    /**
     * Currently active clue.
     */
    private final MediatorLiveData<ClueViewModel> mCurrentClue = new MediatorLiveData<>();

    /**
     * Listens for changes to cell contents.
     */
    private final MediatorLiveData<Integer> mContentsChanged = new MediatorLiveData<>();

    /**
     * History of actions. Enables "undo" functionality.
     */
    private final Stack<Action> mUndoStack = new Stack<>();

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
        mIsSolved = new MutableLiveData<>(puzzleFile.isSolved());

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
                    mAcrossFocus.setValue(false);  // should trigger another update
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

        // When text changes, trigger updates.
        for (int row = 0; row < getNumRows(); row++) {
            for (int col = 0; col < getNumColumns(); col++) {
                CellViewModel cellViewModel = mGrid[row][col];
                if (cellViewModel == null) {
                    continue;
                }
                mContentsChanged.addSource(cellViewModel.getContents(), contents -> {
                    mPuzzleFile.setCellContents(cellViewModel.getRow(), cellViewModel.getCol(),
                                                contents);
                    mIsSolved.setValue(mPuzzleFile.isSolved());
                    mContentsChanged.setValue(0);
                });
            }
        }
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
        return mIsSolved;
    }

    public void toggleDirection() {
        mAcrossFocus.setValue(!mAcrossFocus.getValue());
    }

    private CellViewModel getNextCell(boolean wasFilled, boolean skipFilledClues,
                                      boolean skipFilledSquares, boolean skipFilledSquaresWrap,
                                      boolean completedClueNext) {
        // Find our location in the current clue.
        CellViewModel currentCell = mCurrentCell.getValue();
        List<CellViewModel> currentClueCells = mCurrentClue.getValue().getCells();
        int i = currentClueCells.indexOf(currentCell);
        if (i < 0) {
            Log.e(TAG, "CellViewModel should be in ClueViewModel but isn't!");
            return currentCell;
        }

        if (!wasFilled) {
            // Behavior after filling in an empty square.
            if (skipFilledSquares) {
                // Move to next empty square in the clue
                if (i < currentClueCells.size() - 1) {
                    for (int j = i + 1; j < currentClueCells.size(); j++) {
                        if (currentClueCells.get(j).getContents().getValue().isEmpty()) {
                            return currentClueCells.get(j);
                        }
                    }
                }
                // Last (empty) square in the clue. Wrap around?
                if (skipFilledSquaresWrap) {
                    for (int j = 0; j < i; j++) {
                        if (currentClueCells.get(j).getContents().getValue().isEmpty()) {
                            return currentClueCells.get(j);
                        }
                    }
                }
                // Move to next clue?
                if (completedClueNext) {
                    return getCellInNextClue(skipFilledClues, skipFilledSquares);
                }
                return currentCell;
            }

            // Move to next square in the clue, regardless of whether it is filled.
            if (i < currentClueCells.size() - 1) {
                return currentClueCells.get(i + 1);
            }
            // Last square in the clue. Wrap, next, or stop?
            if (completedClueNext) {
                // Move to the next clue.
                return getCellInNextClue(skipFilledClues, skipFilledSquares);
            }
            // Stop.
            return currentCell;

        }

        // Behavior after editing a previously-filled square.
        // Move to next square in the clue, regardless of whether it is filled.
        if (i < currentClueCells.size() - 1) {
            return currentClueCells.get(i + 1);
        }
        // Last square in the clue.
        if (completedClueNext) {
            // Move to the next clue.
            return getCellInNextClue(skipFilledClues, skipFilledSquares);
        }
        // Stop.
        return currentCell;

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
        mCurrentCell.setValue(cell);
    }

    public void moveToNextClue(boolean skipFilledClues, boolean skipFilledSquares) {
        CellViewModel cell = getCellInNextClue(skipFilledClues, skipFilledSquares);
        mCurrentCell.setValue(cell);
    }

    private CellViewModel getCellInNextClue(boolean skipFilledClues, boolean skipFilledSquares) {
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
        return cell;
    }

    public void setCurrentCellContents(String newContents, boolean skipFilledClues,
                                       boolean skipFilledSquares, boolean skipFilledSquaresWrap,
                                       boolean completedClueNext) {
        CellViewModel currentCell = mCurrentCell.getValue();
        String oldContents = currentCell.setContents(newContents);
        mUndoStack.push(new Action(currentCell, currentCell, oldContents, newContents,
                                   mAcrossFocus.getValue()));
        CellViewModel newCell =
                getNextCell(!oldContents.isEmpty(), skipFilledClues, skipFilledSquares,
                            skipFilledSquaresWrap, completedClueNext);
        mCurrentCell.setValue(newCell);
    }

    public void doUndo() {
        if (mUndoStack.empty()) {
            return;
        }

        Action lastAction = mUndoStack.pop();
        lastAction.mModifiedCell.setContents(lastAction.mOldContents);
        mCurrentCell.setValue(lastAction.mSelectedCell);
        mAcrossFocus.setValue(lastAction.mAcrossFocus);
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
            String oldContents = newCell.setContents("");
            mUndoStack.push(new Action(newCell, currentCell, oldContents, "",
                                       mAcrossFocus.getValue()));
            mCurrentCell.setValue(newCell);
        } else {
            // Delete current cell's contents.
            String oldContents = currentCell.setContents("");
            mUndoStack.push(new Action(currentCell, currentCell, oldContents, "",
                                       mAcrossFocus.getValue()));
        }
    }

    public void saveToFile() throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(mFile)) {
            mPuzzleFile.savePuzzleFile(outputStream);
        }
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

    public void resetPuzzle() {
        for (CellViewModel[] row : mGrid) {
            for (CellViewModel cell : row) {
                if (cell != null) {
                    cell.reset();
                }
            }
        }
        mUndoStack.clear();
        mIsSolved.setValue(false);
    }

    public boolean isCorrect(int row, int col) {
        return mPuzzleFile.isCorrect(row, col);
    }

    public boolean isCheckable() {
        return mPuzzleFile.getScrambleState() == AbstractPuzzleFile.ScrambleState.UNSCRAMBLED;
    }

    public void checkCurrentCell() {
        if (!isCheckable()) {
            return;
        }
        mCurrentCell.getValue().checkContents();
    }

    public void checkCurrentClue() {
        if (!isCheckable()) {
            return;
        }
        for (CellViewModel cell : mCurrentClue.getValue().getCells()) {
            cell.checkContents();
        }
    }

    public void checkPuzzle() {
        if (!isCheckable()) {
            return;
        }
        for (CellViewModel[] row : mGrid) {
            for (CellViewModel cell : row) {
                if (cell != null) {
                    cell.checkContents();
                }
            }
        }
    }

    public String getSolution(int row, int col) {
        return mPuzzleFile.getSolution(row, col);
    }

    public void revealCurrentCell() {
        if (!isCheckable()) {
            return;
        }
        mCurrentCell.getValue().revealContents();
    }

    public void revealCurrentClue() {
        if (!isCheckable()) {
            return;
        }
        for (CellViewModel cell : mCurrentClue.getValue().getCells()) {
            cell.revealContents();
        }
    }

    public void revealPuzzle() {
        if (!isCheckable()) {
            return;
        }
        for (CellViewModel[] row : mGrid) {
            for (CellViewModel cell : row) {
                if (cell != null) {
                    cell.revealContents();
                }
            }
        }
    }

    public LiveData<CellViewModel> getCurrentCell() {
        return mCurrentCell;
    }

    public LiveData<Integer> getContentsChanged() {
        return mContentsChanged;
    }

    public void selectCell(CellViewModel cellViewModel) {
        if (cellViewModel == null) {
            return;
        }
        if (mCurrentCell.getValue() == cellViewModel) {
            // Toggle directions.
            mAcrossFocus.setValue(!mAcrossFocus.getValue());
        } else {
            mCurrentCell.setValue(cellViewModel);
        }
    }

    public void selectFirstCell() {
        for (CellViewModel cellViewModel : mGrid[0]) {
            if (cellViewModel != null) {
                mCurrentCell.setValue(cellViewModel);
                return;
            }
        }
    }

    public void moveToNextCell() {
        CellViewModel currentCell = mCurrentCell.getValue();
        Position position = new Position(currentCell.getRow(), currentCell.getCol());
        boolean isAcross = mAcrossFocus.getValue();
        do {
            if (isAcross) {
                position.moveRowMajor(1);
            } else {
                position.moveColumnMajor(1);
            }
        } while (mGrid[position.row][position.col] == null);
        mCurrentCell.setValue(mGrid[position.row][position.col]);
    }

    public void moveToPreviousCell() {
        CellViewModel currentCell = mCurrentCell.getValue();
        Position position = new Position(currentCell.getRow(), currentCell.getCol());
        boolean isAcross = mAcrossFocus.getValue();
        do {
            if (isAcross) {
                position.moveRowMajor(-1);
            } else {
                position.moveColumnMajor(-1);
            }
        } while (mGrid[position.row][position.col] == null);
        mCurrentCell.setValue(mGrid[position.row][position.col]);
    }

    private static class Action {
        final CellViewModel mModifiedCell;
        final CellViewModel mSelectedCell;
        final String mOldContents;
        final String mNewContents;
        final boolean mAcrossFocus;

        public Action(CellViewModel modifiedCell, CellViewModel selectedCell, String oldContents,
                      String newContents, boolean acrossFocus) {
            mModifiedCell = modifiedCell;
            mSelectedCell = selectedCell;
            mOldContents = oldContents;
            mNewContents = newContents;
            mAcrossFocus = acrossFocus;
        }
    }

    /**
     * Helper class for moving around the grid in row-major and column-major ways.
     */
    private class Position {
        int row;
        int col;

        public Position(int row, int col) {
            this.row = row;
            this.col = col;
        }

        public void moveRowMajor(int offset) {
            int width = mPuzzleFile.getWidth();
            int height = mPuzzleFile.getHeight();
            int rowMajorPosition = row * width + col;
            rowMajorPosition = (rowMajorPosition + (width * height) + offset) % (width * height);
            row = rowMajorPosition / width;
            col = rowMajorPosition % width;
        }

        public void moveColumnMajor(int offset) {
            int width = mPuzzleFile.getWidth();
            int height = mPuzzleFile.getHeight();
            int columnMajorPosition = col * height + row;
            columnMajorPosition =
                    (columnMajorPosition + (width * height) + offset) % (width * height);
            row = columnMajorPosition % height;
            col = columnMajorPosition / height;
        }
    }
}

