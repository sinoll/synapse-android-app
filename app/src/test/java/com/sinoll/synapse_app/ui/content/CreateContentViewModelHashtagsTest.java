package com.sinoll.synapse_app.ui.content;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class CreateContentViewModelHashtagsTest {

    @Test
    public void extractsWrappedHashtagsInOrderAndRemovesDuplicates() {
        assertEquals(Arrays.asList("日常", "周末"),
                CreateContentViewModel.extractHashtags(
                        "今天 #日常#，记录一下 #周末#，又一个 #日常#"));
    }

    @Test
    public void ignoresEmptyOrUnclosedHashtags() {
        assertEquals(Collections.singletonList("有效"),
                CreateContentViewModel.extractHashtags("## #有效# #未结束"));
    }

    @Test
    public void returnsNoHashtagsForNullOrPlainText() {
        assertEquals(Collections.emptyList(),
                CreateContentViewModel.extractHashtags("普通正文"));
        assertEquals(Collections.emptyList(),
                CreateContentViewModel.extractHashtags(null));
    }
}
