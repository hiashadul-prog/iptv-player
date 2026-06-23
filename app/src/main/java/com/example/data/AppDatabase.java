package com.example.data;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {Channel.class, EpgProgram.class}, version = 4, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract ChannelDao channelDao();
    public abstract EpgDao epgDao();

    private static volatile AppDatabase INSTANCE;
    private static final ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(2);

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "iptv_database")
                            .fallbackToDestructiveMigration()
                            .addCallback(sRoomDatabaseCallback)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    public static AppDatabase getDatabase(final Context context, Object unusedKotlinScope) {
        return getDatabase(context);
    }

    private static final RoomDatabase.Callback sRoomDatabaseCallback = new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);
            databaseWriteExecutor.execute(() -> {
                ChannelDao dao = INSTANCE.channelDao();
                dao.insertChannel(new Channel(0, "Sintel Live", "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8", "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=200", "Movies", true, "", "Movies"));
                dao.insertChannel(new Channel(0, "Tears of Steel", "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8", "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=200", "Movies", false, "", "Movies"));
                dao.insertChannel(new Channel(0, "Oceans Nature", "https://playertest.longtailvideo.com/adaptive/oceans/oceans.m3u8", "https://images.unsplash.com/photo-1505118380757-91f5f5632de0?w=200", "Nature", true, "", "Nature"));
            });
        }
    };
}
