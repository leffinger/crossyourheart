package io.github.leffinger.crossyourheart.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.github.leffinger.crossyourheart.R;
import io.github.leffinger.crossyourheart.databinding.CellBinding;
import io.github.leffinger.crossyourheart.databinding.FragmentPuzzleBinding;
import io.github.leffinger.crossyourheart.databinding.TimerBinding;
import io.github.leffinger.crossyourheart.io.AbstractPuzzleFile;
import io.github.leffinger.crossyourheart.io.AbstractPuzzleFile.TimerInfo;
import io.github.leffinger.crossyourheart.io.IOUtil;
import io.github.leffinger.crossyourheart.room.Database;
import io.github.leffinger.crossyourheart.room.Puzzle;
import io.github.leffinger.crossyourheart.viewmodels.CellViewModel;
import io.github.leffinger.crossyourheart.viewmodels.PuzzleViewModel;

import static android.app.Activity.RESULT_OK;
import static android.inputmethodservice.Keyboard.KEYCODE_CANCEL;
import static android.inputmethodservice.Keyboard.KEYCODE_DELETE;
import static android.inputmethodservice.Keyboard.KEYCODE_MODE_CHANGE;

/**
 * Puzzle-solving activity.
 */
public class PuzzleFragment extends Fragment {
    private static final String TAG = "PuzzleFragment";

    // Activity request codes.
    private static final int REQUEST_CODE_REBUS_ENTRY = 0;

    // Used to write puzzle file changes to disk in the background.
    private final ExecutorService mExecutorService = Executors.newSingleThreadExecutor();

    // View and model state.
    private SharedPreferences mPreferences;
    private FragmentPuzzleBinding mFragmentPuzzleBinding;
    private GridLayoutManager mGridLayoutManager;
    private CellAdapter mCellAdapter;
    private AbstractPuzzleFile mPuzzleFile;
    private File mFilename;
    private Menu mMenu;
    private TimerBinding mTimerBinding;
    private Database mDatabase;

    public static PuzzleFragment newInstance(String filename, AbstractPuzzleFile puzzleFile) {
        Bundle args = new Bundle();
        args.putString("filename", filename);
        args.putSerializable("puzzle", puzzleFile);
        PuzzleFragment fragment = new PuzzleFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    @SuppressWarnings("StaticFieldLeak")
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        Bundle bundle;
        if (savedInstanceState != null) {
            bundle = savedInstanceState;
        } else {
            bundle = requireArguments();
        }

        // Create or load database.
        mDatabase = Room.databaseBuilder(getActivity().getApplicationContext(), Database.class,
                                         "puzzles").build();

        mPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        mPuzzleFile = (AbstractPuzzleFile) bundle.getSerializable("puzzle");
        mCellAdapter = new CellAdapter(mPuzzleFile.getWidth(), mPuzzleFile.getHeight());
        mFilename = IOUtil.getPuzzleFile(getContext(), bundle.getString("filename"));
        boolean startWithDownClues = mPreferences
                .getBoolean(getString(R.string.preference_start_with_down_clues), false);

        PuzzleViewModel viewModel = new ViewModelProvider(getActivity()).get(PuzzleViewModel.class);
        viewModel.initialize(mPuzzleFile, mFilename, startWithDownClues);  // kicks off async task
    }

    private PuzzleViewModel getViewModel() {
        PuzzleViewModel viewModel = new ViewModelProvider(getActivity()).get(PuzzleViewModel.class);
        assert viewModel.initialized();
        return viewModel;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        mFragmentPuzzleBinding =
                DataBindingUtil.inflate(inflater, R.layout.fragment_puzzle, container, false);
        mFragmentPuzzleBinding.setLifecycleOwner(getActivity());

        mTimerBinding = DataBindingUtil.inflate(inflater, R.layout.timer, container, false);
        mTimerBinding.setLifecycleOwner(this);

        // Display timer in the action bar.
        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setCustomView(mTimerBinding.getRoot());
        mTimerBinding.solved.setVisibility(View.INVISIBLE);

        mFragmentPuzzleBinding.puzzle.setVisibility(View.INVISIBLE);
        mGridLayoutManager = new GridLayoutManager(getActivity(), mPuzzleFile.getWidth(),
                                                   GridLayoutManager.VERTICAL, false);
        mFragmentPuzzleBinding.puzzle.setLayoutManager(mGridLayoutManager);
        mFragmentPuzzleBinding.puzzle.setAdapter(mCellAdapter);

        Keyboard keyboard = new Keyboard(getActivity(), R.xml.keys_layout);
        mFragmentPuzzleBinding.keyboard.setKeyboard(keyboard);
        mFragmentPuzzleBinding.keyboard.setPreviewEnabled(true);

        return mFragmentPuzzleBinding.getRoot();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("filename", getViewModel().getFile().getName());
        outState.putSerializable("puzzle", getViewModel().getPuzzleFile());
    }

    @Override
    public void onStart() {
        super.onStart();

        // should be safe to set up listeners since the View is initialized, even if the
        // ViewModel is not fully there
        setUpViewModelListeners();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Wait for the puzzle file's TimerInfo to be initialized before starting the timer (else
        // we could overwrite the existing value).
        LiveData<TimerInfo> timerInfoLiveData = getViewModel().getTimerInfo();
        timerInfoLiveData.observe(getViewLifecycleOwner(), new Observer<TimerInfo>() {
            @Override
            public void onChanged(TimerInfo timerInfo) {
                if (timerInfo == null) {
                    return;
                }

                Log.i(TAG, "STARTING TIMER AT " + timerInfo.elapsedTimeSecs + " SECONDS");
                mTimerBinding.timer.setBase(
                        SystemClock.elapsedRealtime() - (timerInfo.elapsedTimeSecs * 1000));
                if (timerInfo.isRunning) {
                    mTimerBinding.timer.start();
                }

                timerInfoLiveData.removeObserver(this);
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();

        TimerInfo timerInfo = getViewModel().getTimerInfo().getValue();
        if (timerInfo != null && timerInfo.isRunning) {
            long elapsedTime =
                    (SystemClock.elapsedRealtime() - mTimerBinding.timer.getBase()) / 1000;
            Log.i(TAG, "PAUSING TIMER AT " + elapsedTime + " SECONDS");
            getViewModel().getTimerInfo().setValue(new TimerInfo(elapsedTime, true));
            mTimerBinding.timer.stop();
        }
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
            getViewModel().saveToFile();
        } catch (IOException e) {
            Log.e(TAG,
                  String.format("Saving puzzle file %s failed", getViewModel().getFile().getName()),
                  e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.fragment_puzzle, menu);
        mMenu = menu;

        configureCheckMenuItems();
        mPreferences.registerOnSharedPreferenceChangeListener((sharedPreferences, key) -> {
            if (key.equals(getString(R.string.preference_enable_hints))) {
                configureCheckMenuItems();
            }
        });

        // Show option to "resume" or "pause" timer, depending on timer state.
        getViewModel().getTimerInfo().observe(getViewLifecycleOwner(), timerInfo -> {
            if (timerInfo == null) {
                return;
            }

            mMenu.findItem(R.id.stop_timer).setVisible(timerInfo.isRunning);
            mMenu.findItem(R.id.restart_timer).setVisible(!timerInfo.isRunning);
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.puzzle_info) {
            FragmentManager fragmentManager = getParentFragmentManager();
            PuzzleInfoFragment infoFragment =
                    PuzzleInfoFragment.newInstance(getViewModel().getPuzzleInfoViewModel());
            infoFragment.show(fragmentManager, "PuzzleInfo");
            return true;
        }
        if (itemId == R.id.settings) {
            startActivity(SettingsActivity.newIntent(getContext()));
            return true;
        }
        if (itemId == R.id.check_square) {
            getViewModel().checkCurrentCell();
            return true;
        }
        if (itemId == R.id.check_clue) {
            getViewModel().checkCurrentClue();
            return true;
        }
        if (itemId == R.id.check_puzzle) {
            getViewModel().checkPuzzle();
            return true;
        }
        if (itemId == R.id.reveal_square) {
            getViewModel().revealCurrentCell();
            return true;
        }
        if (itemId == R.id.reveal_clue) {
            getViewModel().revealCurrentClue();
            return true;
        }
        if (itemId == R.id.reveal_puzzle) {
            getViewModel().revealPuzzle();
            return true;
        }
        if (itemId == R.id.stop_timer) {
            getViewModel().getTimerInfo()
                    .observe(getViewLifecycleOwner(), new Observer<TimerInfo>() {
                        @Override
                        public void onChanged(TimerInfo timerInfo) {
                            if (!timerInfo.isRunning) {
                                getViewModel().getTimerInfo().removeObserver(this);
                                return;
                            }
                            long elapsedTime = (SystemClock.elapsedRealtime() -
                                    mTimerBinding.timer.getBase()) / 1000;
                            mTimerBinding.timer.stop();
                            Log.i(TAG, "STOPPING TIMER AT " + elapsedTime + " SECONDS");
                            getViewModel().getTimerInfo()
                                    .setValue(new TimerInfo(elapsedTime, false));
                            getViewModel().getTimerInfo().removeObserver(this);
                        }
                    });
            return true;
        }
        if (itemId == R.id.restart_timer) {
            getViewModel().getTimerInfo()
                    .observe(getViewLifecycleOwner(), new Observer<TimerInfo>() {
                        @Override
                        public void onChanged(TimerInfo timerInfo) {
                            if (timerInfo.isRunning) {
                                getViewModel().getTimerInfo().removeObserver(this);
                                return;
                            }

                            Log.i(TAG,
                                  "RESTARTING TIMER AT " + timerInfo.elapsedTimeSecs + " SECONDS");
                            mTimerBinding.timer.setBase(SystemClock.elapsedRealtime() -
                                                                (timerInfo.elapsedTimeSecs * 1000));
                            mTimerBinding.timer.start();
                            getViewModel().getTimerInfo()
                                    .setValue(new TimerInfo(timerInfo.elapsedTimeSecs, true));
                            getViewModel().getTimerInfo().removeObserver(this);
                        }
                    });
            return true;
        }
        if (itemId == R.id.reset_puzzle) {
            AlertDialog alertDialog =
                    new AlertDialog.Builder(getContext()).setTitle(R.string.reset_puzzle)
                            .setMessage(R.string.reset_puzzle_alert)
                            .setPositiveButton(R.string.reset_puzzle, (dialogInterface, i) -> {
                                getViewModel().resetPuzzle();
                            }).setNegativeButton(android.R.string.cancel, null).setCancelable(true)
                            .create();
            alertDialog.show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setUpViewModelListeners() {
        Log.i(TAG, "Initializing PuzzleViewModel listeners");

        PuzzleViewModel viewModel = getViewModel();

        // Sets up basic data binding, e.g. clue number & text.
        mFragmentPuzzleBinding.setViewModel(viewModel);

        // Change direction when clue is tapped (if configured).
        mFragmentPuzzleBinding.clue.setOnClickListener(view -> {
            if (mPreferences
                    .getBoolean(getContext().getString(R.string.preference_tap_clue_behavior),
                                true)) {
                doHapticFeedback(mFragmentPuzzleBinding.clue, HapticFeedbackConstants.KEYBOARD_TAP);
                viewModel.toggleDirection();
            }
        });

        // When a cell is selected, tell the grid manager to scroll so the cell is visible.
        viewModel.getCurrentCell().observe(getActivity(), cellViewModel -> {
            if (cellViewModel != null) {
                int firstVisiblePosition =
                        mGridLayoutManager.findFirstCompletelyVisibleItemPosition();
                int lastVisibleItemPosition =
                        mGridLayoutManager.findLastCompletelyVisibleItemPosition();
                int position =
                        cellViewModel.getRow() * viewModel.getNumColumns() + cellViewModel.getCol();
                if (position < firstVisiblePosition || position > lastVisibleItemPosition) {
                    mGridLayoutManager.scrollToPositionWithOffset(position, 2);
                }
            }
        });

        // Move to previous clue when button is pressed.
        mFragmentPuzzleBinding.prev.setOnClickListener(view -> {
            doHapticFeedback(mFragmentPuzzleBinding.prev, HapticFeedbackConstants.KEYBOARD_TAP);
            viewModel.moveToPreviousClue(skipFilledClues(), skipFilledSquares());
        });

        // Move to next clue when button is pressed.
        mFragmentPuzzleBinding.next.setOnClickListener(view -> {
            doHapticFeedback(mFragmentPuzzleBinding.next, HapticFeedbackConstants.KEYBOARD_TAP);
            viewModel.moveToNextClue(skipFilledClues(), skipFilledSquares());
        });

        // Move to next cell when button is pressed.
        mFragmentPuzzleBinding.nextCell.setOnClickListener(view -> {
            doHapticFeedback(mFragmentPuzzleBinding.nextCell, HapticFeedbackConstants.KEYBOARD_TAP);
            viewModel.moveToNextCell();
        });

        // Move to previous cell when button is pressed.
        mFragmentPuzzleBinding.prevCell.setOnClickListener(view -> {
            doHapticFeedback(mFragmentPuzzleBinding.prevCell, HapticFeedbackConstants.KEYBOARD_TAP);
            viewModel.moveToPreviousCell();
        });

        // Set up keyboard listener.
        mFragmentPuzzleBinding.keyboard.setOnKeyboardActionListener(new PuzzleKeyboardListener());

        // Persist changes in content to disk.
        assert !viewModel.getContentsChanged().hasActiveObservers();
        viewModel.getContentsChanged().observe(getActivity(), i -> mExecutorService.submit(() -> {
            try {
                viewModel.saveToFile();
            } catch (IOException e) {
                Log.e(TAG,
                      String.format("Saving puzzle file %s failed", viewModel.getFile().getName()),
                      e);
            }
        }));

        // If the puzzle was not solved to begin with, display a message when it is solved.
        // This also handles situations where the puzzle goes from solved to unsolved, e.g. reset.
        viewModel.isSolved().removeObservers(getViewLifecycleOwner());
        viewModel.isSolved().observe(getViewLifecycleOwner(), new Observer<Boolean>() {
            private boolean shouldCongratulate = false;

            @Override
            public void onChanged(Boolean solved) {
                mTimerBinding.solved.setVisibility(solved ? View.VISIBLE : View.INVISIBLE);

                if (solved && shouldCongratulate) {
                    // Stop the timer and save the final time in the viewModel.
                    mTimerBinding.timer.stop();
                    String time = mTimerBinding.timer.getText().toString();
                    long elapsedTime = Math.round(
                            (SystemClock.elapsedRealtime() - mTimerBinding.timer.getBase()) /
                                    1000.0);
                    Log.i(TAG, "STOPPING TIMER AT " + elapsedTime + " SECONDS");
                    getViewModel().getTimerInfo().setValue(new TimerInfo(elapsedTime, false));

                    // Show a congratulatory dialog.
                    Toast.makeText(getActivity(), getString(R.string.alert_solved, time),
                                   Toast.LENGTH_SHORT).show();
                    shouldCongratulate = false;
                } else if (!solved && !shouldCongratulate) {
                    shouldCongratulate = true;
                }
            }
        });
        viewModel.isSolved().observe(getViewLifecycleOwner(), unused -> AsyncTask.execute(
                () -> mDatabase.puzzleDao()
                        .update(Puzzle.fromPuzzleFile(viewModel.getFile().getName(),
                                                      viewModel.getPuzzleFile()))));

        // Restart the timer when the puzzle is reset.
        viewModel.getTimerInfo().observe(getViewLifecycleOwner(), timerInfo -> {
            if (timerInfo == null) {
                return;
            }

            if (timerInfo.elapsedTimeSecs == 0L && timerInfo.isRunning) {
                // Puzzle was reset; restart timer from beginning.
                Log.i(TAG, "RESTARTING TIMER AT 0");
                mTimerBinding.timer.setBase(SystemClock.elapsedRealtime());
                mTimerBinding.timer.start();
            }
        });

        mCellAdapter.notifyDataSetChanged();
        mFragmentPuzzleBinding.puzzle.setVisibility(View.VISIBLE);
    }

    private void configureCheckMenuItems() {
        boolean visible =
                mPreferences.getBoolean(getString(R.string.preference_enable_hints), true);
        boolean enabled = getViewModel().isCheckable();
        mMenu.setGroupVisible(R.id.check_items, visible);
        mMenu.setGroupEnabled(R.id.check_items, enabled);
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

    private boolean skipFilledSquaresWrap() {
        return mPreferences
                .getBoolean(getString(R.string.preference_skip_filled_squares_wrap), false);
    }

    private boolean completedClueNext() {
        return mPreferences.getBoolean(getString(R.string.preference_completed_clue_next), true);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_CODE_REBUS_ENTRY && resultCode == RESULT_OK) {
            String newContents = RebusFragment.getContents(data);
            getViewModel()
                    .setCurrentCellContents(newContents, skipFilledClues(), skipFilledSquares(),
                                            skipFilledSquaresWrap(), completedClueNext());
        }
    }

    private class CellHolder extends RecyclerView.ViewHolder {
        private final CellBinding mBinding;

        private CellHolder(CellBinding binding) {
            super(binding.getRoot());
            mBinding = binding;

            mBinding.getRoot().setOnClickListener(view -> {
                getViewModel().selectCell(mBinding.getCellViewModel());
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
            CellBinding binding = DataBindingUtil.inflate(inflater, R.layout.cell, parent, false);
            return new CellHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull CellHolder holder, int position) {
            if (getViewModel() == null) {
                return;
            }
            CellViewModel cellViewModel =
                    getViewModel().getCellViewModel(position / mWidth, position % mWidth);
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
            doHapticFeedback(mFragmentPuzzleBinding.keyboard,
                             HapticFeedbackConstants.KEYBOARD_PRESS);
        }

        @Override
        public void onRelease(int i) {

        }

        @Override
        public void onKey(int primaryCode, int[] keyCodes) {
            if (getViewModel().isSolved().getValue()) {
                return;
            }
            switch (primaryCode) {
            case KEYCODE_DELETE:
                getViewModel().doBackspace();
                break;
            case KEYCODE_CANCEL:
                getViewModel().doUndo();
                break;
            case KEYCODE_MODE_CHANGE:
                FragmentManager fragmentManager = getParentFragmentManager();
                RebusFragment rebusFragment = RebusFragment.newInstance(
                        getViewModel().getCurrentCell().getValue().getContents().getValue());
                rebusFragment.setTargetFragment(PuzzleFragment.this, REQUEST_CODE_REBUS_ENTRY);
                rebusFragment.show(fragmentManager, "Rebus");
                break;
            default:
                char letter = (char) primaryCode;
                getViewModel().setCurrentCellContents(String.valueOf(letter), skipFilledClues(),
                                                      skipFilledSquares(), skipFilledSquaresWrap(),
                                                      completedClueNext());
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
}
