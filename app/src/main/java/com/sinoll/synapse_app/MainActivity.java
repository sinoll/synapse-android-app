package com.sinoll.synapse_app;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.NavigationUI;

import com.sinoll.synapse_app.R;
import com.sinoll.synapse_app.data.session.SessionStore;
import com.sinoll.synapse_app.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private static final String STATE_VIDEO_FULLSCREEN = "video_fullscreen";
    private static final String STATE_VERTICAL_VIDEO_FULLSCREEN =
            "vertical_video_fullscreen";

    private ActivityMainBinding binding;
    private boolean videoFullscreen;
    private boolean verticalVideoFullscreen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        NavController navController =
                Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupWithNavController(binding.navView, navController);
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int icon = destination.getId() == R.id.navigation_video
                    ? R.drawable.ic_add : R.drawable.ic_video;
            binding.navView.getMenu().findItem(R.id.navigation_video).setIcon(icon);
        });
        binding.navView.setOnNavigationItemReselectedListener(item -> {
            if (item.getItemId() == R.id.navigation_video
                    && navController.getCurrentDestination() != null
                    && navController.getCurrentDestination().getId() == R.id.navigation_video) {
                openCreateVideo(navController);
            }
        });
        if (savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_VIDEO_FULLSCREEN, false)) {
            setVideoFullscreen(true,
                    savedInstanceState.getBoolean(STATE_VERTICAL_VIDEO_FULLSCREEN, false));
        }
    }

    private void openCreateVideo(NavController navController) {
        String authorization = null;
        try {
            authorization = new SessionStore(this).getAuthorizationHeader();
        } catch (Exception error) {
            // Treat an unreadable local session as logged out, as on the video feed.
        }
        if (authorization == null) {
            Toast.makeText(this, R.string.content_create_login_required,
                    Toast.LENGTH_SHORT).show();
            navController.navigate(R.id.navigation_profile);
            return;
        }
        navController.navigate(R.id.action_global_create_video);
    }

    public void setVideoFullscreen(boolean fullscreen) {
        setVideoFullscreen(fullscreen, false);
    }

    public void setVideoFullscreen(boolean fullscreen, boolean verticalVideo) {
        boolean usePortrait = fullscreen && verticalVideo;
        if (videoFullscreen == fullscreen && verticalVideoFullscreen == usePortrait) {
            return;
        }
        videoFullscreen = fullscreen;
        verticalVideoFullscreen = usePortrait;
        binding.navView.setVisibility(fullscreen && !usePortrait ? View.GONE : View.VISIBLE);
        Window window = getWindow();
        if (fullscreen) {
            window.getDecorView().setSystemUiVisibility(usePortrait
                    ? View.SYSTEM_UI_FLAG_FULLSCREEN
                    : View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            setRequestedOrientation(usePortrait
                    ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }

    public boolean isVideoFullscreen() {
        return videoFullscreen;
    }

    public boolean isVerticalVideoFullscreen() {
        return verticalVideoFullscreen;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        binding.getRoot().requestLayout();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(STATE_VIDEO_FULLSCREEN, videoFullscreen);
        outState.putBoolean(STATE_VERTICAL_VIDEO_FULLSCREEN, verticalVideoFullscreen);
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