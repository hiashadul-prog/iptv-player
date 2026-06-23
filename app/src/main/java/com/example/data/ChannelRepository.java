package com.example.data;

import androidx.lifecycle.LiveData;
import java.util.List;

public class ChannelRepository {
    private final ChannelDao channelDao;
    private final EpgDao epgDao;

    public ChannelRepository(ChannelDao channelDao, EpgDao epgDao) {
        this.channelDao = channelDao;
        this.epgDao = epgDao;
    }

    public LiveData<List<Channel>> getAllChannels() {
        return channelDao.getAllChannels();
    }

    public void insert(Channel channel) {
        channelDao.insertChannel(channel);
    }

    public void insertAll(List<Channel> channels) {
        channelDao.insertAll(channels);
    }

    public void update(Channel channel) {
        channelDao.updateChannel(channel);
    }

    public void delete(Channel channel) {
        channelDao.deleteChannel(channel);
    }

    public LiveData<List<EpgProgram>> getAllActiveAndFuturePrograms(long currentTime) {
        return epgDao.getAllActiveAndFuturePrograms(currentTime);
    }

    public void insertPrograms(List<EpgProgram> programs) {
        epgDao.insertAll(programs);
    }

    public void clearAllPrograms() {
        epgDao.clearAll();
    }
}
