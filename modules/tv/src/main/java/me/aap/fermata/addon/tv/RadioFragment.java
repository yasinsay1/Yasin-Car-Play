package me.aap.fermata.addon.tv;

import static me.aap.utils.async.Async.forEach;
import static me.aap.utils.async.Completed.completedVoid;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.Collator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.tv.m3u.TvM3uItem;
import me.aap.fermata.addon.tv.m3u.TvM3uTrackItem;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Favorites;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.fragment.MainActivityFragment;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;

/**
 * Modern radio screen: every radio/audio station found in all M3U sources is listed on a single
 * screen as a large card (logo, name, favorite and play buttons), with search and a favorites
 * filter. TV/video channels are never listed here.
 */
public class RadioFragment extends MainActivityFragment implements FermataServiceUiBinder.Listener {
	/** Screen width (dp) needed for each grid column. */
	private static final int COLUMN_WIDTH_DP = 400;

	private final List<Station> allStations = new ArrayList<>();
	private final List<Station> visible = new ArrayList<>();
	private final StationAdapter adapter = new StationAdapter();
	private GridLayoutManager layoutManager;
	private RecyclerView list;
	private EditText search;
	private View searchClear;
	private TextView chipAll;
	private TextView chipFav;
	private TextView count;
	private View loading;
	private View empty;
	private ImageView emptyIcon;
	private TextView emptyTitle;
	private TextView emptyMessage;
	private TextView emptyAction;
	private FermataServiceUiBinder binder;
	private DefaultMediaLib lib;
	private boolean favoritesOnly;
	private boolean loaded;
	private boolean loadFailed;
	private String query = "";
	private int loadToken;

	@Override
	public int getFragmentId() {
		return me.aap.fermata.R.id.radio_fragment;
	}

	@Override
	public CharSequence getTitle() {
		return getResources().getString(me.aap.fermata.R.string.addon_name_radio);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
													 @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.radio_fragment, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		list = view.findViewById(R.id.radio_list);
		search = view.findViewById(R.id.radio_search);
		searchClear = view.findViewById(R.id.radio_search_clear);
		chipAll = view.findViewById(R.id.radio_chip_all);
		chipFav = view.findViewById(R.id.radio_chip_fav);
		count = view.findViewById(R.id.radio_count);
		loading = view.findViewById(R.id.radio_loading);
		empty = view.findViewById(R.id.radio_empty);
		emptyIcon = view.findViewById(R.id.radio_empty_icon);
		emptyTitle = view.findViewById(R.id.radio_empty_title);
		emptyMessage = view.findViewById(R.id.radio_empty_message);
		emptyAction = view.findViewById(R.id.radio_empty_action);

		layoutManager = new GridLayoutManager(view.getContext(), spanCount());
		list.setLayoutManager(layoutManager);
		list.setAdapter(adapter);

		chipAll.setOnClickListener(v -> setFavoritesOnly(false));
		chipFav.setOnClickListener(v -> setFavoritesOnly(true));
		searchClear.setOnClickListener(v -> search.setText(""));
		emptyAction.setOnClickListener(v -> onEmptyAction());
		search.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}

			@Override
			public void afterTextChanged(Editable s) {
				query = s.toString();
				searchClear.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
				applyFilter();
			}
		});
		search.setOnEditorActionListener((v, actionId, event) -> {
			if (actionId != EditorInfo.IME_ACTION_SEARCH) return false;
			hideKeyboard();
			return true;
		});

		updateChips();
		updateState();

		MainActivityDelegate.getActivityDelegate(view.getContext()).main().onSuccess(a -> {
			if (getView() == null) return;
			binder = a.getMediaServiceBinder();
			binder.addBroadcastListener(this);
			loadStations(true);
		});
	}

	@Override
	public void onDestroyView() {
		loadToken++;
		if (binder != null) {
			binder.removeBroadcastListener(this);
			binder = null;
		}
		list = null;
		super.onDestroyView();
	}

	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		if (layoutManager != null) layoutManager.setSpanCount(spanCount());
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (hidden) {
			hideKeyboard();
			return;
		}
		// Sources or favorites may have changed while the screen was hidden.
		if (loaded && (binder != null)) loadStations(false);
	}

	@Override
	public void onPlayableChanged(PlayableItem oldItem, PlayableItem newItem) {
		RecyclerView l = list;
		if (l == null) return;
		l.post(() -> {
			for (int i = 0; i < visible.size(); i++) {
				PlayableItem t = visible.get(i).track;
				if (t.equals(oldItem) || t.equals(newItem)) adapter.notifyItemChanged(i);
			}
		});
	}

	// ---------------------------------------------------------------------------------------------
	// Loading
	// ---------------------------------------------------------------------------------------------

	private void loadStations(boolean showLoading) {
		MainActivityDelegate a = getActivityDelegate();
		if (a == null) return;
		lib = (DefaultMediaLib) a.getLib();
		int token = ++loadToken;

		if (showLoading) {
			loaded = false;
			loadFailed = false;
			updateState();
		}

		DefaultMediaLib l = lib;
		collectStations(l).main().onCompletion((result, err) -> {
			if ((token != loadToken) || (getView() == null)) return;
			allStations.clear();

			if ((err != null) || (result == null)) {
				if (err != null) Log.e(err, "Failed to load radio stations");
				else Log.e("Failed to load radio stations");
				loadFailed = true;
			} else {
				loadFailed = false;
				allStations.addAll(result);
			}

			loaded = true;
			applyFilter();
		});
	}

	/** Reads every M3U source and returns all radio stations, de-duplicated and sorted by name. */
	private FutureSupplier<List<Station>> collectStations(DefaultMediaLib l) {
		// The interface types are used on purpose: the implementing classes are not public.
		BrowsableItem root = rootItem(l);
		Favorites fav = l.getFavorites();
		List<TvM3uTrackItem> tracks = new ArrayList<>();

		return root.getUnsortedChildren().then(children -> forEach(child -> {
					if (child instanceof TvM3uItem m3u) {
						return m3u.getRadioTracks().ifFail(err -> {
							Log.e(err, "Failed to read radio stations from source ", m3u);
							return Collections.<TvM3uTrackItem>emptyList();
						}).onSuccess(r -> tracks.addAll(r));
					}
					return completedVoid();
				}, children))
				// Favorites must be loaded before the heart icons can be drawn.
				.then(v -> fav.getUnsortedChildren().ifFail(err -> {
					Log.e(err, "Failed to load favorites");
					return Collections.<Item>emptyList();
				}))
				.map(v -> toStations(tracks));
	}

	private static TvRootItem rootItem(DefaultMediaLib l) {
		AddonManager am = AddonManager.get();
		TvAddon tv = am.getAddon(TvAddon.class);
		if (tv != null) return tv.getRootItem(l);
		return Objects.requireNonNull(am.getAddon(RadioAddon.class)).getRootItem(l);
	}

	private static List<Station> toStations(List<TvM3uTrackItem> tracks) {
		// The same stream listed in several sources is shown only once.
		Map<String, Station> unique = new LinkedHashMap<>();
		for (TvM3uTrackItem t : tracks) {
			String key = String.valueOf(t.getResource().getRid());
			if (!unique.containsKey(key)) unique.put(key, new Station(t));
		}

		List<Station> result = new ArrayList<>(unique.values());
		Collator collator = Collator.getInstance();
		collator.setStrength(Collator.PRIMARY);
		Collections.sort(result, (a, b) -> collator.compare(a.name, b.name));
		return result;
	}

	// ---------------------------------------------------------------------------------------------
	// Filtering and state
	// ---------------------------------------------------------------------------------------------

	private void setFavoritesOnly(boolean only) {
		if (favoritesOnly == only) return;
		favoritesOnly = only;
		updateChips();
		applyFilter();
	}

	private void applyFilter() {
		if (list == null) return;
		String q = normalize(query.trim());
		visible.clear();

		for (Station s : allStations) {
			if (favoritesOnly && !isFavorite(s)) continue;
			if (!q.isEmpty() && !s.searchText.contains(q)) continue;
			visible.add(s);
		}

		adapter.notifyDataSetChanged();
		updateState();
	}

	private void updateChips() {
		styleChip(chipAll, !favoritesOnly);
		styleChip(chipFav, favoritesOnly);
	}

	private void styleChip(TextView chip, boolean selected) {
		chip.setBackgroundResource(selected ? R.drawable.radio_chip_on : R.drawable.radio_chip_off);
		chip.setTextColor(color(selected ? R.color.radio_on_accent : R.color.radio_text));
	}

	private void updateState() {
		if (empty == null) return;
		Context ctx = empty.getContext();

		if (!loaded) {
			loading.setVisibility(View.VISIBLE);
			empty.setVisibility(View.GONE);
			count.setText("");
			return;
		}

		loading.setVisibility(View.GONE);
		count.setText(ctx.getString(R.string.radio_station_count, visible.size()));

		if (!visible.isEmpty()) {
			empty.setVisibility(View.GONE);
			return;
		}

		emptyAction.setVisibility(View.GONE);

		if (loadFailed) {
			showEmpty(me.aap.fermata.R.drawable.audiotrack, R.color.radio_accent,
					ctx.getString(R.string.radio_error_title), ctx.getString(R.string.radio_error_message));
			showAction(ctx.getString(R.string.radio_retry));
		} else if (allStations.isEmpty()) {
			showEmpty(me.aap.fermata.R.drawable.audiotrack, R.color.radio_accent,
					ctx.getString(R.string.radio_empty_title), ctx.getString(R.string.radio_empty_message));
			if (hasTvAddon()) showAction(ctx.getString(R.string.radio_open_tv));
		} else if (!query.trim().isEmpty()) {
			showEmpty(me.aap.fermata.R.drawable.search, R.color.radio_text_secondary,
					ctx.getString(R.string.radio_no_results_title),
					ctx.getString(R.string.radio_no_results_message, query.trim()));
		} else {
			showEmpty(me.aap.fermata.R.drawable.favorite, R.color.radio_favorite,
					ctx.getString(R.string.radio_no_favorites_title),
					ctx.getString(R.string.radio_no_favorites_message));
		}
	}

	private void showEmpty(int icon, int tint, String title, String message) {
		emptyIcon.setImageResource(icon);
		emptyIcon.setImageTintList(ColorStateList.valueOf(color(tint)));
		emptyTitle.setText(title);
		emptyMessage.setText(message);
		empty.setVisibility(View.VISIBLE);
	}

	private void showAction(String text) {
		emptyAction.setText(text);
		emptyAction.setVisibility(View.VISIBLE);
	}

	private void onEmptyAction() {
		if (loadFailed) {
			loadStations(true);
			return;
		}
		MainActivityDelegate a = getActivityDelegate();
		if ((a != null) && hasTvAddon()) a.showFragment(me.aap.fermata.R.id.tv_fragment);
	}

	private static boolean hasTvAddon() {
		return AddonManager.get().getAddon(TvAddon.class) != null;
	}

	// ---------------------------------------------------------------------------------------------
	// Actions
	// ---------------------------------------------------------------------------------------------

	private void play(Station s) {
		MainActivityDelegate a = getActivityDelegate();
		if (a == null) return;
		hideKeyboard();

		if (s.track.equals(a.getCurrentPlayable())) {
			// Already the current station: resumes it when paused, otherwise does nothing.
			a.getMediaServiceBinder().playItem(s.track);
		} else {
			a.getBody().playItem(s.track);
		}
	}

	private boolean isFavorite(Station s) {
		if (lib == null) return false;
		Favorites fav = lib.getFavorites();
		return fav.isFavoriteItem(s.track);
	}

	private boolean isPlaying(Station s) {
		MainActivityDelegate a = getActivityDelegate();
		return (a != null) && s.track.equals(a.getCurrentPlayable());
	}

	private void toggleFavorite(Station s) {
		if (lib == null) return;
		Favorites fav = lib.getFavorites();
		FutureSupplier<Void> f = fav.isFavoriteItem(s.track) ? fav.removeItem(s.track) : fav.addItem(s.track);

		f.main().onCompletion((v, err) -> {
			if (err != null) Log.e(err, "Failed to update favorites: ", s.name);
			if (list == null) return;

			if (favoritesOnly) {
				applyFilter();
			} else {
				int idx = visible.indexOf(s);
				if (idx >= 0) adapter.notifyItemChanged(idx);
			}
		});
	}

	// ---------------------------------------------------------------------------------------------
	// Helpers
	// ---------------------------------------------------------------------------------------------

	private int spanCount() {
		int widthDp = getResources().getConfiguration().screenWidthDp;
		return Math.max(1, widthDp / COLUMN_WIDTH_DP);
	}

	private int color(int res) {
		return ContextCompat.getColor(requireContext(), res);
	}

	private void hideKeyboard() {
		if (search == null) return;
		InputMethodManager imm =
				(InputMethodManager) search.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
		if (imm != null) imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
		search.clearFocus();
	}

	/** Lower-cases and strips accents so that e.g. "radyo", "RADYO" and "Rådyo" all match. */
	private static String normalize(String s) {
		if (s == null) return "";
		String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
		return n.replaceAll("\\p{M}+", "").replace('ı', 'i');
	}

	// ---------------------------------------------------------------------------------------------
	// Model and adapter
	// ---------------------------------------------------------------------------------------------

	private static final class Station {
		final TvM3uTrackItem track;
		final String name;
		final String group;
		final String searchText;

		Station(TvM3uTrackItem track) {
			this.track = track;
			this.name = track.getName();
			BrowsableItem p = track.getParent();
			String g = (p instanceof me.aap.fermata.media.lib.M3uGroupItem) ? p.getName() : null;
			this.group = ((g != null) && !g.trim().isEmpty()) ? g.trim() : null;
			this.searchText = normalize(name + ' ' + ((group != null) ? group : ""));
		}
	}

	private final class StationAdapter extends RecyclerView.Adapter<StationHolder> {

		@NonNull
		@Override
		public StationHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			View v = LayoutInflater.from(parent.getContext())
					.inflate(R.layout.radio_station_item, parent, false);
			return new StationHolder(v);
		}

		@Override
		public void onBindViewHolder(@NonNull StationHolder holder, int position) {
			holder.bind(visible.get(position));
		}

		@Override
		public void onViewRecycled(@NonNull StationHolder holder) {
			holder.unbind();
		}

		@Override
		public int getItemCount() {
			return visible.size();
		}
	}

	private final class StationHolder extends RecyclerView.ViewHolder {
		private final View card;
		private final ImageView logo;
		private final ImageView fav;
		private final ImageView play;
		private final TextView name;
		private final TextView sub;
		private Station bound;
		private int bindSeq;

		StationHolder(@NonNull View v) {
			super(v);
			card = v.findViewById(R.id.radio_card);
			logo = v.findViewById(R.id.radio_logo);
			fav = v.findViewById(R.id.radio_fav);
			play = v.findViewById(R.id.radio_play);
			name = v.findViewById(R.id.radio_name);
			sub = v.findViewById(R.id.radio_sub);

			View.OnClickListener playClick = x -> {
				if (bound != null) play(bound);
			};
			card.setOnClickListener(playClick);
			play.setOnClickListener(playClick);
			fav.setOnClickListener(x -> {
				if (bound != null) toggleFavorite(bound);
			});
		}

		void bind(Station s) {
			bound = s;
			int seq = ++bindSeq;
			Context ctx = card.getContext();
			boolean playing = isPlaying(s);

			name.setText(s.name);
			sub.setText(s.group);
			sub.setVisibility((s.group != null) ? View.VISIBLE : View.GONE);
			card.setContentDescription(ctx.getString(R.string.radio_play_station, s.name));
			card.setBackgroundResource(
					playing ? R.drawable.radio_card_bg_playing : R.drawable.radio_card_bg);

			play.setImageResource(
					playing ? me.aap.fermata.R.drawable.equalizer : me.aap.fermata.R.drawable.play);
			play.setImageTintList(ColorStateList.valueOf(color(R.color.radio_on_accent)));

			boolean isFav = isFavorite(s);
			fav.setImageResource(isFav ? me.aap.fermata.R.drawable.favorite_filled
					: me.aap.fermata.R.drawable.favorite);
			fav.setImageTintList(ColorStateList.valueOf(
					color(isFav ? R.color.radio_favorite : R.color.radio_text_secondary)));
			fav.setContentDescription(ctx.getString(
					isFav ? R.string.radio_remove_favorite : R.string.radio_add_favorite));

			showPlaceholderLogo();
			loadLogo(s, seq);
		}

		void unbind() {
			bound = null;
			bindSeq++;
		}

		private void showPlaceholderLogo() {
			logo.setImageResource(me.aap.fermata.R.drawable.audiotrack);
			logo.setImageTintList(ColorStateList.valueOf(color(R.color.radio_accent)));
		}

		private void loadLogo(Station s, int seq) {
			DefaultMediaLib l = lib;
			if (l == null) return;

			s.track.getMediaDescription().main().onSuccess(md -> {
				if ((bound != s) || (bindSeq != seq)) return;
				Uri uri = md.getIconUri();
				if ((uri == null) || ContentResolver.SCHEME_ANDROID_RESOURCE.equals(uri.getScheme())) return;

				l.getBitmap(uri.toString(), true, true).main().onSuccess(bm -> {
					if ((bm == null) || (bound != s) || (bindSeq != seq)) return;
					logo.setImageTintList(null);
					logo.setImageBitmap(bm);
				});
			});
		}
	}
}
