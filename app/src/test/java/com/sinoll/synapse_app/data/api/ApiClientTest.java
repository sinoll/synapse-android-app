package com.sinoll.synapse_app.data.api;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ApiClientTest {

    @Test
    public void acceptsHttpsApiBaseUrl() {
        assertNotNull(new ApiClient("https://192.168.10.10/"));
    }

    @Test
    public void applicationApiClientUsesConfiguredBaseUrl() {
        assertNotNull(ApiClient.forApplication());
    }

    @Test
    public void developmentMediaUrlUsesConfiguredApiHost() {
        ApiClient client = ApiClient.forApplication();

        assertEquals("http://192.168.8.10/uploads/thumb/blog/cover.jpg",
                client.resolveMediaUrl("http://192.168.8.10/uploads/thumb/blog/cover.jpg"));
    }

    @Test
    public void developmentMediaPathUsesConfiguredApiHost() {
        ApiClient client = ApiClient.forApplication();

        assertEquals("http://192.168.8.10/uploads/thumb/blog/cover.jpg",
                client.resolveMediaUrl("uploads/thumb/blog/cover.jpg"));
    }

    @Test
    public void rejectsDevelopmentMediaUrlWithUserInfoOrFragment() {
        ApiClient client = ApiClient.forApplication();

        assertNull(client.resolveMediaUrl("http://user@192.168.8.10/uploads/image.jpg"));
        assertNull(client.resolveMediaUrl("http://192.168.8.10/uploads/image.jpg#fragment"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsHttpApiBaseUrl() {
        new ApiClient("http://192.168.10.10/");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsApiBaseUrlWithoutAHost() {
        new ApiClient("https:/api");
    }
}
