package io.github.leffinger.crossyourheart.room;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;

/**
 * Metadata specific to PUZ format.
 */
@Entity(primaryKeys = {"filename"}, foreignKeys = {
        @ForeignKey(entity = Puzzle.class, parentColumns = {"filename"},
                    childColumns = {"filename"}, onDelete = ForeignKey.CASCADE)})
public class PuzFileMetadata {
    @ColumnInfo
    public @NonNull
    String filename;

    @ColumnInfo
    public int headerChecksum;

    public PuzFileMetadata(@NonNull String filename, int headerChecksum) {
        this.filename = filename;
        this.headerChecksum = headerChecksum;
    }
}
