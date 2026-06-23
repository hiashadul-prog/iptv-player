package com.example.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface ChannelDao {
    @Query("SELECT * FROM channels ORDER BY name ASC")
    LiveData<List<Channel>> getAllChannels();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertChannel(Channel channel);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Channel> channels);

    @Update
    void updateChannel(Channel channel);

    @Delete
    void deleteChannel(Channel channel);
}
