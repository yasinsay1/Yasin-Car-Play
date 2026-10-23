package me.aap.fermata.addon.tv;

import androidx.annotation.IdRes;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.addon.FermataFragmentAddon;
import me.aap.utils.ui.fragment.ActivityFragment;

/**
 * Radio add-on.  Reuses the TV module (live streams / M3U) but exposes a
 * dedicated fragment id so the home screen radio card has its own identity.
 *
 * @author Yasin Car Play
 */
@Keep
@SuppressWarnings("unused")
public class RadioAddon implements FermataFragmentAddon {
	@NonNull
	private static final AddonInfo info = FermataAddon.findAddonInfo(RadioAddon.class.getName());

	@IdRes
	@Override
	public int getAddonId() {
		return me.aap.fermata.R.id.radio_fragment;
	}

	@NonNull
	@Override
	public AddonInfo getInfo() {
		return info;
	}

	@NonNull
	@Override
	public ActivityFragment createFragment() {
		return new RadioFragment();
	}
}
