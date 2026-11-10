package com.sinoll.synapse_app.ui.profile;

import com.sinoll.synapse_app.data.model.ContentType;
import com.sinoll.synapse_app.ui.content.ContentListFragment;

public final class MyContentFragment extends ContentListFragment {

    public static final String ARG_USER_ID = "user_id";

    @Override
    protected ContentType initialContentType() {
        return ContentType.BLOG;
    }

    @Override
    protected boolean showsTypeSelector() {
        return true;
    }

    @Override
    protected boolean isOwnerContent() {
        return true;
    }
}
