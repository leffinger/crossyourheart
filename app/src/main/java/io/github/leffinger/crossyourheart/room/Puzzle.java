package io.github.leffinger.crossyourheart.room;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
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

    public Puzzle(@NonNull String filename) {
        this.filename = filename;
    }

    public static Puzzle fromPuzzleFile(String filename, AbstractPuzzleFile file,
                                        boolean usePencil) {
        Puzzle puzzle = new Puzzle(filename);
        puzzle.title = file.getTitle();
        puzzle.author = file.getAuthor();
        puzzle.copyright = file.getCopyright();
        puzzle.solved = file.isSolved();
        puzzle.usePencil = usePencil;
        return puzzle;
    }

    public int getSolvedStateResId() {
        return solved ? R.drawable.solved : R.drawable.in_progress;
    }

    public String getFilename() {
        return filename;
    }
}
