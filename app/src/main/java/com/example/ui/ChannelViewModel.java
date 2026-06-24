package com.example.ui;

import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.example.data.AppDatabase;
import com.example.data.Channel;
import com.example.data.ChannelRepository;
import com.example.data.EpgProgram;
import com.example.util.M3uParser;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChannelViewModel extends AndroidViewModel {

    private final ChannelRepository repository;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    private final LiveData<List<Channel>> allChannels;
    private final MediatorLiveData<List<Channel>> filteredChannels = new MediatorLiveData<>();

    private final MutableLiveData<Channel> selectedChannel = new MutableLiveData<>(null);
    private final MutableLiveData<String> selectedGroup = new MutableLiveData<>("All");
    private final MutableLiveData<Boolean> showFavoritesOnly = new MutableLiveData<>(false);
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>("");

    private final MutableLiveData<Boolean> isInPip = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isImporting = new MutableLiveData<>(false);
    private final MutableLiveData<String> importStateMessage = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> isEpgImporting = new MutableLiveData<>(false);
    private final MutableLiveData<String> epgImportStateMessage = new MutableLiveData<>(null);

    private final LiveData<List<EpgProgram>> epgPrograms;

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean wasNetworkDropped = false;

    public ChannelViewModel(@NonNull Application application) {
        super(application);

        AppDatabase db = AppDatabase.getDatabase(application);
        repository = new ChannelRepository(db.channelDao(), db.epgDao());

        allChannels = repository.getAllChannels();
        epgPrograms = repository.getAllActiveAndFuturePrograms(System.currentTimeMillis() - 2 * 3600 * 1000);

        // Reactively combine search query, category selected, and favorites list using MediatorLiveData
        filteredChannels.addSource(allChannels, channels -> filterData());
        filteredChannels.addSource(selectedGroup, group -> filterData());
        filteredChannels.addSource(showFavoritesOnly, fav -> filterData());
        filteredChannels.addSource(searchQuery, query -> filterData());

        setupNetworkCallback();
    }

    private void filterData() {
        List<Channel> channels = allChannels.getValue();
        String group = selectedGroup.getValue();
        Boolean favOnly = showFavoritesOnly.getValue();
        String query = searchQuery.getValue();

        if (channels == null) {
            filteredChannels.setValue(new ArrayList<>());
            return;
        }

        List<Channel> filtered = new ArrayList<>();
        for (Channel c : channels) {
            boolean matchesGroup = "All".equalsIgnoreCase(group) || 
                    (c.getCategory() != null && c.getCategory().equalsIgnoreCase(group));
            boolean matchesFav = (favOnly == null || !favOnly) || c.isFavorite();
            boolean matchesSearch = query == null || query.trim().isEmpty() ||
                    (c.getName() != null && c.getName().toLowerCase().contains(query.toLowerCase())) ||
                    (c.getCategory() != null && c.getCategory().toLowerCase().contains(query.toLowerCase())) ||
                    (c.getGroupName() != null && c.getGroupName().toLowerCase().contains(query.toLowerCase()));

            if (matchesGroup && matchesFav && matchesSearch) {
                filtered.add(c);
            }
        }
        filteredChannels.setValue(filtered);
    }

    private void setupNetworkCallback() {
        try {
            Context context = getApplication();
            connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager != null) {
                Network activeNet = connectivityManager.getActiveNetwork();
                NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(activeNet);
                wasNetworkDropped = caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);

                networkCallback = new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(@NonNull Network network) {
                        super.onAvailable(network);
                        if (wasNetworkDropped) {
                            wasNetworkDropped = false;
                            // Optionally trigger retry/reload inside UI
                        }
                    }

                    @Override
                    public void onLost(@NonNull Network network) {
                        super.onLost(network);
                        wasNetworkDropped = true;
                    }
                };

                NetworkRequest request = new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build();
                connectivityManager.registerNetworkCallback(request, networkCallback);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public LiveData<List<Channel>> getAllChannels() { return allChannels; }
    public LiveData<List<Channel>> getFilteredChannels() { return filteredChannels; }
    public LiveData<Channel> getSelectedChannel() { return selectedChannel; }
    public LiveData<String> getSelectedGroup() { return selectedGroup; }
    public LiveData<Boolean> getShowFavoritesOnly() { return showFavoritesOnly; }
    public LiveData<String> getSearchQuery() { return searchQuery; }
    public LiveData<Boolean> getIsInPip() { return isInPip; }
    public LiveData<Boolean> getIsImporting() { return isImporting; }
    public LiveData<String> getImportStateMessage() { return importStateMessage; }
    public LiveData<Boolean> getIsEpgImporting() { return isEpgImporting; }
    public LiveData<String> getEpgImportStateMessage() { return epgImportStateMessage; }
    public LiveData<List<EpgProgram>> getEpgPrograms() { return epgPrograms; }

    public void selectChannel(Channel channel) { selectedChannel.setValue(channel); }
    public void selectGroup(String group) { selectedGroup.setValue(group); }
    public void setShowFavoritesOnly(boolean show) { showFavoritesOnly.setValue(show); }
    public void setSearchQuery(String query) { searchQuery.setValue(query); }
    public void setInPip(boolean inPip) { isInPip.setValue(inPip); }
    public void clearImportMessage() { importStateMessage.postValue(null); }
    public void clearEpgImportMessage() { epgImportStateMessage.postValue(null); }

    public void toggleFavorite(Channel channel) {
        executor.execute(() -> {
            Channel updated = new Channel(
                    channel.getId(),
                    channel.getName(),
                    channel.getUrl(),
                    channel.getLogoUrl(),
                    channel.getGroupName(),
                    !channel.isFavorite(),
                    channel.getTvgId(),
                    channel.getCategory()
            );
            repository.update(updated);
            Channel current = selectedChannel.getValue();
            if (current != null && current.getId() == channel.getId()) {
                selectedChannel.postValue(updated);
            }
        });
    }

    public void addChannel(String name, String url, String logoUrl, String groupName, String category) {
        executor.execute(() -> {
            String formattedLogo = (logoUrl == null || logoUrl.trim().isEmpty()) ?
                    "https://images.unsplash.com/photo-1542751371-adc38448a05e?w=200" : logoUrl.trim();
            String formattedGroup = (groupName == null || groupName.trim().isEmpty()) ? "Default" : groupName.trim();
            String formattedCategory = (category == null || category.trim().isEmpty()) ? formattedGroup : category.trim();
            repository.insert(new Channel(0, name, url, formattedLogo, formattedGroup, false, "", formattedCategory));
        });
    }

    public void updateChannel(int id, String name, String url, String logoUrl, String groupName, String category, boolean isFavorite) {
        executor.execute(() -> {
            String formattedLogo = (logoUrl == null || logoUrl.trim().isEmpty()) ?
                    "https://images.unsplash.com/photo-1542751371-adc38448a05e?w=200" : logoUrl.trim();
            String formattedGroup = (groupName == null || groupName.trim().isEmpty()) ? "Default" : groupName.trim();
            String formattedCategory = (category == null || category.trim().isEmpty()) ? formattedGroup : category.trim();
            Channel updated = new Channel(id, name, url, formattedLogo, formattedGroup, isFavorite, "", formattedCategory);
            repository.update(updated);
            Channel current = selectedChannel.getValue();
            if (current != null && current.getId() == id) {
                selectedChannel.postValue(updated);
            }
        });
    }

    public void deleteChannel(Channel channel) {
        executor.execute(() -> {
            repository.delete(channel);
            Channel current = selectedChannel.getValue();
            if (current != null && current.getId() == channel.getId()) {
                selectedChannel.postValue(null);
            }
        });
    }

    public void importM3uFromStream(InputStream inputStream) {
        executor.execute(() -> {
            isImporting.postValue(true);
            importStateMessage.postValue("Importing channels, please wait...");
            try {
                List<Channel> channels = M3uParser.parse(inputStream);
                if (!channels.isEmpty()) {
                    repository.insertAll(channels);
                    importStateMessage.postValue("Imported " + channels.size() + " channels successfully!");
                } else {
                    importStateMessage.postValue("No valid channels found in the M3U file.");
                }
            } catch (Exception e) {
                importStateMessage.postValue("Import failed: " + e.getLocalizedMessage());
            } finally {
                isImporting.postValue(false);
            }
        });
    }

    public void importM3uFromUrl(String m3uUrl) {
        executor.execute(() -> {
            isImporting.postValue(true);
            importStateMessage.postValue("Downloading and parsing playlist...");
            try {
                URLConnection connection = new URL(m3uUrl).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                try (InputStream inputStream = connection.getInputStream()) {
                    List<Channel> channels = M3uParser.parse(inputStream);
                    if (!channels.isEmpty()) {
                        repository.insertAll(channels);
                        importStateMessage.postValue("Imported " + channels.size() + " channels from URL!");
                    } else {
                        importStateMessage.postValue("No valid channels found in the URL playlist.");
                    }
                }
            } catch (Exception e) {
                importStateMessage.postValue("Failed to import from URL: " + e.getLocalizedMessage());
            } finally {
                isImporting.postValue(false);
            }
        });
    }

    public void importEpgFromStream(InputStream inputStream) {
        executor.execute(() -> {
            isEpgImporting.postValue(true);
            epgImportStateMessage.postValue("Parsing and loading EPG guide...");
            try {
                List<EpgProgram> programs = com.example.util.XmlTvParser.parse(inputStream);
                if (!programs.isEmpty()) {
                    repository.clearAllPrograms();
                    repository.insertPrograms(programs);
                    epgImportStateMessage.postValue("Imported " + programs.size() + " EPG listings successfully!");
                } else {
                    epgImportStateMessage.postValue("No valid EPG programs found in XMLTV.");
                }
            } catch (Exception e) {
                epgImportStateMessage.postValue("EPG import failed: " + e.getLocalizedMessage());
            } finally {
                isEpgImporting.postValue(false);
            }
        });
    }

    public void importEpgFromUrl(String epgUrl) {
        executor.execute(() -> {
            isEpgImporting.postValue(true);
            epgImportStateMessage.postValue("Downloading and parsing EPG guide...");
            try {
                URLConnection connection = new URL(epgUrl).openConnection();
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(30000);
                try (InputStream inputStream = connection.getInputStream()) {
                    List<EpgProgram> programs = com.example.util.XmlTvParser.parse(inputStream);
                    if (!programs.isEmpty()) {
                        repository.clearAllPrograms();
                        repository.insertPrograms(programs);
                        epgImportStateMessage.postValue("Imported " + programs.size() + " EPG listings successfully!");
                    } else {
                        epgImportStateMessage.postValue("No valid EPG programs found in XMLTV remote URL.");
                    }
                }
            } catch (Exception e) {
                epgImportStateMessage.postValue("EPG download failed: " + e.getLocalizedMessage());
            } finally {
                isEpgImporting.postValue(false);
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        try {
            if (networkCallback != null && connectivityManager != null) {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        executor.shutdown();
    }

    public static class Factory implements ViewModelProvider.Factory {
        private final Application application;

        public Factory(Application application) {
            this.application = application;
        }

        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            if (modelClass.isAssignableFrom(ChannelViewModel.class)) {
                return (T) new ChannelViewModel(application);
            }
            throw new IllegalArgumentException("Unknown ViewModel class");
        }
    }
}
