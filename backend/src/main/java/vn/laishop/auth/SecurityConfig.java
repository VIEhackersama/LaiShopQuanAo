package vn.laishop.auth;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/v1/cart/**", "/api/v1/checkout", "/api/v1/orders/**").authenticated()
            .anyRequest().permitAll())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((req,res,ex) -> { res.setStatus(401); res.setContentType("application/problem+json"); res.getWriter().write("{\"status\":401,\"code\":\"LOGIN_REQUIRED\",\"detail\":\"Vui lòng đăng nhập.\",\"traceId\":\"" + req.getAttribute("traceId") + "\"}"); })
                .accessDeniedHandler((req,res,ex) -> { res.setStatus(403); res.setContentType("application/problem+json"); res.getWriter().write("{\"status\":403,\"code\":\"CSRF_INVALID\",\"detail\":\"Phiên xác thực yêu cầu đã hết hạn. Hãy tải lại trang.\",\"traceId\":\"" + req.getAttribute("traceId") + "\"}"); }))
            .formLogin(form -> form.disable()).httpBasic(basic -> basic.disable())
            .logout(logout -> logout.disable()).build();
    }
}
