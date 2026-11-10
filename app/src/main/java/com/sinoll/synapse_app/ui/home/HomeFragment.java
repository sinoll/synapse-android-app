package com.sinoll.synapse_app.ui.home;

import com.sinoll.synapse_app.R;
import com.sinoll.synapse_app.data.model.ContentType;
import com.sinoll.synapse_app.ui.content.ContentListFragment;

public final class HomeFragment extends ContentListFragment {

    @Override
    protected ContentType initialContentType() {
        return ContentType.BLOG;
    }

    @Override
    protected int contentHeadingResource() {
        return R.string.title_blog;
    }

    @Override
    protected int contentSubheadingResource() {
        return R.string.blog_subheading;
    }

    @Override
    protected boolean showsIntroHeader() {
        return false;
    }
}