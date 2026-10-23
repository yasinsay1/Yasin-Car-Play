package me.aap.fermata.addon.tv;

import me.aap.fermata.R;

/**
 * Radio screen.
 *
 * Reuses the TV/live-stream implementation (M3U sources) but presents itself as a
 * separate fragment so the radio card has its own title and identity.  The actual
 * playback path is unchanged, which keeps TV playback and Android Auto behaviour
 * intact.
 */
public class RadioFragment extends TvFragment {

	@Override
	public int getFragmentId() {
		return R.id.radio_fragment;
	}

	@Override
	public CharSequence getFragmentTitle() {
		return getResources().getString(R.string.addon_name_radio);
	}
}
