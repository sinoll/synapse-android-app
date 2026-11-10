package com.sinoll.synapse_app.ui.home;

import com.sinoll.synapse_app.data.model.ContentType;
import com.sinoll.synapse_app.ui.content.ContentListFragment;

public final class HomeFragment extends ContentListFragment {

    @Override
    protected ContentType initialContentType() {
        return ContentType.BLOG;
    }

    @Override
    protected boolean showsTypeSelector() {
        return true;
    }

    @Override
    protected boolean showsIntroHeader() {
        return false;
    }
}