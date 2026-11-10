package com.sinoll.synapse_app.ui.content;

import com.sinoll.synapse_app.R;
import com.sinoll.synapse_app.data.model.ContentType;

public final class GalleryFragment extends ContentListFragment {

    @Override
    protected ContentType initialContentType() {
        return ContentType.IMAGE_GALLERY;
    }

    @Override
    protected int contentHeadingResource() {
        return R.string.title_gallery;
    }

    @Override
    protected int contentSubheadingResource() {
        return R.string.gallery_subheading;
    }

    @Override
    protected boolean showsIntroHeader() {
        return false;
    }
}
