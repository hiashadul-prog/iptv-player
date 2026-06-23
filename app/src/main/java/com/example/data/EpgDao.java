package com.example.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface EpgDao {
    @Query("SELECT * FROM epg_programs WHERE endTime >= :currentTime ORDER BY startTime ASC")
    LiveData<List<EpgProgram>> getAllActiveAndFuturePrograms(long currentTime);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<EpgProgram> programs);

    @Query("DELETE FROM epg_programs")
    void clearAll();
}
