package com.example.yasincarplay;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

public class M3uParser {

    public static class Channel {
        public final String name;
        public final String url;
        public final String group;
        public final String logo;

        public Channel(String name, String url, String group, String logo) {
            this.name = name;
            this.url = url;
            this.group = group;
            this.logo = logo;
        }
    }

    public static List<Channel> parse(Reader reader) throws IOException {
        List<Channel> channels = new ArrayList<>();
        BufferedReader br = new BufferedReader(reader);
        String line;
        String name = null;
        String group = "";
        String logo = "";

        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#EXTM3U")) {
                continue;
            }
            if (line.startsWith("#EXTINF")) {
                int comma = line.lastIndexOf(',');
                name = comma >= 0 ? line.substring(comma + 1).trim() : "Kanal";
                group = attr(line, "group-title");
                logo = attr(line, "tvg-logo");
            } else if (!line.startsWith("#")) {
                if (name == null || name.isEmpty()) {
                    name = line;
                }
                channels.add(new Channel(name, line, group, logo));
                name = null;
                group = "";
                logo = "";
            }
        }
        return channels;
    }

    private static String attr(String line, String key) {
        String token = key + "=\"";
        int start = line.indexOf(token);
        if (start < 0) {
            return "";
        }
        start += token.length();
        int end = line.indexOf('"', start);
        return end < 0 ? "" : line.substring(start, end);
    }
}
