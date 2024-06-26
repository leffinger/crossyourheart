package io.github.leffinger.crossyourheart.viewmodels;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.leffinger.crossyourheart.io.AbstractPuzzleFile;

public class PuzzleViewModel extends ViewModel {
    private static final String TAG = "PuzzleViewModel";
    /**
     * True if the currently active clue is an Across, false if Down.
     */
    private final MutableLiveData<Boolean> mAcrossFocus = new MutableLiveData<>();
    /**
     * The view model for the currently selected square.
     */
    private final MutableLiveData<CellViewModel> mCurrentCell = new MutableLiveData<>();
    /**
     * Currently active clue.
     */
    private final MediatorLiveData<ClueViewModel> mCurrentClue = new MediatorLiveData<>();
    /**
     * Currently active clue text.
     */
    private final MediatorLiveData<String> mCurrentClueText = new MediatorLiveData<>();
    /**
     * Listens for changes to cell contents.
     */
    private final MediatorLiveData<CellViewModel> mContentsChanged = new MediatorLiveData<>();
    /**
     * History of actions. Enables "undo" functionality.
     */
    private final Stack<Action> mUndoStack = new Stack<>();
    /**
     * Whether the puzzle's solution is currently correct.
     */
    private final MutableLiveData<Boolean> mIsSolved = new MutableLiveData<>();

    /**
     * Whether the timer is running, and how many seconds have elapsed.
     */
    private final MutableLiveData<AbstractPuzzleFile.TimerInfo> mTimerInfo =
            new MutableLiveData<>();

    /**
     * Whether to hide across clues.
     */
    private final MutableLiveData<Boolean> mDownsOnlyMode = new MutableLiveData<>();

    /**
     * True once the grid is ready to be viewed.
     */
    private final MutableLiveData<Boolean> mCellViewModelsReady = new MutableLiveData<>(false);

    /** Ensures that initialize() is only called once. */
    private final AtomicBoolean mInitialized = new AtomicBoolean(false);

    /**
     * Representation of on-disk puzzle file.
     */
    private AbstractPuzzleFile mPuzzleFile;
    /**
     * Name of the file where the puzzle should be saved.
     */
    private File mFile;
    /**
     * Grid of mutable CellViewModels. Black cells are null.
     */
    private CellViewModel[][] mGrid;

    private List<ClueViewModel> mAcrossClues;
    private List<ClueViewModel> mDownClues;

    private float mAverageWordLength;

    public PuzzleViewModel() {

    }

    // This can be called from a background thread, so it should not call setValue() on any
    // LiveData objects.
    public PuzzleViewModel(AbstractPuzzleFile puzzleFile, File file, boolean startWithDownClues) {
        initialize(puzzleFile, file, startWithDownClues, false);
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

    @SuppressLint("StaticFieldLeak")
    public void initialize(AbstractPuzzleFile puzzleFile, File file, boolean startWithDownClues,
                           boolean downsOnlyMode) {
        if (!mInitialized.compareAndSet(false, true)) {
            // already initialized
            return;
        }

        mPuzzleFile = puzzleFile;
        mFile = file;

        // Do as much as possible off the UI thread, but some tasks (e.g. addSource) must be done
        // on the UI thread.
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                // Construct a structure of ClueViewModels linked to CellViewModels, and vice versa.
                ClueViewModel[] clues = new ClueViewModel[mPuzzleFile.getNumClues()];
                for (int i = 0; i < clues.length; i++) {
                    AbstractPuzzleFile.Clue clue = mPuzzleFile.getClue(i);
                    clues[i] = new ClueViewModel(PuzzleViewModel.this, clue.isAcross(),
                            clue.getNumber(), clue.getText());
                }

                // Save clues for later retrieval.
                mAcrossClues = new ArrayList<>();
                mDownClues = new ArrayList<>();
                for (ClueViewModel clue : clues) {
                    if (clue.isAcross()) {
                        clue.setIndex(mAcrossClues.size());
                        mAcrossClues.add(clue);
                    } else {
                        clue.setIndex(mDownClues.size());
                        mDownClues.add(clue);
                    }
                }

                mGrid = new CellViewModel[getNumRows()][getNumColumns()];
                for (int row = 0; row < getNumRows(); row++) {
                    for (int col = 0; col < getNumColumns(); col++) {
                        if (mPuzzleFile.isBlack(row, col)) {
                            continue;
                        }

                        mGrid[row][col] = new CellViewModel(PuzzleViewModel.this, row, col,
                                mPuzzleFile.getCellContents(row, col),
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

                // Compute average word length.
                int totalLetters = 0;
                for (ClueViewModel clue : clues) {
                    totalLetters += clue.getCells().size();
                }
                mAverageWordLength = ((float) totalLetters) / clues.length;

                // Adds clue references (e.g. "see 15-Across") to ClueViewModels.
                boolean[][] clueReferences = mPuzzleFile.getClueReferences();
                for (int i = 0; i < clues.length; i++) {
                    for (int j = 0; j < clues.length; j++) {
                        if (clueReferences[i][j]) {
                            clues[j].addReference(clues[i]);
                        }
                    }
                }

                mAcrossFocus.postValue(!startWithDownClues);
                mIsSolved.postValue(puzzleFile.isSolved());
                mTimerInfo.postValue(puzzleFile.getTimerInfo());

                selectFirstCell();

                return null;
            }

            @SuppressLint("StaticFieldLeak")
            @Override
            protected void onPostExecute(Void aVoid) {
                mDownsOnlyMode.setValue(downsOnlyMode);

                // When across/down focus changes, or the current cell changes, update the currently
                // selected clue.
                Observer<Object> observer = o -> {
                    ClueViewModel oldValue = mCurrentClue.getValue();
                    CellViewModel currentCell = mCurrentCell.getValue();
                    Boolean acrossFocus = mAcrossFocus.getValue();

                    Log.i(TAG, "currentClue update: currentCell=" + currentCell + " acrossFocus=" +
                            acrossFocus);

                    if (currentCell == null || acrossFocus == null) {
                        return;
                    }
                    if (acrossFocus) {
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

                    ClueViewModel newClue = mCurrentClue.getValue();
                    if (oldValue != mCurrentClue.getValue()) {
                        Log.i(TAG, String.format("Selecting clue %d-%s", newClue.getNumber(),
                                newClue.isAcross() ? "A" : "D"));
                    }
                };
                mCurrentClue.addSource(mAcrossFocus, observer);
                mCurrentClue.addSource(mCurrentCell, observer);

                // Update the current clue's text based on the current clue and whether we are in
                // downs-only mode.
                Observer<Object> clueTextObserver = o -> {
                    ClueViewModel clue = mCurrentClue.getValue();
                    if (clue == null) {
                        return;
                    }
                    boolean downsOnlyMode = mDownsOnlyMode.getValue();

                    if (clue.isAcross() && downsOnlyMode) {
                        mCurrentClueText.setValue("--");
                    } else {
                        mCurrentClueText.setValue(clue.getText());
                    }
                };
                mCurrentClueText.addSource(mCurrentClue, clueTextObserver);
                mCurrentClueText.addSource(mDownsOnlyMode, clueTextObserver);

                // When text changes, trigger updates.
                for (int row = 0; row < getNumRows(); row++) {
                    for (int col = 0; col < getNumColumns(); col++) {
                        CellViewModel cellViewModel = mGrid[row][col];
                        if (cellViewModel == null) {
                            continue;
                        }
                        mContentsChanged.addSource(cellViewModel.getContents(), contents -> {
                            mPuzzleFile.setCellContents(cellViewModel.getRow(),
                                    cellViewModel.getCol(), contents);
                            mIsSolved.setValue(mPuzzleFile.isSolved());
                            mContentsChanged.setValue(cellViewModel);
                        });
                    }
                }

                mCellViewModelsReady.setValue(true);
            }
        }.execute();
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

    public int getNumColumns() { return mPuzzleFile.getWidth(); }

    public int getOffset(int row, int col) {
        return mPuzzleFile.getOffset(row, col);
    }

    public LiveData<ClueViewModel> getCurrentClue() {
        return mCurrentClue;
    }

    public LiveData<Integer> getCurrentClueNumber() {
        return Transformations.map(mCurrentClue, clue -> clue == null ? 0 : clue.getNumber());
    }

    public LiveData<String> getCurrentClueText() {
        return mCurrentClueText;
    }

    public LiveData<Boolean> getAcrossFocus() {
        return mAcrossFocus;
    }

    public LiveData<Boolean> isSolved() {
        return mIsSolved;
    }

    public boolean toggleDownsOnlyMode() {
        boolean downsOnlyMode = !mDownsOnlyMode.getValue();
        mDownsOnlyMode.setValue(downsOnlyMode);
        return downsOnlyMode;
    }

    public MutableLiveData<Boolean> isDownsOnlyMode() {
        return mDownsOnlyMode;
    }

    public void toggleDirection() {
        mAcrossFocus.setValue(!mAcrossFocus.getValue());
    }

    private CellViewModel getNextCell(boolean wasFilled, boolean skipFilledClues,
                                      boolean skipFilledSquares, boolean unlessCurrentSquareFilled,
                                      boolean skipFilledSquaresWrap, boolean completedClueNext) {
        // Find our location in the current clue.
        CellViewModel currentCell = mCurrentCell.getValue();
        List<CellViewModel> currentClueCells = mCurrentClue.getValue().getCells();
        int i = currentClueCells.indexOf(currentCell);
        if (i < 0) {
            Log.e(TAG, "CellViewModel should be in ClueViewModel but isn't!");
            return currentCell;
        }

        boolean clueWasFilled = wasFilled;
        if (wasFilled) {
            for (CellViewModel cell : currentClueCells) {
                if (cell.getContents().getValue().isEmpty()) {
                    clueWasFilled = false;
                    break;
                }
            }
        }

        if (clueWasFilled || !skipFilledSquares || (wasFilled && unlessCurrentSquareFilled)) {
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
            return getCellInNextClue(skipFilledClues, true);
        }

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

        mAcrossFocus.setValue(next.isAcross());
        return cell;
    }

    public void setCurrentCellContents(String newContents, boolean skipFilledClues,
                                       boolean skipFilledSquares, boolean unlessCurrentSquareFilled,
                                       boolean skipFilledSquaresWrap, boolean completedClueNext,
                                       boolean usePencil) {
        CellViewModel currentCell = mCurrentCell.getValue();
        boolean pencil = currentCell.getPencil().getValue();
        String oldContents = currentCell.getContents().getValue();
        currentCell.setContents(newContents, usePencil);
        mUndoStack.push(
                new Action(currentCell, currentCell, oldContents, mAcrossFocus.getValue(), pencil));
        CellViewModel newCell =
                getNextCell(!oldContents.isEmpty(), skipFilledClues, skipFilledSquares,
                        unlessCurrentSquareFilled, skipFilledSquaresWrap, completedClueNext);
        mCurrentCell.setValue(newCell);
    }

    public void doUndo() {
        if (mUndoStack.empty()) {
            return;
        }

        Action lastAction = mUndoStack.pop();
        lastAction.mModifiedCell.setContents(lastAction.mOldContents, lastAction.mPencil);
        mCurrentCell.setValue(lastAction.mSelectedCell);
        mAcrossFocus.setValue(lastAction.mAcrossFocus);
    }

    public void doBackspace() {
        // If current cell is empty, move to the previous cell and delete its contents.
        CellViewModel currentCell = mCurrentCell.getValue();
        if (currentCell.getContents().getValue().isEmpty()) {
            ClueViewModel currentClue = mCurrentClue.getValue();
            List<CellViewModel> cells = currentClue.getCells();
            int i = cells.indexOf(currentCell);

            CellViewModel newCell;
            boolean across;
            if (i == 0) {
                // Move to the last cell of the previous clue.
                ClueViewModel previousClue = currentClue.getPreviousClue();
                newCell = previousClue.getCells().get(previousClue.getCells().size() - 1);
                across = previousClue.isAcross();
            } else {
                // Move to the previous cell.
                newCell = cells.get(i - 1);
                across = currentClue.isAcross();
            }

            // Delete the cell's contents and move to that cell.
            boolean pencil = newCell.getPencil().getValue();
            String oldContents = newCell.getContents().getValue();
            newCell.setContents("", false);
            mUndoStack.push(
                    new Action(newCell, currentCell, oldContents, mAcrossFocus.getValue(), pencil));
            mCurrentCell.setValue(newCell);
            mAcrossFocus.setValue(across);
        } else {
            // Delete current cell's contents.
            boolean pencil = currentCell.getPencil().getValue();
            String oldContents = currentCell.getContents().getValue();
            currentCell.setContents("", false);
            mUndoStack.push(
                    new Action(currentCell, currentCell, oldContents, mAcrossFocus.getValue(),
                            pencil));
        }
    }

    public void saveToFile() throws IOException {
        mPuzzleFile.setTimerInfo(mTimerInfo.getValue());
        mPuzzleFile.savePuzzleFile(mFile);
    }

    public PuzzleInfoViewModel getPuzzleInfoViewModel() {
        return new PuzzleInfoViewModel(getTitle(), getAuthor(), getCopyright(), getNote(),
                mPuzzleFile.getNumClues(), getNumColumns(), getNumRows(), mAverageWordLength);
    }

    public File getFile() {
        return mFile;
    }

    public AbstractPuzzleFile getPuzzleFile() {
        return mPuzzleFile;
    }

    public void resetPuzzle() {
        mTimerInfo.setValue(new AbstractPuzzleFile.TimerInfo(0L, true));
        for (CellViewModel[] row : mGrid) {
            for (CellViewModel cell : row) {
                if (cell != null) {
                    cell.reset();
                }
            }
        }
        mUndoStack.clear();
    }

    public boolean isCorrect(int row, int col) {
        return mPuzzleFile.isCorrect(row, col);
    }

    public boolean isCheckable() {
        return mPuzzleFile.getScrambleState() == AbstractPuzzleFile.ScrambleState.UNSCRAMBLED;
    }

    public void checkCurrentCell() {
        checkCell(mCurrentCell.getValue());
    }

    public void checkCell(int row, int col) {
        checkCell(mGrid[row][col]);
    }

    private void checkCell(CellViewModel cell) {
        if (!isCheckable()) {
            return;
        }
        if (cell != null) {
            cell.checkContents();
        }
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

    public String getCellContents(int row, int col) {
        return mPuzzleFile.getCellContents(row, col);
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

    public LiveData<CellViewModel> getContentsChanged() {
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

    public void selectClue(ClueViewModel clueViewModel, int position) {
        mCurrentCell.setValue(clueViewModel.getCells().get(position));
        mAcrossFocus.setValue(clueViewModel.isAcross());
    }

    public void selectFirstCell() {
        for (CellViewModel cellViewModel : mGrid[0]) {
            if (cellViewModel != null) {
                mCurrentCell.postValue(cellViewModel);
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

    public MutableLiveData<AbstractPuzzleFile.TimerInfo> getTimerInfo() {
        return mTimerInfo;
    }

    public ClueViewModel getClue(boolean isAcross, int i) {
        if (isAcross) {
            return mAcrossClues.get(i);
        }
        return mDownClues.get(i);
    }

    public int getNumAcrossClues() {
        return mAcrossClues.size();
    }

    public int getNumDownClues() {
        return mDownClues.size();
    }

    public LiveData<Boolean> cellViewModelsReady() {
        return mCellViewModelsReady;
    }

    /**
     * Actions are stored in the undo stack.
     */
    private static class Action {
        final CellViewModel mModifiedCell;
        final CellViewModel mSelectedCell;
        final String mOldContents;
        final boolean mAcrossFocus;
        final boolean mPencil;

        public Action(CellViewModel modifiedCell, CellViewModel selectedCell, String oldContents,
                      boolean acrossFocus, boolean pencil) {
            mModifiedCell = modifiedCell;
            mSelectedCell = selectedCell;
            mOldContents = oldContents;
            mAcrossFocus = acrossFocus;
            mPencil = pencil;
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

