package io.github.leffinger.crossyourheart.room;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface CellDao {

    @Query("SELECT * FROM cell WHERE filename = :filename")
    List<Cell> getCellsForPuzzle(String filename);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Cell puzzle);
}
