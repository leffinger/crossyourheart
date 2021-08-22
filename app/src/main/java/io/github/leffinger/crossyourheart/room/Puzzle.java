package io.github.leffinger.crossyourheart.room;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.io.Serializable;

import io.github.leffinger.crossyourheart.R;
import io.github.leffinger.crossyourheart.io.AbstractPuzzleFile;

@Entity
public class Puzzle implements Serializable {
    @PrimaryKey
    public @NonNull
    String filename;
    @ColumnInfo
    public String title;
    @ColumnInfo
    public String author;
    @ColumnInfo
    public String copyright;
    @ColumnInfo
    public boolean solved;
    @ColumnInfo
    public boolean usePencil;
    @ColumnInfo(defaultValue = "true")
    public boolean opened;

    @Ignore
    public Puzzle(@NonNull String filename) {
        this.filename = filename;
    }

    public Puzzle(@NonNull String filename, String title, String author, String copyright,
                  boolean solved, boolean usePencil, boolean opened) {
        this.filename = filename;
        this.title = title;
        this.author = author;
        this.copyright = copyright;
        this.solved = solved;
        this.usePencil = usePencil;
        this.opened = opened;
    }

    public int getPuzzleStatusResId() {
        return solved ? R.drawable.solved :
                (opened ? R.drawable.in_progress : R.drawable.new_puzzle);
    }

    public String getFilename() {
        return filename;
    }
}
