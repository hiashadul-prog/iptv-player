package com.example.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.data.Channel;
import com.example.data.EpgProgram;
import com.google.android.material.card.MaterialCardView;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChannelAdapter extends ListAdapter<Channel, ChannelAdapter.ChannelViewHolder> {

    public interface ChannelListener {
        void onChannelClick(Channel channel);
        void onChannelLongClick(Channel channel, View itemView);
        void onToggleFavorite(Channel channel);
    }

    private final ChannelListener listener;
    private Channel selectedChannel;
    private final Map<String, List<EpgProgram>> epgByChannel = new HashMap<>();
    private final SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public ChannelAdapter(ChannelListener listener) {
        super(new DiffUtil.ItemCallback<Channel>() {
            @Override
            public boolean areItemsTheSame(@NonNull Channel oldItem, @NonNull Channel newItem) {
                return oldItem.getId() == newItem.getId();
            }

            @Override
            public boolean areContentsTheSame(@NonNull Channel oldItem, @NonNull Channel newItem) {
                return oldItem.getName().equals(newItem.getName()) &&
                        oldItem.getUrl().equals(newItem.getUrl()) &&
                        oldItem.getLogoUrl().equals(newItem.getLogoUrl()) &&
                        oldItem.getGroupName().equals(newItem.getGroupName()) &&
                        oldItem.isFavorite() == newItem.isFavorite();
            }
        });
        this.listener = listener;
    }

    public void setSelectedChannel(Channel category) {
        this.selectedChannel = category;
        notifyItemRangeChanged(0, getItemCount());
    }

    public void setEpgPrograms(List<EpgProgram> programs) {
        epgByChannel.clear();
        if (programs != null) {
            for (EpgProgram p : programs) {
                String key = p.getChannelId().toLowerCase().trim();
                if (!epgByChannel.containsKey(key)) {
                    epgByChannel.put(key, new java.util.ArrayList<>());
                }
                epgByChannel.get(key).add(p);
            }
        }
        notifyItemRangeChanged(0, getItemCount());
    }

    @NonNull
    @Override
    public ChannelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(com.example.R.layout.item_channel, parent, false);
        return new ChannelViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChannelViewHolder holder, int position) {
        Channel channel = getItem(position);
        holder.bind(channel);
    }

    class ChannelViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView cardRoot;
        private final ImageView imgLogo;
        private final TextView txtName;
        private final View nowPlayingContainer;
        private final TextView txtNowPlaying;
        private final ProgressBar epgProgress;
        private final TextView txtNextPlaying;
        private final View fallbackTagContainer;
        private final TextView txtDefaultTag;
        private final TextView txtGroup;
        private final ImageView btnFavorite;

        public ChannelViewHolder(@NonNull View itemView) {
            super(itemView);
            cardRoot = itemView.findViewById(com.example.R.id.card_root);
            imgLogo = itemView.findViewById(com.example.R.id.img_logo);
            txtName = itemView.findViewById(com.example.R.id.txt_name);
            nowPlayingContainer = itemView.findViewById(com.example.R.id.now_playing_container);
            txtNowPlaying = itemView.findViewById(com.example.R.id.txt_now_playing);
            epgProgress = itemView.findViewById(com.example.R.id.epg_progress);
            txtNextPlaying = itemView.findViewById(com.example.R.id.txt_next_playing);
            fallbackTagContainer = itemView.findViewById(com.example.R.id.fallback_tag_container);
            txtDefaultTag = itemView.findViewById(com.example.R.id.txt_default_tag);
            txtGroup = itemView.findViewById(com.example.R.id.txt_group);
            btnFavorite = itemView.findViewById(com.example.R.id.btn_favorite);
        }

        public void bind(Channel channel) {
            txtName.setText(channel.getName());

            // Handle selection outline styling
            boolean isSelected = selectedChannel != null && selectedChannel.getId() == channel.getId();
            if (isSelected) {
                cardRoot.setStrokeColor(ContextCompat.getColor(itemView.getContext(), com.example.R.color.accent));
                cardRoot.setStrokeWidth(4);
                cardRoot.setCardBackgroundColor(ContextCompat.getColor(itemView.getContext(), com.example.R.color.selected_card_background));
            } else {
                cardRoot.setStrokeColor(ContextCompat.getColor(itemView.getContext(), com.example.R.color.card_border));
                cardRoot.setStrokeWidth(2);
                cardRoot.setCardBackgroundColor(ContextCompat.getColor(itemView.getContext(), com.example.R.color.card_background));
            }

            // Image loading via Glide
            String logoUrl = channel.getLogoUrl();
            if (logoUrl != null && !logoUrl.trim().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(logoUrl)
                        .placeholder(android.R.drawable.presence_video_online)
                        .error(android.R.drawable.presence_video_online)
                        .into(imgLogo);
            } else {
                imgLogo.setImageResource(android.R.drawable.presence_video_online);
            }

            // Bind Favorite Star
            if (channel.isFavorite()) {
                btnFavorite.setImageResource(android.R.drawable.btn_star_big_on);
            } else {
                btnFavorite.setImageResource(android.R.drawable.btn_star_big_off);
            }

            btnFavorite.setOnClickListener(v -> {
                if (listener != null) listener.onToggleFavorite(channel);
            });

            // Bind EPG values
            long currentTime = System.currentTimeMillis();
            List<EpgProgram> channelPrograms = null;
            if (channel.getTvgId() != null && !channel.getTvgId().trim().isEmpty()) {
                channelPrograms = epgByChannel.get(channel.getTvgId().toLowerCase().trim());
            }
            if (channelPrograms == null) {
                channelPrograms = epgByChannel.get(channel.getName().toLowerCase().trim());
            }

            EpgProgram nowProg = null;
            EpgProgram nextProg = null;

            if (channelPrograms != null) {
                List<EpgProgram> active = new java.util.ArrayList<>();
                for (EpgProgram p : channelPrograms) {
                    if (p.getEndTime() >= currentTime) {
                        active.add(p);
                    }
                }
                active.sort((o1, o2) -> Long.compare(o1.getStartTime(), o2.getStartTime()));
                if (!active.isEmpty()) {
                    nowProg = active.get(0);
                    if (active.size() > 1) {
                        nextProg = active.get(1);
                    }
                }
            }

            if (nowProg != null) {
                nowPlayingContainer.setVisibility(View.VISIBLE);
                String startTimeStr = timeFormatter.format(new java.util.Date(nowProg.getStartTime()));
                txtNowPlaying.setText("Now: " + nowProg.getTitle() + " (" + startTimeStr + ")");

                // Calculate progress
                if (nowProg.getEndTime() > nowProg.getStartTime()) {
                    float progress = (float) (currentTime - nowProg.getStartTime()) / (nowProg.getEndTime() - nowProg.getStartTime());
                    int clamped = (int) (Math.max(0f, Math.min(1f, progress)) * 100);
                    epgProgress.setProgress(clamped);
                    epgProgress.setProgressTintList(ColorStateList.valueOf(ContextCompat.getColor(itemView.getContext(), com.example.R.color.accent)));
                } else {
                    epgProgress.setProgress(0);
                }

                fallbackTagContainer.setVisibility(View.GONE);
            } else {
                nowPlayingContainer.setVisibility(View.GONE);
                fallbackTagContainer.setVisibility(View.VISIBLE);
                txtDefaultTag.setText(isSelected ? "PLAYING" : "STREAM");
                txtDefaultTag.setTextColor(isSelected ? ContextCompat.getColor(itemView.getContext(), com.example.R.color.accent) : ContextCompat.getColor(itemView.getContext(), com.example.R.color.text_secondary));
                if (channel.getGroupName() != null && !channel.getGroupName().trim().isEmpty() && !channel.getGroupName().equalsIgnoreCase("Default")) {
                    txtGroup.setVisibility(View.VISIBLE);
                    txtGroup.setText(channel.getGroupName().toUpperCase());
                } else {
                    txtGroup.setVisibility(View.GONE);
                }
            }

            if (nextProg != null) {
                txtNextPlaying.setVisibility(View.VISIBLE);
                String nextStartStr = timeFormatter.format(new java.util.Date(nextProg.getStartTime()));
                txtNextPlaying.setText("Next: " + nextProg.getTitle() + " (" + nextStartStr + ")");
            } else {
                txtNextPlaying.setVisibility(View.GONE);
            }

            // Click listeners
            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onChannelClick(channel);
            });

            itemView.setOnLongClickListener(v -> {
                if (listener != null) listener.onChannelLongClick(channel, itemView);
                return true;
            });
        }
    }
}
