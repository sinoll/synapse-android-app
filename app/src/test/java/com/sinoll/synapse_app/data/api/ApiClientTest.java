package com.sinoll.synapse_app.data.api;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ApiClientTest {

    private static ApiClient developmentClient() {
        return ApiClient.createForApplication("http://192.168.10.10/", true);
    }

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
        assertEquals("http://192.168.10.10/uploads/thumb/blog/cover.jpg",
                developmentClient().resolveMediaUrl(
                        "http://192.168.8.10/uploads/thumb/blog/cover.jpg"));
    }

    @Test
    public void developmentMediaPathUsesConfiguredApiHost() {
        assertEquals("http://192.168.10.10/uploads/thumb/blog/cover.jpg",
                developmentClient().resolveMediaUrl("uploads/thumb/blog/cover.jpg"));
    }

    @Test
    public void rejectsDevelopmentMediaUrlWithUserInfoOrFragment() {
        ApiClient client = developmentClient();

        assertNull(client.resolveMediaUrl("http://user@192.168.8.10/uploads/image.jpg"));
        assertNull(client.resolveMediaUrl("http://192.168.8.10/uploads/image.jpg#fragment"));
    }

    @Test
    public void invalidApplicationConfigurationFailsOnRequestInsteadOfConstruction() {
        ApiClient client = ApiClient.createForApplication("http://192.168.10.10/", false);

        assertNull(client.resolveMediaUrl("uploads/image.jpg"));
        try {
            client.get("blog", "", null);
            fail("Expected ApiException");
        } catch (ApiException error) {
            assertTrue(error.getMessage().contains("HTTPS"));
        } catch (java.io.IOException error) {
            fail("Configuration errors must not hit the network: " + error);
        }
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
