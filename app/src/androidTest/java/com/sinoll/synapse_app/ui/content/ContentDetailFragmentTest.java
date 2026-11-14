package com.sinoll.synapse_app.ui.content;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class ContentDetailFragmentTest {

    @Test
    public void bodyDocumentRetainsAdjacentImagesAsResponsiveBlocks() {
        String body = "<img src=\"/uploads/first.jpg\"><img src=\"/uploads/second.jpg\">";

        String document = ContentDetailFragment.buildBodyDocument(body, "#27243A");

        assertTrue(document.contains(body));
        assertTrue(document.contains("img{display:block!important;max-width:100%!important;"));
        assertTrue(document.contains("height:auto!important;"));
    }

    @Test
    public void hidesSeparateThumbnailsForMomentAndBlogDetails() {
        assertFalse(ContentDetailFragment.shouldShowGallery("moment"));
        assertFalse(ContentDetailFragment.shouldShowGallery("blog"));
        assertTrue(ContentDetailFragment.shouldShowGallery("images"));
    }

    @Test
    public void rendersGalleryBodyWithWebView() {
        assertTrue(ContentDetailFragment.usesWebView("images"));
        assertTrue(ContentDetailFragment.usesWebView("blog"));
        assertTrue(ContentDetailFragment.usesWebView("moment"));
        assertFalse(ContentDetailFragment.usesWebView("short_video"));
    }
}
