/*
 * Copyright 2026 Aleksei Kuleshov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contact: alex@kuleshov.tech
 */

package me.golemcore.brain.adapter.out.http;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Guards outbound HTTP requests against SSRF by rejecting URLs whose target
 * host resolves to a loopback, private, link-local, multicast or any-local
 * address. Callers must invoke {@link #requirePublicHttp(String)} before
 * issuing the request, and should disable HTTP redirect following on the
 * underlying client (the resolved address is not pinned, so a redirect could
 * point at a private host).
 */
@Component
public final class OutboundUrlGuard {

    private final boolean allowPrivateAddresses;

    public OutboundUrlGuard(@Value("${brain.outbound.allow-private-addresses:false}") boolean allowPrivateAddresses) {
        this.allowPrivateAddresses = allowPrivateAddresses;
    }

    public URI requirePublicHttp(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL is required");
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Malformed URL: " + exception.getMessage());
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Only http/https URLs are allowed");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL must include a host");
        }
        if (allowPrivateAddresses) {
            return uri;
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("Unable to resolve host: " + host);
        }
        if (addresses.length == 0) {
            throw new IllegalArgumentException("No addresses resolved for host: " + host);
        }
        for (InetAddress address : addresses) {
            if (isPrivateOrLocal(address)) {
                throw new IllegalArgumentException(
                        "Refusing to call non-public address " + address.getHostAddress() + " for host " + host);
            }
        }
        return uri;
    }

    private static boolean isPrivateOrLocal(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        if (address instanceof Inet4Address ipv4) {
            byte[] octets = ipv4.getAddress();
            int first = octets[0] & 0xff;
            int second = octets[1] & 0xff;
            // 100.64.0.0/10 — Carrier-grade NAT
            if (first == 100 && second >= 64 && second <= 127) {
                return true;
            }
            // 0.0.0.0/8 — "this" network (isAnyLocalAddress only matches 0.0.0.0 exactly)
            if (first == 0) {
                return true;
            }
            // 255.255.255.255 — limited broadcast
            if (first == 255 && second == 255 && (octets[2] & 0xff) == 255 && (octets[3] & 0xff) == 255) {
                return true;
            }
            return false;
        }
        if (address instanceof Inet6Address ipv6) {
            byte[] bytes = ipv6.getAddress();
            // fc00::/7 — Unique Local Addresses (ULA). Java InetAddress has no helper for
            // this.
            int firstOctet = bytes[0] & 0xff;
            if ((firstOctet & 0xfe) == 0xfc) {
                return true;
            }
            // ::ffff:a.b.c.d — IPv4-mapped IPv6 (10 zero bytes, two 0xff bytes, then a v4
            // address).
            // Java often resolves these as Inet4Address, but if a hex form like
            // ::ffff:7f00:1
            // arrives as Inet6Address we must recurse on the embedded v4.
            if (isIpv4Mapped(bytes)) {
                byte[] v4 = new byte[] { bytes[12], bytes[13], bytes[14], bytes[15] };
                try {
                    return isPrivateOrLocal(InetAddress.getByAddress(v4));
                } catch (UnknownHostException ignored) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isIpv4Mapped(byte[] bytes) {
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return (bytes[10] & 0xff) == 0xff && (bytes[11] & 0xff) == 0xff;
    }
}
