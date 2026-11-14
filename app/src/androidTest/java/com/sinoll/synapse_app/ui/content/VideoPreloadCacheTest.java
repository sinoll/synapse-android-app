package com.sinoll.synapse_app.ui.content;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class VideoPreloadCacheTest {

    @Test
    public void selectsFiveDistinctVideosAfterCurrentPosition() {
        List<String> urls = Arrays.asList(
                "/video/current.mp4",
                "/video/one.mp4",
                "",
                "/video/two.mp4",
                "/video/one.mp4",
                "/video/three.mp4",
                "/video/four.mp4",
                "/video/five.mp4",
                "/video/six.mp4");

        List<String> selected = VideoPreloadCache.selectUpcomingVideoUrls(urls, 0, 5);

        assertEquals(Arrays.asList(
                "/video/one.mp4",
                "/video/two.mp4",
                "/video/three.mp4",
                "/video/four.mp4",
                "/video/five.mp4"), selected);
        assertEquals(Collections.emptyList(),
                VideoPreloadCache.selectUpcomingVideoUrls(urls, urls.size() - 1, 5));
    }
}
