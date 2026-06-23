package com.example;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Rational;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import com.example.data.Channel;
import com.example.ui.CategoryAdapter;
import com.example.ui.ChannelAdapter;
import com.example.ui.ChannelViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements ChannelAdapter.ChannelListener, CategoryAdapter.CategoryListener {

    private ChannelViewModel viewModel;
    private ExoPlayer player;
    private PlayerView playerView;
    private View playerOverlay;
    private ImageView btnPip;

    private RecyclerView rvChannels;
    private RecyclerView rvCategories;
    private ChannelAdapter channelAdapter;
    private CategoryAdapter categoryAdapter;

    private TextInputEditText searchInput;
    private View emptyStateView;
    private ProgressBar loadingIndicator;

    private View appBarLayout;
    private View searchLayout;
    private View channelsContainer;
    private FloatingActionButton fabAddChannel;
    private View playerContainer;
    private int originalPlayerHeight = -1;

    private int retryCount = 0;
    private static final int MAX_RETRY_COUNT = 5;
    private static final long RETRY_DELAY_MS = 2000;
    private Handler retryHandler;
    private Runnable retryRunnable;

    private final ActivityResultLauncher<String> m3uFilePicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    try {
                        InputStream stream = getContentResolver().openInputStream(uri);
                        if (stream != null) {
                            viewModel.importM3uFromStream(stream);
                        }
                    } catch (Exception e) {
                        Toast.makeText(this, "Failed to read M3U file", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    private final ActivityResultLauncher<String> epgFilePicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    try {
                        InputStream stream = getContentResolver().openInputStream(uri);
                        if (stream != null) {
                            viewModel.importEpgFromStream(stream);
                        }
                    } catch (Exception e) {
                        Toast.makeText(this, "Failed to read EPG file", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean("dark_mode", true); // Default to dark mode matching design
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }

        super.onCreate(savedInstanceState);
        setContentView(com.example.R.layout.activity_main);

        // Apply Edge-to-Edge window inset bindings
        View mainView = findViewById(com.example.R.id.player_container);
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(0, systemBars.top, 0, 0);
                return insets;
            });
        }

        viewModel = new ViewModelProvider(this, new ChannelViewModel.Factory(getApplication())).get(ChannelViewModel.class);

        initializeViews();
        setupPlayer();
        setupRecyclerViews();
        observeViewModel();
    }

    private void initializeViews() {
        playerView = findViewById(com.example.R.id.player_view);
        playerOverlay = findViewById(com.example.R.id.player_overlay);
        btnPip = findViewById(com.example.R.id.btn_pip);

        rvChannels = findViewById(com.example.R.id.rv_channels);
        rvCategories = findViewById(com.example.R.id.rv_categories);
        searchInput = findViewById(com.example.R.id.search_input);
        emptyStateView = findViewById(com.example.R.id.empty_state_view);
        loadingIndicator = findViewById(com.example.R.id.loading_indicator);

        appBarLayout = findViewById(com.example.R.id.app_bar_layout);
        searchLayout = findViewById(com.example.R.id.search_layout);
        channelsContainer = findViewById(com.example.R.id.channels_container);
        playerContainer = findViewById(com.example.R.id.player_container);
        if (playerContainer != null && playerContainer.getLayoutParams() != null) {
            originalPlayerHeight = playerContainer.getLayoutParams().height;
        }

        ImageView btnImportEpg = findViewById(com.example.R.id.btn_import_epg);
        ImageView btnImportM3u = findViewById(com.example.R.id.btn_import_m3u);
        ImageView btnTheme = findViewById(com.example.R.id.btn_theme);
        fabAddChannel = findViewById(com.example.R.id.fab_add_channel);

        boolean currentDarkMode = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) 
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        btnTheme.setImageResource(currentDarkMode ? android.R.drawable.ic_menu_today : android.R.drawable.ic_menu_compass);

        btnTheme.setOnClickListener(v -> {
            SharedPreferences currentPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
            boolean isDark = currentPrefs.getBoolean("dark_mode", true);
            boolean nextDark = !isDark;
            currentPrefs.edit().putBoolean("dark_mode", nextDark).apply();

            if (nextDark) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        });

        btnImportEpg.setOnClickListener(v -> showEpgImportDialog());
        btnImportM3u.setOnClickListener(v -> showM3uImportDialog());
        fabAddChannel.setOnClickListener(v -> showAddChannelDialog());

        btnPip.setOnClickListener(v -> enterPipMode());

        // Simple text watcher for searching channels
        searchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.setSearchQuery(s.toString());
            }
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
    }

    private void setupPlayer() {
        retryHandler = new Handler(Looper.getMainLooper());
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                boolean isNetworkError = isRecoverableNetworkError(error);
                if (isNetworkError && retryCount < MAX_RETRY_COUNT) {
                    retryCount++;
                    Toast.makeText(MainActivity.this, 
                        "Connection lost. Retrying (" + retryCount + "/" + MAX_RETRY_COUNT + ")...", 
                        Toast.LENGTH_SHORT).show();
                    
                    if (retryRunnable != null) {
                        retryHandler.removeCallbacks(retryRunnable);
                    }
                    retryRunnable = () -> {
                        Channel currentChannel = viewModel.getSelectedChannel().getValue();
                        if (currentChannel != null) {
                            long currentPosition = player.getCurrentPosition();
                            player.setMediaItem(MediaItem.fromUri(Uri.parse(currentChannel.getUrl())));
                            player.prepare();
                            player.seekTo(currentPosition);
                            player.play();
                        }
                    };
                    retryHandler.postDelayed(retryRunnable, RETRY_DELAY_MS);
                } else {
                    Toast.makeText(MainActivity.this, "Stream Error: " + error.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                    retryCount = 0;
                }
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    retryCount = 0;
                }
            }
        });
    }

    private boolean isRecoverableNetworkError(PlaybackException error) {
        int errorCode = error.errorCode;
        return (errorCode >= 2000 && errorCode < 3000) || 
               error.getCause() instanceof java.io.IOException;
    }

    private void setupRecyclerViews() {
        channelAdapter = new ChannelAdapter(this);
        rvChannels.setLayoutManager(new LinearLayoutManager(this));
        rvChannels.setAdapter(channelAdapter);

        categoryAdapter = new CategoryAdapter(this);
        rvCategories.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvCategories.setAdapter(categoryAdapter);
    }

    private void observeViewModel() {
        viewModel.getFilteredChannels().observe(this, channels -> {
            channelAdapter.submitList(channels);
            if (channels == null || channels.isEmpty()) {
                emptyStateView.setVisibility(View.VISIBLE);
            } else {
                emptyStateView.setVisibility(View.GONE);
            }

            // Extract distinct groups for Categories dynamically
            Set<String> uniqueGroups = new HashSet<>();
            if (channels != null) {
                for (Channel c : channels) {
                    if (c.getCategory() != null && !c.getCategory().trim().isEmpty()) {
                        uniqueGroups.add(c.getCategory());
                    }
                }
            }
            categoryAdapter.setCategories(new ArrayList<>(uniqueGroups));
        });

        viewModel.getSelectedChannel().observe(this, channel -> {
            channelAdapter.setSelectedChannel(channel);
            if (channel != null) {
                retryCount = 0;
                if (retryRunnable != null && retryHandler != null) {
                    retryHandler.removeCallbacks(retryRunnable);
                }
                playerOverlay.setVisibility(View.VISIBLE);
                playUri(channel.getUrl());
                updatePipParams(true);
            } else {
                playerOverlay.setVisibility(View.GONE);
                player.stop();
                updatePipParams(false);
            }
        });

        viewModel.getEpgPrograms().observe(this, programs -> {
            channelAdapter.setEpgPrograms(programs);
        });

        viewModel.getSelectedGroup().observe(this, group -> {
            categoryAdapter.setSelectedCategory(group);
        });

        viewModel.getIsImporting().observe(this, importing -> {
            loadingIndicator.setVisibility(importing ? View.VISIBLE : View.GONE);
        });

        viewModel.getIsEpgImporting().observe(this, importing -> {
            loadingIndicator.setVisibility(importing ? View.VISIBLE : View.GONE);
        });

        viewModel.getImportStateMessage().observe(this, msg -> {
            if (msg != null) {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                viewModel.clearImportMessage();
            }
        });

        viewModel.getEpgImportStateMessage().observe(this, msg -> {
            if (msg != null) {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                viewModel.clearEpgImportMessage();
            }
        });

        viewModel.getIsInPip().observe(this, inPip -> {
            if (inPip) {
                playerView.setUseController(false);
                if (appBarLayout != null) appBarLayout.setVisibility(View.GONE);
                if (searchLayout != null) searchLayout.setVisibility(View.GONE);
                if (rvCategories != null) rvCategories.setVisibility(View.GONE);
                if (channelsContainer != null) channelsContainer.setVisibility(View.GONE);
                if (fabAddChannel != null) fabAddChannel.setVisibility(View.GONE);
                if (playerOverlay != null) playerOverlay.setVisibility(View.GONE);

                if (playerContainer != null) {
                    android.view.ViewGroup.LayoutParams params = playerContainer.getLayoutParams();
                    if (params != null) {
                        params.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
                        playerContainer.setLayoutParams(params);
                    }
                }
            } else {
                playerView.setUseController(true);
                if (appBarLayout != null) appBarLayout.setVisibility(View.VISIBLE);
                if (searchLayout != null) searchLayout.setVisibility(View.VISIBLE);
                if (rvCategories != null) rvCategories.setVisibility(View.VISIBLE);
                if (channelsContainer != null) channelsContainer.setVisibility(View.VISIBLE);
                if (fabAddChannel != null) fabAddChannel.setVisibility(View.VISIBLE);
                if (playerOverlay != null && viewModel.getSelectedChannel().getValue() != null) {
                    playerOverlay.setVisibility(View.VISIBLE);
                }

                if (playerContainer != null && originalPlayerHeight != -1) {
                    android.view.ViewGroup.LayoutParams params = playerContainer.getLayoutParams();
                    if (params != null) {
                        params.height = originalPlayerHeight;
                        playerContainer.setLayoutParams(params);
                    }
                }
            }
        });
    }

    private void playUri(String streamUrl) {
        if (streamUrl != null && !streamUrl.trim().isEmpty()) {
            MediaItem mediaItem = MediaItem.fromUri(Uri.parse(streamUrl));
            player.setMediaItem(mediaItem);
            player.prepare();
            player.play();
        }
    }

    private void updatePipParams(boolean hasActiveChannel) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(16, 9));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(hasActiveChannel);
            }
            try {
                setPictureInPictureParams(builder.build());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(16, 9));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(true);
            }
            try {
                enterPictureInPictureMode(builder.build());
            } catch (Exception e) {
                enterPictureInPictureMode();
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            enterPictureInPictureMode();
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (viewModel.getSelectedChannel().getValue() != null) {
            enterPipMode();
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, @NonNull Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        viewModel.setInPip(isInPictureInPictureMode);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (!isInPictureInPictureMode()) {
                if (player != null) {
                    player.pause();
                }
            }
        } else {
            if (player != null) {
                player.pause();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (retryHandler != null && retryRunnable != null) {
            retryHandler.removeCallbacks(retryRunnable);
        }
        if (player != null) {
            player.release();
        }
    }

    // Callbacks from Channel Listener
    @Override
    public void onChannelClick(Channel channel) {
        viewModel.selectChannel(channel);
    }

    @Override
    public void onChannelLongClick(Channel channel, View itemView) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(channel.getName())
                .setItems(new CharSequence[]{"Toggle Favorite", "Edit Custom Stream", "Delete Channel"}, (dialog, which) -> {
                    if (which == 0) {
                        viewModel.toggleFavorite(channel);
                    } else if (which == 1) {
                        showEditChannelDialog(channel);
                    } else if (which == 2) {
                        viewModel.deleteChannel(channel);
                    }
                })
                .show();
    }

    @Override
    public void onToggleFavorite(Channel channel) {
        viewModel.toggleFavorite(channel);
    }

    // Callbacks from Category Listener
    @Override
    public void onCategoryClick(String category) {
        viewModel.selectGroup(category);
    }

    // Action Form Popups
    private void showM3uImportDialog() {
        Context context = this;
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        TextView labelFile = new TextView(context);
        labelFile.setText("Option 1: Import Local Playlist File");
        labelFile.setTextColor(androidx.core.content.ContextCompat.getColor(context, com.example.R.color.accent));
        labelFile.setTextSize(12);
        labelFile.setPadding(0, 0, 0, 12);
        layout.addView(labelFile);

        Button btnFile = new Button(context);
        btnFile.setText("Select M3U Playlist File");
        btnFile.setBackgroundColor(androidx.core.content.ContextCompat.getColor(context, com.example.R.color.search_background));
        btnFile.setTextColor(androidx.core.content.ContextCompat.getColor(context, com.example.R.color.text_primary));
        layout.addView(btnFile);

        View separator = new View(context);
        separator.setBackgroundColor(androidx.core.content.ContextCompat.getColor(context, com.example.R.color.card_border));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2);
        params.setMargins(0, 24, 0, 24);
        separator.setLayoutParams(params);
        layout.addView(separator);

        TextView labelUrl = new TextView(context);
        labelUrl.setText("Option 2: Import Remote URL");
        labelUrl.setTextColor(androidx.core.content.ContextCompat.getColor(context, com.example.R.color.accent));
        labelUrl.setTextSize(12);
        labelUrl.setPadding(0, 0, 0, 12);
        layout.addView(labelUrl);

        TextInputLayout tilUrl = new TextInputLayout(context);
        tilUrl.setHint("M3U Request Link URL");
        TextInputEditText etUrl = new TextInputEditText(context);
        tilUrl.addView(etUrl);
        layout.addView(tilUrl);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Import Playlist (M3U)")
                .setView(layout)
                .setPositiveButton("Download Remote URL", (d, which) -> {
                    String url = etUrl.getText().toString().trim();
                    if (!url.isEmpty() && (url.startsWith("http://") || url.startsWith("https://"))) {
                        viewModel.importM3uFromUrl(url);
                    } else {
                        Toast.makeText(this, "Please enter a valid HTTP M3U link URL", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Close", null)
                .create();

        btnFile.setOnClickListener(v -> {
            m3uFilePicker.launch("*/*");
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showEpgImportDialog() {
        Context context = this;
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        TextView labelFile = new TextView(context);
        labelFile.setText("Option 1: Import Local Guide File");
        labelFile.setTextColor(androidx.core.content.ContextCompat.getColor(context, com.example.R.color.accent));
        labelFile.setTextSize(12);
        labelFile.setPadding(0, 0, 0, 12);
        layout.addView(labelFile);

        Button btnFile = new Button(context);
        btnFile.setText("Select XML or XML.GZ File");
        btnFile.setBackgroundColor(androidx.core.content.ContextCompat.getColor(context, com.example.R.color.search_background));
        btnFile.setTextColor(androidx.core.content.ContextCompat.getColor(context, com.example.R.color.text_primary));
        layout.addView(btnFile);

        View separator = new View(context);
        separator.setBackgroundColor(androidx.core.content.ContextCompat.getColor(context, com.example.R.color.card_border));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2);
        params.setMargins(0, 24, 0, 24);
        separator.setLayoutParams(params);
        layout.addView(separator);

        TextView labelUrl = new TextView(context);
        labelUrl.setText("Option 2: Import Remote URL");
        labelUrl.setTextColor(androidx.core.content.ContextCompat.getColor(context, com.example.R.color.accent));
        labelUrl.setTextSize(12);
        labelUrl.setPadding(0, 0, 0, 12);
        layout.addView(labelUrl);

        TextInputLayout tilUrl = new TextInputLayout(context);
        tilUrl.setHint("XMLTV guide URL (.xml / .xml.gz)");
        TextInputEditText etUrl = new TextInputEditText(context);
        tilUrl.addView(etUrl);
        layout.addView(tilUrl);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Import TV Guide (XMLTV)")
                .setView(layout)
                .setPositiveButton("Download Remote URL", (d, which) -> {
                    String url = etUrl.getText().toString().trim();
                    if (!url.isEmpty() && (url.startsWith("http://") || url.startsWith("https://"))) {
                        viewModel.importEpgFromUrl(url);
                    } else {
                        Toast.makeText(this, "Please enter a valid HTTP XMLTV link URL", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Close", null)
                .create();

        btnFile.setOnClickListener(v -> {
            epgFilePicker.launch("*/*");
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showAddChannelDialog() {
        Context context = this;
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        TextInputLayout tilName = new TextInputLayout(context);
        tilName.setHint("Stream Title");
        TextInputEditText etName = new TextInputEditText(context);
        tilName.addView(etName);
        layout.addView(tilName);

        TextInputLayout tilUrl = new TextInputLayout(context);
        tilUrl.setHint("Stream Stream URL (.m3u8, etc.)");
        TextInputEditText etUrl = new TextInputEditText(context);
        tilUrl.addView(etUrl);
        layout.addView(tilUrl);

        TextInputLayout tilLogo = new TextInputLayout(context);
        tilLogo.setHint("Stream Logo Cover URL");
        TextInputEditText etLogo = new TextInputEditText(context);
        tilLogo.addView(etLogo);
        layout.addView(tilLogo);

        TextInputLayout tilGroup = new TextInputLayout(context);
        tilGroup.setHint("Category Group");
        TextInputEditText etGroup = new TextInputEditText(context);
        tilGroup.addView(etGroup);
        layout.addView(tilGroup);

        TextInputLayout tilCategory = new TextInputLayout(context);
        tilCategory.setHint("Filter Category Tab");
        TextInputEditText etCategory = new TextInputEditText(context);
        tilCategory.addView(etCategory);
        layout.addView(tilCategory);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Add Single Custom Stream")
                .setView(layout)
                .setPositiveButton("Add Channel", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String url = etUrl.getText().toString().trim();
                    String logo = etLogo.getText().toString().trim();
                    String group = etGroup.getText().toString().trim();
                    String category = etCategory.getText().toString().trim();

                    if (!name.isEmpty() && !url.isEmpty()) {
                        viewModel.addChannel(name, url, logo, group, category);
                    } else {
                        Toast.makeText(this, "Title and URL are required", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditChannelDialog(Channel channel) {
        Context context = this;
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        TextInputLayout tilName = new TextInputLayout(context);
        tilName.setHint("Stream Title");
        TextInputEditText etName = new TextInputEditText(context);
        etName.setText(channel.getName());
        tilName.addView(etName);
        layout.addView(tilName);

        TextInputLayout tilUrl = new TextInputLayout(context);
        tilUrl.setHint("Stream Stream URL");
        TextInputEditText etUrl = new TextInputEditText(context);
        etUrl.setText(channel.getUrl());
        tilUrl.addView(etUrl);
        layout.addView(tilUrl);

        TextInputLayout tilLogo = new TextInputLayout(context);
        tilLogo.setHint("Stream Logo Cover URL");
        TextInputEditText etLogo = new TextInputEditText(context);
        etLogo.setText(channel.getLogoUrl());
        tilLogo.addView(etLogo);
        layout.addView(tilLogo);

        TextInputLayout tilGroup = new TextInputLayout(context);
        tilGroup.setHint("Category Group");
        TextInputEditText etGroup = new TextInputEditText(context);
        etGroup.setText(channel.getGroupName());
        tilGroup.addView(etGroup);
        layout.addView(tilGroup);

        TextInputLayout tilCategory = new TextInputLayout(context);
        tilCategory.setHint("Filter Category Tab");
        TextInputEditText etCategory = new TextInputEditText(context);
        etCategory.setText(channel.getCategory());
        tilCategory.addView(etCategory);
        layout.addView(tilCategory);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Edit Stream Details")
                .setView(layout)
                .setPositiveButton("Save Changes", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String url = etUrl.getText().toString().trim();
                    String logo = etLogo.getText().toString().trim();
                    String group = etGroup.getText().toString().trim();
                    String category = etCategory.getText().toString().trim();

                    if (!name.isEmpty() && !url.isEmpty()) {
                        viewModel.updateChannel(channel.getId(), name, url, logo, group, category, channel.isFavorite());
                    } else {
                        Toast.makeText(this, "Title and URL are required", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
