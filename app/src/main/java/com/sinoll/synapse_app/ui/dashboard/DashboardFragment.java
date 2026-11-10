package com.sinoll.synapse_app.ui.dashboard;

import com.sinoll.synapse_app.data.model.ContentType;
import com.sinoll.synapse_app.ui.content.ContentListFragment;

public final class DashboardFragment extends ContentListFragment {

    @Override
    protected ContentType initialContentType() {
        return ContentType.SHORT_VIDEO;
    }
}