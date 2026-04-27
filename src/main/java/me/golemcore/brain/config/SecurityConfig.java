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

package me.golemcore.brain.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
public class SecurityConfig {

    @Value("${brain.security.csrf-enabled:true}")
    private boolean csrfEnabled;

    @Value("${brain.security.cookie-secure:true}")
    private boolean cookieSecure;

    /**
     * Application-wide CSP. Frontend is bundled and served from the same origin; no
     * inline scripts are produced by the React build, but Tailwind preflight may
     * emit inline style attributes — keep 'unsafe-inline' for style-src and tighten
     * as the markdown sanitizer matures.
     */
    private static final String CSP = "default-src 'self'; "
            + "img-src 'self' data: blob:; "
            + "media-src 'self' blob:; "
            + "style-src 'self' 'unsafe-inline'; "
            + "script-src 'self'; "
            + "connect-src 'self'; "
            + "font-src 'self' data:; "
            + "frame-ancestors 'none'; "
            + "base-uri 'self'; "
            + "form-action 'self'";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepo.setCookieCustomizer(c -> c.secure(cookieSecure).sameSite("Lax").path("/"));
        // Plain CsrfTokenRequestAttributeHandler (instead of
        // XorCsrfTokenRequestAttributeHandler)
        // is what allows the SPA to echo the XSRF-TOKEN cookie back as X-XSRF-TOKEN
        // unchanged.
        // setCsrfRequestAttributeName(null) keeps the resolver looking up "_csrf" only.
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);

        http
                .csrf(csrf -> {
                    if (!csrfEnabled) {
                        csrf.disable();
                        return;
                    }
                    csrf.csrfTokenRepository(csrfRepo)
                            .csrfTokenRequestHandler(csrfHandler)
                            .ignoringRequestMatchers(SecurityConfig::isBearerRequest);
                })
                .headers(headers -> headers
                        .frameOptions(FrameOptionsConfig::deny)
                        .contentTypeOptions(opts -> {
                        })
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000L))
                        .referrerPolicy(ref -> ref
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CSP)))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(f -> f.disable())
                .httpBasic(h -> h.disable())
                .logout(l -> l.disable())
                .anonymous(a -> a.disable())
                .requestCache(rc -> rc.disable())
                // Authorization is enforced by the application's own AuthContextResolver —
                // Spring
                // Security only provides CSRF + headers + filter ordering here.
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        if (csrfEnabled) {
            http.addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class);
        }

        return http.build();
    }

    private static boolean isBearerRequest(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ");
    }
}
