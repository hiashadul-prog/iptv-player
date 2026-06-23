package com.example.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "epg_programs")
public class EpgProgram {
    @PrimaryKey(autoGenerate = true)
    private int id = 0;
    private String channelId;
    private String title;
    private String description;
    private long startTime;
    private long endTime;

    public EpgProgram(int id, String channelId, String title, String description, long startTime, long endTime) {
        this.id = id;
        this.channelId = channelId;
        this.title = title;
        this.description = description;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getChannelId() { return channelId; }
    public void setChannelId(String channelId) { this.channelId = channelId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }

    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }
}
