package io.github.leffinger.crossyourheart.activities;

import android.content.Intent;
import android.content.SharedPreferences;
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
    static final int REQUEST_CODE_REBUS_ENTRY = 0;
    private static final String TAG = "PuzzleFragment";
    private final ExecutorService mExecutorService = Executors.newSingleThreadExecutor();
    private volatile PuzzleViewModel mViewModel;
    private volatile boolean mSolved;
    private SharedPreferences mPreferences;
    private FragmentPuzzleBinding mFragmentPuzzleBinding;
    private CellAdapter mCellAdapter;
    private AbstractPuzzleFile mPuzzleFile;
    private File mFilename;

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
        mSolved = mPuzzleFile.isSolved();

        new AsyncTask<Void, Void, PuzzleViewModel>() {

            @Override
            protected PuzzleViewModel doInBackground(Void... voids) {
                boolean startWithDownClues = mPreferences
                        .getBoolean(getString(R.string.preference_start_with_down_clues), false);
                return new PuzzleViewModel(mPuzzleFile, mFilename, startWithDownClues);
            }

            @Override
            protected void onPostExecute(PuzzleViewModel puzzleViewModel) {
                mViewModel = puzzleViewModel;
                setUpViewModelListeners();
                mCellAdapter.notifyDataSetChanged();
                mFragmentPuzzleBinding.puzzle.setVisibility(View.VISIBLE);
            }
        }.execute();
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
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
        case R.id.puzzle_info:
            FragmentManager fragmentManager = getParentFragmentManager();
            PuzzleInfoFragment infoFragment =
                    PuzzleInfoFragment.newInstance(mViewModel.getPuzzleInfoViewModel());
            infoFragment.show(fragmentManager, "PuzzleInfo");
            return true;
        case R.id.settings:
            startActivity(SettingsActivity.newIntent(getContext()));
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
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
        mFragmentPuzzleBinding.puzzle
                .setLayoutManager(new GridLayoutManager(getActivity(), mPuzzleFile.getWidth()));
        mFragmentPuzzleBinding.puzzle.setAdapter(mCellAdapter);

        Keyboard keyboard = new Keyboard(getActivity(), R.xml.keys_layout);
        mFragmentPuzzleBinding.keyboard.setKeyboard(keyboard);

        return mFragmentPuzzleBinding.getRoot();
    }

    private void setUpViewModelListeners() {
        if (mViewModel == null) {
            throw new RuntimeException(
                    "setUpViewModelListeners() must be called after mViewModel is initialized");
        }

        mFragmentPuzzleBinding.setViewModel(mViewModel);

        mFragmentPuzzleBinding.clue.setOnClickListener(view -> {
            if (mPreferences
                    .getBoolean(getContext().getString(R.string.preference_tap_clue_behavior),
                                true)) {
                doHapticFeedback(mFragmentPuzzleBinding.clue, HapticFeedbackConstants.KEYBOARD_TAP);
                mViewModel.toggleDirection();
            }
        });

        mFragmentPuzzleBinding.prev.setOnClickListener(view -> {
            doHapticFeedback(mFragmentPuzzleBinding.prev, HapticFeedbackConstants.KEYBOARD_TAP);
            mViewModel.moveToPreviousClue(skipFilledClues(), skipFilledSquares());
        });

        mFragmentPuzzleBinding.next.setOnClickListener(view -> {
            doHapticFeedback(mFragmentPuzzleBinding.next, HapticFeedbackConstants.KEYBOARD_TAP);
            mViewModel.moveToNextClue(skipFilledClues(), skipFilledSquares());
        });

        mFragmentPuzzleBinding.keyboard
                .setOnKeyboardActionListener(new KeyboardView.OnKeyboardActionListener() {

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
                        if (mSolved) {
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
                            Toast.makeText(getActivity(), R.string.rebus_message,
                                           Toast.LENGTH_SHORT).show();
//                    FragmentManager fragmentManager = getParentFragmentManager();
//                    RebusFragment rebusFragment =
//                            RebusFragment.newInstance(mViewModel.getCurrentCellContents());
//                    rebusFragment.setTargetFragment(PuzzleFragment.this,
//                    REQUEST_CODE_REBUS_ENTRY);
//                    rebusFragment.show(fragmentManager, "Rebus");
                            break;
                        default:
                            char letter = (char) primaryCode;
                            mViewModel.setCurrentCellContents(String.valueOf(letter),
                                                              skipFilledClues(),
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
                });

        if (!mSolved) {
            mViewModel.isSolved().observe(getViewLifecycleOwner(), solved -> {
                mSolved = solved;
                if (solved) {
                    AlertDialog dialog =
                            new AlertDialog.Builder(getActivity()).setMessage(R.string.alert_solved)
                                    .setPositiveButton(android.R.string.ok, null).create();
                    dialog.show();
                }
            });
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

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        Toast.makeText(getActivity(), "onActivityResult", Toast.LENGTH_SHORT).show();
        if (requestCode == REQUEST_CODE_REBUS_ENTRY && resultCode == RESULT_OK) {
            String newContents = RebusFragment.getContents(data);
            Toast.makeText(getActivity(), "Got contents: " + newContents, Toast.LENGTH_SHORT)
                    .show();
            mViewModel.setCurrentCellContents(newContents, skipFilledClues(), skipFilledSquares());
        }
    }

    private class CellHolder extends RecyclerView.ViewHolder {
        private final CellBinding mBinding;

        private CellHolder(CellBinding binding) {
            super(binding.getRoot());
            mBinding = binding;
        }

        private void bind(final CellViewModel viewModel) {
            if (viewModel == null) {
                // black square
                return;
            }

            mBinding.setCellViewModel(viewModel);
            mBinding.setLifecycleOwner(getActivity());

            // When a square is focused on, trigger updates to the UI (e.g. clue text).
            mBinding.entry.setOnFocusChangeListener((view, hasFocus) -> {
                if (hasFocus) {
                    mBinding.getCellViewModel().onFocus();
                }
            });

            // Listen for focus changes from the MainViewModel.
            mBinding.getCellViewModel().setListener(mBinding.entry::requestFocus);

            // "Activate" squares in the same clue as the focused square.
            mBinding.getCellViewModel().isHighlighted()
                    .observe(getActivity(), mBinding.entry::setActivated);

            // Auto-focus on the first cell for clue 1.
            if (mBinding.getCellViewModel().getClueNumber() == 1) {
                mBinding.getCellViewModel().requestFocus();
            }

            // Toggle direction when a clue is clicked on (does not apply to first focusing click).
            mBinding.entry.setOnClickListener(view -> mViewModel.toggleDirection());

            // Persist changes in content to disk.
            mBinding.getCellViewModel().getContents().observe(getActivity(), s -> {
                mBinding.getCellViewModel().onContentsChanged();
                mExecutorService.submit(() -> {
                    try {
                        mViewModel.saveToFile();
                    } catch (IOException e) {
                        Log.e(TAG, String.format("Saving puzzle file %s failed",
                                                 mViewModel.getFile().getName()), e);
                    }
                });
            });
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
    }
}
