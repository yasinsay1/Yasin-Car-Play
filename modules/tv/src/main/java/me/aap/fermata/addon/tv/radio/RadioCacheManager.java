package me.aap.fermata.addon.tv.radio;

import android.content.Context;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
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
 * Manages local disk caching for radio stations in JSON format.
 */
public class RadioCacheManager {
	private static final String CACHE_FILE_NAME = "radio_stations_tr.json";
	private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

	@NonNull
	public static FutureSupplier<List<RadioStation>> loadCache(@NonNull Context context) {
		Promise<List<RadioStation>> promise = new Promise<>();
		EXECUTOR.execute(() -> {
			try {
				File cacheFile = new File(context.getCacheDir(), CACHE_FILE_NAME);
				if (!cacheFile.exists() || cacheFile.length() == 0) {
					promise.complete(Collections.emptyList());
					return;
				}

				try (BufferedReader reader = new BufferedReader(
						new InputStreamReader(new FileInputStream(cacheFile), StandardCharsets.UTF_8))) {
					StringBuilder sb = new StringBuilder();
					String line;
					while ((line = reader.readLine()) != null) {
						sb.append(line);
					}

					JSONArray jsonArray = new JSONArray(sb.toString());
					List<RadioStation> stations = new ArrayList<>();

					for (int i = 0; i < jsonArray.length(); i++) {
						JSONObject obj = jsonArray.getJSONObject(i);
						RadioStation station = RadioStation.createFromJson(obj);
						if (station != null && RadioBrowserApi.isSafeStreamUrl(station.getUrl())) {
							stations.add(station);
						}
					}

					promise.complete(stations);
				}
			} catch (Throwable t) {
				Log.e(t, "Failed to load radio stations cache");
				promise.complete(Collections.emptyList());
			}
		});
		return promise;
	}

	@NonNull
	public static FutureSupplier<Void> saveCache(@NonNull Context context, @NonNull List<RadioStation> stations) {
		Promise<Void> promise = new Promise<>();
		EXECUTOR.execute(() -> {
			try {
				JSONArray jsonArray = new JSONArray();
				for (RadioStation station : stations) {
					jsonArray.put(station.toJson());
				}

				File cacheFile = new File(context.getCacheDir(), CACHE_FILE_NAME);
				try (OutputStreamWriter writer = new OutputStreamWriter(
						new FileOutputStream(cacheFile), StandardCharsets.UTF_8)) {
					writer.write(jsonArray.toString());
					writer.flush();
				}

				promise.complete(null);
			} catch (Throwable t) {
				Log.e(t, "Failed to save radio stations cache");
				promise.complete(null);
			}
		});
		return promise;
	}
}
