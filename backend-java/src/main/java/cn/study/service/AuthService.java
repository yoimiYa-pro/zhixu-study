package cn.study.service;

import cn.study.repository.Db;
import cn.study.security.JwtTokens;
import cn.study.security.SingleUser;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Duration;
import java.time.Clock;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    public record LoginResult(Map<String,Object> body, String refresh) {}
    private final Db db; private final StringRedisTemplate redis; private final PasswordEncoder passwords;
    private final JwtTokens jwt; private final String prefix; public final int refreshDays; private final SecureRandom random = new SecureRandom();
    private final Clock clock;
    public AuthService(Db db, StringRedisTemplate redis, PasswordEncoder passwords, JwtTokens jwt, @Value("${auth.refresh-days}") int days,@Value("${auth.redis-prefix:study}") String prefix,Clock clock) {
        this.db=db; this.redis=redis; this.passwords=passwords; this.jwt=jwt;
        if (days<1 || days>30) throw new IllegalStateException("REFRESH_TOKEN_DAYS must be 1..30");
        refreshDays=days;this.prefix=prefix;this.clock=clock;
    }
    public LoginResult login(String username, String password, String ip) {
        if (password.getBytes(StandardCharsets.UTF_8).length>72) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"密码超过 72 字节");
        String bucket=prefix+":login:"+hash(ip);
            checkLimit(bucket);
            var user=user();
            boolean matches=passwords.matches(password,user.get("passwordHash").toString());
            if (!matches || !username.equals(user.get("username"))) {
                failedAttempt(bucket);
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"用户名或密码不正确");
            }
            storage(()->redis.delete(bucket));
            String refresh=randomToken();
            storage(()->{redis.opsForValue().set(key(refresh),sessionVersion(user),Duration.ofDays(refreshDays));return true;});
            return result(refresh,user);
    }
    public LoginResult refresh(String old) {
        if (old==null || !old.matches("[A-Za-z0-9_-]{43}")) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"刷新令牌无效");
        String next=randomToken();
            String id=storage(()->redis.execute(new DefaultRedisScript<String>("local u=redis.call('GET',KEYS[1]); if not u then return nil end; redis.call('DEL',KEYS[1]); redis.call('SET',KEYS[2],u,'EX',ARGV[1]); return u",String.class),List.of(key(old),key(next)),String.valueOf(Duration.ofDays(refreshDays).toSeconds())));
            var user=user();
            boolean legacy=SingleUser.ID.toString().equals(id) && ((Number)user.get("credentialVersion")).longValue()==0;
            if (!legacy && !sessionVersion(user).equals(id)) {
                storage(()->redis.delete(key(next)));
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"刷新令牌已失效");
            }
            return result(next,user);
    }
    public void logout(String token) {
        if (token!=null && token.length()<256) storage(()->redis.delete(key(token)));
    }
    private LoginResult result(String refresh, Map<String,Object> user) {
        return new LoginResult(Map.of("accessToken",jwt.issue(((Number)user.get("credentialVersion")).longValue()),"expiresIn",jwt.lifetimeSeconds,"username",user.get("username"),"avatar",avatar(user),"timezone",clock.getZone().getId()),refresh);
    }
    private Map<String,Object> user() { return db.one("select username,password_hash,credential_version,avatar from users where id=?",SingleUser.ID); }
    private String sessionVersion(Map<String,Object> user) { return SingleUser.ID+":"+user.get("credentialVersion"); }
    private static String avatar(Map<String,Object> user) { return user.get("avatar") instanceof byte[] bytes ? "data:image/png;base64,"+Base64.getEncoder().encodeToString(bytes) : ""; }
    public Map<String,Object> verifyPassword(String password,String ip) {
        String bucket=prefix+":account-password:"+hash(ip);
        checkLimit(bucket);
        var user=user();
        if (password.getBytes(StandardCharsets.UTF_8).length>72 || !passwords.matches(password,user.get("passwordHash").toString())) {
            failedAttempt(bucket);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"当前密码不正确");
        }
        storage(()->redis.delete(bucket));
        return user;
    }
    private void checkLimit(String bucket) {
        String count=storage(()->redis.opsForValue().get(bucket));
        if (count!=null && Integer.parseInt(count)>=10) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"密码验证尝试过多，请 15 分钟后再试");
    }
    private void failedAttempt(String bucket) {
        storage(()->redis.execute(new DefaultRedisScript<Long>("local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],900) end; return n",Long.class),List.of(bucket)));
    }
    private String randomToken() { byte[] bytes=new byte[32]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    public Map<String,Object> me() { var row=db.one("select id,username,avatar,created_at from users where id=?",SingleUser.ID);row.put("avatar",avatar(row));row.put("timezone",clock.getZone().getId());return row; }
    private String key(String token) { return prefix+":refresh:"+hash(token); }
    private <T> T storage(java.util.function.Supplier<T> action) {
        try { return action.get(); }
        catch (org.springframework.dao.DataAccessException e) { throw unavailable(); }
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private ResponseStatusException unavailable() { return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"认证存储 Redis 暂时不可用，请稍后重试"); }
}
