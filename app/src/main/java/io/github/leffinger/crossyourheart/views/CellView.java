package io.github.leffinger.crossyourheart.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.BindingAdapter;

import java.util.Arrays;

import io.github.leffinger.crossyourheart.R;

public class CellView extends androidx.appcompat.widget.AppCompatTextView {
    private int mCellNumber = 0;
    private final Paint mNumberPaint = new Paint();
    private final Paint mCirclePaint = new Paint();
    private final Paint mIncorrectPaint = new Paint();
    private final Paint mCorrectPaint = new Paint();
    private final Paint mRevealedPaint = new Paint();

    private boolean mIsSelected;
    private boolean mIsCircled;
    private boolean mIsHighlighted;
    private boolean mIsMarkedIncorrect;
    private boolean mIsMarkedCorrect;
    private boolean mIsRevealed;
    private boolean mIsPencil;
    private boolean mIsReferenced;

    private final Path reusablePath = new Path();

    public CellView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        mNumberPaint.setStyle(Paint.Style.FILL);
        mCirclePaint.setStyle(Paint.Style.STROKE);

        mIncorrectPaint.setColor(getResources().getColor(R.color.colorIncorrectSlash, null));
        mIncorrectPaint.setStyle(Paint.Style.FILL_AND_STROKE);

        mCorrectPaint.setColor(getResources().getColor(R.color.colorCorrectTag, null));
        mCorrectPaint.setStyle(Paint.Style.FILL_AND_STROKE);

        mRevealedPaint.setColor(getResources().getColor(R.color.colorRevealedTag, null));
        mRevealedPaint.setStyle(Paint.Style.FILL_AND_STROKE);
    }

    public void setCellNumber(int cellNumber) {
        mCellNumber = cellNumber;
    }

    @BindingAdapter("isSelected")
    public static void setSelected(CellView cellView, boolean isSelected) {
        cellView.setSelected(isSelected);
    }

    @BindingAdapter("isCircled")
    public static void setCircled(CellView cellView, boolean isCircled) {
        cellView.setCircled(isCircled);
    }

    @BindingAdapter("isHighlighted")
    public static void setHighlighted(CellView cellView, boolean isHighlighted) {
        cellView.setHighlighted(isHighlighted);
    }

    @BindingAdapter("isMarkedIncorrect")
    public static void setMarkedIncorrect(CellView cellView, boolean isMarkedIncorrect) {
        cellView.setMarkedIncorrect(isMarkedIncorrect);
    }

    @BindingAdapter("isMarkedCorrect")
    public static void setMarkedCorrect(CellView cellView, boolean isMarkedCorrect) {
        cellView.setMarkedCorrect(isMarkedCorrect);
    }

    @BindingAdapter("isRevealed")
    public static void setRevealed(CellView cellView, boolean isRevealed) {
        cellView.setRevealed(isRevealed);
    }

    @BindingAdapter("isPencil")
    public static void setPencil(CellView cellView, boolean isPencil) {
        cellView.setPencil(isPencil);
    }

    @BindingAdapter("isReferenced")
    public static void setReferenced(CellView cellView, boolean isReferenced) {
        cellView.setReferenced(isReferenced);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        //noinspection SuspiciousNameCombination
        super.onMeasure(widthMeasureSpec, widthMeasureSpec);
    }

    @Override
    public void setSelected(boolean selected) {
        if (mIsSelected != selected) {
            mIsSelected = selected;
            refreshDrawableState();
        }
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

    public void setMarkedIncorrect(boolean markedIncorrect) {
        if (mIsMarkedIncorrect != markedIncorrect) {
            mIsMarkedIncorrect = markedIncorrect;
            // Need to call invalidate() here (vs refreshDrawableState())
            // to ensure that onDraw() is called later.
            invalidate();
        }
    }

    public void setMarkedCorrect(boolean markedCorrect) {
        if (mIsMarkedCorrect != markedCorrect) {
            mIsMarkedCorrect = markedCorrect;
            invalidate();
        }
    }

    public void setRevealed(boolean isRevealed) {
        if (mIsRevealed != isRevealed) {
            mIsRevealed = isRevealed;
            invalidate();
        }
    }

    public void setPencil(boolean isPencil) {
        if (mIsPencil != isPencil) {
            mIsPencil = isPencil;
            // Adjust opacity to indicate pencil/pen.
            this.setTextColor(getTextColors().withAlpha(isPencil ? 150 : 255));
            refreshDrawableState();
        }
    }

    public void setReferenced(boolean isReferenced) {
        if (mIsReferenced != isReferenced) {
            mIsReferenced = isReferenced;
            refreshDrawableState();
        }
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        int[] extraDrawableStates = new int[8];
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
        if (mIsMarkedIncorrect) {
            extraDrawableStates[count++] = R.attr.isMarkedIncorrect;
        }
        if (mIsMarkedCorrect) {
            extraDrawableStates[count++] = R.attr.isMarkedCorrect;
        }
        if (mIsRevealed) {
            extraDrawableStates[count++] = R.attr.isRevealed;
        }
        if (mIsPencil) {
            extraDrawableStates[count++] = R.attr.isPencil;
        }
        if (mIsReferenced) {
            extraDrawableStates[count++] = R.attr.isReferenced;
        }
        if (count > 0) {
            int[] resized = Arrays.copyOf(extraDrawableStates, count);
            final int[] drawableState = super.onCreateDrawableState(extraSpace + count);
            mergeDrawableStates(drawableState, resized);
            return drawableState;
        }
        return super.onCreateDrawableState(extraSpace);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        // Current text color with alpha removed.
        int colorWithoutAlpha = getCurrentTextColor() | 0xFF000000;

        if (mCellNumber > 0) {
            float padding = width * 0.05f;
            float numberHeight = width * 0.3f;
            mNumberPaint.setColor(colorWithoutAlpha);
            mNumberPaint.setTextSize(numberHeight);
            canvas.drawText(String.valueOf(mCellNumber), padding, numberHeight, mNumberPaint);
        }

        if (mIsCircled) {
            float radius = width / 2f;
            mCirclePaint.setColor(colorWithoutAlpha);
            mCirclePaint.setStrokeWidth(width * 0.02f);
            canvas.drawCircle(radius, radius, radius * .95f, mCirclePaint);
        }

        if (mIsRevealed) {
            reusablePath.reset();
            reusablePath.moveTo(width * 0.7f, 0);
            reusablePath.lineTo(width, 0);
            reusablePath.lineTo(width, height * 0.3f);
            reusablePath.close();
            canvas.drawPath(reusablePath, mRevealedPaint);
        } else if (mIsMarkedCorrect) {
            reusablePath.reset();
            reusablePath.moveTo(width * 0.7f, 0);
            reusablePath.lineTo(width, 0);
            reusablePath.lineTo(width, height * 0.3f);
            reusablePath.close();
            canvas.drawPath(reusablePath, mCorrectPaint);
        } else if (mIsMarkedIncorrect) {
            mIncorrectPaint.setStrokeWidth(width * 0.05f);
            canvas.drawLine(width, 0, 0, height, mIncorrectPaint);
        }
    }
}
