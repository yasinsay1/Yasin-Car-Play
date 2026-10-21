package com.example.yasincarplay;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String PREFS = "yasincarplay";
    private static final String KEY_URL = "m3u_url";

    private EditText urlInput;
    private EditText searchInput;
    private TextView status;
    private ChannelAdapter adapter;
    private final List<M3uParser.Channel> allChannels = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final int pad = dp(16);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(pad,
                    pad + insets.getSystemWindowInsetTop(),
                    pad,
                    pad + insets.getSystemWindowInsetBottom());
            return insets;
        });

        TextView title = new TextView(this);
        title.setText("Yasin Car Play");
        title.setTextSize(24);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        urlInput = new EditText(this);
        urlInput.setHint("M3U liste adresi (http...)");
        urlInput.setSingleLine(true);
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        root.addView(urlInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        Button loadButton = new Button(this);
        loadButton.setText("Kanalları Yükle");
        root.addView(loadButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        searchInput = new EditText(this);
        searchInput.setHint("Kanal ara");
        searchInput.setSingleLine(true);
        root.addView(searchInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        status = new TextView(this);
        status.setText("M3U adresini yapıştırıp \"Kanalları Yükle\"ye bas.");
        status.setTextSize(14);
        root.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        RecyclerView list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChannelAdapter();
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f));

        setContentView(root);

        loadButton.setOnClickListener(v -> {
            String address = urlInput.getText().toString().trim();
            if (!address.startsWith("http://") && !address.startsWith("https://")) {
                status.setText("Adres http:// veya https:// ile başlamalı.");
                return;
            }
            loadChannels(address);
        });

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                applyFilter();
            }
        });

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String saved = prefs.getString(KEY_URL, "");
        if (saved != null && !saved.isEmpty()) {
            urlInput.setText(saved);
            loadChannels(saved);
        }
    }

    private void loadChannels(final String address) {
        status.setText("Yükleniyor...");
        new Thread(() -> {
            try {
                final List<M3uParser.Channel> result = download(address);
                runOnUiThread(() -> {
                    getSharedPreferences(PREFS, MODE_PRIVATE)
                            .edit().putString(KEY_URL, address).apply();
                    allChannels.clear();
                    allChannels.addAll(result);
                    applyFilter();
                });
            } catch (Exception e) {
                final String message = e.getMessage() == null ? e.toString() : e.getMessage();
                runOnUiThread(() -> status.setText("Liste yüklenemedi: " + message));
            }
        }).start();
    }

    private static List<M3uParser.Channel> download(String address) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
        try {
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", "YasinCarPlay/1.1");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("Sunucu hatası: " + code);
            }
            InputStreamReader reader = new InputStreamReader(conn.getInputStream(), "UTF-8");
            try {
                return M3uParser.parse(reader);
            } finally {
                reader.close();
            }
        } finally {
            conn.disconnect();
        }
    }

    private void applyFilter() {
        String query = searchInput.getText().toString().trim().toLowerCase(Locale.getDefault());
        List<M3uParser.Channel> filtered = new ArrayList<>();
        for (M3uParser.Channel c : allChannels) {
            if (query.isEmpty()
                    || c.name.toLowerCase(Locale.getDefault()).contains(query)
                    || c.group.toLowerCase(Locale.getDefault()).contains(query)) {
                filtered.add(c);
            }
        }
        adapter.setItems(filtered);
        status.setText(filtered.size() + " kanal");
    }

    private void openChannel(M3uParser.Channel channel) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_NAME, channel.name);
        intent.putExtra(PlayerActivity.EXTRA_URL, channel.url);
        startActivity(intent);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.Holder> {

        private final List<M3uParser.Channel> items = new ArrayList<>();

        void setItems(List<M3uParser.Channel> newItems) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            TextView text = new TextView(parent.getContext());
            text.setTextSize(18);
            int p = dp(14);
            text.setPadding(p, p, p, p);
            text.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return new Holder(text);
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            final M3uParser.Channel channel = items.get(position);
            holder.text.setText(channel.group.isEmpty()
                    ? channel.name
                    : channel.name + "  •  " + channel.group);
            holder.text.setOnClickListener(v -> openChannel(channel));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView text;

            Holder(TextView text) {
                super(text);
                this.text = text;
            }
        }
    }
}
