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

    @Query("SELECT * FROM puzzle ORDER BY filename DESC LIMIT :n")
    List<Puzzle> getFirstN(int n);

    @Query("SELECT filename FROM puzzle")
    List<String> getFiles();

    @Query("SELECT * FROM puzzle p JOIN puzfilemetadata m ON p.filename = m.filename WHERE title " +
                   "= :title AND author = :author AND headerChecksum = :headerChecksum")
    List<Puzzle> getMatchingPuzFiles(String title, String author, int headerChecksum);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(Puzzle puzzle);

    @Update(entity = Puzzle.class)
    void updateSolved(SolvedUpdate solvedUpdate);

    @Update(entity = Puzzle.class)
    void updateOpened(OpenedUpdate openedUpdate);

    @Update(entity = Puzzle.class)
    void updateUsePencil(UsePencilUpdate usePencilUpdate);

    @Update(entity = Puzzle.class)
    void updateDownsOnlyMode(DownsOnlyModeUpdate downsOnlyModeUpdate);

    @Delete
    void deletePuzzles(List<Puzzle> puzzles);

    @Delete
    void deletePuzzle(Puzzle puzzle);

    class SolvedUpdate {
        String filename;
        boolean solved;

        public SolvedUpdate(String filename, boolean solved) {
            this.filename = filename;
            this.solved = solved;
        }
    }

    class OpenedUpdate {
        String filename;
        boolean opened;

        public OpenedUpdate(String filename, boolean opened) {
            this.filename = filename;
            this.opened = opened;
        }
    }

    class UsePencilUpdate {
        String filename;
        boolean usePencil;

        public UsePencilUpdate(String filename, boolean usePencil) {
            this.filename = filename;
            this.usePencil = usePencil;
        }
    }

    class DownsOnlyModeUpdate {
        String filename;
        boolean downsOnlyMode;

        public DownsOnlyModeUpdate(String filename, boolean downsOnlyMode) {
            this.filename = filename;
            this.downsOnlyMode = downsOnlyMode;
        }
    }
}
