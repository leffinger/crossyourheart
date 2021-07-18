package io.github.leffinger.crossyourheart.activities;

import android.annotation.SuppressLint;
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
import androidx.room.Room;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
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
        mDatabase = Room.databaseBuilder(getActivity().getApplicationContext(), Database.class,
                                         "puzzles").build();

        fetchPuzzleFiles();
    }

    @SuppressWarnings("StaticFieldLeak")
    private void fetchPuzzleFiles() {
        // Fetch puzzles in a background task.
        new AsyncTask<Void, Void, Void>() {
            private boolean fileMismatch = false;

            @Override
            protected Void doInBackground(Void... voids) {
                mPuzzles = mDatabase.puzzleDao().getAll();

                Context context = getContext();
                if (context == null) {
                    return null;
                }
                Set<String> dbFiles = new HashSet<>();
                for (Puzzle puzzle : mPuzzles) {
                    dbFiles.add(puzzle.filename);
                }

                File puzzleDir = IOUtil.getPuzzleDir(context);
                String[] files = puzzleDir.list();
                Set<String> dirFiles = new HashSet<>();
                dirFiles.addAll(Arrays.asList(files));

                fileMismatch = !dbFiles.equals(dirFiles);

                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                mAdapter.notifyDataSetChanged();

                if (fileMismatch) {
                    Context context = getContext();
                    if (context == null) {
                        return;
                    }
                    new AlertDialog.Builder(context).setMessage(R.string.reindex_alert)
                            .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                                reindexFiles();
                            }).setNegativeButton(android.R.string.cancel, null).setCancelable(true)
                            .create().show();
                }
            }
        }.execute();
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
        case R.id.open_file:
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(intent, REQUEST_CODE_OPEN_FILE);
            break;
        case R.id.download_file:
            ((Callbacks) getActivity()).onDownloadSelected();
            break;
        case R.id.delete_bad_files:
            deleteBadFiles();
            break;
        case R.id.reindex_files:
            reindexFiles();
            break;
        }
        return super.onOptionsItemSelected(item);
    }

    @SuppressLint("StaticFieldLeak")
    private void reindexFiles() {
        new AsyncTask<Void, Integer, Void>() {
            private AlertDialog mAlertDialog;
            private AlertProgressBinding mAlertProgressBinding;

            @Override
            protected void onPreExecute() {
                mAlertProgressBinding = DataBindingUtil
                        .inflate(getLayoutInflater(), R.layout.alert_progress, null, false);
                mAlertDialog = new AlertDialog.Builder(getContext())
                        .setView(mAlertProgressBinding.getRoot()).setCancelable(false)
                        .setTitle("Reindexing files...").show();
            }

            @Override
            protected void onProgressUpdate(Integer... values) {
                mAlertProgressBinding.progressBar.setProgress(values[0]);
            }

            @Override
            protected Void doInBackground(Void... voids) {
                Context context = getContext();
                if (context == null) {
                    return null;
                }
                mDatabase.puzzleDao().deletePuzzles(mPuzzles);
                File puzzleDir = IOUtil.getPuzzleDir(context);
                File[] files = puzzleDir.listFiles();
                mAlertProgressBinding.progressBar.setMax(files.length);
                for (int i = 0; i < files.length; i++) {
                    File file = files[i];
                    try (FileInputStream inputStream = new FileInputStream(file)) {
                        PuzFile puzzleLoader = PuzFile.loadPuzFile(inputStream);
                        mDatabase.puzzleDao()
                                .insert(Puzzle.fromPuzzleFile(file.getName(), puzzleLoader));
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to load puzzle file " + file.getName(), e);
                        e.printStackTrace();
                    }
                    publishProgress(i + 1);
                }
                mPuzzles = mDatabase.puzzleDao().getAll();
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                mAdapter.notifyDataSetChanged();
                Toast.makeText(getContext(), getString(R.string.reindexed_files, mPuzzles.size()),
                               Toast.LENGTH_SHORT).show();
                mAlertDialog.dismiss();
            }
        }.execute();
    }

    @SuppressLint("StaticFieldLeak")
    private void deleteBadFiles() {
        new AsyncTask<Void, Void, List<File>>() {

            @Override
            protected List<File> doInBackground(Void... voids) {
                File puzzleDir = IOUtil.getPuzzleDir(getContext());
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
            }

            @Override
            protected void onPostExecute(List<File> files) {
                if (files.isEmpty()) {
                    new AlertDialog.Builder(getContext()).setMessage("No corrupted files found")
                            .setPositiveButton(android.R.string.ok, null).create().show();
                } else {
                    AlertDialog alertDialog = new AlertDialog.Builder(getContext())
                            .setMessage("Delete " + files.size() + " files?")
                            .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                                for (File file : files) {
                                    file.delete();
                                }
                            }).setCancelable(true).create();
                    alertDialog.show();
                }
            }
        }.execute();
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
        void onFileSelected(String filename);

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
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS);
                ((Callbacks) getActivity()).onFileSelected(mBinding.getPuzzle().getFilename());
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