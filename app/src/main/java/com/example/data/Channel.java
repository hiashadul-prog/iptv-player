package com.example.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "channels")
public class Channel {
    @PrimaryKey(autoGenerate = true)
    private int id = 0;
    private String name;
    private String url;
    private String logoUrl;
    private String groupName;
    private boolean isFavorite;
    private String tvgId;
    private String category;

    public Channel(int id, String name, String url, String logoUrl, String groupName, boolean isFavorite, String tvgId, String category) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.logoUrl = logoUrl;
        this.groupName = groupName != null ? groupName : "Default";
        this.isFavorite = isFavorite;
        this.tvgId = tvgId != null ? tvgId : "";
        this.category = category != null ? category : this.groupName;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public boolean isFavorite() { return isFavorite; }
    public void setFavorite(boolean favorite) { isFavorite = favorite; }

    public String getTvgId() { return tvgId; }
    public void setTvgId(String tvgId) { this.tvgId = tvgId; }

    public String getCategory() { return category != null ? category : groupName; }
    public void setCategory(String category) { this.category = category; }
}
