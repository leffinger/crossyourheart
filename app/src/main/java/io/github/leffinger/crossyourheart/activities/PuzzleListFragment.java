package io.github.leffinger.crossyourheart.activities;

import static android.app.Activity.RESULT_OK;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import androidx.core.view.MenuProvider;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;

import io.github.leffinger.crossyourheart.R;
import io.github.leffinger.crossyourheart.databinding.AlertProgressBinding;
import io.github.leffinger.crossyourheart.databinding.FragmentPuzzleFileBinding;
import io.github.leffinger.crossyourheart.databinding.FragmentPuzzleListBinding;
import io.github.leffinger.crossyourheart.io.IOUtil;
import io.github.leffinger.crossyourheart.io.PuzzleDirectory;
import io.github.leffinger.crossyourheart.room.Database;
import io.github.leffinger.crossyourheart.room.Puzzle;

/**
 * Displays a list of puzzle files.
 */
public class PuzzleListFragment extends Fragment {
    static final String REQUEST_KEY_ADD_PUZZLES = "addPuzzles";
    private static final String TAG = "PuzzleListFragment";
    private static final int REQUEST_CODE_OPEN_FILE = 0;
    private volatile List<Puzzle> mPuzzles;
    private PuzzleFileAdapter mAdapter;
    private Database mDatabase;
    private RecyclerView.LayoutManager mLayoutManager;

    public static PuzzleListFragment newInstance() {
        PuzzleListFragment fragment = new PuzzleListFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    public static void setNewPuzzlesFragmentResult(FragmentManager fragmentManager,
                                                   int numPuzzles) {
        Bundle bundle = new Bundle();
        bundle.putInt("num_puzzles", numPuzzles);
        fragmentManager.setFragmentResult(PuzzleListFragment.REQUEST_KEY_ADD_PUZZLES, bundle);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPuzzles = new ArrayList<>();
        mAdapter = new PuzzleFileAdapter();
        mLayoutManager = new LinearLayoutManager(getContext());

        // Create or load database.
        mDatabase = Database.getInstance(requireActivity().getApplicationContext());

        // Register a result listener for when new puzzles are added.
        getParentFragmentManager().setFragmentResultListener(REQUEST_KEY_ADD_PUZZLES, this,
                (requestKey, result) -> fetchNewPuzzleFiles(result.getInt("num_puzzles")));
    }


    @Override
    public void onResume() {
        super.onResume();
        fetchPuzzleFiles();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void fetchPuzzleFiles() {
        // Fetch puzzles in a background task.
        Handler handler = new Handler(Looper.getMainLooper());
        Executors.newSingleThreadExecutor().execute(() -> {
            mPuzzles = PuzzleDirectory.getInstance().getAllPuzzles(requireContext());
            handler.post(() -> {
                mAdapter.notifyDataSetChanged();
                checkIndexAndOfferToReindex();
            });
        });
    }

    private void fetchNewPuzzleFiles(int numPuzzles) {
        Handler handler = new Handler(Looper.getMainLooper());
        Executors.newSingleThreadExecutor().execute(() -> {
            mPuzzles.addAll(0, mDatabase.puzzleDao().getFirstN(numPuzzles));
            handler.post(() -> {
                mAdapter.notifyItemRangeInserted(0, numPuzzles);
                mLayoutManager.scrollToPosition(0);
            });
        });
    }

    private void checkIndexAndOfferToReindex() {
        Handler handler = new Handler(Looper.getMainLooper());
        Executors.newSingleThreadExecutor().execute(() -> {
            Context context = getContext();
            if (context == null) {
                Log.i(TAG, "Fragment detached; not checking index");
            }

            PuzzleDirectory puzzleDirectory = PuzzleDirectory.getInstance();
            if (!puzzleDirectory.databaseAndDirectoryHaveSameFiles(requireContext())) {
                AlertDialog.Builder dialog =
                        new AlertDialog.Builder(context).setTitle(R.string.reindex_alert)
                                                        .setMessage(R.string.reindex_safe)
                                                        .setPositiveButton(android.R.string.ok,
                                                                (dialogInterface, i) -> reindexFiles())
                                                        .setNegativeButton(android.R.string.cancel,
                                                                null)
                                                        .setCancelable(true);
                handler.post(() -> dialog.create().show());
            }
        });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        FragmentPuzzleListBinding binding =
                DataBindingUtil.inflate(getLayoutInflater(), R.layout.fragment_puzzle_list,
                        container, false);
        binding.list.setLayoutManager(mLayoutManager);
        binding.list.setAdapter(mAdapter);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.fragment_puzzle_list, menu);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                int itemId = item.getItemId();
                if (itemId == R.id.open_file) {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    startActivityForResult(intent, REQUEST_CODE_OPEN_FILE);
                    return true;
                }
                if (itemId == R.id.download_file) {
                    ((Callbacks) requireActivity()).onDownloadSelected();
                    return true;
                }
                if (itemId == R.id.settings) {
                    startActivity(SettingsActivity.newIntent(getContext(), R.xml.root_preferences));
                    return true;
                }
                if (itemId == R.id.reindex_files) {
                    reindexFiles();
                    return true;
                }
                if (itemId == R.id.show_tutorial) {
                    startActivity(new Intent(getContext(), TutorialActivity.class));
                    return true;
                }
                if (itemId == R.id.send_feedback) {
                    Intent intent = new Intent(Intent.ACTION_SENDTO);
                    intent.setData(Uri.parse("mailto:"));
                    intent.putExtra(Intent.EXTRA_EMAIL,
                            new String[]{"crossyourheartapp@gmail.com"});
                    intent.putExtra(Intent.EXTRA_SUBJECT, "Feedback");
                    startActivity(intent);
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Closing database");
        mDatabase.close();
    }

    private void reindexFiles() {
        AlertProgressBinding progressBinding =
                DataBindingUtil.inflate(getLayoutInflater(), R.layout.alert_progress, null, false);
        AlertDialog progressDialog =
                new AlertDialog.Builder(getContext()).setView(progressBinding.getRoot())
                                                     .setCancelable(false)
                                                     .setTitle("Reindexing files...")
                                                     .show();
        progressDialog.show();

        Handler handler = new Handler(Looper.getMainLooper());
        Executors.newSingleThreadExecutor().execute(() -> {
            Context context = getContext();
            if (context == null) {
                Log.i(TAG, "Fragment detached, skipping reindexFiles()");
                return;
            }

            List<File> corruptFiles = PuzzleDirectory.getInstance()
                                                     .reindexFiles(context,
                                                             progressBinding.progressBar);
            mPuzzles = mDatabase.puzzleDao().getAll();
            AlertDialog.Builder alertDialogBuilder;
            if (!corruptFiles.isEmpty()) {
                alertDialogBuilder = new AlertDialog.Builder(context).setMessage(
                                                                             getString(R.string.delete_corrupted_files_prompt, corruptFiles.size()))
                                                                     .setPositiveButton(
                                                                             android.R.string.yes,
                                                                             (dialog, which) -> deleteCorruptedFiles(
                                                                                     corruptFiles))
                                                                     .setNegativeButton(
                                                                             android.R.string.no,
                                                                             null);
            } else {
                alertDialogBuilder = new AlertDialog.Builder(context).setMessage(
                                                                             context.getString(R.string.reindexed_files, mPuzzles.size()))
                                                                     .setPositiveButton(
                                                                             android.R.string.ok,
                                                                             null);
            }
            handler.post(() -> {
                mAdapter.notifyDataSetChanged();
                progressDialog.dismiss();
                alertDialogBuilder.show();
            });
        });
    }

    private void deleteCorruptedFiles(List<File> corruptFiles) {
        Handler handler = new Handler(Looper.getMainLooper());
        Executors.newSingleThreadExecutor().execute(() -> {
            Context context = getContext();
            if (context == null) {
                Log.w(TAG, "Context unavailable, bailing");
            }
            boolean success = PuzzleDirectory.getInstance().deleteFiles(corruptFiles);
            if (success) {
                handler.post(() -> new AlertDialog.Builder(context).setMessage(
                                                        getString(R.string.deleted_corrupted_files, corruptFiles.size()))
                                                               .setPositiveButton(android.R.string.ok, null)
                                                               .show());
            } else {
                handler.post(() -> new AlertDialog.Builder(context).setMessage("Something went wrong :(")
                                                               .setPositiveButton(android.R.string.ok, null)
                                                               .show());
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_CODE_OPEN_FILE && resultCode == RESULT_OK) {
            ClipData clipData = Objects.requireNonNull(data).getClipData();
            if (clipData != null && clipData.getItemCount() > 0) {
                List<Uri> puzzleUris = new ArrayList<>();
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    ClipData.Item item = clipData.getItemAt(i);
                    puzzleUris.add(item.getUri());
                }
                ((Callbacks) requireActivity()).onMultipleUrisSelected(puzzleUris);
            } else {
                ((Callbacks) requireActivity()).onUriSelected(data.getData());
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void deletePuzzle(String filename) {
        AsyncTask.execute(() -> {
            if (!IOUtil.getPuzzleFile(getContext(), filename).delete()) {
                Log.w(TAG, "deletePuzzle: failed to delete " + filename);
            }
            mDatabase.puzzleDao().deletePuzzle(new Puzzle(filename));
        });
    }

    public interface Callbacks {
        void onPuzzleSelected(Puzzle puzzle);

        void onUriSelected(Uri uri);

        void onMultipleUrisSelected(List<Uri> uris);

        void onDownloadSelected();
    }

    private class PuzzleFileHolder extends RecyclerView.ViewHolder {
        private final FragmentPuzzleFileBinding mBinding;

        public PuzzleFileHolder(FragmentPuzzleFileBinding binding) {
            super(binding.getRoot());
            mBinding = binding;
        }

        public void bind(Puzzle puzzle) {
            mBinding.setPuzzle(puzzle);

            // Clicking on the puzzle file opens that file.
            mBinding.getRoot().setOnClickListener(view -> {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                ((Callbacks) requireActivity()).onPuzzleSelected(mBinding.getPuzzle());
            });

            // Long-clicking the puzzle file brings up an option to delete the file.
            mBinding.getRoot().setOnLongClickListener(view -> {
                AlertDialog alertDialog =
                        new AlertDialog.Builder(getContext()).setMessage(R.string.delete_puzzle)
                                                             .setPositiveButton(android.R.string.ok,
                                                                     (dialogInterface, i) -> {
                                                                         int index =
                                                                                 getAdapterPosition();
                                                                         mPuzzles.remove(index);
                                                                         mAdapter.notifyItemRemoved(
                                                                                 index);
                                                                         deletePuzzle(
                                                                                 mBinding.getPuzzle()
                                                                                         .getFilename());
                                                                     })
                                                             .setNegativeButton(
                                                                     android.R.string.cancel, null)
                                                             .setCancelable(true)
                                                             .create();
                alertDialog.show();
                return true;
            });

        }
    }

    private class PuzzleFileAdapter extends RecyclerView.Adapter<PuzzleFileHolder> {

        public PuzzleFileAdapter() {
            setHasStableIds(true);
        }

        @NonNull
        @Override
        public PuzzleFileHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(getActivity());
            FragmentPuzzleFileBinding binding =
                    DataBindingUtil.inflate(inflater, R.layout.fragment_puzzle_file, parent, false);
            return new PuzzleFileHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull PuzzleFileHolder holder, int position) {
            holder.bind(mPuzzles.get(position));
        }

        @Override
        public int getItemCount() {
            return mPuzzles.size();
        }

        @Override
        public long getItemId(int position) {
            return mPuzzles.get(position).getFilename().hashCode();
        }
    }
}