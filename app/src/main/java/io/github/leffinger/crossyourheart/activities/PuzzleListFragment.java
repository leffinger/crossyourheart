package io.github.leffinger.crossyourheart.activities;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
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
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.leffinger.crossyourheart.R;
import io.github.leffinger.crossyourheart.databinding.FragmentPuzzleFileBinding;
import io.github.leffinger.crossyourheart.databinding.FragmentPuzzleListBinding;
import io.github.leffinger.crossyourheart.io.IOUtil;
import io.github.leffinger.crossyourheart.io.PuzFile;
import io.github.leffinger.crossyourheart.viewmodels.PuzzleFileViewModel;

import static android.app.Activity.RESULT_OK;

/**
 * A fragment representing a list of puzzle files.
 */
public class PuzzleListFragment extends Fragment {
    private static final String TAG = "PuzzleListFragment";
    private static final int REQUEST_CODE_OPEN_FILE = 0;
    private static final int MESSAGE_PUZZLE = 0;

    private List<PuzzleFileViewModel> mPuzzles;
    private PuzzleFileAdapter mAdapter;
    private Handler mHandler;

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

        // Implements a message handler that adds puzzles to the list as they are read from disk.
        mHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                if (msg.what == MESSAGE_PUZZLE) {
                    PuzzleFileViewModel viewModel = (PuzzleFileViewModel) msg.obj;
                    mPuzzles.add(viewModel);
                    mAdapter.notifyItemInserted(mPuzzles.size() - 1);
                } else {
                    Log.e(TAG, "Handler received unknown message type: " + msg.what);
                }
            }
        };

        // Fetch puzzles in a background task.
        new FetchPuzzleFilesTask().execute();
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
        }
        return super.onOptionsItemSelected(item);
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

    public interface Callbacks {
        void onFileSelected(String filename);

        void onUriSelected(Uri uri);

        void onMultipleUrisSelected(List<Uri> uris);

        void onDownloadSelected();
    }

    private class FetchPuzzleFilesTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            File puzzleDir = IOUtil.getPuzzleDir(getContext());
            File[] files = puzzleDir.listFiles();
            Arrays.sort(files, (file1, file2) -> file2.getName().compareTo(file1.getName()));
            for (File file : files) {
                try (FileInputStream inputStream = new FileInputStream(file)) {
                    PuzFile puzzleLoader = PuzFile.loadPuzFile(inputStream);
                    PuzzleFileViewModel viewModel =
                            new PuzzleFileViewModel(file.getName(), puzzleLoader);
                    Message completeMessage = mHandler.obtainMessage(MESSAGE_PUZZLE, viewModel);
                    completeMessage.sendToTarget();
                } catch (IOException e) {
                    Log.e(TAG, "Failed to load puzzle file " + file.getName(), e);
                    e.printStackTrace();
                }
            }
            return null;
        }
    }

    private class PuzzleFileHolder extends RecyclerView.ViewHolder {
        private final FragmentPuzzleFileBinding mBinding;

        public PuzzleFileHolder(FragmentPuzzleFileBinding binding) {
            super(binding.getRoot());
            mBinding = binding;
        }

        public void bind(PuzzleFileViewModel viewModel) {
            mBinding.setViewModel(viewModel);

            // Clicking on the puzzle file opens that file.
            mBinding.getRoot().setOnClickListener(view -> {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS);
                ((Callbacks) getActivity()).onFileSelected(mBinding.getViewModel().getFilename());
            });

            // Long-clicking the puzzle file brings up an option to deete the file.
            mBinding.getRoot().setOnLongClickListener(view -> {
                AlertDialog alertDialog =
                        new AlertDialog.Builder(getContext()).setMessage(R.string.delete_puzzle)
                                .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                                    int index = getAdapterPosition();
                                    mPuzzles.remove(index);
                                    mAdapter.notifyItemRemoved(index);
                                    String filename = mBinding.getViewModel().getFilename();
                                    IOUtil.getPuzzleFile(getContext(), filename).delete();
                                }).setCancelable(true).create();
                alertDialog.show();
                return true;
            });

            // Display the puzzle's status in an image on the right.
            mBinding.solved.setImageResource(viewModel.getSolvedStateResId());
            mBinding.solved.setOnClickListener(view -> {
                Toast.makeText(getActivity(), viewModel.getSolvedMessageResId(), Toast.LENGTH_SHORT)
                        .show();
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