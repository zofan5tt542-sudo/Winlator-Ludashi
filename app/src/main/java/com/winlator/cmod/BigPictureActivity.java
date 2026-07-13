package com.winlator.cmod;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SnapHelper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.winlator.cmod.R;
import com.winlator.cmod.bigpicture.BigPictureAdapter;
import com.winlator.cmod.bigpicture.CarouselItemDecoration;
import com.winlator.cmod.bigpicture.StaticBackgroundView;
import com.winlator.cmod.bigpicture.steamgrid.SteamGridDBApi;
import com.winlator.cmod.bigpicture.steamgrid.SteamGridGridsResponse;
import com.winlator.cmod.bigpicture.steamgrid.SteamGridGridsResponseDeserializer;
import com.winlator.cmod.bigpicture.steamgrid.SteamGridSearchResponse;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class BigPictureActivity extends AppCompatActivity {
    @Override
    protected void attachBaseContext(Context newBase) {
        Locale locale = new Locale("en");
        Locale.setDefault(locale);

        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        config.setLocale(locale);
        config.setLayoutDirection(locale);

        super.attachBaseContext(newBase.createConfigurationContext(config));
    }
    
    private ImageView coverArtView;
    private TextView gameTitleView, graphicsDriverView, graphicsDriverVersionView, dxWrapperView, dxWrapperConfigView, audioDriverView, box64PresetView, playCountView, playtimeView;
    private RecyclerView recyclerView;
    private ContainerManager manager;
    private BigPictureAdapter adapter;
    private ImageButton playButton;

    private Shortcut currentShortcut;
    private int lastFocusedItemIndex = RecyclerView.NO_POSITION;

    private static String API_KEY = "0324c52513634547a7b32d6d323635d0";
    private static final String BASE_URL = "https://www.steamgriddb.com/api/v2/";

    private static final int REQUEST_CODE_UPLOAD_CUSTOM_COVER = 1069;
    private TextView uploadText;

    private TextView emptyStateTextView;

    private static final int REQUEST_CODE_SELECT_MP3 = 1070;
    private Uri selectedMp3Uri;

    private MediaPlayer mediaPlayer;
    private Handler musicHandler = new Handler(Looper.getMainLooper());

    private static final int REQUEST_CODE_SELECT_WALLPAPER = 1080;
    private static final String WALLPAPER_PREF_KEY = "custom_wallpaper_path";
    private static final String WALLPAPER_DISPLAY_PREF_KEY = "wallpaper_display_mode";

    private boolean isMusicPrepared = false;
    private boolean wasMusicPlaying = false;
    
    private boolean isActivityVisible = false;
    private boolean isMusicLoading = false;

    @Override
    protected void onStart() {
        super.onStart();
        isActivityVisible = true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        isActivityVisible = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        musicHandler.removeCallbacksAndMessages(null);
        releaseMediaPlayer();
        
        StaticBackgroundView backgroundView = findViewById(R.id.staticBackgroundView);
        if (backgroundView != null) {
            backgroundView.cleanup();
        }
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.reset();
                mediaPlayer.release();
            } catch (Exception e) {
                Log.e("MediaPlayer", "Error releasing MediaPlayer: " + e.getMessage());
            } finally {
                mediaPlayer = null;
                isMusicPrepared = false;
                isMusicLoading = false;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        getSupportActionBar().hide();
        setContentView(R.layout.big_picture_activity);

        // تهيئة الـ View
        initializeViews();
        
        // إعداد الخلفية
        setupBackground();
        
        // إعداد الموسيقى
        setupMusic();
        
        // إعداد القوائم
        setupLists();
        
        // تفعيل الوضع الملء
        enableImmersiveMode();
    }

    private void initializeViews() {
        coverArtView = findViewById(R.id.IVCoverArt);
        gameTitleView = findViewById(R.id.TVGameTitle);
        graphicsDriverView = findViewById(R.id.TVGraphicsDriver);
        graphicsDriverVersionView = findViewById(R.id.TVGraphicsDriverVersion);
        dxWrapperView = findViewById(R.id.TVDXWrapper);
        dxWrapperConfigView = findViewById(R.id.TVDXWrapperConfig);
        audioDriverView = findViewById(R.id.TVAudioDriver);
        box64PresetView = findViewById(R.id.TVBox64Preset);
        playCountView = findViewById(R.id.TVPlayCount);
        playtimeView = findViewById(R.id.TVPlaytime);
        recyclerView = findViewById(R.id.RecyclerView);
        playButton = findViewById(R.id.playButton);
        emptyStateTextView = findViewById(R.id.TVEmptyState);

        // تلوين الأيقونات
        Drawable playIcon = playButton.getDrawable();
        if (playIcon != null) {
            playIcon.mutate();
            playIcon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        }

        // إعداد الـ RecyclerView
        recyclerView.addItemDecoration(new CarouselItemDecoration(15));
        
        SnapHelper snapHelper = new LinearSnapHelper();
        snapHelper.attachToRecyclerView(recyclerView);
    }

    private void setupBackground() {
        Button selectWallpaperButton = findViewById(R.id.selectWallpaperButton);
        Button resetWallpaperButton = findViewById(R.id.resetWallpaperButton);

        selectWallpaperButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(intent, REQUEST_CODE_SELECT_WALLPAPER);
        });

        resetWallpaperButton.setOnClickListener(v -> {
            StaticBackgroundView backgroundView = findViewById(R.id.staticBackgroundView);
            if (backgroundView != null) {
                backgroundView.resetToDefaultWallpaper();
                
                SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
                editor.remove(WALLPAPER_PREF_KEY);
                editor.remove(WALLPAPER_DISPLAY_PREF_KEY);
                editor.apply();
                
                Toast.makeText(this, "Reset to default wallpaper", Toast.LENGTH_SHORT).show();
            }
        });

        // تطبيق الخلفية المحفوظة
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String savedWallpaperPath = prefs.getString(WALLPAPER_PREF_KEY, null);
        if (savedWallpaperPath != null) {
            File wallpaperFile = new File(savedWallpaperPath);
            if (wallpaperFile.exists()) {
                try {
                    String displayMode = prefs.getString(WALLPAPER_DISPLAY_PREF_KEY, "stretch");
                    applyWallpaper(Uri.fromFile(wallpaperFile), displayMode);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void setupMusic() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        Button disableBgMusicButton = findViewById(R.id.disableBgMusicButton);
        Button selectMp3Button = findViewById(R.id.selectMp3Button);
        Button resetMp3Button = findViewById(R.id.resetMp3Button);

        boolean isBgMusicEnabled = prefs.getBoolean("bg_music_enabled", true);
        updateBgMusicButtonText(disableBgMusicButton, isBgMusicEnabled);

        disableBgMusicButton.setOnClickListener(v -> {
            SharedPreferences prefs2 = PreferenceManager.getDefaultSharedPreferences(this);
            boolean currentBgMusicState = prefs2.getBoolean("bg_music_enabled", true);
            boolean newBgMusicState = !currentBgMusicState;

            prefs2.edit().putBoolean("bg_music_enabled", newBgMusicState).apply();
            updateBgMusicButtonText(disableBgMusicButton, newBgMusicState);

            if (newBgMusicState) {
                startBackgroundMusic();
            } else {
                stopAllMusic();
            }
        });

        selectMp3Button.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("audio/mpeg");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, REQUEST_CODE_SELECT_MP3);
        });

        resetMp3Button.setOnClickListener(v -> {
            if (isMusicLoading) {
                Toast.makeText(this, "Loading music...", Toast.LENGTH_SHORT).show();
                return;
            }
            
            isMusicLoading = true;
            stopAllMusic();
            releaseMediaPlayer();
            
            prefs.edit().remove("selected_mp3_path").apply();
            
            Toast.makeText(this, "Resetting to default music...", Toast.LENGTH_SHORT).show();
            
            musicHandler.postDelayed(() -> {
                playDefaultMp3FromAssets();
                isMusicLoading = false;
            }, 100);
        });

        String selectedMp3Path = prefs.getString("selected_mp3_path", null);

        if (isBgMusicEnabled) {
            if (selectedMp3Path != null) {
                File mp3File = new File(selectedMp3Path);
                if (mp3File.exists()) {
                    playMp3(mp3File);
                } else {
                    Log.e("BigPictureActivity", "MP3 file not found: " + selectedMp3Path);
                    playDefaultMp3FromAssets();
                }
            } else {
                playDefaultMp3FromAssets();
            }
        }
    }

    private void setupLists() {
        ImageButton settingsButton = findViewById(R.id.settingsButton);
        ImageButton backButton = findViewById(R.id.backButton);

        // تلوين الأيقونات
        Drawable settingsIcon = settingsButton.getDrawable();
        if (settingsIcon != null) {
            settingsIcon.mutate();
            settingsIcon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        }

        Drawable backIcon = backButton.getDrawable();
        if (backIcon != null) {
            backIcon.mutate();
            backIcon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        }

        settingsButton.setOnClickListener(v -> {
            if (findViewById(R.id.settingsLayout).getVisibility() == View.VISIBLE) {
                hideSettingsView();
            } else {
                showSettingsView();
            }
        });

        backButton.setOnClickListener(v -> {
            if (findViewById(R.id.settingsLayout).getVisibility() == View.VISIBLE) {
                hideSettingsView();
            } else {
                showSettingsView();
            }
        });

        // إعداد اللمس
        setupTouchListeners(settingsButton, backButton);

        // تحميل الـ Shortcuts
        manager = new ContainerManager(this);
        loadShortcutsList();

        playButton.setOnClickListener(v -> {
            if (currentShortcut != null) {
                wasMusicPlaying = isMusicPlaying();
                stopAllMusic();
                releaseMediaPlayer();
                runFromShortcut(currentShortcut);
            }
        });

        coverArtView.setOnClickListener(v -> {
            if (currentShortcut != null) {
                if (currentShortcut.getCustomCoverArtPath() != null) {
                    showCoverArtOptionsDialog();
                } else {
                    promptForCustomCoverArtUpload();
                }
            }
        });
    }

    private void setupTouchListeners(View... views) {
        for (View view : views) {
            view.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    v.requestFocus();
                    v.performClick();
                }
                return true;
            });
        }
    }

    @Override
    public void onBackPressed() {
        if (findViewById(R.id.settingsLayout).getVisibility() == View.VISIBLE) {
            hideSettingsView();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        if (!isMusicPlaying() && isActivityVisible && !isMusicLoading) {
            checkAndStartBackgroundMusic();
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        wasMusicPlaying = isMusicPlaying();
        stopAllMusic();
    }

    private void checkAndStartBackgroundMusic() {
        if (!isActivityVisible || isMusicLoading) return;
        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean enabled = prefs.getBoolean("bg_music_enabled", true);
        if (enabled && !isMusicPlaying()) {
            startBackgroundMusic();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isActivityVisible = true;
        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean isBgMusicEnabled = prefs.getBoolean("bg_music_enabled", true);
        if (isBgMusicEnabled) {
            startBackgroundMusic();
        }
        
        String savedWallpaperPath = prefs.getString(WALLPAPER_PREF_KEY, null);
        if (savedWallpaperPath != null) {
            File wallpaperFile = new File(savedWallpaperPath);
            if (wallpaperFile.exists()) {
                try {
                    String displayMode = prefs.getString(WALLPAPER_DISPLAY_PREF_KEY, "stretch");
                    applyWallpaper(Uri.fromFile(wallpaperFile), displayMode);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isActivityVisible = false;
        wasMusicPlaying = isMusicPlaying();
        stopAllMusic();
    }

    private void updateBgMusicButtonText(Button button, boolean isEnabled) {
        button.setText(isEnabled ? "Disable BG Music" : "Enable BG Music");
    }

    private void showSettingsView() {
        final LinearLayout mainLayout = findViewById(R.id.mainLayout);
        final LinearLayout settingsLayout = findViewById(R.id.settingsLayout);

        settingsLayout.setVisibility(View.VISIBLE);

        settingsLayout.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                settingsLayout.getViewTreeObserver().removeOnPreDrawListener(this);
                
                ObjectAnimator mainSlideOut = ObjectAnimator.ofFloat(mainLayout, "translationX", 0f, -mainLayout.getWidth());
                mainSlideOut.setInterpolator(new AccelerateDecelerateInterpolator());
                mainSlideOut.setDuration(500);
                mainSlideOut.start();

                ObjectAnimator settingsSlideIn = ObjectAnimator.ofFloat(settingsLayout, "translationX", settingsLayout.getWidth(), 0f);
                settingsSlideIn.setInterpolator(new AccelerateDecelerateInterpolator());
                settingsSlideIn.setDuration(500);
                settingsSlideIn.start();
                return true;
            }
        });
    }

    private void hideSettingsView() {
        LinearLayout mainLayout = findViewById(R.id.mainLayout);
        LinearLayout settingsLayout = findViewById(R.id.settingsLayout);

        ObjectAnimator mainSlideIn = ObjectAnimator.ofFloat(mainLayout, "translationX", -mainLayout.getWidth(), 0f);
        mainSlideIn.setInterpolator(new AccelerateDecelerateInterpolator());
        mainSlideIn.setDuration(500);
        mainSlideIn.start();

        ObjectAnimator settingsSlideOut = ObjectAnimator.ofFloat(settingsLayout, "translationX", 0f, settingsLayout.getWidth());
        settingsSlideOut.setInterpolator(new AccelerateDecelerateInterpolator());
        settingsSlideOut.setDuration(500);
        settingsSlideOut.start();
        settingsSlideOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                settingsLayout.setVisibility(View.GONE);
            }
        });
    }

    private void showCoverArtOptionsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Cover Art Options")
                .setItems(new CharSequence[]{"Remove Custom Cover Art", "Upload New Cover Art"}, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            removeCustomCoverArt();
                            break;
                        case 1:
                            promptForCustomCoverArtUpload();
                            break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void removeCustomCoverArt() {
        if (currentShortcut != null) {
            currentShortcut.removeCustomCoverArt();

            File cachedFile = new File(getCacheDir(), "coverArtCache/" + currentShortcut.name + ".png");
            if (cachedFile.exists()) {
                cachedFile.delete();
            }

            coverArtView.setImageResource(R.drawable.icon_action_bar_import);
            coverArtView.setBackgroundColor(Color.parseColor("#99000000"));

            loadShortcutData(currentShortcut);
        }
    }

    private void promptForCustomCoverArtUpload() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_CODE_UPLOAD_CUSTOM_COVER);
    }

    private void enableImmersiveMode() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    private int getCenterItemPosition() {
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();
        int lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition();

        int centerPosition = RecyclerView.NO_POSITION;
        float closestToCenter = Float.MAX_VALUE;
        int recyclerViewCenter = recyclerView.getWidth() / 2;

        for (int i = firstVisibleItemPosition; i <= lastVisibleItemPosition; i++) {
            if (i >= 0) {
                View itemView = layoutManager.findViewByPosition(i);
                if (itemView != null) {
                    int itemCenter = (itemView.getLeft() + itemView.getRight()) / 2;
                    float distanceFromCenter = Math.abs(recyclerViewCenter - itemCenter);

                    if (distanceFromCenter < closestToCenter) {
                        closestToCenter = distanceFromCenter;
                        centerPosition = i;
                    }
                }
            }
        }

        return centerPosition;
    }

    private void loadShortcutsList() {
        List<Shortcut> shortcuts = manager.loadShortcuts();

        if (shortcuts.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            playButton.setVisibility(View.GONE);
            emptyStateTextView.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyStateTextView.setVisibility(View.GONE);

            adapter = new BigPictureAdapter(shortcuts, recyclerView);
            recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            recyclerView.setAdapter(adapter);

            loadShortcutData(shortcuts.get(0));
        }
    }

    public void loadShortcutData(Shortcut shortcut) {
        currentShortcut = shortcut;

        gameTitleView.setText(shortcut.name);

        SharedPreferences playtimePrefs = getSharedPreferences("playtime_stats", Context.MODE_PRIVATE);
        long totalPlaytime = playtimePrefs.getLong(shortcut.name + "_playtime", 0);
        int playCount = playtimePrefs.getInt(shortcut.name + "_play_count", 0);
        playCountView.setText("Times Played: " + playCount);
        playtimeView.setText("Playtime: " + formatPlaytime(totalPlaytime));

        Container container = manager.getContainerForShortcut(shortcut);
        String graphicsDriver = shortcut.getExtra("graphicsDriver");
        
        setTextOrPlaceholder(graphicsDriverView, graphicsDriver, container.getGraphicsDriver());
        setTextOrPlaceholder(graphicsDriverVersionView, shortcut.getExtra("graphicsDriverConfig"), container.getGraphicsDriverConfig());
        setTextOrPlaceholder(dxWrapperView, shortcut.getExtra("dxwrapper"), container.getDXWrapper());
        setTextOrPlaceholder(dxWrapperConfigView, shortcut.getExtra("dxwrapperConfig"), container.getDXWrapperConfig());
        setTextOrPlaceholder(audioDriverView, shortcut.getExtra("audioDriver"), container.getAudioDriver());
        setTextOrPlaceholder(box64PresetView, shortcut.getExtra("box64Preset"), container.getBox64Preset());

        Bitmap coverArt = null;
        if (shortcut.getCustomCoverArtPath() != null && !shortcut.getCustomCoverArtPath().isEmpty()) {
            coverArt = BitmapFactory.decodeFile(shortcut.getCustomCoverArtPath());
        }

        if (coverArt == null) {
            coverArt = loadCachedCoverArt(shortcut.name);
        }

        if (coverArt != null) {
            coverArtView.setImageBitmap(coverArt);
        } else {
            coverArtView.setImageResource(R.drawable.cover_art_placeholder);
            fetchCoverArt(shortcut);
        }
    }

    private void runFromShortcut(Shortcut shortcut) {
        stopAllMusic();
        releaseMediaPlayer();
        
        Intent intent = new Intent(this, XServerDisplayActivity.class);
        intent.putExtra("container_id", shortcut.container.id);
        intent.putExtra("shortcut_path", shortcut.file.getPath());
        intent.putExtra("shortcut_name", shortcut.name);
        String disableXinputValue = shortcut.getExtra("disableXinput", "0");
        intent.putExtra("disableXinput", disableXinputValue);
        startActivity(intent);
    }

    private void setTextOrPlaceholder(TextView textView, String shortcutValue, String containerValue) {
        if (shortcutValue != null && !shortcutValue.isEmpty()) {
            textView.setText(shortcutValue);
        } else if (containerValue != null && !containerValue.isEmpty()) {
            textView.setText(containerValue);
        } else {
            textView.setText("Not Set");
        }
    }

    private void fetchCoverArt(Shortcut shortcut) {
        boolean isCustomApiKeyEnabled = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("enable_custom_api_key", false);
        
        if (isCustomApiKeyEnabled) {
            String customApiKey = PreferenceManager.getDefaultSharedPreferences(this)
                    .getString("custom_api_key", "");
            if (customApiKey != null && !customApiKey.isEmpty()) {
                API_KEY = customApiKey;
            }
        }

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(new OkHttpClient())
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        SteamGridDBApi api = retrofit.create(SteamGridDBApi.class);
        Call<SteamGridSearchResponse> call = api.searchGame("Bearer " + API_KEY, shortcut.name);

        call.enqueue(new Callback<SteamGridSearchResponse>() {
            @Override
            public void onResponse(Call<SteamGridSearchResponse> call, Response<SteamGridSearchResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<SteamGridSearchResponse.GameData> gameData = response.body().data;
                    if (gameData != null && !gameData.isEmpty()) {
                        fetchGridsForGame(gameData.get(0).id, shortcut);
                    } else {
                        showCustomCoverArtUploadOption(shortcut);
                    }
                } else {
                    showCustomCoverArtUploadOption(shortcut);
                }
            }

            @Override
            public void onFailure(Call<SteamGridSearchResponse> call, Throwable t) {
                showCustomCoverArtUploadOption(shortcut);
            }
        });
    }

    private void showCustomCoverArtUploadOption(Shortcut shortcut) {
        runOnUiThread(() -> {
            coverArtView.setImageResource(android.R.color.transparent);
            coverArtView.setBackgroundColor(Color.parseColor("#99000000"));
            coverArtView.setImageResource(R.drawable.cover_art_placeholder);

            if (uploadText != null) {
                ViewGroup parent = (ViewGroup) uploadText.getParent();
                if (parent != null) {
                    parent.removeView(uploadText);
                }
            }

            uploadText = new TextView(this);
            uploadText.setText("");
            uploadText.setTextColor(Color.WHITE);
            uploadText.setTextSize(18);
            uploadText.setPadding(20, 20, 20, 20);
            uploadText.setGravity(Gravity.CENTER);
            uploadText.setBackgroundColor(Color.parseColor("#99000000"));

            ViewGroup parent = (ViewGroup) coverArtView.getParent();
            parent.addView(uploadText);
        });
    }

    private void fetchGridsForGame(int gameId, Shortcut shortcut) {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(SteamGridGridsResponse.class, new SteamGridGridsResponseDeserializer())
                .setPrettyPrinting()
                .create();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(new OkHttpClient())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        SteamGridDBApi api = retrofit.create(SteamGridDBApi.class);

        Call<SteamGridGridsResponse> gridsCall = api.getGridsByGameId("Bearer " + API_KEY, gameId, "alternate", "600x900", "static");

        gridsCall.enqueue(new Callback<SteamGridGridsResponse>() {
            @Override
            public void onResponse(Call<SteamGridGridsResponse> call, Response<SteamGridGridsResponse> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().data.isEmpty()) {
                    downloadCoverArt(response.body().data.get(0).url, shortcut);
                }
            }

            @Override
            public void onFailure(Call<SteamGridGridsResponse> call, Throwable t) { }
        });
    }

    private void downloadCoverArt(String url, Shortcut shortcut) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                connection.connect();
                InputStream input = connection.getInputStream();
                Bitmap coverArt = BitmapFactory.decodeStream(input);

                cacheCoverArt(coverArt, shortcut.name);

                runOnUiThread(() -> coverArtView.setImageBitmap(coverArt));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void cacheCoverArt(Bitmap coverArt, String shortcutName) {
        try {
            File cacheDir = new File(getCacheDir(), "coverArtCache");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            File coverFile = new File(cacheDir, shortcutName + ".png");
            FileOutputStream outputStream = new FileOutputStream(coverFile);
            coverArt.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            outputStream.flush();
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Bitmap loadCachedCoverArt(String shortcutName) {
        try {
            File cacheDir = new File(getCacheDir(), "coverArtCache");
            File coverFile = new File(cacheDir, shortcutName + ".png");
            if (coverFile.exists()) {
                return BitmapFactory.decodeFile(coverFile.getAbsolutePath());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_SELECT_WALLPAPER && resultCode == RESULT_OK && data != null) {
            handleWallpaperSelection(data);
        } else if (requestCode == REQUEST_CODE_SELECT_MP3 && resultCode == RESULT_OK && data != null) {
            handleMp3Selection(data);
        } else if (requestCode == REQUEST_CODE_UPLOAD_CUSTOM_COVER && resultCode == RESULT_OK && data != null) {
            handleCoverArtUpload(data);
        }
    }

    private void handleWallpaperSelection(Intent data) {
        Uri selectedImageUri = data.getData();

        try {
            InputStream inputStream = getContentResolver().openInputStream(selectedImageUri);
            Bitmap wallpaper = BitmapFactory.decodeStream(inputStream);
            if (wallpaper != null) {
                File wallpaperFile = new File(getFilesDir(), "custom_bg.png");
                FileOutputStream outputStream = new FileOutputStream(wallpaperFile);
                wallpaper.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                outputStream.flush();
                outputStream.close();

                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString(WALLPAPER_PREF_KEY, wallpaperFile.getAbsolutePath());
                editor.apply();

                String[] displayOptions = {"Stretch (ملء الشاشة)", "Center (منتصف)", "Tile (تكرار)"};
                new AlertDialog.Builder(this)
                        .setTitle("اختر طريقة العرض")
                        .setItems(displayOptions, (dialog, which) -> {
                            String mode;
                            switch (which) {
                                case 0:
                                    mode = "stretch";
                                    break;
                                case 1:
                                    mode = "center";
                                    break;
                                case 2:
                                    mode = "tile";
                                    break;
                                default:
                                    mode = "stretch";
                            }
                            
                            editor.putString(WALLPAPER_DISPLAY_PREF_KEY, mode);
                            editor.apply();

                            try {
                                applyWallpaper(Uri.fromFile(wallpaperFile), mode);
                            } catch (FileNotFoundException e) {
                                e.printStackTrace();
                            }
                        })
                        .show();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleMp3Selection(Intent data) {
        selectedMp3Uri = data.getData();

        File appStorageDir = getFilesDir();
        File musicFile = new File(appStorageDir, "bigpicturemode_bgmusic.mp3");

        if (FileUtils.copy(this, selectedMp3Uri, musicFile, null)) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
            preferences.edit().putString("selected_mp3_path", musicFile.getAbsolutePath()).apply();

            stopAllMusic();
            releaseMediaPlayer();
            
            musicHandler.postDelayed(() -> playMp3(musicFile), 100);
        } else {
            Log.e("BigPictureActivity", "Failed to copy the MP3 file.");
        }
    }

    private void handleCoverArtUpload(Intent data) {
        Uri selectedImageUri = data.getData();
        try {
            InputStream inputStream = getContentResolver().openInputStream(selectedImageUri);
            Bitmap customCoverArt = BitmapFactory.decodeStream(inputStream);

            if (currentShortcut != null) {
                currentShortcut.saveCustomCoverArt(customCoverArt);
                cacheCoverArt(customCoverArt, currentShortcut.name);
                coverArtView.setImageBitmap(customCoverArt);

                if (uploadText != null) {
                    uploadText.setVisibility(View.GONE);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void applyWallpaper(Uri wallpaperUri, String mode) throws FileNotFoundException {
        StaticBackgroundView backgroundView = findViewById(R.id.staticBackgroundView);
        if (backgroundView != null && wallpaperUri != null) {
            Bitmap wallpaper = BitmapFactory.decodeStream(getContentResolver().openInputStream(wallpaperUri));
            if (wallpaper != null) {
                backgroundView.setWallpaper(wallpaper, mode);
            }
        }
    }

    private void playMp3(File mp3File) {
        if (!isActivityVisible || mp3File == null || isMusicLoading) return;
        
        isMusicLoading = true;
        
        try {
            releaseMediaPlayer();
            
            mediaPlayer = new MediaPlayer();
            
            FileInputStream fis = new FileInputStream(mp3File);
            mediaPlayer.setDataSource(fis.getFD());
            fis.close();

            mediaPlayer.setOnPreparedListener(mp -> {
                if (isActivityVisible) {
                    mp.start();
                    isMusicPrepared = true;
                }
                isMusicLoading = false;
            });
            
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e("MediaPlayer", "Error: " + what + ", " + extra);
                isMusicLoading = false;
                releaseMediaPlayer();
                return true;
            });
            
            mediaPlayer.setLooping(true);
            mediaPlayer.prepareAsync();
            
        } catch (IOException e) {
            Log.e("MediaPlayer", "Error playing MP3: " + e.getMessage());
            isMusicLoading = false;
            releaseMediaPlayer();
        }
    }

    private void playDefaultMp3FromAssets() {
        if (!isActivityVisible || isMusicLoading) return;
        
        isMusicLoading = true;
        
        try {
            releaseMediaPlayer();
            
            mediaPlayer = new MediaPlayer();
            
            AssetFileDescriptor afd = getAssets().openFd("default_music.mp3");
            mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();

            mediaPlayer.setOnPreparedListener(mp -> {
                if (isActivityVisible) {
                    mp.start();
                    isMusicPrepared = true;
                    Log.d("MediaPlayer", "Default music started successfully");
                }
                isMusicLoading = false;
            });
            
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e("MediaPlayer", "Error playing default: " + what + ", " + extra);
                isMusicLoading = false;
                releaseMediaPlayer();
                return true;
            });
            
            mediaPlayer.setLooping(true);
            mediaPlayer.prepareAsync();
            
        } catch (IOException e) {
            Log.e("MediaPlayer", "Error playing default MP3: " + e.getMessage());
            isMusicLoading = false;
            releaseMediaPlayer();
        }
    }

    private String formatPlaytime(long playtimeInMillis) {
        long seconds = (playtimeInMillis / 1000) % 60;
        long minutes = (playtimeInMillis / (1000 * 60)) % 60;
        long hours = (playtimeInMillis / (1000 * 60 * 60)) % 24;
        long days = (playtimeInMillis / (1000 * 60 * 60 * 24));

        return String.format("%dd %02dh %02dm %02ds", days, hours, minutes, seconds);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            View currentFocus = getCurrentFocus();

            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (currentFocus == recyclerView) {
                        playButton.requestFocus();
                        return true;
                    } else if (currentFocus == playButton) {
                        graphicsDriverView.requestFocus();
                        return true;
                    } else if (currentFocus != coverArtView) {
                        playButton.requestFocus();
                        return true;
                    }
                    break;

                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (currentFocus == playButton) {
                        focusClosestCarouselItem();
                        return true;
                    }
                    break;

                case KeyEvent.KEYCODE_BUTTON_A:
                    if (currentFocus == playButton) {
                        playButton.performClick();
                        return true;
                    } else if (currentFocus == coverArtView) {
                        coverArtView.performClick();
                        return true;
                    }
                    break;
                    
                case KeyEvent.KEYCODE_BUTTON_R1:
                case KeyEvent.KEYCODE_BUTTON_R2:
                    if (findViewById(R.id.settingsLayout).getVisibility() == View.VISIBLE) {
                        hideSettingsView();
                    } else {
                        showSettingsView();
                    }
                    return true;
            }
        }

        return super.dispatchKeyEvent(event);
    }

    private void focusClosestCarouselItem() {
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();
        int lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition();

        int closestPosition = RecyclerView.NO_POSITION;
        float closestDistance = Float.MAX_VALUE;

        int recyclerViewCenter = recyclerView.getWidth() / 2;

        for (int i = firstVisibleItemPosition; i <= lastVisibleItemPosition; i++) {
            if (i >= 0) {
                View itemView = layoutManager.findViewByPosition(i);
                if (itemView != null) {
                    int itemCenter = (itemView.getLeft() + itemView.getRight()) / 2;
                    float distanceFromCenter = Math.abs(recyclerViewCenter - itemCenter);

                    if (distanceFromCenter < closestDistance) {
                        closestDistance = distanceFromCenter;
                        closestPosition = i;
                    }
                }
            }
        }

        if (closestPosition != RecyclerView.NO_POSITION) {
            recyclerView.scrollToPosition(closestPosition);
            View itemView = layoutManager.findViewByPosition(closestPosition);
            if (itemView != null) {
                itemView.requestFocus();
            }
        }
    }

    private void startBackgroundMusic() {
        if (!isActivityVisible || isMusicLoading) return;
        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean enabled = prefs.getBoolean("bg_music_enabled", true);
        if (!enabled) {
            stopAllMusic();
            return;
        }

        String path = prefs.getString("selected_mp3_path", null);
        
        if (path != null) {
            File mp3 = new File(path);
            if (mp3.exists()) {
                playMp3(mp3);
            } else {
                playDefaultMp3FromAssets();
            }
        } else {
            playDefaultMp3FromAssets();
        }
    }

    private void stopAllMusic() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                }
            } catch (Exception e) {
                Log.e("MediaPlayer", "Error pausing: " + e.getMessage());
            }
        }
    }

    private boolean isMusicPlaying() {
        if (mediaPlayer != null) {
            try {
                return mediaPlayer.isPlaying();
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }
    }
