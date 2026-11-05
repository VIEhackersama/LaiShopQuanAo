package vn.laishop.auth;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import vn.laishop.common.ApiException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final CustomerRepository customers;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    public AuthController(CustomerRepository customers, JdbcTemplate jdbc, PasswordEncoder passwords) { this.customers=customers; this.jdbc=jdbc; this.passwords=passwords; }
    public record RegisterRequest(@NotBlank @Size(max=120) String fullName, @NotBlank @Email @Size(max=254) String email, @NotBlank @Size(min=10,max=72) String password) { }
    public record LoginRequest(@NotBlank @Email @Size(max=254) String email, @NotBlank @Size(max=72) String password) { }
    public record CustomerResponse(long id, String fullName, String email, String phone, String shippingAddress) { }
    public record CsrfResponse(String token, String headerName) { }
    public record LogoutResponse(boolean loggedOut) { }
    @GetMapping("/csrf") CsrfResponse csrf(CsrfToken token) { return new CsrfResponse(token.getToken(),token.getHeaderName()); }
    @PostMapping("/register") CustomerResponse register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request, HttpServletResponse response) {
        String email=body.email().strip().toLowerCase(Locale.ROOT);
        if (customers.findByEmail(email).isPresent()) throw ApiException.conflict("EMAIL_EXISTS", "Email đã được sử dụng.");
        if (body.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72) throw new ApiException(400,"PASSWORD_TOO_LONG","Mật khẩu tối đa 72 byte UTF-8.");
        Long id=jdbc.queryForObject("INSERT INTO customers(full_name,email,password_hash,created_at) VALUES (?,?,?,?) RETURNING id",Long.class,body.fullName().strip(),email,passwords.encode(body.password()),java.sql.Timestamp.from(Instant.now()));
        loginSession(id,request,response); return customer(id);
    }
    @PostMapping("/login") CustomerResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        var customer=customers.findByEmail(body.email().strip().toLowerCase(Locale.ROOT)).orElseThrow(() -> new ApiException(401,"INVALID_CREDENTIALS","Email hoặc mật khẩu không đúng."));
        if (!passwords.matches(body.password(),customer.getPasswordHash())) throw new ApiException(401,"INVALID_CREDENTIALS","Email hoặc mật khẩu không đúng.");
        loginSession(customer.getId(),request,response); return customer(customer.getId());
    }
    private void loginSession(long id, HttpServletRequest request, HttpServletResponse response) {
        request.getSession(); request.changeSessionId();
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(Long.toString(id),null,List.of()));
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context,request,response);
    }
    @GetMapping("/me") CustomerResponse me(Principal principal) { if(principal==null) throw new ApiException(401,"LOGIN_REQUIRED","Vui lòng đăng nhập."); return customer(Long.parseLong(principal.getName())); }
    @PostMapping("/logout") LogoutResponse logout(HttpServletRequest request) { var session=request.getSession(false); if(session!=null) session.invalidate(); SecurityContextHolder.clearContext(); return new LogoutResponse(true); }
    private CustomerResponse customer(long id) { return jdbc.queryForObject("SELECT id,full_name,email,phone,shipping_address FROM customers WHERE id=?",(r,n)->new CustomerResponse(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5)),id); }
}
