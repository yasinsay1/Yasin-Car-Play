package me.aap.fermata.addon.tv;

import static java.util.Objects.requireNonNull;

import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.media.lib.DefaultMediaLib;

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
}
