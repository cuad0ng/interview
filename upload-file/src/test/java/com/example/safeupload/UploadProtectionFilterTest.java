package com.example.safeupload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class UploadProtectionFilterTest {
    private static final String TOKEN = "0123456789abcdef0123456789abcdef";

    @Test
    void rejectsMissingBearerToken() throws Exception {
        var filter = new UploadProtectionFilter(TOKEN);
        var request = uploadRequest();
        var response = new MockHttpServletResponse();
        var called = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> called.set(true));

        assertEquals(401, response.getStatus());
        assertEquals("Bearer", response.getHeader(HttpHeaders.WWW_AUTHENTICATE));
        assertFalse(called.get());
    }

    @Test
    void protectsLargeUploadEndpoints() throws Exception {
        var filter = new UploadProtectionFilter(TOKEN);
        var request = new MockHttpServletRequest("POST", "/uploads/large");
        request.setServletPath("/uploads/large");
        var response = new MockHttpServletResponse();
        var called = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> called.set(true));

        assertEquals(401, response.getStatus());
        assertFalse(called.get());
    }

    @Test
    void allowsAuthorizedRequestsAndLimitsExcessUploads() throws Exception {
        var filter = new UploadProtectionFilter(TOKEN);
        for (int i = 0; i < 20; i++) {
            var response = new MockHttpServletResponse();
            filter.doFilter(authorizedRequest(), response, (req, res) -> {});
            assertEquals(200, response.getStatus());
        }

        var response = new MockHttpServletResponse();
        filter.doFilter(authorizedRequest(), response, (req, res) -> {});

        assertEquals(429, response.getStatus());
        assertEquals("60", response.getHeader(HttpHeaders.RETRY_AFTER));
    }

    private MockHttpServletRequest uploadRequest() {
        var request = new MockHttpServletRequest("POST", "/upload");
        request.setServletPath("/upload");
        return request;
    }

    private MockHttpServletRequest authorizedRequest() {
        var request = uploadRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);
        return request;
    }
}
