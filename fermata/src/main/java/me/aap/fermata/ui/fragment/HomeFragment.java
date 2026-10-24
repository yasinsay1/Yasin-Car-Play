package me.aap.fermata.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;

/**
 * Home screen with four quick-access cards.
 *
 * Extends MainActivityFragment rather than MediaLibFragment: this screen shows
 * a static card layout, so it must not provide a media adapter and must not
 * expose a MediaItemListView.
 */
public class HomeFragment extends MainActivityFragment {

	@Override
	public int getFragmentId() {
		return R.id.home_fragment;
	}

	@Override
	public CharSequence getTitle() {
		return getResources().getString(R.string.home_screen);
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
											 @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_home, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		setupHomeScreen(view);
	}

	private void setupHomeScreen(View view) {
		View tv = view.findViewById(R.id.card_tv);
		if (tv != null) tv.setOnClickListener(v -> openTv());

		View radio = view.findViewById(R.id.card_radio);
		if (radio != null) radio.setOnClickListener(v -> openRadio());

		View music = view.findViewById(R.id.card_music);
		if (music != null) music.setOnClickListener(v -> openLibrary());

		View video = view.findViewById(R.id.card_video);
		if (video != null) video.setOnClickListener(v -> openLibrary());
	}

	/**
	 * TV lives in an optional add-on. showFragment() must never be called for an id
	 * that is not registered: the base ActivityDelegate.createFragment() throws
	 * IllegalArgumentException for unknown ids.
	 */
	private void openTv() {
		if (hasTvAddon()) openFragment(R.id.tv_fragment);
		else showUnavailable(R.string.tv_unavailable);
	}

	/** Live radio streams are provided by the same TV / live-stream add-on. */
	private void openRadio() {
		if (hasRadioAddon()) openFragment(R.id.radio_fragment);
		else showUnavailable(R.string.radio_unavailable);
	}

	/** Music and video are browsed through the media library. */
	private void openLibrary() {
		openFragment(R.id.folders_fragment);
	}

	private void openFragment(int id) {
		MainActivityDelegate activity = getActivityDelegate();
		if (activity != null) activity.showFragment(id);
	}

	private static boolean hasTvAddon() {
		return FermataApplication.get().getAddonManager().hasAddon(R.id.tv_fragment);
	}

	private static boolean hasRadioAddon() {
		return FermataApplication.get().getAddonManager().hasAddon(R.id.radio_fragment);
	}

	private void showUnavailable(int message) {
		if (getContext() == null) return;
		Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
	}
}
