package io.github.leffinger.crossyourheart.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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
    private final MutableLiveData<Boolean> mAcrossFocus = new MutableLiveData<>(true);
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

        StringBuilder solution = new StringBuilder();
        for (int row = 0; row < getNumRows(); row++) {
            for (int col = 0; col < getNumColumns(); col++) {
                solution.append(puzzleFile.getCellSolution(row, col));
            }
            solution.append('\n');
        }

        mGrid = new CellViewModel[getNumRows()][getNumColumns()];

        ClueViewModel[] clues = new ClueViewModel[mPuzzleFile.getNumClues()];
        for (int i = 0; i < clues.length; i++) {
            clues[i] = new ClueViewModel();
            clues[i].setText(mPuzzleFile.getClue(i));
        }

        // Split cells into groups of across clues, iterating in row-major order.
        List<List<CellViewModel>> acrossClues = new ArrayList<>();
        for (int row = 0; row < getNumRows(); row++) {
            List<CellViewModel> nextClue = null;
            for (int col = 0; col < getNumColumns(); col++) {
                if (mPuzzleFile.isBlack(row, col)) {
                    if (nextClue != null && nextClue.size() > 2) {
                        acrossClues.add(nextClue);
                    }
                    nextClue = null;
                } else {
                    if (nextClue == null) {
                        nextClue = new ArrayList<>();
                    }
                    String contents = puzzleFile.getCellContents(row, col);
                    mGrid[row][col] = new CellViewModel(this, row, col, contents);
                    nextClue.add(mGrid[row][col]);
                }
            }
            if (nextClue != null && nextClue.size() > 2) {
                acrossClues.add(nextClue);
            }
        }

        // Split cells into groups of down clues, iterating in column-major order.
        List<List<CellViewModel>> downClues = new ArrayList<>();
        for (int col = 0; col < getNumColumns(); col++) {
            List<CellViewModel> nextClue = null;
            for (int row = 0; row < getNumRows(); row++) {
                if (mPuzzleFile.isBlack(row, col)) {
                    if (nextClue != null && nextClue.size() > 2) {
                        downClues.add(nextClue);
                    }
                    nextClue = null;
                } else {
                    if (nextClue == null) {
                        nextClue = new ArrayList<>();
                    }
                    nextClue.add(mGrid[row][col]);
                }
            }
            if (nextClue != null && nextClue.size() > 2) {
                downClues.add(nextClue);
            }
        }

        // TODO(effinger): Verify this during puzzle load so we fail earlier.
        if (acrossClues.size() + downClues.size() != mPuzzleFile.getNumClues()) {
            throw new RuntimeException(String.format(
                    "Wrong number of clues: expected %d, but had %d across and %d down (%d total)",
                    mPuzzleFile.getNumClues(), acrossClues.size(), downClues.size(),
                    acrossClues.size() + downClues.size()));
        }

        // Sort down clues in row-major order for clue assignment.
        Collections.sort(downClues, (clue1, clue2) -> clue1.get(0).compareTo(clue2.get(0)));

        // For each group of cells, assign it to a ClueViewModel.
        int acrossIndex = 0;
        int downIndex = 0;
        int clueNumber = 1;
        for (int clueIndex = 0; clueIndex < clues.length; clueIndex++) {
            ClueViewModel clueViewModel = clues[clueIndex];
            if (acrossIndex >= acrossClues.size()) {
                // No more across clues
                clueViewModel.setClueInfo(false, downClues.get(downIndex++), clueNumber++);
            } else if (downIndex >= downClues.size()) {
                // No more down clues
                clueViewModel.setClueInfo(true, acrossClues.get(acrossIndex++), clueNumber++);
            } else {
                List<CellViewModel> acrossCells = acrossClues.get(acrossIndex);
                List<CellViewModel> downCells = downClues.get(downIndex);
                int cmp = acrossCells.get(0).compareTo(downCells.get(0));
                if (cmp == 0) {
                    // Across and down start on the same square and share a clue number.
                    clueViewModel.setClueInfo(true, acrossClues.get(acrossIndex++), clueNumber);
                    clueViewModel = clues[++clueIndex];
                    clueViewModel.setClueInfo(false, downClues.get(downIndex++), clueNumber++);
                } else if (cmp <= 0) {
                    // Across clue.
                    clueViewModel.setClueInfo(true, acrossClues.get(acrossIndex++), clueNumber++);
                } else {
                    // Down clue.
                    clueViewModel.setClueInfo(false, downClues.get(downIndex++), clueNumber++);
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
                    mAcrossFocus.setValue(prev.isAcross());
                    return;
                }
            }

            // No empty cell available.
            prev = prev.getPreviousClue();
        }

        // No empty clue available. Request first cell in previous clue.
        prev.getPreviousClue().getCells().get(0).requestFocus();
        mAcrossFocus.setValue(prev.getPreviousClue().isAcross());
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
        mAcrossFocus.setValue(next.getNextClue().isAcross());
    }

    public String getCurrentCellContents() {
        return mCurrentCell.getValue().getContents().getValue();
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

