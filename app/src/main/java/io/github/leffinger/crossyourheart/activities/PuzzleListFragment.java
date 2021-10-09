package io.github.leffinger.crossyourheart.activities;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
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
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.common.collect.ImmutableList;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.leffinger.crossyourheart.R;
import io.github.leffinger.crossyourheart.databinding.AlertProgressBinding;
import io.github.leffinger.crossyourheart.databinding.FragmentPuzzleFileBinding;
import io.github.leffinger.crossyourheart.databinding.FragmentPuzzleListBinding;
import io.github.leffinger.crossyourheart.io.IOUtil;
import io.github.leffinger.crossyourheart.io.PuzFile;
import io.github.leffinger.crossyourheart.room.Database;
import io.github.leffinger.crossyourheart.room.PuzFileMetadata;
import io.github.leffinger.crossyourheart.room.Puzzle;

import static android.app.Activity.RESULT_OK;

/**
 * A fragment representing a list of puzzle files.
 */
public class PuzzleListFragment extends Fragment {
    private static final String TAG = "PuzzleListFragment";
    private static final int REQUEST_CODE_OPEN_FILE = 0;

    private List<Puzzle> mPuzzles;
    private PuzzleFileAdapter mAdapter;
    private Database mDatabase;

    public static PuzzleListFragment newInstance() {
        PuzzleListFragment fragment = new PuzzleListFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        mPuzzles = new ArrayList<>();
        mAdapter = new PuzzleFileAdapter();

        // Create or load database.
        mDatabase = Database.getInstance(getActivity().getApplicationContext());

        fetchPuzzleFiles();
    }

    private void fetchPuzzleFiles() {
        // Fetch puzzles in a background task.
        new FetchPuzzleFilesTask(new FragmentReference(this)).execute();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        FragmentPuzzleListBinding binding = DataBindingUtil
                .inflate(getLayoutInflater(), R.layout.fragment_puzzle_list, container, false);
        binding.list.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.list.setAdapter(mAdapter);
        return binding.getRoot();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.fragment_puzzle_list, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
        case R.id.open_file: {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(intent, REQUEST_CODE_OPEN_FILE);
            return true;
        }
        case R.id.download_file:
            ((Callbacks) getActivity()).onDownloadSelected();
            return true;
        case R.id.delete_bad_files:
            deleteBadFiles();
            return true;
        case R.id.reindex_files:
            reindexFiles();
            return true;
        case R.id.show_tutorial:
            startActivity(new Intent(getContext(), TutorialActivity.class));
            return true;
        case R.id.send_feedback: {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:"));
            intent.putExtra(Intent.EXTRA_EMAIL, new String[]{"crossyourheartapp@gmail.com"});
            intent.putExtra(Intent.EXTRA_SUBJECT, "Feedback");
            startActivity(intent);
        }
        }
        return super.onOptionsItemSelected(item);
    }

    private void reindexFiles() {
        new ReindexFilesTask(new FragmentReference(this)).execute();
    }

    private void deleteBadFiles() {
        new DeleteBadFilesTask(new FragmentReference(this)).execute();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_CODE_OPEN_FILE && resultCode == RESULT_OK) {
            ClipData clipData = data.getClipData();
            if (clipData != null && clipData.getItemCount() > 0) {
                List<Uri> puzzleUris = new ArrayList<>();
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    ClipData.Item item = clipData.getItemAt(i);
                    puzzleUris.add(item.getUri());
                }
                ((Callbacks) getActivity()).onMultipleUrisSelected(puzzleUris);
            } else {
                ((Callbacks) getActivity()).onUriSelected(data.getData());
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void deletePuzzle(String filename) {
        AsyncTask.execute(() -> {
            IOUtil.getPuzzleFile(getContext(), filename).delete();
            mDatabase.puzzleDao().deletePuzzle(new Puzzle(filename));
        });
    }

    public interface Callbacks {
        void onPuzzleSelected(Puzzle puzzle);

        void onUriSelected(Uri uri);

        void onMultipleUrisSelected(List<Uri> uris);

        void onDownloadSelected();
    }

    private static class FragmentDetachedException extends Exception {
    }

    private static class FragmentReference {
        private final WeakReference<PuzzleListFragment> mFragment;

        public FragmentReference(PuzzleListFragment fragment) {
            mFragment = new WeakReference<>(fragment);
        }

        public Database getDatabase() throws FragmentDetachedException {
            PuzzleListFragment fragment = mFragment.get();
            if (fragment == null) {
                throw new FragmentDetachedException();
            }
            return fragment.mDatabase;
        }

        public void setPuzzleList(List<Puzzle> puzzleList) throws FragmentDetachedException {
            PuzzleListFragment fragment = mFragment.get();
            if (fragment == null) {
                throw new FragmentDetachedException();
            }
            fragment.mPuzzles = puzzleList;
        }

        public void notifyPuzzleListChanged() throws FragmentDetachedException {
            PuzzleListFragment fragment = mFragment.get();
            if (fragment == null) {
                throw new FragmentDetachedException();
            }
            fragment.mAdapter.notifyDataSetChanged();
        }

        public @NonNull
        Context getContext() throws FragmentDetachedException {
            PuzzleListFragment fragment = mFragment.get();
            if (fragment == null) {
                throw new FragmentDetachedException();
            }
            Context context = fragment.getContext();
            if (context == null) {
                throw new FragmentDetachedException();
            }
            return context;
        }

        public @NonNull
        PuzzleListFragment getFragment() throws FragmentDetachedException {
            PuzzleListFragment fragment = mFragment.get();
            if (fragment == null) {
                throw new FragmentDetachedException();
            }
            return fragment;
        }
    }

    private static class FetchPuzzleFilesTask extends AsyncTask<Void, Void, Void> {
        private final FragmentReference mFragmentReference;

        private boolean fileMismatch = false;

        private FetchPuzzleFilesTask(FragmentReference fragmentReference) {
            mFragmentReference = fragmentReference;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                List<Puzzle> puzzles = mFragmentReference.getDatabase().puzzleDao().getAll();
                mFragmentReference.setPuzzleList(puzzles);

                Set<String> dbFiles = new HashSet<>();
                for (Puzzle puzzle : puzzles) {
                    dbFiles.add(puzzle.filename);
                }

                File puzzleDir = IOUtil.getPuzzleDir(mFragmentReference.getContext());
                String[] files = puzzleDir.list();
                Set<String> dirFiles = new HashSet<>();
                dirFiles.addAll(Arrays.asList(files));

                fileMismatch = !dbFiles.equals(dirFiles);
            } catch (FragmentDetachedException e) {
                Log.w(TAG, "Fragment detached, unable to complete task", e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            try {
                mFragmentReference.notifyPuzzleListChanged();
                if (fileMismatch) {
                    PuzzleListFragment fragment = mFragmentReference.getFragment();
                    Context context = mFragmentReference.getContext();
                    new AlertDialog.Builder(context).setTitle(R.string.reindex_alert)
                            .setMessage(R.string.reindex_safe)
                            .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                                fragment.reindexFiles();
                            }).setNegativeButton(android.R.string.cancel, null).setCancelable(true)
                            .create().show();
                }
            } catch (FragmentDetachedException e) {
                Log.w(TAG, "Fragment detached, unable to complete task", e);
            }
        }
    }

    private static class DeleteBadFilesTask extends AsyncTask<Void, Void, List<File>> {

        private final FragmentReference mFragmentReference;

        public DeleteBadFilesTask(FragmentReference fragmentReference) {
            super();
            mFragmentReference = fragmentReference;
        }

        @Override
        protected List<File> doInBackground(Void... voids) {
            try {
                File puzzleDir = IOUtil.getPuzzleDir(mFragmentReference.getContext());
                File[] files = puzzleDir.listFiles();
                List<File> badFiles = new ArrayList<>();
                for (File file : files) {
                    try (FileInputStream inputStream = new FileInputStream(file)) {
                        PuzFile.loadPuzFile(inputStream);
                    } catch (IOException e) {
                        badFiles.add(file);
                    }
                }
                return badFiles;
            } catch (FragmentDetachedException e) {
                Log.w(TAG, "Fragment detached, unable to complete task", e);
                return ImmutableList.of();
            }
        }

        @Override
        protected void onPostExecute(List<File> files) {
            try {
                Context context = mFragmentReference.getContext();
                if (files.isEmpty()) {
                    new AlertDialog.Builder(context).setMessage("No corrupted files found")
                            .setPositiveButton(android.R.string.ok, null).create().show();
                } else {
                    AlertDialog alertDialog = new AlertDialog.Builder(context)
                            .setMessage("Delete " + files.size() + " files?")
                            .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                                for (File file : files) {
                                    file.delete();
                                }
                            }).setCancelable(true).create();
                    alertDialog.show();
                }
            } catch (FragmentDetachedException e) {
                Log.w(TAG, "Fragment detached, unable to complete task", e);
            }
        }
    }

    private static class ReindexFilesTask extends AsyncTask<Void, Integer, Void> {
        private final FragmentReference mFragmentReference;
        private AlertDialog mAlertDialog;
        private AlertProgressBinding mAlertProgressBinding;

        public ReindexFilesTask(FragmentReference fragmentReference) {
            mFragmentReference = fragmentReference;
        }

        @Override
        protected void onPreExecute() {
            try {
                mAlertProgressBinding = DataBindingUtil
                        .inflate(mFragmentReference.getFragment().getLayoutInflater(),
                                 R.layout.alert_progress, null, false);
                mAlertDialog = new AlertDialog.Builder(mFragmentReference.getContext())
                        .setView(mAlertProgressBinding.getRoot()).setCancelable(false)
                        .setTitle("Reindexing files...").show();
            } catch (FragmentDetachedException e) {
                Log.w(TAG, "Fragment detached", e);
            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            mAlertProgressBinding.progressBar.setProgress(values[0]);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            if (mAlertProgressBinding == null) {
                return null;
            }

            try {
                Database database = mFragmentReference.getDatabase();
                Context context = mFragmentReference.getContext();

                // Current list of files (existing files will be updated; missing files will be
                // deleted).
                Set<String> currentFiles = new HashSet<>(database.puzzleDao().getFiles());
                Set<String> foundFiles = new HashSet<>();

                // Scan and update puzzle files.
                File puzzleDir = IOUtil.getPuzzleDir(context);
                File[] files = puzzleDir.listFiles();
                mAlertProgressBinding.progressBar.setMax(files.length);
                for (int i = 0; i < files.length; i++) {
                    File file = files[i];
                    foundFiles.add(file.getName());
                    try (FileInputStream inputStream = new FileInputStream(file)) {
                        PuzFile puzzleLoader = PuzFile.loadPuzFile(inputStream);
                        database.puzzleDao()
                                .insert(new Puzzle(file.getName(), puzzleLoader.getTitle(),
                                                   puzzleLoader.getAuthor(),
                                                   puzzleLoader.getCopyright(),
                                                   puzzleLoader.isSolved(), false,
                                                   !puzzleLoader.isEmpty(),
                                                   puzzleLoader.getScrambleState()));
                        database.puzFileMetadataDao().insert(new PuzFileMetadata(file.getName(),
                                                                                 puzzleLoader
                                                                                         .getHeaderChecksum()));
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to load puzzle file " + file.getName(), e);
                        e.printStackTrace();
                    }
                    publishProgress(i + 1);
                }

                // Delete files that were in the DB but not on disk.
                currentFiles.removeAll(foundFiles);
                Log.i(TAG, "Removing " + foundFiles.size() + " files from DB");
                List<Puzzle> toBeDeleted = new ArrayList<>();
                for (String missingFile : currentFiles) {
                    toBeDeleted.add(new Puzzle(missingFile));
                }
                database.puzzleDao().deletePuzzles(toBeDeleted);

                mFragmentReference.setPuzzleList(database.puzzleDao().getAll());
            } catch (FragmentDetachedException e) {
                Log.w(TAG, "Fragment detached", e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            try {
                PuzzleListFragment fragment = mFragmentReference.getFragment();
                mFragmentReference.notifyPuzzleListChanged();
                Toast.makeText(mFragmentReference.getContext(), mFragmentReference.getContext()
                                       .getString(R.string.reindexed_files,
                                                  fragment.mPuzzles.size()),
                               Toast.LENGTH_SHORT).show();
            } catch (FragmentDetachedException e) {
                Log.w(TAG, "Fragment detached", e);
            }

            if (mAlertDialog != null) {
                mAlertDialog.dismiss();
            }
        }
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
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS);
                ((Callbacks) getActivity()).onPuzzleSelected(mBinding.getPuzzle());
            });

            // Long-clicking the puzzle file brings up an option to delete the file.
            mBinding.getRoot().setOnLongClickListener(view -> {
                AlertDialog alertDialog =
                        new AlertDialog.Builder(getContext()).setMessage(R.string.delete_puzzle)
                                .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                                    int index = getAdapterPosition();
                                    mPuzzles.remove(index);
                                    mAdapter.notifyItemRemoved(index);
                                    deletePuzzle(mBinding.getPuzzle().getFilename());
                                }).setNegativeButton(android.R.string.cancel, null)
                                .setCancelable(true).create();
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