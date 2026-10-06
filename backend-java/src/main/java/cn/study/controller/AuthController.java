package cn.study.controller;

import cn.study.service.AuthService;
import java.time.Duration;
import java.util.Map;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    public record Credentials(@NotBlank @Size(max=100) String username,@NotBlank @Size(max=72) String password) {}
    private final AuthService auth; private final boolean secure;
    public AuthController(AuthService auth,@Value("${auth.cookie-secure}") boolean secure) { this.auth=auth;this.secure=secure; }
    private String cookie(String value, Duration age) {
        return ResponseCookie.from("study_refresh",value).httpOnly(true).secure(secure).sameSite("Strict").path("/api/auth").maxAge(age).build().toString();
    }
    @PostMapping(value="/login", consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> login(@Valid @RequestBody Credentials input, HttpServletRequest request) {
        var result=auth.login(input.username(),input.password(),request.getRemoteAddr());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,cookie(result.refresh(),Duration.ofDays(auth.refreshDays))).body(result.body());
    }
    @PostMapping(value="/refresh", consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> refresh(@CookieValue(name="study_refresh",required=false) String token) {
        var result=auth.refresh(token);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,cookie(result.refresh(),Duration.ofDays(auth.refreshDays))).body(result.body());
    }
    @PostMapping(value="/logout", consumes=MediaType.APPLICATION_JSON_VALUE)
    public Map<String,Boolean> logout(@CookieValue(name="study_refresh",required=false) String token,HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE,cookie("",Duration.ZERO)); auth.logout(token);
        return Map.of("success",true);
    }
    @GetMapping("/me") public Map<String,Object> me() { return auth.me(); }
}
