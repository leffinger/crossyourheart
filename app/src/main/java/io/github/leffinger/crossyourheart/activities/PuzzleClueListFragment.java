package io.github.leffinger.crossyourheart.activities;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import io.github.leffinger.crossyourheart.R;
import io.github.leffinger.crossyourheart.databinding.CellBinding;
import io.github.leffinger.crossyourheart.databinding.ClueListHeaderBinding;
import io.github.leffinger.crossyourheart.databinding.FragmentClueBinding;
import io.github.leffinger.crossyourheart.databinding.FragmentClueListBinding;
import io.github.leffinger.crossyourheart.viewmodels.ClueViewModel;
import io.github.leffinger.crossyourheart.viewmodels.PuzzleViewModel;

/** Displays a list of clues and entries. */
public class PuzzleClueListFragment extends Fragment {

    private Context mContext;
    private PuzzleViewModel mPuzzleViewModel;

    private PuzzleClueListFragment() {
    }

    public static PuzzleClueListFragment newInstance() {
        return new PuzzleClueListFragment();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mContext = context;
        mPuzzleViewModel =
                new ViewModelProvider((ViewModelStoreOwner) context).get(PuzzleViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        FragmentClueListBinding binding =
                DataBindingUtil.inflate(inflater, R.layout.fragment_clue_list, container, false);

        ConcatAdapter concatAdapter =
                new ConcatAdapter(
                        new ClueListHeaderAdapter("ACROSS"),
                        new ClueListAdapter(true, mPuzzleViewModel.getNumAcrossClues()),
                        new ClueListHeaderAdapter("DOWN"),
                        new ClueListAdapter(false, mPuzzleViewModel.getNumDownClues()));

        binding.list.setLayoutManager(new LinearLayoutManager(mContext));
        binding.list.setAdapter(concatAdapter);

        return binding.getRoot();
    }

    private class ClueListHeader extends RecyclerView.ViewHolder {
        public ClueListHeader(ClueListHeaderBinding binding) {
            super(binding.getRoot());
        }
    }

    /** Single-element adapter for list headers ("ACROSS" and "DOWN"). */
    private class ClueListHeaderAdapter extends RecyclerView.Adapter<ClueListHeader> {
        private final String headerText;

        private ClueListHeaderAdapter(String headerText) {
            this.headerText = headerText;
        }

        @NonNull
        @Override
        public ClueListHeader onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ClueListHeaderBinding binding = DataBindingUtil.inflate(LayoutInflater.from(mContext),
                    R.layout.clue_list_header, parent, false);
            binding.setHeaderText(headerText);
            return new ClueListHeader(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull ClueListHeader holder, int position) {
        }

        @Override
        public int getItemCount() {
            return 1;
        }
    }

    /** Holds a clue and entry pair. */
    private class ClueHolder extends RecyclerView.ViewHolder {
        private final FragmentClueBinding mBinding;

        public ClueHolder(FragmentClueBinding binding) {
            super(binding.getRoot());
            mBinding = binding;
        }

        private void bind(final ClueViewModel viewModel) {
            mBinding.setViewModel(viewModel);
            mBinding.setLifecycleOwner(getViewLifecycleOwner());
            mBinding.clueCells.setLayoutManager(
                    new GridLayoutManager(mContext, Math.max(viewModel.getCells().size(), 10)));
            mBinding.clueCells.setAdapter(new CellAdapter(viewModel));
            mBinding.getRoot()
                    .setOnClickListener(
                            unused -> mPuzzleViewModel.selectClue(mBinding.getViewModel(), 0));
        }
    }

    private class ClueListAdapter extends RecyclerView.Adapter<ClueHolder> {
        private final boolean mIsAcross;
        private final int mNumClues;

        public ClueListAdapter(boolean mIsAcross, int mNumClues) {
            this.mIsAcross = mIsAcross;
            this.mNumClues = mNumClues;
        }

        @NonNull
        @Override
        public ClueHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(mContext);
            FragmentClueBinding binding =
                    DataBindingUtil.inflate(inflater, R.layout.fragment_clue, parent, false);
            return new ClueHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull ClueHolder holder, int position) {
            holder.bind(mPuzzleViewModel.getClue(mIsAcross, position));
        }

        @Override
        public int getItemCount() {
            return mNumClues;
        }
    }

    /** Holds a single cell of an entry. */
    private class CellHolder extends RecyclerView.ViewHolder {
        private final CellBinding mBinding;

        private CellHolder(CellBinding binding) {
            super(binding.getRoot());
            mBinding = binding;
            mBinding.clueNumber.setVisibility(View.INVISIBLE);
        }

        private void bind(ClueViewModel clueViewModel, int position) {
            mBinding.setCellViewModel(clueViewModel.getCells().get(position));
            mBinding.setLifecycleOwner(getViewLifecycleOwner());
            mBinding.getRoot()
                    .setOnClickListener(
                            unused -> mPuzzleViewModel.selectClue(clueViewModel, position));
        }
    }

    private class CellAdapter extends RecyclerView.Adapter<CellHolder> {

        private final ClueViewModel mClueViewModel;

        private CellAdapter(ClueViewModel mClueViewModel) {
            this.mClueViewModel = mClueViewModel;
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
            holder.bind(mClueViewModel, position);
        }

        @Override
        public int getItemCount() {
            return mClueViewModel.getCells().size();
        }
    }
}
