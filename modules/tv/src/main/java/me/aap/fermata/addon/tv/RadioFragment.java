package me.aap.fermata.addon.tv;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.Collator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.tv.radio.RadioBrowserApi;
import me.aap.fermata.addon.tv.radio.RadioCacheManager;
import me.aap.fermata.addon.tv.radio.RadioStation;
import me.aap.fermata.addon.tv.radio.RadioStationItem;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib.Favorites;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.fragment.MainActivityFragment;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;

/**
 * Modern radio screen: 100 popular Turkey internet radio stations fetched from Radio Browser API,
 * cached locally for fast offline/instant display, with search, favorites and dark station cards.
 */
public class RadioFragment extends MainActivityFragment implements FermataServiceUiBinder.Listener {
	/** Screen width (dp) needed for each grid column. */
	private static final int COLUMN_WIDTH_DP = 400;

	private final List<StationHolderModel> allStations = new ArrayList<>();
	private final List<StationHolderModel> visible = new ArrayList<>();
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
			loadStations();
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
		if (loaded && (binder != null)) applyFilter();
	}

	@Override
	public void onPlayableChanged(PlayableItem oldItem, PlayableItem newItem) {
		RecyclerView l = list;
		if (l == null) return;
		l.post(() -> {
			for (int i = 0; i < visible.size(); i++) {
				PlayableItem t = visible.get(i).item;
				if (t.equals(oldItem) || t.equals(newItem)) adapter.notifyItemChanged(i);
			}
		});
	}

	// ---------------------------------------------------------------------------------------------
	// Loading & Caching Logic
	// ---------------------------------------------------------------------------------------------

	private void loadStations() {
		MainActivityDelegate a = getActivityDelegate();
		if (a == null) return;
		lib = (DefaultMediaLib) a.getLib();
		Context ctx = requireContext();
		int token = ++loadToken;

		if (allStations.isEmpty()) {
			loaded = false;
			loadFailed = false;
			updateState();
		}

		// 1. Load from local JSON cache first
		RadioCacheManager.loadCache(ctx).main().onSuccess(cached -> {
			if (token != loadToken || getView() == null) return;

			if (!cached.isEmpty()) {
				setStationList(cached);
				loaded = true;
				loadFailed = false;
				applyFilter();
			}

			// 2. Fetch fresh top 100 TR stations from Radio Browser API in background
			RadioBrowserApi.fetchTurkeyStations().main().onCompletion((fresh, err) -> {
				if (token != loadToken || getView() == null) return;

				if (err != null || fresh == null || fresh.isEmpty()) {
					if (allStations.isEmpty()) {
						Log.e(err, "Failed to load radio stations from API and cache is empty");
						loadFailed = true;
						loaded = true;
						updateState();
					} else {
						Log.w("Failed to refresh radio stations from API, using cached list");
					}
				} else {
					setStationList(fresh);
					loaded = true;
					loadFailed = false;
					applyFilter();
					RadioCacheManager.saveCache(ctx, fresh);
				}
			});
		});
	}

	private void setStationList(List<RadioStation> stations) {
		allStations.clear();
		RadioAddon addon = AddonManager.get().getAddon(RadioAddon.class);
		RadioRootItem root = (addon != null && lib != null) ? addon.getRootItem(lib) : null;

		for (RadioStation s : stations) {
			if (root != null) {
				RadioStationItem item = root.getOrCreateStationItem(s);
				allStations.add(new StationHolderModel(item, s));
			}
		}

		Collator collator = Collator.getInstance(new Locale("tr", "TR"));
		collator.setStrength(Collator.PRIMARY);
		Collections.sort(allStations, (a, b) -> collator.compare(a.station.getName(), b.station.getName()));
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

		for (StationHolderModel s : allStations) {
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
			loadStations();
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Actions
	// ---------------------------------------------------------------------------------------------

	private void play(StationHolderModel s) {
		MainActivityDelegate a = getActivityDelegate();
		if (a == null) return;
		hideKeyboard();

		if (!RadioBrowserApi.isSafeStreamUrl(s.station.getUrl())) {
			Toast.makeText(requireContext(), "Geçersiz veya desteklenmeyen yayın adresi", Toast.LENGTH_SHORT).show();
			return;
		}

		try {
			if (s.item.equals(a.getCurrentPlayable())) {
				a.getMediaServiceBinder().playItem(s.item);
			} else {
				a.getBody().playItem(s.item);
			}
		} catch (Exception e) {
			Log.e(e, "Playback failed for station ", s.station.getName());
			Toast.makeText(requireContext(), "Radyo yayını başlatılamadı", Toast.LENGTH_SHORT).show();
		}
	}

	private boolean isFavorite(StationHolderModel s) {
		if (lib == null) return false;
		Favorites fav = lib.getFavorites();
		return fav.isFavoriteItem(s.item);
	}

	private boolean isPlaying(StationHolderModel s) {
		MainActivityDelegate a = getActivityDelegate();
		return (a != null) && s.item.equals(a.getCurrentPlayable());
	}

	private void toggleFavorite(StationHolderModel s) {
		if (lib == null) return;
		Favorites fav = lib.getFavorites();
		FutureSupplier<Void> f = fav.isFavoriteItem(s.item) ? fav.removeItem(s.item) : fav.addItem(s.item);

		f.main().onCompletion((v, err) -> {
			if (err != null) Log.e(err, "Failed to update favorites: ", s.station.getName());
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

	private static final class StationHolderModel {
		final RadioStationItem item;
		final RadioStation station;
		final String searchText;

		StationHolderModel(RadioStationItem item, RadioStation station) {
			this.item = item;
			this.station = station;
			String tags = station.getTags();
			this.searchText = normalize(station.getName() + ' ' + (tags.isEmpty() ? "" : tags));
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
		private StationHolderModel bound;
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

		void bind(StationHolderModel s) {
			bound = s;
			int seq = ++bindSeq;
			Context ctx = card.getContext();
			boolean playing = isPlaying(s);

			name.setText(s.station.getName());
			String tags = s.station.getTags();
			sub.setText(tags.isEmpty() ? "İnternet Radyosu" : tags);
			sub.setVisibility(View.VISIBLE);
			card.setContentDescription(ctx.getString(R.string.radio_play_station, s.station.getName()));
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

		private void loadLogo(StationHolderModel s, int seq) {
			DefaultMediaLib l = lib;
			if (l == null) return;
			String favicon = s.station.getFavicon();
			if (favicon.isEmpty()) return;

			l.getBitmap(favicon, true, true).main().onSuccess(bm -> {
				if ((bm == null) || (bound != s) || (bindSeq != seq)) return;
				logo.setImageTintList(null);
				logo.setImageBitmap(bm);
			});
		}
	}
}
