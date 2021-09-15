package io.github.leffinger.crossyourheart.room;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;

@Dao
public interface PuzFileMetadataDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(PuzFileMetadata puzFileMetadata);
}
