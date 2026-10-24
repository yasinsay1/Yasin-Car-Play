package me.aap.fermata.addon.tv.radio;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

/**
 * Data model for a radio station returned by Radio Browser API.
 */
public class RadioStation implements Serializable {
	private static final long serialVersionUID = 1L;

	private final String uuid;
	private final String name;
	private final String url;
	private final String favicon;
	private final String tags;
	private final int clickCount;
	private final int votes;

	public RadioStation(@NonNull String uuid, @NonNull String name, @NonNull String url,
						@Nullable String favicon, @Nullable String tags,
						int clickCount, int votes) {
		this.uuid = uuid;
		this.name = name;
		this.url = url;
		this.favicon = favicon != null ? favicon : "";
		this.tags = tags != null ? tags : "";
		this.clickCount = clickCount;
		this.votes = votes;
	}

	@NonNull
	public String getUuid() {
		return uuid;
	}

	@NonNull
	public String getName() {
		return name;
	}

	@NonNull
	public String getUrl() {
		return url;
	}

	@NonNull
	public String getFavicon() {
		return favicon;
	}

	@NonNull
	public String getTags() {
		return tags;
	}

	public int getClickCount() {
		return clickCount;
	}

	public int getVotes() {
		return votes;
	}

	@NonNull
	public JSONObject toJson() throws JSONException {
		JSONObject json = new JSONObject();
		json.put("uuid", uuid);
		json.put("name", name);
		json.put("url", url);
		json.put("favicon", favicon);
		json.put("tags", tags);
		json.put("clickCount", clickCount);
		json.put("votes", votes);
		return json;
	}

	@Nullable
	public static RadioStation createFromJson(@NonNull JSONObject json) {
		try {
			String uuid = json.optString("uuid", json.optString("stationuuid", ""));
			String name = json.optString("name", "");
			String url = json.optString("url_resolved", json.optString("url", ""));
			if (uuid.isEmpty() || name.isEmpty() || url.isEmpty()) return null;

			String favicon = json.optString("favicon", "");
			String tags = json.optString("tags", "");
			int clickCount = json.optInt("clickcount", json.optInt("clickCount", 0));
			int votes = json.optInt("votes", 0);

			return new RadioStation(uuid, name, url, favicon, tags, clickCount, votes);
		} catch (Exception e) {
			return null;
		}
	}
}
