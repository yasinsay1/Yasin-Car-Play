package com.example.yasincarplay;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Radio Browser (radio-browser.info) açık radyo rehberinden Türkiye radyolarını getirir.
 */
final class RadioBrowser {

    private static final String[] HOSTS = {
            "de1.api.radio-browser.info",
            "de2.api.radio-browser.info",
            "nl1.api.radio-browser.info",
            "fi1.api.radio-browser.info"
    };

    private RadioBrowser() {
    }

    static List<M3uParser.Channel> topTurkishStations() throws IOException {
        IOException last = null;
        for (String host : HOSTS) {
            try {
                return fetch(host);
            } catch (IOException e) {
                last = e;
            }
        }
        if (last != null) {
            throw last;
        }
        throw new IOException("Radyo rehberine ulaşılamadı");
    }

    private static List<M3uParser.Channel> fetch(String host) throws IOException {
        URL url = new URL("https://" + host
                + "/json/stations/bycountrycodeexact/TR"
                + "?hidebroken=true&order=clickcount&reverse=true&limit=200");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("User-Agent", "YasinCarPlay/1.2");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("Radyo rehberi hatası: " + code);
            }
            StringBuilder text = new StringBuilder();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    text.append(line);
                }
            } finally {
                reader.close();
            }
            return parse(text.toString());
        } finally {
            conn.disconnect();
        }
    }

    private static List<M3uParser.Channel> parse(String json) throws IOException {
        List<M3uParser.Channel> channels = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String name = obj.optString("name", "").trim();
                String stream = obj.optString("url_resolved", "").trim();
                if (stream.isEmpty()) {
                    stream = obj.optString("url", "").trim();
                }
                if (name.isEmpty() || stream.isEmpty()) {
                    continue;
                }
                String tags = obj.optString("tags", "").trim();
                String firstTag = tags.contains(",") ? tags.substring(0, tags.indexOf(',')) : tags;
                String logo = obj.optString("favicon", "").trim();
                channels.add(new M3uParser.Channel(name, stream, firstTag, logo));
            }
        } catch (JSONException e) {
            throw new IOException("Radyo listesi okunamadı");
        }
        return channels;
    }
}
