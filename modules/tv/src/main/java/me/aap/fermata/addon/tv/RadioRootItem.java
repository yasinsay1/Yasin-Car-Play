package me.aap.fermata.addon.tv;

import static me.aap.utils.async.Async.forEach;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedVoid;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import me.aap.fermata.addon.tv.m3u.TvM3uItem;
import me.aap.fermata.addon.tv.radio.RadioStation;
import me.aap.fermata.addon.tv.radio.RadioStationItem;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

public class RadioRootItem extends TvRootItem implements RadioItem {
	public static final String ID = "RADIO";
	private final Map<String, RadioStationItem> stationsMap = new ConcurrentHashMap<>();

	public RadioRootItem(DefaultMediaLib lib) {
		super(lib);
	}

	@Override
	protected FutureSupplier<String> buildTitle() {
		return completed(getLib().getContext().getString(me.aap.fermata.R.string.addon_name_radio));
	}

	public RadioStationItem getOrCreateStationItem(RadioStation station) {
		String id = RadioStationItem.SCHEME + ":" + station.getUuid();
		return stationsMap.computeIfAbsent(id, k -> new RadioStationItem(k, this, station));
	}

	@Nullable
	@Override
	public FutureSupplier<? extends Item> getItem(@Nullable String scheme, String id) {
		if (scheme == null) return ID.equals(id) ? completed(this) : null;
		if (RadioStationItem.SCHEME.equals(scheme)) {
			RadioStationItem cached = stationsMap.get(id);
			if (cached != null) {
				return completed(cached);
			}
			return null;
		}
		return super.getItem(scheme, id);
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		return super.listChildren().then(children -> {
			List<Item> filtered = new ArrayList<>(children.size());
			return forEach(item -> {
				if (item instanceof TvM3uItem m3u) {
					return m3u.hasRadioTracksLoaded().onSuccess(has -> {
						if (has) filtered.add(m3u);
					});
				}
				filtered.add(item);
				return completedVoid();
			}, children).map(v -> filtered);
		});
	}
}
