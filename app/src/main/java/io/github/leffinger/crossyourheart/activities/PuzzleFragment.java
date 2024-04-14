package io.github.leffinger.crossyourheart.activities;

import static android.app.Activity.RESULT_OK;
import static android.inputmethodservice.Keyboard.KEYCODE_CANCEL;
import static android.inputmethodservice.Keyboard.KEYCODE_DELETE;
import static android.inputmethodservice.Keyboard.KEYCODE_MODE_CHANGE;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.MenuProvider;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.github.leffinger.crossyourheart.R;
import io.github.leffinger.crossyourheart.databinding.FragmentPuzzleBinding;
import io.github.leffinger.crossyourheart.databinding.SimpleCellBinding;
import io.github.leffinger.crossyourheart.room.Cell;
import io.github.leffinger.crossyourheart.room.Database;
import io.github.leffinger.crossyourheart.room.Puzzle;
import io.github.leffinger.crossyourheart.room.PuzzleDao;
import io.github.leffinger.crossyourheart.viewmodels.CellViewModel;
import io.github.leffinger.crossyourheart.viewmodels.ClueViewModel;
import io.github.leffinger.crossyourheart.viewmodels.PuzzleViewModel;

/**
 * Puzzle-solving activity.
 */
public class PuzzleFragment extends Fragment {
    // Instance state arguments.
    public static final String ARG_FILENAME = "filename";
    public static final String ARG_PUZZLE = "puzzle";
    private static final String TAG = "PuzzleFragment";
    private static final String ARG_USE_PENCIL = "usePencil";
    private static final String ARG_DOWNS_ONLY_MODE = "downsOnlyMode";
    private static final String ARG_AUTOCHECK_MODE = "autocheckMode";
    // Activity request codes.
    private static final int REQUEST_CODE_REBUS_ENTRY = 0;
    // Used to write puzzle file changes to disk in the background.
    private final ExecutorService mExecutorService = Executors.newSingleThreadExecutor();

    private boolean mInitialDownsOnlyMode;
    private boolean mAutocheckMode;

    // View and model state.
    private FragmentPuzzleBinding mFragmentPuzzleBinding;
    private GridLayoutManager mGridLayoutManager;
    private CellAdapter mCellAdapter;
    private Menu mMenu;
    private boolean mUsePencil;
    private Typeface mTypeface;

    // State that is only available when the fragment is attached.
    private Database mDatabase;
    private PuzzleViewModel mPuzzleViewModel;
    private SharedPreferences mPreferences;

    public static PuzzleFragment newInstance(Puzzle puzzle) {
        Bundle args = new Bundle();
        args.putBoolean(ARG_USE_PENCIL, puzzle.usePencil);
        args.putBoolean(ARG_DOWNS_ONLY_MODE, puzzle.downsOnlyMode);
        PuzzleFragment fragment = new PuzzleFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        Log.i(TAG, "onAttach");
        super.onAttach(context);

        // Attach activity- and application-dependent state.
        mDatabase = Database.getInstance(context.getApplicationContext());
        mPuzzleViewModel =
                new ViewModelProvider((ViewModelStoreOwner) context).get(PuzzleViewModel.class);
        mPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        Log.i(TAG, "Closing database");
        mDatabase.close();
        mDatabase = null;
        mPuzzleViewModel = null;
        mPreferences = null;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Log.e(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        Bundle bundle;
        if (savedInstanceState != null) {
            bundle = savedInstanceState;
        } else {
            bundle = requireArguments();
        }

        mCellAdapter =
                new CellAdapter(mPuzzleViewModel.getNumColumns(), mPuzzleViewModel.getNumRows());
        mTypeface = Typeface.create(
                mPreferences.getString(getString(R.string.preference_font_selection),
                        getString(R.string.default_font_family)), Typeface.NORMAL);
        mUsePencil = bundle.getBoolean(ARG_USE_PENCIL, false);
        mInitialDownsOnlyMode = bundle.getBoolean(ARG_DOWNS_ONLY_MODE,
                mPuzzleViewModel.isDownsOnlyMode().getValue());
        mAutocheckMode = bundle.getBoolean(ARG_AUTOCHECK_MODE, false);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Log.e(TAG, "onCreateView");
        mFragmentPuzzleBinding =
                DataBindingUtil.inflate(inflater, R.layout.fragment_puzzle, container, false);
        mFragmentPuzzleBinding.setLifecycleOwner(getActivity());
        mFragmentPuzzleBinding.puzzle.setPuzzleSize(mPuzzleViewModel.getNumRows(), mPuzzleViewModel.getNumColumns());

        mGridLayoutManager = new GridLayoutManager(getActivity(), mPuzzleViewModel.getNumColumns(),
                GridLayoutManager.VERTICAL, false);
        mFragmentPuzzleBinding.puzzle.setLayoutManager(mGridLayoutManager);
        mFragmentPuzzleBinding.puzzle.setAdapter(mCellAdapter);

        Keyboard keyboard = new Keyboard(getActivity(), R.xml.keys_layout);
        mFragmentPuzzleBinding.keyboard.setKeyboard(keyboard);
        mFragmentPuzzleBinding.keyboard.setPreviewEnabled(false);

        setUpViewModelListeners();

        return mFragmentPuzzleBinding.getRoot();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(ARG_FILENAME, mPuzzleViewModel.getFile().getName());
        outState.putSerializable(ARG_PUZZLE, mPuzzleViewModel.getPuzzleFile());
        outState.putBoolean(ARG_USE_PENCIL, mUsePencil);
        outState.putBoolean(ARG_AUTOCHECK_MODE, mAutocheckMode);
    }

    @Override
    public void onResume() {
        Log.i(TAG, "onResume");
        super.onResume();
        if (isDetached()) {
            return;
        }

        // Autocheck the grid, if enabled.
        if (mAutocheckMode) {
            mPuzzleViewModel.checkPuzzle();
        }
    }

    @Override
    public void onStart() {
        Log.i(TAG, "onStart");
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        try {
            mExecutorService.awaitTermination(1000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        try {
            mPuzzleViewModel.saveToFile();
        } catch (IOException e) {
            Log.e(TAG, String.format("Saving puzzle file %s failed",
                    mPuzzleViewModel.getFile().getName()), e);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Log.i(TAG, "onViewCreated");
        requireActivity().addMenuProvider(new PuzzleMenuProvider(), getViewLifecycleOwner());
    }

    private void setUpViewModelListeners() {
        Log.i(TAG, "Initializing PuzzleViewModel listeners");

        // Sets up basic data binding, e.g. clue number & text.
        mFragmentPuzzleBinding.setViewModel(mPuzzleViewModel);

        // Change direction when clue is tapped (if configured).
        mFragmentPuzzleBinding.clue.setOnClickListener(view -> {
            if (mPreferences.getBoolean(
                    getContext().getString(R.string.preference_tap_clue_behavior), true)) {
                doHapticFeedback(mFragmentPuzzleBinding.clue, HapticFeedbackConstants.KEYBOARD_TAP);
                mPuzzleViewModel.toggleDirection();
            }
        });

        // Open clue list view when the clue is long-tapped.
        mFragmentPuzzleBinding.clue.setOnLongClickListener(v -> {
            doHapticFeedback(mFragmentPuzzleBinding.clue, HapticFeedbackConstants.LONG_PRESS);
            return openClueListView();
        });

        // Move to previous clue when button is pressed.
        mFragmentPuzzleBinding.prev.setOnClickListener(view -> {
            doHapticFeedback(mFragmentPuzzleBinding.prev, HapticFeedbackConstants.KEYBOARD_TAP);
            mPuzzleViewModel.moveToPreviousClue(skipFilledClues(), skipFilledSquares());
            adjustViewport();
        });

        // Move to next clue when button is pressed.
        mFragmentPuzzleBinding.next.setOnClickListener(view -> {
            doHapticFeedback(mFragmentPuzzleBinding.next, HapticFeedbackConstants.KEYBOARD_TAP);
            mPuzzleViewModel.moveToNextClue(skipFilledClues(), skipFilledSquares());
            adjustViewport();
        });

        // Move to next cell when button is pressed.
        mFragmentPuzzleBinding.nextCell.setOnClickListener(view -> {
            doHapticFeedback(mFragmentPuzzleBinding.nextCell, HapticFeedbackConstants.KEYBOARD_TAP);
            mPuzzleViewModel.moveToNextCell();
            adjustViewport();
        });

        // Move to previous cell when button is pressed.
        mFragmentPuzzleBinding.prevCell.setOnClickListener(view -> {
            doHapticFeedback(mFragmentPuzzleBinding.prevCell, HapticFeedbackConstants.KEYBOARD_TAP);
            mPuzzleViewModel.moveToPreviousCell();
            adjustViewport();
        });

        // Set up keyboard listener.
        mFragmentPuzzleBinding.keyboard.setOnKeyboardActionListener(new PuzzleKeyboardListener());

        // Persist changes in content to disk.
        mPuzzleViewModel.getContentsChanged()
                        .observe(getActivity(), unused -> mExecutorService.submit(() -> {
                            try {
                                mPuzzleViewModel.saveToFile();
                            } catch (IOException e) {
                                Log.e(TAG, String.format("Saving puzzle file %s failed",
                                        mPuzzleViewModel.getFile().getName()), e);
                            }
                        }));

        // Autocheck, if enabled.
        mPuzzleViewModel.getContentsChanged().observe(getViewLifecycleOwner(), cellViewModel -> {
            if (mAutocheckMode) {
                mPuzzleViewModel.checkCell(cellViewModel.getRow(), cellViewModel.getCol());
            }
        });


        mPuzzleViewModel.isSolved()
                        .observe(getViewLifecycleOwner(), solved -> AsyncTask.execute(
                                () -> mDatabase.puzzleDao()
                                               .updateSolved(new PuzzleDao.SolvedUpdate(
                                                       mPuzzleViewModel.getFile().getName(),
                                                       solved))));

        mPuzzleViewModel.cellViewModelsReady().observe(getViewLifecycleOwner(), ready -> {
            if (!ready) return;
            // Populate pencil status for each cell.
            final PuzzleViewModel viewModel = mPuzzleViewModel;
            final Database database = mDatabase;
            Executors.newSingleThreadExecutor().execute(() -> {
                List<Cell> allCells =
                        database.cellDao().getCellsForPuzzle(viewModel.getFile().getName());
                for (Cell cell : allCells) {
                    CellViewModel cellViewModel = viewModel.getCellViewModel(cell.row, cell.col);
                    cellViewModel.getPencil().postValue(cell.pencil);
                }
            });

            // Persist pencil state to DB.
            for (int row = 0; row < viewModel.getNumRows(); row++) {
                for (int col = 0; col < viewModel.getNumColumns(); col++) {
                    CellViewModel cellViewModel = viewModel.getCellViewModel(row, col);
                    if (cellViewModel == null) {
                        continue;
                    }
                    cellViewModel.getPencil().observe(getViewLifecycleOwner(), pencil -> {
                        AsyncTask.execute(() -> mDatabase.cellDao()
                                                         .insert(new Cell(
                                                                 viewModel.getFile().getName(),
                                                                 cellViewModel.getRow(),
                                                                 cellViewModel.getCol(), pencil)));
                    });
                }
            }
        });
    }

    private void adjustViewport() {
        ClueViewModel clueViewModel = mPuzzleViewModel.getCurrentClue().getValue();
        CellViewModel cellViewModel = mPuzzleViewModel.getCurrentCell().getValue();
        if (clueViewModel == null || cellViewModel == null) {
            Log.w(TAG, "Unable to adjust because at least one ViewModel is null");
            return;
        }
        int selectedCell = cellViewModel.getOffset();
        List<CellViewModel> clueCells = clueViewModel.getCells();
        int firstCellInClue = clueCells.get(0).getOffset();
        int lastCellInClue = clueCells.get(clueCells.size() - 1).getOffset();
        mFragmentPuzzleBinding.puzzle.adjustViewport(firstCellInClue, selectedCell, lastCellInClue);
    }

    private boolean openClueListView() {
        Activity activity = getActivity();
        if (activity == null) {
            return false;
        }
        ((Callbacks) activity).onClueListViewSelected();
        return true;
    }

    private void configureUsePencilMenuItem() {
        MenuItem item = mMenu.findItem(R.id.pencil);
        item.setChecked(mUsePencil);
        if (mUsePencil) {
            item.getIcon().setColorFilter(Color.WHITE, PorterDuff.Mode.XOR);
        } else {
            item.getIcon().clearColorFilter();
        }
    }

    private void configureDownsOnlyModeMenuItem(boolean downsOnlyMode) {
        MenuItem item = mMenu.findItem(R.id.downs_only_mode);
        item.setChecked(downsOnlyMode);
        if (downsOnlyMode) {
            item.getIcon().setColorFilter(Color.WHITE, PorterDuff.Mode.XOR);
        } else {
            item.getIcon().clearColorFilter();
        }
    }

    private void doHapticFeedback(View view, int type) {
        if (mPreferences.getBoolean(getString(R.string.preference_enable_haptic_feedback), true)) {
            view.performHapticFeedback(type);
        }
    }

    private boolean skipFilledClues() {
        return mPreferences.getBoolean(getString(R.string.preference_skip_filled_clues), true);
    }

    private boolean skipFilledSquares() {
        return mPreferences.getBoolean(getString(R.string.preference_skip_filled_squares), true);
    }

    private boolean unlessCurrentSquareFilled() {
        return mPreferences.getBoolean(
                getString(R.string.preference_unless_current_square_is_filled), true);
    }

    private boolean skipFilledSquaresWrap() {
        return mPreferences.getBoolean(getString(R.string.preference_skip_filled_squares_wrap),
                false);
    }

    private boolean completedClueNext() {
        return mPreferences.getBoolean(getString(R.string.preference_completed_clue_next), true);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_CODE_REBUS_ENTRY && resultCode == RESULT_OK) {
            String newContents = RebusFragment.getContents(data);
            mPuzzleViewModel.setCurrentCellContents(newContents, skipFilledClues(),
                    skipFilledSquares(), unlessCurrentSquareFilled(), skipFilledSquaresWrap(),
                    completedClueNext(), mUsePencil);
            adjustViewport();
        }
    }

    interface Callbacks {
        void onClueListViewSelected();
    }

    private class CellHolder extends RecyclerView.ViewHolder {
        private final SimpleCellBinding mBinding;

        private CellHolder(SimpleCellBinding binding) {
            super(binding.getRoot());
            mBinding = binding;

//            mBinding.entry.setTypeface(mTypeface);

            mBinding.getRoot().setOnClickListener(view -> {
                Log.i("LAURA", "Received click for cell: " + mBinding.getCellViewModel());
                mPuzzleViewModel.selectCell(mBinding.getCellViewModel());
            });
        }

        private void recycle() {
            if (mBinding.getCellViewModel() != null) {
                mBinding.setCellViewModel(null);
            }
        }

        private void bind(final CellViewModel viewModel) {
            if (viewModel == null) {
                // black square
                recycle();
                return;
            }

            mBinding.setCellViewModel(viewModel);
            mBinding.setLifecycleOwner(getActivity());
            mBinding.cell.setCellNumber(viewModel.getClueNumber());
        }
    }

    private class CellAdapter extends RecyclerView.Adapter<CellHolder> {
        private final int mSize;
        private final int mWidth;

        public CellAdapter(int width, int height) {
            mSize = width * height;
            mWidth = width;
            setHasStableIds(true);
        }

        @NonNull
        @Override
        public CellHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(getActivity());
            SimpleCellBinding binding = DataBindingUtil.inflate(inflater, R.layout.simple_cell, parent, false);
            return new CellHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull CellHolder holder, int position) {
            CellViewModel cellViewModel =
                    mPuzzleViewModel.getCellViewModel(position / mWidth, position % mWidth);
            holder.bind(cellViewModel);
        }

        @Override
        public int getItemCount() {
            return mSize;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public void onViewRecycled(@NonNull CellHolder holder) {
            holder.recycle();
        }
    }

    private class PuzzleKeyboardListener implements KeyboardView.OnKeyboardActionListener {

        @RequiresApi(api = Build.VERSION_CODES.O_MR1)
        @Override
        public void onPress(int i) {
            doHapticFeedback(mFragmentPuzzleBinding.keyboard, HapticFeedbackConstants.VIRTUAL_KEY);
        }

        @Override
        public void onRelease(int i) {

        }

        @Override
        public void onKey(int primaryCode, int[] keyCodes) {
            if (mPuzzleViewModel.isSolved().getValue()) {
                return;
            }
            switch (primaryCode) {
                case KEYCODE_DELETE:
                    mPuzzleViewModel.doBackspace();
                    break;
                case KEYCODE_CANCEL:
                    mPuzzleViewModel.doUndo();
                    break;
                case KEYCODE_MODE_CHANGE:
                    FragmentManager fragmentManager = getParentFragmentManager();
                    RebusFragment rebusFragment = RebusFragment.newInstance(
                            mPuzzleViewModel.getCurrentCell().getValue().getContents().getValue());
                    rebusFragment.setTargetFragment(PuzzleFragment.this, REQUEST_CODE_REBUS_ENTRY);
                    rebusFragment.show(fragmentManager, "Rebus");
                    break;
                default:
                    char letter = (char) primaryCode;
                    mPuzzleViewModel.setCurrentCellContents(String.valueOf(letter),
                            skipFilledClues(), skipFilledSquares(), unlessCurrentSquareFilled(),
                            skipFilledSquaresWrap(), completedClueNext(), mUsePencil);
                    adjustViewport();
            }
        }

        @Override
        public void onText(CharSequence charSequence) {

        }

        @Override
        public void swipeLeft() {

        }

        @Override
        public void swipeRight() {

        }

        @Override
        public void swipeDown() {

        }

        @Override
        public void swipeUp() {

        }
    }

    private class PuzzleMenuProvider implements MenuProvider {
        @Override
        public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
            menuInflater.inflate(R.menu.fragment_puzzle, menu);
            mMenu = menu;

            configureUsePencilMenuItem();
            configureDownsOnlyModeMenuItem(mInitialDownsOnlyMode);

            boolean visible =
                    mPreferences.getBoolean(getString(R.string.preference_enable_hints), true);
            boolean enabled = mPuzzleViewModel.isCheckable();
            mMenu.setGroupVisible(R.id.check_items, visible);
            mMenu.setGroupEnabled(R.id.check_items, enabled);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull MenuItem item) {
            int itemId = item.getItemId();
            if (itemId == R.id.puzzle_info) {
                FragmentManager fragmentManager = getParentFragmentManager();
                PuzzleInfoFragment infoFragment = PuzzleInfoFragment.newInstance(
                        mPuzzleViewModel.getPuzzleInfoViewModel());
                infoFragment.show(fragmentManager, "PuzzleInfo");
                return true;
            }
            if (itemId == R.id.pencil) {
                mUsePencil = !mUsePencil;
                AsyncTask.execute(() -> mDatabase.puzzleDao()
                                                 .updateUsePencil(new PuzzleDao.UsePencilUpdate(
                                                         mPuzzleViewModel.getFile().getName(),
                                                         mUsePencil)));
                configureUsePencilMenuItem();
                return true;
            }
            if (itemId == R.id.downs_only_mode) {
                final boolean downsOnlyModeNewValue = mPuzzleViewModel.toggleDownsOnlyMode();
                AsyncTask.execute(() -> mDatabase.puzzleDao()
                                                 .updateDownsOnlyMode(
                                                         new PuzzleDao.DownsOnlyModeUpdate(
                                                                 mPuzzleViewModel.getFile()
                                                                                 .getName(),
                                                                 downsOnlyModeNewValue)));
                configureDownsOnlyModeMenuItem(downsOnlyModeNewValue);
                return true;
            }
            if (itemId == R.id.autocheck_mode) {
                mAutocheckMode = !mAutocheckMode;
                item.setChecked(mAutocheckMode);
                if (mAutocheckMode) {
                    mPuzzleViewModel.checkPuzzle();
                }
                return true;
            }
            if (itemId == R.id.clue_list) {
                if (openClueListView()) {
                    return true;
                }
            }
            if (itemId == R.id.settings) {
                startActivity(
                        SettingsActivity.newIntent(getContext(), R.xml.puzzle_preferences));
                return true;
            }
            if (itemId == R.id.check_square) {
                mPuzzleViewModel.checkCurrentCell();
                return true;
            }
            if (itemId == R.id.check_clue) {
                mPuzzleViewModel.checkCurrentClue();
                return true;
            }
            if (itemId == R.id.check_puzzle) {
                mPuzzleViewModel.checkPuzzle();
                return true;
            }
            if (itemId == R.id.reveal_square) {
                mPuzzleViewModel.revealCurrentCell();
                return true;
            }
            if (itemId == R.id.reveal_clue) {
                mPuzzleViewModel.revealCurrentClue();
                return true;
            }
            if (itemId == R.id.reveal_puzzle) {
                mPuzzleViewModel.revealPuzzle();
                return true;
            }
            if (itemId == R.id.reset_puzzle) {
                AlertDialog alertDialog =
                        new AlertDialog.Builder(getContext()).setTitle(R.string.reset_puzzle)
                                                             .setMessage(
                                                                     R.string.reset_puzzle_alert)
                                                             .setPositiveButton(
                                                                     R.string.reset_puzzle,
                                                                     (dialogInterface, i) -> {
                                                                         mPuzzleViewModel.resetPuzzle();
                                                                     })
                                                             .setNegativeButton(
                                                                     android.R.string.cancel,
                                                                     null)
                                                             .setCancelable(true)
                                                             .create();
                alertDialog.show();
                return true;
            }
            return false;
        }
    }
}
