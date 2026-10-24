package me.aap.fermata.addon.tv.radio;

import static me.aap.utils.async.Completed.completed;

import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.addon.tv.RadioItem;
import me.aap.fermata.addon.tv.RadioRootItem;
import me.aap.fermata.media.engine.MetadataBuilder;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.PlayableItemBase;
import me.aap.fermata.media.pref.StreamItemPrefs;
import me.aap.utils.async.FutureSupplier;

/**
 * Media PlayableItem for Radio Browser stations.
 * Extends PlayableItemBase and implements StreamItem & RadioItem for Fermata media player compatibility.
 */
public class RadioStationItem extends PlayableItemBase implements RadioItem, StreamItemPrefs {
	public static final String SCHEME = "rads";
	private final RadioStation station;

	public RadioStationItem(String id, BrowsableItem parent, RadioStation station) {
		super(id, parent, parent.getLib().getVfsManager().getFile(station.getUrl()).peek());
		this.station = station;
	}

	public RadioStation getStation() {
		return station;
	}

	@NonNull
	public String getName() {
		return station.getName();
	}

	@NonNull
	public String getStreamUrl() {
		return station.getUrl();
	}

	@Nullable
	public String getFaviconUrl() {
		String f = station.getFavicon();
		return f.isEmpty() ? null : f;
	}

	@Override
	public boolean isVideo() {
		return false;
	}

	@Override
	public boolean isStream() {
		return true;
	}

	@Override
	public String getOrigId() {
		return getId();
	}

	@NonNull
	@Override
	protected FutureSupplier<MediaMetadataCompat> loadMeta() {
		return buildMeta(new MetadataBuilder());
	}

	@NonNull
	@Override
	protected FutureSupplier<MediaMetadataCompat> buildMeta(MetadataBuilder meta) {
		meta.putString(MediaMetadataCompat.METADATA_KEY_TITLE, getName());
		meta.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "İnternet Radyosu");
		meta.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, getStreamUrl());
		if (getFaviconUrl() != null) {
			meta.setImageUri(getFaviconUrl());
		}
		return completed(meta.build());
	}

	@NonNull
	public static FutureSupplier<RadioStationItem> create(RadioRootItem root, String id, RadioStation station) {
		RadioStationItem item = root.getOrCreateStationItem(station);
		return completed(item);
	}
}
