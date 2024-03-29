package io.github.leffinger.crossyourheart.room;

import android.content.Context;

import androidx.room.AutoMigration;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@androidx.room.Database(entities = {Puzzle.class, Cell.class, PuzFileMetadata.class}, version = 2,
                        autoMigrations = {@AutoMigration(from = 1, to = 2)})
public abstract class Database extends RoomDatabase {
    public final static String DB_NAME = "puzzles2";

    public static Database getInstance(Context applicationContext) {
        return Room.databaseBuilder(applicationContext, Database.class, DB_NAME).build();
    }

    public abstract PuzzleDao puzzleDao();

    public abstract CellDao cellDao();

    public abstract PuzFileMetadataDao puzFileMetadataDao();
}
