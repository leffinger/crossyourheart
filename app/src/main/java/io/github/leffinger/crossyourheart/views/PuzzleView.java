package io.github.leffinger.crossyourheart.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Objects;

import io.github.leffinger.crossyourheart.R;

public class PuzzleView extends RecyclerView {
    private static final String TAG = "PuzzleView";

    private final ScaleGestureDetector mScaleGestureDetector;
    private final GestureDetector mGestureDetector;
    private final Matrix mTransformMatrix = new Matrix();
    private final Matrix mReverseTransformMatrix = new Matrix();
    private final Rect mTempBounds = new Rect();
    private final RectF mBounds = new RectF();
    private int mNumRows = 0;
    private int mNumCols = 0;

    private float mCurrentTranslateX = 0;
    private float mCurrentTranslateY = 0;
    private float mScaleFactor = 1.f;

    public PuzzleView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mScaleGestureDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        mScaleFactor *= detector.getScaleFactor();
                        adjustMatrixAndInvalidate();
                        return true;
                    }
                });
        mGestureDetector =
                new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onScroll(@NonNull MotionEvent e1, @NonNull MotionEvent e2,
                                            float distanceX, float distanceY) {
                        mCurrentTranslateX += distanceX;
                        mCurrentTranslateY += distanceY;
                        adjustMatrixAndInvalidate();
                        return true;
                    }
                });

        // Add an ItemDecoration to draw the lines between the cells.
        addItemDecoration(new CellBorderDecoration(
                getResources().getDimensionPixelSize(R.dimen.cell_border_size)));
    }

    public void setPuzzleSize(int numRows, int numCols) {
        mNumRows = numRows;
        mNumCols = numCols;
    }

    public void adjustViewport(int firstCellOffset, int selectedCellOffset, int lastCellOffset) {
        View firstCell = getChildAt(firstCellOffset);
        View selectedCell = getChildAt(selectedCellOffset);
        View lastCell = getChildAt(lastCellOffset);
        if (firstCell == null || selectedCell == null || lastCell == null) {
            Log.w(TAG, "Unable to adjust because at least one CellView is null");
            return;
        }
        RecyclerView.LayoutManager layoutManager = Objects.requireNonNull(getLayoutManager());

        // Construct a rectangle that contains the entire entry.
        layoutManager.getDecoratedBoundsWithMargins(firstCell, mTempBounds);
        mBounds.left = mTempBounds.left;
        mBounds.top = mTempBounds.top;

        layoutManager.getDecoratedBoundsWithMargins(lastCell, mTempBounds);
        mBounds.right = mTempBounds.right;
        mBounds.bottom = mTempBounds.bottom;

        // If the full entry is wider and/or higher than the viewport, focus on the selected cell.
        layoutManager.getDecoratedBoundsWithMargins(selectedCell, mTempBounds);
        if (mBounds.width() * mScaleFactor > getWidth()) {
            mBounds.left = mTempBounds.left;
            mBounds.right = mTempBounds.right;
        }
        if (mBounds.height() * mScaleFactor > getHeight()) {
            mBounds.top = mTempBounds.top;
            mBounds.bottom = mTempBounds.bottom;
        }

        // If the selected area is not fully visible, adjust the viewport to make it visible.
        mTransformMatrix.mapRect(mBounds);
        if (mBounds.left < 0) {
            mCurrentTranslateX += mBounds.left;
        } else if (mBounds.right > getWidth()) {
            mCurrentTranslateX += mBounds.right - getWidth();
        }
        if (mBounds.top < 0) {
            mCurrentTranslateY += mBounds.top;
        } else if (mBounds.bottom > getHeight()) {
            mCurrentTranslateY += mBounds.bottom - getHeight();
        }

        adjustMatrixAndInvalidate();
    }

    @Override
    public boolean hasFixedSize() {
        return true;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.save();
        canvas.setMatrix(mTransformMatrix);
        super.dispatchDraw(canvas);
        canvas.restore();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getPointerCount() == 1) {
            mGestureDetector.onTouchEvent(event);
        } else {
            mScaleGestureDetector.onTouchEvent(event);
        }

        // Reverse-transform the touch event so that it goes to the correct position.
        MotionEvent transformEvent = MotionEvent.obtain(event);
        transformEvent.transform(mReverseTransformMatrix);
        boolean returnVal = super.dispatchTouchEvent(transformEvent);
        transformEvent.recycle();

        return returnVal;
    }

    private void adjustMatrixAndInvalidate() {
        // The puzzle's width (at scale 1.0) is always equal to getWidth().
        // The puzzle's height can be inferred from this width and the puzzle ratio.
        float puzzleHeight = mNumRows * ((float) getWidth()) / mNumCols;

        // Don't let the object get too small or too large.
        float minScaleFactor = Math.min(1f, getHeight() / puzzleHeight);
        mScaleFactor = Math.max(minScaleFactor, Math.min(mScaleFactor, 5.0f));

        // Don't move past the edges of the puzzle.
        float maxTranslateX = getWidth() * (mScaleFactor - 1);
        mCurrentTranslateX = Math.max(0f, Math.min(maxTranslateX, mCurrentTranslateX));
        float maxTranslateY = (puzzleHeight * mScaleFactor - getHeight()) * 1.05f;
        mCurrentTranslateY = Math.max(0f, Math.min(maxTranslateY, mCurrentTranslateY));

        // Record the actual transformation as a matrix.
        mTransformMatrix.setScale(mScaleFactor, mScaleFactor);
        mTransformMatrix.postTranslate(-mCurrentTranslateX, -mCurrentTranslateY);
        mTransformMatrix.invert(mReverseTransformMatrix);

        // Redraw the canvas.
        invalidate();
    }

    private class CellBorderDecoration extends RecyclerView.ItemDecoration {
        private final int mCellBorderSize;

        public CellBorderDecoration(int cellBorderSize) {
            mCellBorderSize = cellBorderSize;
        }

        @Override
        public void getItemOffsets(Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                                   @NonNull RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view);
            int left = position % mNumCols == 0 ? mCellBorderSize : 0;
            int top = position < mNumCols ? mCellBorderSize : 0;
            outRect.set(left, top, mCellBorderSize, mCellBorderSize);
        }
    }
}
