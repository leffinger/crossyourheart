package io.github.leffinger.crossyourheart.viewmodels;

import java.io.Serializable;

public class PuzzleInfoViewModel implements Serializable {
    private final String mTitle;
    private final String mAuthor;
    private final String mCopyright;
    private final String mNote;
    private final int mWordCount;
    private final int mWidth;
    private final int mHeight;
    private final float mAverageWordLength;

    public PuzzleInfoViewModel(String title, String author, String copyright, String note,
                               int wordCount, int width, int height, float averageWordLength) {
        mTitle = title;
        mAuthor = author;
        mCopyright = copyright;
        mNote = note;
        mWordCount = wordCount;
        mWidth = width;
        mHeight = height;
        mAverageWordLength = averageWordLength;
    }

    public String getTitle() {
        return mTitle;
    }

    public String getAuthor() {
        return mAuthor;
    }

    public String getCopyright() {
        return mCopyright;
    }

    public String getNote() {
        return mNote;
    }

    public int getWordCount() {
        return mWordCount;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public float getAverageWordLength() {
        return mAverageWordLength;
    }
}
