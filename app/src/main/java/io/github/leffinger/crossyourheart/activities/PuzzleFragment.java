package io.github.leffinger.crossyourheart.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.github.leffinger.crossyourheart.R;
import io.github.leffinger.crossyourheart.databinding.CellBinding;
import io.github.leffinger.crossyourheart.databinding.FragmentPuzzleBinding;
import io.github.leffinger.crossyourheart.io.AbstractPuzzleFile;
import io.github.leffinger.crossyourheart.io.IOUtil;
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

    // Handler message codes.
    private static final int MESSAGE_VIEW_MODEL_READY = 0;
    private static final int MESSAGE_PUZZLE_VIEW_READY = 1;

    // Used to write puzzle file changes to disk in the background.
    private final ExecutorService mExecutorService = Executors.newSingleThreadExecutor();

    // View and model state.
    private PuzzleViewModel mViewModel;
    private SharedPreferences mPreferences;
    private FragmentPuzzleBinding mFragmentPuzzleBinding;
    private GridLayoutManager mGridLayoutManager;
    private CellAdapter mCellAdapter;
    private AbstractPuzzleFile mPuzzleFile;
    private File mFilename;
    private Handler mHandler;
    private Menu mMenu;

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

        mPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        mPuzzleFile = (AbstractPuzzleFile) bundle.getSerializable("puzzle");
        mCellAdapter = new CellAdapter(mPuzzleFile.getWidth(), mPuzzleFile.getHeight());
        mFilename = IOUtil.getPuzzleFile(getContext(), bundle.getString("filename"));

        // This handler listens on the main thread and waits for both the view model and the view
        // to be ready.
        mHandler = new Handler(Looper.getMainLooper()) {
            private boolean mViewModelReady = false;
            private boolean mPuzzleViewReady = false;

            @Override
            public void handleMessage(@NonNull Message msg) {
                switch (msg.what) {
                case MESSAGE_VIEW_MODEL_READY:
                    mViewModelReady = true;
                    break;
                case MESSAGE_PUZZLE_VIEW_READY:
                    mPuzzleViewReady = true;
                    break;
                }

                if (mViewModelReady && mPuzzleViewReady) {
                    setUpViewModelListeners();
                }
            }
        };

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                boolean startWithDownClues = mPreferences
                        .getBoolean(getString(R.string.preference_start_with_down_clues), false);
                mViewModel = new PuzzleViewModel(mPuzzleFile, mFilename, startWithDownClues);
                mHandler.obtainMessage(MESSAGE_VIEW_MODEL_READY).sendToTarget();
                return null;
            }
        }.execute();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        mFragmentPuzzleBinding =
                DataBindingUtil.inflate(inflater, R.layout.fragment_puzzle, container, false);
        mFragmentPuzzleBinding.setLifecycleOwner(getActivity());

        ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(mPuzzleFile.getTitle());

        mFragmentPuzzleBinding.puzzle.setVisibility(View.INVISIBLE);
        mGridLayoutManager = new GridLayoutManager(getActivity(), mPuzzleFile.getWidth(),
                                                   GridLayoutManager.VERTICAL, false);
        mFragmentPuzzleBinding.puzzle.setLayoutManager(mGridLayoutManager);
        mFragmentPuzzleBinding.puzzle.setAdapter(mCellAdapter);

        Keyboard keyboard = new Keyboard(getActivity(), R.xml.keys_layout);
        mFragmentPuzzleBinding.keyboard.setKeyboard(keyboard);

        return mFragmentPuzzleBinding.getRoot();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("filename", mViewModel.getFile().getName());
        outState.putSerializable("puzzle", mViewModel.getPuzzleFile());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            mExecutorService.awaitTermination(1000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        try {
            mViewModel.saveToFile();
        } catch (IOException e) {
            Log.e(TAG,
                  String.format("Saving puzzle file %s failed", mViewModel.getFile().getName()), e);
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.fragment_puzzle, menu);
        mMenu = menu;
        mHandler.obtainMessage(MESSAGE_PUZZLE_VIEW_READY).sendToTarget();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.puzzle_info) {
            FragmentManager fragmentManager = getParentFragmentManager();
            PuzzleInfoFragment infoFragment =
                    PuzzleInfoFragment.newInstance(mViewModel.getPuzzleInfoViewModel());
            infoFragment.show(fragmentManager, "PuzzleInfo");
            return true;
        }
        if (itemId == R.id.settings) {
            startActivity(SettingsActivity.newIntent(getContext()));
            return true;
        }
        if (itemId == R.id.check_square) {
            mViewModel.checkCurrentCell();
            return true;
        }
        if (itemId == R.id.check_clue) {
            mViewModel.checkCurrentClue();
            return true;
        }
        if (itemId == R.id.check_puzzle) {
            mViewModel.checkPuzzle();
            return true;
        }
        if (itemId == R.id.reveal_square) {
            mViewModel.revealCurrentCell();
            return true;
        }
        if (itemId == R.id.reveal_clue) {
            mViewModel.revealCurrentClue();
            return true;
        }
        if (itemId == R.id.reveal_puzzle) {
            mViewModel.revealPuzzle();
            return true;
        }
        if (itemId == R.id.reset_puzzle) {
            AlertDialog alertDialog =
                    new AlertDialog.Builder(getContext()).setTitle(R.string.reset_puzzle)
                            .setMessage(R.string.reset_puzzle_alert)
                            .setPositiveButton(R.string.reset_puzzle, (dialogInterface, i) -> {
                                mViewModel.resetPuzzle();
                                mViewModel.isSolved().observe(getViewLifecycleOwner(), solved -> {
                                    if (solved) {
                                        AlertDialog dialog = new AlertDialog.Builder(getActivity())
                                                .setMessage(R.string.alert_solved)
                                                .setPositiveButton(android.R.string.ok, null)
                                                .create();
                                        dialog.show();
                                    }
                                });
                            }).setNegativeButton(android.R.string.cancel, null).setCancelable(true)
                            .create();
            alertDialog.show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setUpViewModelListeners() {
        if (mViewModel == null) {
            throw new RuntimeException(
                    "setUpViewModelListeners() must be called after mViewModel is initialized");
        }

        // Sets up basic data binding, e.g. clue number & text.
        mFragmentPuzzleBinding.setViewModel(mViewModel);

        // Change direction when clue is tapped (if configured).
        mFragmentPuzzleBinding.clue.setOnClickListener(view -> {
            if (mPreferences
                    .getBoolean(getContext().getString(R.string.preference_tap_clue_behavior),
                                true)) {
                doHapticFeedback(mFragmentPuzzleBinding.clue, HapticFeedbackConstants.KEYBOARD_TAP);
                mViewModel.toggleDirection();
            }
        });

        // When a cell is selected, tell the grid manager to scroll so the cell is visible.
        mViewModel.selectFirstCell();
        mViewModel.getCurrentCell().observe(getActivity(), cellViewModel -> {
            if (cellViewModel != null) {
                int position =
                        cellViewModel.getRow() * mViewModel.getNumRows() + cellViewModel.getCol();
                mGridLayoutManager.scrollToPositionWithOffset(position, 2);
            }
        });

        // Move to previous clue when button is pressed.
        mFragmentPuzzleBinding.prev.setOnClickListener(view -> {
            doHapticFeedback(mFragmentPuzzleBinding.prev, HapticFeedbackConstants.KEYBOARD_TAP);
            mViewModel.moveToPreviousClue(skipFilledClues(), skipFilledSquares());
        });

        // Move to next clue when button is pressed.
        mFragmentPuzzleBinding.next.setOnClickListener(view -> {
            doHapticFeedback(mFragmentPuzzleBinding.next, HapticFeedbackConstants.KEYBOARD_TAP);
            mViewModel.moveToNextClue(skipFilledClues(), skipFilledSquares());
        });

        // Set up keyboard listener.
        mFragmentPuzzleBinding.keyboard.setOnKeyboardActionListener(new PuzzleKeyboardListener());

        // Persist changes in content to disk.
        mViewModel.getContentsChanged().observe(getActivity(), i -> mExecutorService.submit(() -> {
            try {
                mViewModel.saveToFile();
            } catch (IOException e) {
                Log.e(TAG,
                      String.format("Saving puzzle file %s failed", mViewModel.getFile().getName()),
                      e);
            }
        }));

        // If the puzzle was not solved to begin with, display a message when it is solved.
        if (!mViewModel.isSolved().getValue()) {
            mViewModel.isSolved().observe(getViewLifecycleOwner(), solved -> {
                if (solved) {
                    AlertDialog dialog =
                            new AlertDialog.Builder(getActivity()).setMessage(R.string.alert_solved)
                                    .setPositiveButton(android.R.string.ok, null).create();
                    dialog.show();
                }
            });
        }

        mCellAdapter.notifyDataSetChanged();
        mFragmentPuzzleBinding.puzzle.setVisibility(View.VISIBLE);

        configureCheckMenuItems();
        mPreferences.registerOnSharedPreferenceChangeListener((sharedPreferences, key) -> {
            if (key.equals(getString(R.string.preference_enable_hints))) {
                configureCheckMenuItems();
            }
        });
    }

    private void configureCheckMenuItems() {
        boolean visible =
                mPreferences.getBoolean(getString(R.string.preference_enable_hints), true);
        boolean enabled = mViewModel.isCheckable();
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

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_CODE_REBUS_ENTRY && resultCode == RESULT_OK) {
            String newContents = RebusFragment.getContents(data);
            mViewModel.setCurrentCellContents(newContents, skipFilledClues(), skipFilledSquares());
        }
    }

    private class CellHolder extends RecyclerView.ViewHolder {
        private final CellBinding mBinding;

        private CellHolder(CellBinding binding) {
            super(binding.getRoot());
            mBinding = binding;

            mBinding.getRoot().setOnClickListener(view -> {
                mViewModel.selectCell(mBinding.getCellViewModel());
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
            if (mViewModel == null) {
                return;
            }
            CellViewModel cellViewModel =
                    mViewModel.getCellViewModel(position / mWidth, position % mWidth);
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
            if (mViewModel.isSolved().getValue()) {
                return;
            }
            switch (primaryCode) {
            case KEYCODE_DELETE:
                mViewModel.doBackspace();
                break;
            case KEYCODE_CANCEL:
                mViewModel.doUndo();
                break;
            case KEYCODE_MODE_CHANGE:
                Toast.makeText(getActivity(), R.string.rebus_message, Toast.LENGTH_SHORT).show();
//                    FragmentManager fragmentManager = getParentFragmentManager();
//                    RebusFragment rebusFragment =
//                            RebusFragment.newInstance(mViewModel.getCurrentCellContents());
//                    rebusFragment.setTargetFragment(PuzzleFragment.this,
//                    REQUEST_CODE_REBUS_ENTRY);
//                    rebusFragment.show(fragmentManager, "Rebus");
                break;
            default:
                char letter = (char) primaryCode;
                mViewModel.setCurrentCellContents(String.valueOf(letter), skipFilledClues(),
                                                  skipFilledSquares());
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
