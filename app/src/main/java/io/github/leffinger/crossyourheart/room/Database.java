package io.github.leffinger.crossyourheart.room;

import androidx.room.RoomDatabase;

@androidx.room.Database(entities = {Puzzle.class, Cell.class}, version = 1)
public abstract class Database extends RoomDatabase {
    public abstract PuzzleDao puzzleDao();
    public abstract CellDao cellDao();
}
