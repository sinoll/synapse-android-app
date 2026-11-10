package com.sinoll.synapse_app.ui.content;

import com.sinoll.synapse_app.R;
import com.sinoll.synapse_app.data.model.ContentType;

public final class MomentFragment extends ContentListFragment {

    @Override
    protected ContentType initialContentType() {
        return ContentType.MOMENT;
    }

    @Override
    protected int contentHeadingResource() {
        return R.string.title_moments;
    }

    @Override
    protected int contentSubheadingResource() {
        return R.string.moments_subheading;
    }

    @Override
    protected boolean showsIntroHeader() {
        return false;
    }
}
