package vn.laishop.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(-200)
public class RequestFilter extends OncePerRequestFilter {
    private record Window(long minute, int requests) { }
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        String trace = UUID.randomUUID().toString(); request.setAttribute("traceId", trace); response.setHeader("X-Trace-Id", trace);
        String path = request.getRequestURI();
        if (path.startsWith("/api/v1/search") || path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/register")) {
            long minute = Instant.now().getEpochSecond() / 60;
            if (windows.size() > 10000) windows.entrySet().removeIf(e -> e.getValue().minute() < minute);
            String key = request.getRemoteAddr() + ":" + (path.contains("auth/") ? "auth" : "search");
            Window window = windows.compute(key, (k, old) -> old == null || old.minute() != minute ? new Window(minute, 1) : new Window(minute, old.requests() + 1));
            int limit = path.contains("auth/") ? 20 : 120;
            if (window.requests() > limit) {
                response.setStatus(429); response.setContentType("application/problem+json"); response.setHeader("Retry-After", "60");
                response.getWriter().write("{\"status\":429,\"code\":\"RATE_LIMITED\",\"detail\":\"Quá nhiều yêu cầu. Vui lòng thử lại sau một phút.\",\"traceId\":\"" + trace + "\"}"); return;
            }
        }
        chain.doFilter(request, response);
    }
}
