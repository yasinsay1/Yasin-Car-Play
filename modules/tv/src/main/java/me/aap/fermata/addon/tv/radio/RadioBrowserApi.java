package me.aap.fermata.addon.tv.radio;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.log.Log;

/**
 * Client for fetching radio stations from Radio Browser API.
 * Uses background threads and validates safe stream URLs.
 */
public class RadioBrowserApi {
	private static final String[] BASE_URLS = {
			"https://de1.api.radio-browser.info/json/stations/search",
			"https://nl1.api.radio-browser.info/json/stations/search",
			"https://at1.api.radio-browser.info/json/stations/search"
	};

	private static final int TIMEOUT_MS = 8000;
	private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

	public interface Callback {
		void onSuccess(@NonNull List<RadioStation> stations);
		void onError(@NonNull Throwable error);
	}

	/**
	 * Asynchronously fetches top 100 Turkey (countrycode=TR) radio stations ordered by click count.
	 */
	@NonNull
	public static FutureSupplier<List<RadioStation>> fetchTurkeyStations() {
		Promise<List<RadioStation>> promise = new Promise<>();
		EXECUTOR.execute(() -> {
			try {
				List<RadioStation> stations = fetchTurkeyStationsSync();
				promise.complete(stations);
			} catch (Throwable t) {
				Log.e(t, "Failed to fetch radio stations from Radio Browser API");
				promise.completeExceptionally(t);
			}
		});
		return promise;
	}

	@NonNull
	private static List<RadioStation> fetchTurkeyStationsSync() throws Exception {
		Exception lastException = null;

		for (String baseUrl : BASE_URLS) {
			try {
				String queryUrl = baseUrl + "?countrycode=TR&hidebroken=true&order=clickcount&reverse=true&limit=100";
				URL url = new URL(queryUrl);
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setConnectTimeout(TIMEOUT_MS);
				conn.setReadTimeout(TIMEOUT_MS);
				conn.setRequestProperty("User-Agent", "YasinCarPlay/1.0");
				conn.setRequestProperty("Accept", "application/json");

				if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
					try (BufferedReader reader = new BufferedReader(
							new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
						StringBuilder builder = new StringBuilder();
						String line;
						while ((line = reader.readLine()) != null) {
							builder.append(line);
						}

						JSONArray jsonArray = new JSONArray(builder.toString());
						List<RadioStation> stations = new ArrayList<>();

						for (int i = 0; i < jsonArray.length(); i++) {
							JSONObject obj = jsonArray.getJSONObject(i);
							RadioStation station = RadioStation.createFromJson(obj);
							if (station != null && isSafeStreamUrl(station.getUrl())) {
								stations.add(station);
							}
						}

						conn.disconnect();
						return stations;
					}
				} else {
					conn.disconnect();
				}
			} catch (Exception e) {
				lastException = e;
			}
		}

		if (lastException != null) {
			throw lastException;
		}
		return Collections.emptyList();
	}

	/**
	 * Validates stream URLs to prevent dangerous or unsupported protocols.
	 * Only allows http and https protocols.
	 */
	public static boolean isSafeStreamUrl(@Nullable String streamUrl) {
		if (streamUrl == null) return false;
		String trimmed = streamUrl.trim().toLowerCase();
		if (trimmed.isEmpty()) return false;

		if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
			return false;
		}

		// Reject known local or invalid hosts
		if (trimmed.contains("127.0.0.1") || trimmed.contains("localhost")) {
			return false;
		}

		return true;
	}
}
