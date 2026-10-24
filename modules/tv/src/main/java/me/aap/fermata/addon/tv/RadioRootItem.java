package me.aap.fermata.addon.tv;

import static me.aap.utils.async.Completed.completed;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.addon.tv.m3u.TvM3uItem;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

public class RadioRootItem extends TvRootItem implements RadioItem {
	public static final String ID = "RADIO";

	public RadioRootItem(DefaultMediaLib lib) {
		super(lib);
	}

	@Override
	protected FutureSupplier<String> buildTitle() {
		return completed(getLib().getContext().getString(me.aap.fermata.R.string.addon_name_radio));
	}

	@Nullable
	@Override
	public FutureSupplier<? extends Item> getItem(@Nullable String scheme, String id) {
		if (scheme == null) return ID.equals(id) ? completed(this) : null;
		return super.getItem(scheme, id);
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		return super.listChildren().map(children -> {
			List<Item> filtered = new ArrayList<>();
			for (Item item : children) {
				if (item instanceof TvM3uItem m3u) {
					if (m3u.hasRadioTracks()) {
						filtered.add(m3u);
					}
				} else {
					filtered.add(item);
				}
			}
			return filtered;
		});
	}
}
