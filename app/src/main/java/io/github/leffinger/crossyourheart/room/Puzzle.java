package io.github.leffinger.crossyourheart.room;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.io.Serializable;

import io.github.leffinger.crossyourheart.R;
import io.github.leffinger.crossyourheart.io.AbstractPuzzleFile.ScrambleState;

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
    @ColumnInfo(defaultValue = "NULL")
    public ScrambleState scrambleState;
    @ColumnInfo(defaultValue = "false")
    public boolean downsOnlyMode;

    @Ignore
    public Puzzle(@NonNull String filename) {
        this.filename = filename;
    }

    public Puzzle(@NonNull String filename, String title, String author, String copyright,
                  boolean solved, boolean usePencil, boolean opened, ScrambleState scrambleState, boolean downsOnlyMode) {
        this.filename = filename;
        this.title = title;
        this.author = author;
        this.copyright = copyright;
        this.solved = solved;
        this.usePencil = usePencil;
        this.opened = opened;
        this.scrambleState = scrambleState;
        this.downsOnlyMode = downsOnlyMode;
    }

    public int getPuzzleStatusResId() {
        if (!opened) {
            return R.drawable.new_puzzle_png;
        }
        if (solved) {
            return R.drawable.solved;
        }
        if (scrambleState == ScrambleState.LOCKED) {
            return R.drawable.locked;
        }
        return R.drawable.in_progress;
    }

    public String getFilename() {
        return filename;
    }
}
