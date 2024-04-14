package io.github.leffinger.crossyourheart.activities;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.MenuProvider;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Objects;

import io.github.leffinger.crossyourheart.R;
import io.github.leffinger.crossyourheart.databinding.ClueListHeaderBinding;
import io.github.leffinger.crossyourheart.databinding.FragmentClueBinding;
import io.github.leffinger.crossyourheart.databinding.SimpleCellBinding;
import io.github.leffinger.crossyourheart.viewmodels.ClueViewModel;
import io.github.leffinger.crossyourheart.viewmodels.PuzzleViewModel;

/**
 * Displays a list of clues and entries.
 */
public class PuzzleClueListFragment extends Fragment {
    private static final String TAG = "PuzzleClueListFragment";

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
        Log.i(TAG, "Current clue: " + mPuzzleViewModel.getCurrentClue().getValue());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        io.github.leffinger.crossyourheart.databinding.FragmentClueListBinding mClueListBinding =
                DataBindingUtil.inflate(inflater, R.layout.fragment_clue_list, container, false);

        ConcatAdapter concatAdapter = new ConcatAdapter(new ConcatAdapter.Config.Builder().build(),
                new ClueListHeaderAdapter("ACROSS"),
                new ClueListAdapter(true, mPuzzleViewModel.getNumAcrossClues()),
                new ClueListHeaderAdapter("DOWN"),
                new ClueListAdapter(false, mPuzzleViewModel.getNumDownClues()));

        mClueListBinding.list.setLayoutManager(new LinearLayoutManager(mContext));
        mClueListBinding.list.setAdapter(concatAdapter);
        mClueListBinding.list.setHasFixedSize(true);

        ClueViewModel currentClue = mPuzzleViewModel.getCurrentClue().getValue();
        if (currentClue != null) {
            int position = getAbsolutePosition(currentClue);
            Log.i(TAG, "Scrolling to position: " + position);
            mClueListBinding.list.scrollToPosition(position);
        }

        return mClueListBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {

            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                if (item.getItemId() == android.R.id.home) {
                    requireActivity().onBackPressed();
                    return true;
                }
                return false;
            }
        });
    }

    private int getAbsolutePosition(ClueViewModel clueViewModel) {
        if (clueViewModel.isAcross()) {
            return clueViewModel.getIndex() + 1;
        }
        return clueViewModel.getIndex() + mPuzzleViewModel.getNumAcrossClues() + 2;
    }

    private static class ClueListHeader extends RecyclerView.ViewHolder {
        public ClueListHeader(ClueListHeaderBinding binding) {
            super(binding.getRoot());
        }
    }

    /**
     * Single-element adapter for list headers ("ACROSS" and "DOWN").
     */
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

    /**
     * Holds a clue and entry pair.
     */
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
            mBinding.clueCells.setHasFixedSize(true);

            mBinding.clueCells.addItemDecoration(new CellBorderDecoration(getResources().getDimensionPixelSize(R.dimen.cell_border_size)));
            mBinding.getRoot()
                    .setOnClickListener(
                            unused -> mPuzzleViewModel.selectClue(mBinding.getViewModel(), 0));
        }

        /* Handles drawing evenly sized and spaced borders around each cell. */
        private class CellBorderDecoration extends RecyclerView.ItemDecoration {
            private final Rect mBounds = new Rect();
            private final Paint mPaint = new Paint();
            private final int mCellBorderSize;

            public CellBorderDecoration(int cellBorderSize) {
                mCellBorderSize = cellBorderSize;
                mPaint.setStyle(Paint.Style.STROKE);
            }

            @Override
            public void onDrawOver(@NonNull Canvas canvas, @NonNull RecyclerView parent,
                                   @NonNull RecyclerView.State state) {
                final int top = 0;
                final int left = 0;
                final int bottom = parent.getHeight();

                // Draw vertical lines at the right of each child.
                mPaint.setStrokeWidth(mCellBorderSize);
                RecyclerView.LayoutManager layoutManager =
                        Objects.requireNonNull(parent.getLayoutManager());
                int right = 0;
                for (int i = 0; i < parent.getChildCount(); i++) {
                    final View child = parent.getChildAt(i);
                    layoutManager.getDecoratedBoundsWithMargins(child, mBounds);
                    right = mBounds.right;
                    canvas.drawLine(right, top, right, bottom, mPaint);
                }

                // Draw lines around the border (top/left/bottom).
                mPaint.setStrokeWidth(mCellBorderSize * 2);
                canvas.drawLine(left, top, right, top, mPaint);
                canvas.drawLine(left, top, left, bottom, mPaint);
                canvas.drawLine(left, bottom, right, bottom, mPaint);
            }
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

    /**
     * Holds a single cell of an entry.
     */
    private class CellHolder extends RecyclerView.ViewHolder {
        private final SimpleCellBinding mBinding;

        private CellHolder(SimpleCellBinding binding) {
            super(binding.getRoot());
            mBinding = binding;
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
            SimpleCellBinding binding =
                    DataBindingUtil.inflate(inflater, R.layout.simple_cell, parent, false);
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
