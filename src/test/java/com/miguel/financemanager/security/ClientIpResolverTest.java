package com.miguel.financemanager.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private static MockHttpServletRequest request(String remoteAddr, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }

    @Test
    void byDefault_ignoresForwardedFor() {
        ClientIpResolver resolver = new ClientIpResolver(false);

        assertThat(resolver.resolve(request("10.0.0.5", "203.0.113.9"))).isEqualTo("10.0.0.5");
    }

    @Test
    void whenTrusted_usesLastForwardedForValue() {
        ClientIpResolver resolver = new ClientIpResolver(true);

        assertThat(resolver.resolve(request("10.0.0.5", "203.0.113.9"))).isEqualTo("203.0.113.9");
        // Os valores anteriores vêm do cliente e podem ser inventados.
        assertThat(resolver.resolve(request("10.0.0.5", "1.2.3.4, 203.0.113.9"))).isEqualTo("203.0.113.9");
    }

    @Test
    void whenTrusted_fallsBackToRemoteAddr_ifHeaderMissingOrInvalid() {
        ClientIpResolver resolver = new ClientIpResolver(true);

        assertThat(resolver.resolve(request("10.0.0.5", null))).isEqualTo("10.0.0.5");
        assertThat(resolver.resolve(request("10.0.0.5", "   "))).isEqualTo("10.0.0.5");
        assertThat(resolver.resolve(request("10.0.0.5", "unknown"))).isEqualTo("10.0.0.5");
        assertThat(resolver.resolve(request("10.0.0.5", "example.com"))).isEqualTo("10.0.0.5");
        assertThat(resolver.resolve(request("10.0.0.5", "999.1.1.1"))).isEqualTo("10.0.0.5");
        assertThat(resolver.resolve(request("10.0.0.5", "x".repeat(5000)))).isEqualTo("10.0.0.5");
    }

    @Test
    void ipv6_isGroupedBySlash64() {
        ClientIpResolver resolver = new ClientIpResolver(true);

        String a = resolver.resolve(request("10.0.0.5", "2001:db8:1:2:aaaa::1"));
        String b = resolver.resolve(request("10.0.0.5", "2001:db8:1:2:ffff:ffff:ffff:ffff"));
        String other = resolver.resolve(request("10.0.0.5", "2001:db8:1:3::1"));

        assertThat(a).isEqualTo("2001:db8:1:2::/64").isEqualTo(b);
        assertThat(other).isNotEqualTo(a);
    }

    @Test
    void ipv4MappedIpv6_isTreatedAsIpv4() {
        assertThat(ClientIpResolver.normalize("::ffff:203.0.113.9")).isEqualTo("203.0.113.9");
    }

    @Test
    void invalidIpv6_isRejected() {
        assertThat(ClientIpResolver.normalize("1::g")).isNull();
        assertThat(ClientIpResolver.normalize("face:cafe")).isNull();
    }
}
