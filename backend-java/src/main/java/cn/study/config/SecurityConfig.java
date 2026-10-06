package cn.study.config;

import cn.study.repository.Db;
import cn.study.security.JwtFilter;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
    @Bean SecurityFilterChain chain(HttpSecurity http, JwtFilter jwt) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.requestMatchers("/api/health", "/actuator/health", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll().anyRequest().authenticated())
            .exceptionHandling(errors -> errors.authenticationEntryPoint((req,res,error) -> {
                res.setStatus(401); res.setContentType("application/json;charset=UTF-8");
                res.getWriter().write("{\"code\":\"AUTH_REQUIRED\",\"message\":\"请先登录\"}");
            }))
            .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class).build();
    }
    @Bean UserDetailsService users(Db db) {
        return username -> {
            var rows = db.rows("select username,password_hash from users where username=?", username);
            if (rows.isEmpty()) throw new UsernameNotFoundException("Account not found");
            var user = rows.getFirst();
            return User.withUsername(user.get("username").toString()).password(user.get("passwordHash").toString()).roles("USER").build();
        };
    }
}
