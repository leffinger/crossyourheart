package io.github.leffinger.crossyourheart.views;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

public class PuzzleView extends RecyclerView {
    private int mPuzzleWidth;
    private int mCellWidth;

    public PuzzleView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public void setPuzzleWidth(int puzzleWidth) {
        mPuzzleWidth = puzzleWidth;
    }

    public int getCellWidth() {
        return mCellWidth;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        int width = getMeasuredWidth();
        mCellWidth = width / mPuzzleWidth;
    }
}
