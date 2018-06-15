package com.pierfrancescosoffritti.youtubeplayer.player;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.pierfrancescosoffritti.youtubeplayer.R;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * WebView implementing the actual YouTube Player
 */
class WebViewYouTubePlayer extends WebView implements YouTubePlayer, YouTubePlayerBridge.YouTubePlayerBridgeCallbacks {

    @NonNull private final Set<YouTubePlayerListener> youTubePlayerListeners;
    @NonNull private final Handler mainThreadHandler;

    private YouTubePlayerInitListener youTubePlayerInitListener;

    protected WebViewYouTubePlayer(Context context) {
        this(context, null);
    }

    protected WebViewYouTubePlayer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    protected WebViewYouTubePlayer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mainThreadHandler = new Handler(Looper.getMainLooper());
        youTubePlayerListeners = new HashSet<>();
    }

    protected void initialize(@NonNull YouTubePlayerInitListener initListener) {
        youTubePlayerInitListener = initListener;

        initWebView();
    }

    @Override
    public void onYouTubeIframeAPIReady() {
        youTubePlayerInitListener.onInitSuccess(this);
    }

    @Override
    public void loadVideo(final String videoId, final float startSeconds) {
        mainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                loadUrl("javascript:loadVideo('" +videoId +"', " +startSeconds +")");
            }
        });
    }

    @Override
    public void cueVideo(final String videoId, final float startSeconds) {
        mainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                loadUrl("javascript:cueVideo('" +videoId +"', " +startSeconds +")");
            }
        });
    }

    @Override
    public void play() {
        mainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                loadUrl("javascript:playVideo()");
            }
        });
    }

    @Override
    public void pause() {
        mainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                loadUrl("javascript:pauseVideo()");
            }
        });
    }

    @Override
    public void setVolume(final int volumePercent) {
        if (volumePercent < 0 || volumePercent > 100)
            throw new IllegalArgumentException("Volume must be between 0 and 100");

        mainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                loadUrl("javascript:setVolume(" + volumePercent + ")");
            }
        });
    }

    @Override
    public void seekTo(final float time) {
        mainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                loadUrl("javascript:seekTo(" +time +")");
            }
        });
    }

    @Override
    public void destroy() {
        youTubePlayerListeners.clear();
        mainThreadHandler.removeCallbacksAndMessages(null);
        super.destroy();
    }

    @NonNull
    public Collection<YouTubePlayerListener> getListeners() {
        return Collections.unmodifiableCollection(new HashSet<>(youTubePlayerListeners));
    }

    @Override
    public boolean addListener(@NonNull YouTubePlayerListener listener) {
        if(listener == null) {
            Log.e("YouTubePlayer", "null YouTubePlayerListener not allowed.");
            return false;
        }

        return youTubePlayerListeners.add(listener);
    }

    @Override
    public boolean removeListener(@NonNull YouTubePlayerListener listener) {
        return youTubePlayerListeners.remove(listener);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void initWebView() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            throw new IllegalStateException("Should not use this class prior to API 17 due to security issues");
        }

        WebSettings settings = this.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setMediaPlaybackRequiresUserGesture(false);

        this.addJavascriptInterface(new YouTubePlayerBridge(this), "YouTubePlayerBridge");
        this.loadDataWithBaseURL("https://www.youtube.com", readYouTubePlayerHTMLFromFile(), "text/html", "utf-8", null);

        // if the video's thumbnail is not in memory, show a black screen
        this.setWebChromeClient(new WebChromeClient() {
            @Override
            public Bitmap getDefaultVideoPoster() {
                Bitmap result = super.getDefaultVideoPoster();

                if(result == null)
                    return Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565);
                else
                    return result;
            }
        });
    }

    private String readYouTubePlayerHTMLFromFile() {
        try {
            InputStream inputStream = getResources().openRawResource(R.raw.youtube_player);

            InputStreamReader inputStreamReader = new InputStreamReader(inputStream, "utf-8");
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

            String read;
            StringBuilder sb = new StringBuilder();

            while ( ( read = bufferedReader.readLine() ) != null )
                sb.append(read).append("\n");
            inputStream.close();

            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Can't parse HTML file containing the player.");
        }
    }
}
