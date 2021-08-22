package io.github.leffinger.crossyourheart.room;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface PuzzleDao {
    @Query("SELECT * FROM puzzle ORDER BY filename DESC")
    List<Puzzle> getAll();

    @Query("SELECT filename FROM puzzle")
    List<String> getFiles();

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(Puzzle puzzle);

    @Update
    void update(Puzzle puzzle);

    @Delete
    void deletePuzzles(List<Puzzle> puzzles);

    @Delete
    void deletePuzzle(Puzzle puzzle);
}
