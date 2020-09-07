package io.github.leffinger.crossyourheart.viewmodels;

import java.io.Serializable;

public class PuzzleInfoViewModel implements Serializable {
    private final String mTitle;
    private final String mAuthor;
    private final String mCopyright;
    private final String mNote;

    public PuzzleInfoViewModel(String title, String author, String copyright, String note) {
        mTitle = title;
        mAuthor = author;
        mCopyright = copyright;
        mNote = note;
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
}
