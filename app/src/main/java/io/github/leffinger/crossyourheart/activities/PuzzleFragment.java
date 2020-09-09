package io.github.leffinger.crossyourheart.activities;

import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Observer;
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
import io.github.leffinger.crossyourheart.viewmodels.CellViewModel;
import io.github.leffinger.crossyourheart.viewmodels.PuzzleViewModel;

import static android.inputmethodservice.Keyboard.KEYCODE_CANCEL;
import static android.inputmethodservice.Keyboard.KEYCODE_DELETE;
import static java.util.Objects.requireNonNull;

/**
 * Puzzle-solving activity.
 */
public class PuzzleFragment extends Fragment {
    private static final String TAG = "PuzzleFragment";

    private final ExecutorService mExecutorService = Executors.newSingleThreadExecutor();
    private PuzzleViewModel mViewModel;

    public static PuzzleFragment newInstance(String filename, AbstractPuzzleFile puzzleFile) {
        Bundle args = new Bundle();
        args.putString("filename", filename);
        args.putSerializable("puzzle", puzzleFile);
        PuzzleFragment fragment = new PuzzleFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        Bundle bundle;
        if (savedInstanceState != null) {
            bundle = savedInstanceState;
        } else {
            bundle = requireNonNull(getArguments());
        }

        mViewModel = new PuzzleViewModel((AbstractPuzzleFile) bundle.getSerializable("puzzle"),
                                         new File(getActivity().getFilesDir(),
                                                  bundle.getString("filename")));
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
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        final FragmentPuzzleBinding binding =
                DataBindingUtil.inflate(inflater, R.layout.fragment_puzzle, container, false);
        binding.setViewModel(mViewModel);
        binding.setLifecycleOwner(getActivity());

        ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(mViewModel.getTitle());

        GridLayoutManager layoutManager =
                new GridLayoutManager(getActivity(), mViewModel.getNumColumns());
        binding.puzzle.setLayoutManager(layoutManager);
        binding.puzzle.setAdapter(new CellAdapter());

        binding.clue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                binding.clue.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                mViewModel.toggleDirection();
            }
        });

        binding.prev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                binding.prev.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                mViewModel.moveToPreviousClue();
            }
        });

        binding.next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                binding.next.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                mViewModel.moveToNextClue();
            }
        });

        Keyboard keyboard = new Keyboard(getActivity(), R.xml.keys_layout);
        binding.keyboard.setKeyboard(keyboard);
        binding.keyboard.setOnKeyboardActionListener(new KeyboardView.OnKeyboardActionListener() {

            @RequiresApi(api = Build.VERSION_CODES.O_MR1)
            @Override
            public void onPress(int i) {
                binding.keyboard.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS);
            }

            @Override
            public void onRelease(int i) {

            }

            @Override
            public void onKey(int primaryCode, int[] keyCodes) {
                switch (primaryCode) {
                case KEYCODE_DELETE:
                    mViewModel.doBackspace();
                    break;
                case KEYCODE_CANCEL:
                    mViewModel.doUndo();
                    break;
                default:
                    char letter = (char) primaryCode;
                    mViewModel.setCurrentCellContents(String.valueOf(letter));
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

        mViewModel.isSolved().observe(getViewLifecycleOwner(), new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean solved) {
                Log.i(TAG, "isSolved changed: " + solved);
                if (solved) {
                    AlertDialog dialog =
                            new AlertDialog.Builder(getActivity()).setMessage(R.string.alert_solved)
                                    .setPositiveButton(android.R.string.ok, null).create();
                    dialog.show();
                }
            }
        });

        return binding.getRoot();
    }

    private class CellHolder extends RecyclerView.ViewHolder {
        private CellBinding mBinding;

        private CellHolder(CellBinding binding) {
            super(binding.getRoot());
            mBinding = binding;
        }

        private void bind(final CellViewModel viewModel) {
            if (viewModel == null) {
                // black square
                return;
            }

            mBinding.setViewModel(viewModel);
            mBinding.setLifecycleOwner(getActivity());

            // When a square is focused on, trigger updates to the UI (e.g. clue text).
            mBinding.entry.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View view, boolean hasFocus) {
                    if (hasFocus) {
                        mBinding.getViewModel().onFocus();
                    }
                }
            });

            // Auto-focus on the first non-black square.
            if (mBinding.getViewModel().getClueNumber() == 1) {
                mBinding.entry.requestFocus();
            }

            // Listen for focus changes from the MainViewModel.
            mBinding.getViewModel().setListener(new CellViewModel.Listener() {
                @Override
                public void requestFocus() {
                    mBinding.entry.requestFocus();
                }
            });

            // "Activate" squares in the same clue as the focused square.
            mBinding.getViewModel().isHighlighted().observe(getActivity(), new Observer<Boolean>() {
                @Override
                public void onChanged(Boolean highlighted) {
                    mBinding.entry.setActivated(highlighted);
                }
            });

            // Toggle direction when a clue is clicked on (does not apply to first focusing click).
            mBinding.entry.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mViewModel.toggleDirection();
                }
            });

            // Persist changes in content to disk.
            mBinding.getViewModel().getContents().observe(getActivity(), new Observer<String>() {
                @Override
                public void onChanged(String s) {
                    mBinding.getViewModel().onContentsChanged();
                    mExecutorService.submit(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                mViewModel.saveToFile();
                            } catch (IOException e) {
                                Log.e(TAG, String.format("Saving puzzle file %s failed",
                                                         mViewModel.getFile().getName()), e);
                            }
                        }
                    });
                }
            });
        }
    }

    private class CellAdapter extends RecyclerView.Adapter<CellHolder> {

        @NonNull
        @Override
        public CellHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(getActivity());
            CellBinding binding = DataBindingUtil.inflate(inflater, R.layout.cell, parent, false);
            return new CellHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull CellHolder holder, int position) {
            CellViewModel cellViewModel = mViewModel
                    .getCellViewModel(position / mViewModel.getNumColumns(),
                                      position % mViewModel.getNumColumns());
            holder.bind(cellViewModel);
        }

        @Override
        public int getItemCount() {
            return mViewModel.getNumRows() * mViewModel.getNumColumns();
        }
    }
}
