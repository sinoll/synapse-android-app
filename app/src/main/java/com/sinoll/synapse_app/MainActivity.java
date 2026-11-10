package com.sinoll.synapse_app;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.Navigation;
import androidx.navigation.ui.NavigationUI;

import com.sinoll.synapse_app.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private static final String STATE_VIDEO_FULLSCREEN = "video_fullscreen";

    private ActivityMainBinding binding;
    private boolean videoFullscreen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        NavigationUI.setupWithNavController(binding.navView,
                Navigation.findNavController(this, R.id.nav_host_fragment_activity_main));
        if (savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_VIDEO_FULLSCREEN, false)) {
            setVideoFullscreen(true);
        }
    }

    public void setVideoFullscreen(boolean fullscreen) {
        if (videoFullscreen == fullscreen) {
            return;
        }
        videoFullscreen = fullscreen;
        binding.navView.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        Window window = getWindow();
        if (fullscreen) {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }

    public boolean isVideoFullscreen() {
        return videoFullscreen;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        binding.getRoot().requestLayout();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(STATE_VIDEO_FULLSCREEN, videoFullscreen);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (videoFullscreen) {
            setVideoFullscreen(false);
            return;
        }
        super.onBackPressed();
    }
}