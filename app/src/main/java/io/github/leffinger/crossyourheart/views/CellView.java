package io.github.leffinger.crossyourheart.views;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.BindingAdapter;

import java.util.Arrays;

import io.github.leffinger.crossyourheart.R;

public class CellView extends androidx.appcompat.widget.AppCompatTextView {
    private boolean mIsSelected;
    private boolean mIsCircled;
    private boolean mIsHighlighted;

    public CellView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @BindingAdapter("isSelected")
    public static void setSelected(CellView cellView, boolean isSelected) {
        cellView.setSelected(isSelected);
    }

    @Override
    public void setSelected(boolean selected) {
        if (mIsSelected != selected) {
            mIsSelected = selected;
            refreshDrawableState();
        }
    }

    @BindingAdapter("isCircled")
    public static void setCircled(CellView cellView, boolean isCircled) {
        cellView.setCircled(isCircled);
    }

    @BindingAdapter("isHighlighted")
    public static void setHighlighted(CellView cellView, boolean isHighlighted) {
        cellView.setHighlighted(isHighlighted);
    }

    public void setCircled(boolean circled) {
        if (mIsCircled != circled) {
            mIsCircled = circled;
            refreshDrawableState();
        }
    }

    public void setHighlighted(boolean highlighted) {
        if (mIsHighlighted != highlighted) {
            mIsHighlighted = highlighted;
            refreshDrawableState();
        }
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        int[] extraDrawableStates = new int[3];
        int count = 0;
        if (mIsSelected) {
            extraDrawableStates[count++] = R.attr.isSelected;
        }
        if (mIsHighlighted) {
            extraDrawableStates[count++] = R.attr.isHighlighted;
        }
        if (mIsCircled) {
            extraDrawableStates[count++] = R.attr.isCircled;
        }
        if (count > 0) {
            int[] resized = Arrays.copyOf(extraDrawableStates, count);
            final int[] drawableState = super.onCreateDrawableState(extraSpace + count);
            mergeDrawableStates(drawableState, resized);
            return drawableState;
        }
        return super.onCreateDrawableState(extraSpace);
    }
}
