package cn.study;

import cn.study.repository.Db;
import cn.study.security.SingleUser;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.flyway.schemas=integration_test","spring.flyway.default-schema=integration_test",
    "spring.datasource.hikari.schema=integration_test","auth.redis-prefix=study:test",
    "scheduler.enabled=false","ai.worker-enabled=false"
})
class AuthIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired Db db;
    @Autowired PasswordEncoder encoder;
    @Autowired StringRedisTemplate redis;
    private String username,password;
    @BeforeEach void credentials() {
        cleanRedis();
        username=db.one("select username from users where id=?",SingleUser.ID).get("username").toString();
        password=UUID.randomUUID().toString();
        db.update("update users set password_hash=? where id=?",encoder.encode(password),SingleUser.ID);
    }
    @AfterEach void cleanRedis() { var keys=redis.keys("study:test:*"); if(keys!=null && !keys.isEmpty()) redis.delete(keys); }
    private ResponseEntity<Map> login(String value) { return http.postForEntity("/api/auth/login",Map.of("username",username,"password",value),Map.class); }
    private HttpEntity<?> cookieEntity(String cookie) {
        var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);headers.set(HttpHeaders.COOKIE,cookie);
        return new HttpEntity<>(Map.of(),headers);
    }
    @Test void loginUsesBcryptAndProtectsBusinessRoutes() {
        assertThat(http.getForEntity("/api/auth/me",String.class).getStatusCode().value()).isEqualTo(401);
        assertThat(login("incorrect-password").getStatusCode().value()).isEqualTo(401);
        var result=login(password);
        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("HttpOnly","SameSite=Strict","Max-Age=604800");
        var headers=new HttpHeaders();headers.setBearerAuth(result.getBody().get("accessToken").toString());
        var me=http.exchange("/api/auth/me",HttpMethod.GET,new HttpEntity<>(headers),Map.class);
        assertThat(me.getStatusCode().value()).isEqualTo(200);
        assertThat(me.getBody().get("username")).isEqualTo(username);
    }
    @Test void rotatesRefreshTokenAndRejectsReplayAfterLogout() {
        var result=login(password);
        String old=result.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";")[0];
        var refreshed=http.postForEntity("/api/auth/refresh",cookieEntity(old),Map.class);
        assertThat(refreshed.getStatusCode().value()).isEqualTo(200);
        assertThat(http.postForEntity("/api/auth/refresh",cookieEntity(old),Map.class).getStatusCode().value()).isEqualTo(401);
        String next=refreshed.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";")[0];
        assertThat(http.postForEntity("/api/auth/logout",cookieEntity(next),Map.class).getStatusCode().value()).isEqualTo(200);
        assertThat(http.postForEntity("/api/auth/refresh",cookieEntity(next),Map.class).getStatusCode().value()).isEqualTo(401);
    }
}
