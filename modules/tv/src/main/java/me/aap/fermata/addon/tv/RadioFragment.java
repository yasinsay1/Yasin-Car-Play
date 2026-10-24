package me.aap.fermata.addon.tv;

import static java.util.Objects.requireNonNull;

import java.util.List;

import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.tv.m3u.TvM3uItem;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;

public class RadioFragment extends TvFragment {

	@Override
	public CharSequence getFragmentTitle() {
		return getResources().getString(me.aap.fermata.R.string.addon_name_radio);
	}

	@Override
	public int getFragmentId() {
		return me.aap.fermata.R.id.radio_fragment;
	}

	@Override
	public RadioRootItem getRootItem() {
		return requireNonNull(AddonManager.get().getAddon(RadioAddon.class)).getRootItem(
				(DefaultMediaLib) getMainActivity().getLib());
	}

	/** Radyo ekranında yalnızca radyo/ses kanalları ve radyo içeren gruplar gösterilir. */
	@Override
	protected List<? extends Item> filterChildren(BrowsableItem parent, List<? extends Item> children) {
		// Radyo kökü kaynakları zaten kendisi süzer.
		if (parent == null || parent instanceof RadioRootItem) return children;
		return TvM3uItem.filterRadioChildren(children);
	}

	/** M3U kaynağından geri gidince TV kökü yerine Radyo köküne dön. */
	@Override
	protected BrowsableItem mapParent(BrowsableItem parent) {
		if (parent instanceof TvRootItem && !(parent instanceof RadioRootItem)) return getRootItem();
		return parent;
	}
}
