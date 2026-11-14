package com.sinoll.synapse_app.data.repository;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class ContentRepositoryInlineImagesTest {

    @Test
    public void replacesImagePathsAtTheirOriginalPositionsInOrder() {
        String content = "<p>before</p><img src=\"__SYNAPSE_IMAGE_0__\">"
                + "<p>between</p><img src=\"__SYNAPSE_IMAGE_1__\">";

        assertEquals("<p>before</p><img src=\"/uploads/blog/first.jpg\">"
                        + "<p>between</p><img src=\"/uploads/blog/second.jpg\">",
                ContentRepository.replaceImagePlaceholders(content, Arrays.asList(
                        "/uploads/blog/first.jpg", "/uploads/blog/second.jpg")));
    }

    @Test
    public void leavesImageFreeContentUnchanged() {
        String content = "<p>plain text</p>";

        assertEquals(content, ContentRepository.replaceImagePlaceholders(
                content, Collections.emptyList()));
    }
}
