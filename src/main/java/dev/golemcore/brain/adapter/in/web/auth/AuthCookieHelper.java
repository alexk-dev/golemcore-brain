package dev.golemcore.brain.adapter.in.web.auth;

import dev.golemcore.brain.application.service.auth.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class AuthCookieHelper {

    public Optional<String> readSessionToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> AuthService.SESSION_COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }

    public void writeSessionToken(HttpServletResponse response, String token, long maxAgeSeconds) {
        Cookie cookie = new Cookie(AuthService.SESSION_COOKIE_NAME, token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge((int) maxAgeSeconds);
        response.addCookie(cookie);
    }

    public void clearSessionToken(HttpServletResponse response) {
        Cookie cookie = new Cookie(AuthService.SESSION_COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}
