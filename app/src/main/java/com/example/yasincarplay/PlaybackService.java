package com.example.yasincarplay;

import android.content.SharedPreferences;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.LibraryResult;
import androidx.media3.session.MediaLibraryService;
import androidx.media3.session.MediaSession;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.util.ArrayList;
import java.util.List;

/**
 * Arka planda yalnızca ses çalar ve Android Auto'ya medya uygulaması olarak görünür.
 * Araç ekranında video gösterilmez; TV kanalları burada sadece ses olarak çalınır.
 */
public class PlaybackService extends MediaLibraryService {

    private static final String ROOT_ID = "root";
    private static final String TV_ID = "tv";
    private static final String RADIO_ID = "radio";

    private MediaLibrarySession session;

    @Override
    public void onCreate() {
        super.onCreate();

        ExoPlayer player = new ExoPlayer.Builder(this)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(), true)
                .setHandleAudioBecomingNoisy(true)
                .build();
        player.setTrackSelectionParameters(player.getTrackSelectionParameters()
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                .build());

        session = new MediaLibrarySession.Builder(this, player, new LibraryCallback()).build();
    }

    @Override
    public MediaLibrarySession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return session;
    }

    @Override
    public void onDestroy() {
        if (session != null) {
            session.getPlayer().release();
            session.release();
            session = null;
        }
        super.onDestroy();
    }

    private static MediaItem folderItem(String id, String title) {
        MediaMetadata metadata = new MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build();
        return new MediaItem.Builder()
                .setMediaId(id)
                .setMediaMetadata(metadata)
                .build();
    }

    private static MediaItem channelItem(M3uParser.Channel channel) {
        MediaMetadata metadata = new MediaMetadata.Builder()
                .setTitle(channel.name)
                .setArtist(channel.group)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                .build();
        return new MediaItem.Builder()
                .setMediaId(channel.url)
                .setUri(channel.url)
                .setMediaMetadata(metadata)
                .build();
    }

    private class LibraryCallback implements MediaLibrarySession.Callback {

        @Override
        public ListenableFuture<LibraryResult<MediaItem>> onGetLibraryRoot(
                MediaLibrarySession mediaSession,
                MediaSession.ControllerInfo browser,
                LibraryParams params) {
            MediaMetadata metadata = new MediaMetadata.Builder()
                    .setTitle("Yasin Car Play")
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build();
            MediaItem root = new MediaItem.Builder()
                    .setMediaId(ROOT_ID)
                    .setMediaMetadata(metadata)
                    .build();
            return Futures.immediateFuture(LibraryResult.ofItem(root, params));
        }

        @Override
        public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetChildren(
                MediaLibrarySession mediaSession,
                MediaSession.ControllerInfo browser,
                String parentId,
                int page,
                int pageSize,
                LibraryParams params) {

            if (ROOT_ID.equals(parentId)) {
                ImmutableList<MediaItem> folders = ImmutableList.of(
                        folderItem(RADIO_ID, "Radyo"),
                        folderItem(TV_ID, "TV kanalları (yalnızca ses)"));
                return Futures.immediateFuture(LibraryResult.ofItemList(folders, params));
            }

            String key;
            if (TV_ID.equals(parentId)) {
                key = MainActivity.KEY_URL;
            } else if (RADIO_ID.equals(parentId)) {
                key = MainActivity.KEY_RADIO_URL;
            } else {
                return Futures.immediateFuture(
                        LibraryResult.<ImmutableList<MediaItem>>ofError(
                                LibraryResult.RESULT_ERROR_BAD_VALUE));
            }

            SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
            final String address = prefs.getString(key, "");
            if (address == null || address.isEmpty()) {
                return Futures.immediateFuture(
                        LibraryResult.ofItemList(ImmutableList.<MediaItem>of(), params));
            }

            final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> future =
                    SettableFuture.create();
            final int requestedPage = page;
            final int requestedSize = pageSize;
            final LibraryParams requestedParams = params;
            new Thread(() -> {
                try {
                    List<M3uParser.Channel> channels = MainActivity.download(address);
                    List<MediaItem> items = new ArrayList<>();
                    for (M3uParser.Channel channel : channels) {
                        items.add(channelItem(channel));
                    }
                    long from = Math.min((long) requestedPage * requestedSize, items.size());
                    long to = Math.min(from + requestedSize, items.size());
                    ImmutableList<MediaItem> pageItems =
                            ImmutableList.copyOf(items.subList((int) from, (int) to));
                    future.set(LibraryResult.ofItemList(pageItems, requestedParams));
                } catch (Exception e) {
                    future.set(LibraryResult.<ImmutableList<MediaItem>>ofError(
                            LibraryResult.RESULT_ERROR_IO));
                }
            }).start();
            return future;
        }

        @Override
        public ListenableFuture<LibraryResult<MediaItem>> onGetItem(
                MediaLibrarySession mediaSession,
                MediaSession.ControllerInfo browser,
                String mediaId) {
            return Futures.immediateFuture(
                    LibraryResult.<MediaItem>ofError(LibraryResult.RESULT_ERROR_NOT_SUPPORTED));
        }

        @Override
        public ListenableFuture<List<MediaItem>> onAddMediaItems(
                MediaSession mediaSession,
                MediaSession.ControllerInfo controller,
                List<MediaItem> mediaItems) {
            List<MediaItem> resolved = new ArrayList<>();
            for (MediaItem item : mediaItems) {
                if (item.localConfiguration == null && item.mediaId != null
                        && (item.mediaId.startsWith("http://")
                        || item.mediaId.startsWith("https://"))) {
                    resolved.add(item.buildUpon().setUri(item.mediaId).build());
                } else {
                    resolved.add(item);
                }
            }
            return Futures.immediateFuture(resolved);
        }
    }
}
