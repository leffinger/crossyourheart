package io.github.leffinger.crossyourheart.room;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;

/**
 * This entity is used to persist cell state that cannot be stored in the file itself. In the
 * future, this may become the primary storage mechanism for cell state, with the file itself
 * serving as a backup.
 */
@Entity(primaryKeys = {"filename", "row", "col"}, foreignKeys = {
        @ForeignKey(entity = Puzzle.class, parentColumns = {"filename"},
                    childColumns = {"filename"}, onDelete = ForeignKey.CASCADE)})
public class Cell {
    @ColumnInfo
    public @NonNull
    String filename;

    @ColumnInfo
    public int row;

    @ColumnInfo
    public int col;

    @ColumnInfo
    public boolean pencil;

    public Cell(@NonNull String filename, int row, int col, boolean pencil) {
        this.filename = filename;
        this.row = row;
        this.col = col;
        this.pencil = pencil;
    }
}
