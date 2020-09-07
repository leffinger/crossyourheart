package io.github.leffinger.crossyourheart.activities;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
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
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import io.github.leffinger.crossyourheart.R;
import io.github.leffinger.crossyourheart.databinding.FragmentPuzzleFileBinding;
import io.github.leffinger.crossyourheart.databinding.FragmentPuzzleListBinding;
import io.github.leffinger.crossyourheart.io.PuzFile;
import io.github.leffinger.crossyourheart.viewmodels.PuzzleFileViewModel;

import static android.app.Activity.RESULT_OK;

/**
 * A fragment representing a list of puzzle files.
 */
public class PuzzleListFragment extends Fragment {
    private static final String TAG = "PuzzleListFragment";
    private static final int REQUEST_CODE_OPEN_FILE = 0;
    private List<PuzzleFileViewModel> mPuzzles;
    private PuzzleFileAdapter mAdapter;

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
        File puzzleDir = getContext().getFilesDir();
        File[] files = puzzleDir.listFiles();
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File file1, File file2) {
                return (int) (file2.lastModified() - file1.lastModified());
            }
        });
        for (File file : files) {
            try (FileInputStream inputStream = new FileInputStream(file)) {
                PuzFile puzzleLoader = PuzFile.loadPuzFile(inputStream);
                mPuzzles.add(new PuzzleFileViewModel(file.getName(), puzzleLoader));
            } catch (IOException e) {
                Log.e(TAG, "Failed to load puzzle file", e);
                e.printStackTrace();
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        FragmentPuzzleListBinding binding = DataBindingUtil
                .inflate(getLayoutInflater(), R.layout.fragment_puzzle_list, container, false);
        binding.list.setLayoutManager(new LinearLayoutManager(getContext()));
        mAdapter = new PuzzleFileAdapter();
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
            startActivityForResult(intent, REQUEST_CODE_OPEN_FILE);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_CODE_OPEN_FILE && resultCode == RESULT_OK) {
            ((Callbacks) getActivity()).onUriSelected(data.getData());
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    public interface Callbacks {
        void onFileSelected(String filename);

        void onUriSelected(Uri uri);
    }

    private class PuzzleFileHolder extends RecyclerView.ViewHolder {
        private FragmentPuzzleFileBinding mBinding;

        public PuzzleFileHolder(FragmentPuzzleFileBinding binding) {
            super(binding.getRoot());
            mBinding = binding;
        }

        public void bind(PuzzleFileViewModel viewModel) {
            mBinding.setViewModel(viewModel);
            mBinding.getRoot().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS);
                    ((Callbacks) getActivity())
                            .onFileSelected(mBinding.getViewModel().getFilename());
                }
            });
            mBinding.getRoot().setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    AlertDialog alertDialog =
                            new AlertDialog.Builder(getContext()).setMessage(R.string.delete_puzzle)
                                    .setPositiveButton(android.R.string.ok,
                                                       new DialogInterface.OnClickListener() {
                                                           @Override
                                                           public void onClick(
                                                                   DialogInterface dialogInterface,
                                                                   int i) {
                                                               int index = getAdapterPosition();
                                                               mPuzzles.remove(index);
                                                               mAdapter.notifyItemRemoved(index);
                                                               String filename =
                                                                       mBinding.getViewModel()
                                                                               .getFilename();
                                                               new File(getActivity().getFilesDir(),
                                                                        filename).delete();
                                                           }
                                                       }).setCancelable(true).create();
                    alertDialog.show();
                    return true;
                }
            });
        }
    }

    private class PuzzleFileAdapter extends RecyclerView.Adapter<PuzzleFileHolder> {

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
    }
}