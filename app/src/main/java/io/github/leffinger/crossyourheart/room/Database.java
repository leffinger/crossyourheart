package io.github.leffinger.crossyourheart.room;

import androidx.room.AutoMigration;
import androidx.room.RoomDatabase;

@androidx.room.Database(entities = {Puzzle.class, Cell.class}, version = 2,
                        autoMigrations = {
                                @AutoMigration (from = 1, to = 2),
                        })
public abstract class Database extends RoomDatabase {
    public abstract PuzzleDao puzzleDao();
    public abstract CellDao cellDao();
}
