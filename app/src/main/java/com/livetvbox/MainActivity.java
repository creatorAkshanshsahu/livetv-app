
package com.livetvbox;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    // V1 public API.
    private static final String API = "https://livetgtv.lovable.app/api/public/channels";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ArrayList<Channel> allChannels = new ArrayList<>();
    private final ArrayList<Channel> shownChannels = new ArrayList<>();

    private ChannelAdapter adapter;
    private TextView status;
    private EditText search;
    private RecyclerView grid;
    private LinearLayout browse;
    private FrameLayout playerContainer;
    private PlayerView playerView;
    private ExoPlayer player;

    @Override public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        buildUi();
        loadAllChannels();
    }

    private int dp(float v) {
        return (int)(v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String s, float size, int color) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(color);
        return t;
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(9,11,15));

        browse = new LinearLayout(this);
        browse.setOrientation(LinearLayout.VERTICAL);
        browse.setPadding(dp(22), dp(18), dp(22), dp(12));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView title = text("LIVE TV", 28, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(50), 1));

        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Search channel");
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(Color.rgb(150,158,170));
        search.setTextSize(17);
        search.setPadding(dp(14), 0, dp(14), 0);
        search.setBackgroundColor(Color.rgb(28,33,42));
        search.setImeOptions(3);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(300), dp(48));
        header.addView(search, sp);

        Button refresh = new Button(this);
        refresh.setText("REFRESH");
        refresh.setTextSize(13);
        refresh.setOnClickListener(v -> loadAllChannels());
        header.addView(refresh, new LinearLayout.LayoutParams(dp(120), dp(48)));

        browse.addView(header);

        status = text("Loading channels...", 15, Color.rgb(165,174,187));
        status.setPadding(0, dp(7), 0, dp(8));
        browse.addView(status);

        grid = new RecyclerView(this);
        grid.setHasFixedSize(true);
        grid.setLayoutManager(new GridLayoutManager(this, 5));
        adapter = new ChannelAdapter();
        grid.setAdapter(adapter);
        browse.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));

        search.setOnEditorActionListener((v, actionId, event) -> {
            filter(search.getText().toString());
            return false;
        });
        search.setOnKeyListener((v, key, e) -> {
            if (key == KeyEvent.KEYCODE_DPAD_DOWN && e.getAction() == KeyEvent.ACTION_DOWN) {
                if (grid.getChildCount() > 0) { grid.getChildAt(0).requestFocus(); return true; }
            }
            return false;
        });

        root.addView(browse, new FrameLayout.LayoutParams(-1,-1));
        setContentView(root);

        // Player overlay.
        playerContainer = new FrameLayout(this);
        playerContainer.setBackgroundColor(Color.BLACK);
        playerView = new PlayerView(this);
        playerView.setUseController(true);
        playerContainer.addView(playerView, new FrameLayout.LayoutParams(-1,-1));
        playerContainer.setVisibility(View.GONE);
        root.addView(playerContainer, new FrameLayout.LayoutParams(-1,-1));

        playerContainer.setOnKeyListener((v,key,e) -> {
            if (key == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_UP) {
                closePlayer(); return true;
            }
            return false;
        });
    }

    private void loadAllChannels() {
        status.setText("Loading channel catalogue...");
        executor.execute(() -> {
            try {
                // Empty q asks V1 for the catalogue. If the API returns an object
                // containing a channels/results/data array, parseChannels handles it.
                String body = get(API);
                ArrayList<Channel> parsed = parseChannels(body);
                runOnUiThread(() -> {
                    allChannels.clear(); allChannels.addAll(parsed);
                    filter("");
                    status.setText(parsed.size() + " channels");
                    if (grid.getChildCount() > 0) grid.getChildAt(0).requestFocus();
                });
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("Could not load channels: " + e.getMessage()));
            }
        });
    }

    private void filter(String q) {
        shownChannels.clear();
        String x = q == null ? "" : q.trim().toLowerCase(Locale.US);
        for (Channel c : allChannels) {
            if (x.length() == 0 || c.name.toLowerCase(Locale.US).contains(x))
                shownChannels.add(c);
        }
        adapter.notifyDataSetChanged();
        if (status != null) status.setText(shownChannels.size() + " channels");
    }

    private String get(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(u).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(20000);
        c.setRequestMethod("GET");
        c.setRequestProperty("Accept","application/json");
        InputStream in = c.getResponseCode() < 400 ? c.getInputStream() : c.getErrorStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        StringBuilder b = new StringBuilder(); String line;
        while((line=br.readLine())!=null) b.append(line);
        br.close(); c.disconnect();
        return b.toString();
    }

    private ArrayList<Channel> parseChannels(String s) throws Exception {
        ArrayList<Channel> out = new ArrayList<>();
        Object root = new org.json.JSONTokener(s).nextValue();
        JSONArray a = null;
        if (root instanceof JSONArray) a = (JSONArray)root;
        else if (root instanceof JSONObject) {
            JSONObject o=(JSONObject)root;
            String[] keys={"channels","results","data","items"};
            for(String k:keys) if(o.opt(k) instanceof JSONArray){a=o.optJSONArray(k);break;}
            if(a==null && (o.has("id")||o.has("channel_id"))) { a=new JSONArray(); a.put(o); }
        }
        if(a==null) return out;
        for(int i=0;i<a.length();i++){
            JSONObject o=a.optJSONObject(i); if(o==null) continue;
            String id=o.optString("id",o.optString("channel_id",o.optString("channelId","")));
            String name=o.optString("name",o.optString("title",o.optString("channel_name","Channel "+(i+1))));
            if(id.length()>0) out.add(new Channel(id,name));
        }
        return out;
    }

    private void playChannel(Channel c) {
        status.setText("Loading " + c.name + "...");
        executor.execute(() -> {
            try {
                String body=get(API+"/"+Uri.encode(c.id));
                JSONObject o=new JSONObject(body);
                String stream=findStream(o);
                if(stream==null) throw new Exception("No V1 stream returned");
                runOnUiThread(() -> startPlayer(stream,c.name));
            } catch(Exception e) {
                runOnUiThread(() -> Toast.makeText(this,"Cannot play "+c.name+": "+e.getMessage(),Toast.LENGTH_LONG).show());
            }
        });
    }

    private String findStream(Object o) {
        if(o instanceof JSONObject){
            JSONObject j=(JSONObject)o;
            String[] keys={"playable","url","stream_url","streamUrl","manifest","mpd","manifest_url"};
            for(String k:keys) if(j.opt(k) instanceof String && j.optString(k).length()>8) return j.optString(k);
            if(j.opt("stream") instanceof String) return j.optString("stream");
            java.util.Iterator<String> it=j.keys();
            while(it.hasNext()){String k=it.next(); String r=findStream(j.opt(k)); if(r!=null)return r;}
        } else if(o instanceof JSONArray){
            JSONArray a=(JSONArray)o;
            for(int i=0;i<a.length();i++){String r=findStream(a.opt(i));if(r!=null)return r;}
        }
        return null;
    }

    private void startPlayer(String url,String name) {
        if(player!=null) player.release();
        player=new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.setMediaItem(new MediaItem.Builder()
                .setUri(url)
                .setMimeType(MimeTypes.APPLICATION_MPD)
                .build());
        player.prepare(); player.play();
        playerContainer.setVisibility(View.VISIBLE);
        playerContainer.requestFocus();
    }

    private void closePlayer() {
        if(player!=null){player.stop();player.release();player=null;}
        playerView.setPlayer(null);
        playerContainer.setVisibility(View.GONE);
        if(grid.getChildCount()>0) grid.getChildAt(0).requestFocus();
    }

    @Override public void onBackPressed() {
        if(playerContainer!=null && playerContainer.getVisibility()==View.VISIBLE) closePlayer();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if(player!=null) player.release();
        executor.shutdownNow();
        super.onDestroy();
    }

    private static class Channel {
        String id,name; Channel(String i,String n){id=i;name=n;}
    }

    private class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.Holder> {
        @Override public Holder onCreateViewHolder(android.view.ViewGroup p,int v) {
            TextView t=new TextView(MainActivity.this);
            t.setTextColor(Color.WHITE); t.setTextSize(18); t.setGravity(android.view.Gravity.CENTER_VERTICAL);
            t.setPadding(dp(16),dp(12),dp(16),dp(12)); t.setFocusable(true); t.setClickable(true);
            t.setBackgroundResource(com.livetvbox.R.drawable.card_bg);
            RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(-1,dp(74));
            lp.setMargins(dp(5),dp(5),dp(5),dp(5)); t.setLayoutParams(lp);
            return new Holder(t);
        }
        @Override public void onBindViewHolder(Holder h,int pos) {
            Channel c=shownChannels.get(pos);
            h.t.setText(c.name);
            h.t.setOnClickListener(v->playChannel(c));
            h.t.setOnKeyListener((v,key,e)->{
                if(key==KeyEvent.KEYCODE_DPAD_CENTER && e.getAction()==KeyEvent.ACTION_UP){playChannel(c);return true;}
                return false;
            });
        }
        @Override public int getItemCount(){return shownChannels.size();}
        class Holder extends RecyclerView.ViewHolder { TextView t; Holder(TextView v){super(v);t=v;} }
    }
}
