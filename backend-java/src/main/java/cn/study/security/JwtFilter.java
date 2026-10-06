package cn.study.security;

import java.io.IOException;
import java.util.List;
import cn.study.repository.Db;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtFilter extends OncePerRequestFilter {
    private final JwtTokens tokens;
    private final Db db;
    public JwtFilter(JwtTokens tokens, Db db) { this.tokens = tokens; this.db = db; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return java.util.Set.of("/api/auth/login","/api/auth/refresh","/api/auth/logout","/api/health","/actuator/health").contains(request.getRequestURI());
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null) {
            try {
                if (!header.startsWith("Bearer ")) throw new IllegalArgumentException();
                long version = tokens.verifyVersion(header.substring(7));
                if (version != db.count("select credential_version from users where id=?", SingleUser.ID)) throw new IllegalArgumentException();
                String subject = SingleUser.ID.toString();
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(subject, null, List.of()));
            } catch (RuntimeException error) {
                response.setStatus(401); response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":\"TOKEN_INVALID\",\"message\":\"登录已过期，请重新登录\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
