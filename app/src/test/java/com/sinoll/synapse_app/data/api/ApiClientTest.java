package com.sinoll.synapse_app.data.api;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class ApiClientTest {

    @Test
    public void acceptsHttpsApiBaseUrl() {
        assertNotNull(new ApiClient("https://192.168.10.10/"));
    }

    @Test
    public void applicationApiClientUsesConfiguredBaseUrl() {
        assertNotNull(ApiClient.forApplication());
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
