package cn.study.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Date;
import javax.crypto.SecretKey;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokens {
    private final SecretKey key;
    private final Clock clock;
    public final long lifetimeSeconds;
    public JwtTokens(@Value("${auth.jwt-secret}") String secret, @Value("${auth.access-minutes}") int minutes, Clock clock) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32 || minutes < 1 || minutes > 120)
            throw new IllegalStateException("JWT_SECRET must have >=32 bytes; access lifetime must be 1..120 minutes");
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.clock = clock; lifetimeSeconds = minutes * 60L;
    }
    public String issue() {
        return issue(0);
    }
    public String issue(long version) {
        return Jwts.builder().subject(SingleUser.ID.toString()).issuer("civil-study")
            .claim("credentialVersion", version)
            .audience().add("civil-study-ui").and().issuedAt(Date.from(clock.instant()))
            .expiration(Date.from(clock.instant().plusSeconds(lifetimeSeconds)))
            .signWith(key, Jwts.SIG.HS256).compact();
    }
    public String verify(String token) {
        return claims(token).getSubject();
    }
    public long verifyVersion(String token) {
        Object version = claims(token).get("credentialVersion");
        if (version == null) return 0; // Sessions created before profile settings were introduced.
        if (!(version instanceof Number number) || number.longValue() < 0) throw new JwtException("Invalid credential version");
        return number.longValue();
    }
    private io.jsonwebtoken.Claims claims(String token) {
        var claims = Jwts.parser().verifyWith(key).requireIssuer("civil-study")
            .requireAudience("civil-study-ui").clock(() -> Date.from(clock.instant())).build()
            .parseSignedClaims(token).getPayload();
        if (!SingleUser.ID.toString().equals(claims.getSubject())) throw new JwtException("Invalid subject");
        return claims;
    }
}
