package com.sinoll.synapse_app.data.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ContentQueryTest {

    @Test
    public void usesApiDefaultsAndEncodesSearchFilters() {
        ContentQuery query = ContentQuery.builder()
                .search("拾光 app")
                .tag("#旅行#")
                .userId(7)
                .build();

        assertEquals(1, query.getPage());
        assertEquals(20, query.getPerPage());
        assertEquals("page=1&per_page=20&sort=created_at&order=desc&user_id=7"
                        + "&q=%E6%8B%BE%E5%85%89+app&tag=%23%E6%97%85%E8%A1%8C%23",
                query.toQueryString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsPageSizesAboveApiLimit() {
        ContentQuery.builder().perPage(101);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnsupportedSortFields() {
        ContentQuery.builder().sort("password");
    }
}
