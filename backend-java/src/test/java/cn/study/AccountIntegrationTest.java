package cn.study;

import cn.study.repository.Db;
import cn.study.security.SingleUser;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.LinkedMultiValueMap;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.flyway.schemas=integration_test","spring.flyway.default-schema=integration_test",
    "spring.datasource.hikari.schema=integration_test","auth.redis-prefix=study:test",
    "scheduler.enabled=false","ai.worker-enabled=false"
})
class AccountIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired Db db;
    @Autowired PasswordEncoder encoder;
    @Autowired StringRedisTemplate redis;
    @Autowired SingleUser bootstrap;
    private Map<String,Object> original;
    private String username,password,token;
    @BeforeEach void prepare() {
        cleanRedis();
        original=db.one("select username,password_hash,avatar,credential_version from users where id=?",SingleUser.ID);
        username=original.get("username").toString();password=UUID.randomUUID().toString();
        db.update("update users set password_hash=? where id=?",encoder.encode(password),SingleUser.ID);
        token=login(username,password).getBody().get("accessToken").toString();
    }
    @AfterEach void restore() {
        db.update("update users set username=?,password_hash=?,avatar=?,credential_version=? where id=?",original.get("username"),original.get("passwordHash"),original.get("avatar"),original.get("credentialVersion"),SingleUser.ID);
        cleanRedis();
    }
    private void cleanRedis() { var keys=redis.keys("study:test:*");if(keys!=null && !keys.isEmpty()) redis.delete(keys); }
    private ResponseEntity<Map> login(String name,String pass) { return http.postForEntity("/api/auth/login",Map.of("username",name,"password",pass),Map.class); }
    private HttpHeaders headers(String value) { var headers=new HttpHeaders();headers.setBearerAuth(value);headers.setContentType(MediaType.APPLICATION_JSON);return headers; }
    private ResponseEntity<Map> json(String path,HttpMethod method,Map<String,String> body) { return http.exchange(path,method,new HttpEntity<>(body,headers(token)),Map.class); }
    private ResponseEntity<Map> me(String value) { return http.exchange("/api/auth/me",HttpMethod.GET,new HttpEntity<>(headers(value)),Map.class); }
    private ResponseEntity<Map> avatar(byte[] bytes) {
        var form=new LinkedMultiValueMap<String,Object>();
        form.add("file",new ByteArrayResource(bytes) { @Override public String getFilename() { return "avatar.png"; } });
        var headers=headers(token);headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return http.postForEntity("/api/account/avatar",new HttpEntity<>(form,headers),Map.class);
    }
    private byte[] png(int width,int height) throws Exception {
        var output=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB),"png",output);return output.toByteArray();
    }
    @Test void protectsChangesAndPreservesRenamedCredentialsDuringBootstrap() {
        assertThat(http.postForEntity("/api/account/password",Map.of("currentPassword",password,"newPassword","other-password"),Map.class).getStatusCode().value()).isEqualTo(401);
        assertThat(json("/api/account/profile",HttpMethod.PATCH,Map.of("username","学习者","currentPassword","wrong-password")).getStatusCode().value()).isEqualTo(400);
        var saved=json("/api/account/profile",HttpMethod.PATCH,Map.of("username"," 学习者 ","currentPassword",password));
        assertThat(saved.getStatusCode().value()).isEqualTo(200);
        assertThat(saved.getBody()).containsEntry("username","学习者").doesNotContainKeys("passwordHash","credentialVersion");
        ReflectionTestUtils.invokeMethod(bootstrap,"initialize");
        assertThat(login("学习者",password).getStatusCode().value()).isEqualTo(200);
        assertThat(login(username,password).getStatusCode().value()).isEqualTo(401);
        assertThat(me(token).getBody()).containsEntry("username","学习者");
    }
    @Test void passwordChangeRevokesEveryAccessAndRefreshSession() {
        var second=login(username,password);
        String secondToken=second.getBody().get("accessToken").toString();
        assertThat(json("/api/account/password",HttpMethod.POST,Map.of("currentPassword","wrong-password","newPassword","new-test-password")).getStatusCode().value()).isEqualTo(400);
        assertThat(me(token).getStatusCode().value()).isEqualTo(200);
        assertThat(json("/api/account/password",HttpMethod.POST,Map.of("currentPassword",password,"newPassword","new-test-password")).getStatusCode().value()).isEqualTo(200);
        assertThat(me(token).getStatusCode().value()).isEqualTo(401);
        assertThat(me(secondToken).getStatusCode().value()).isEqualTo(401);
        var cookie=new HttpHeaders();cookie.setContentType(MediaType.APPLICATION_JSON);cookie.set(HttpHeaders.COOKIE,second.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";")[0]);
        assertThat(http.postForEntity("/api/auth/refresh",new HttpEntity<>(Map.of(),cookie),Map.class).getStatusCode().value()).isEqualTo(401);
        assertThat(login(username,password).getStatusCode().value()).isEqualTo(401);
        var next=login(username,"new-test-password");
        assertThat(next.getStatusCode().value()).isEqualTo(200);
        assertThat(me(next.getBody().get("accessToken").toString()).getStatusCode().value()).isEqualTo(200);
        ReflectionTestUtils.invokeMethod(bootstrap,"initialize");
        assertThat(login(username,"new-test-password").getStatusCode().value()).isEqualTo(200);
    }
    @Test void validatesPasswordLengthAndLimitsIncorrectVerificationAttempts() {
        assertThat(json("/api/account/password",HttpMethod.POST,Map.of("currentPassword",password,"newPassword","密".repeat(25))).getStatusCode().value()).isEqualTo(400);
        assertThat(json("/api/account/password",HttpMethod.POST,Map.of("currentPassword",password,"newPassword",password)).getStatusCode().value()).isEqualTo(400);
        for(int i=0;i<10;i++) assertThat(json("/api/account/profile",HttpMethod.PATCH,Map.of("username","other","currentPassword","wrong-password")).getStatusCode().value()).isEqualTo(400);
        assertThat(json("/api/account/profile",HttpMethod.PATCH,Map.of("username","other","currentPassword",password)).getStatusCode().value()).isEqualTo(429);
    }
    @Test void normalizesAvatarAndKeepsItAcrossLoginAndRefresh() throws Exception {
        var saved=avatar(png(600,300));
        assertThat(saved.getStatusCode().value()).isEqualTo(200);
        String value=saved.getBody().get("avatar").toString();
        assertThat(value).startsWith("data:image/png;base64,");
        var decoded=ImageIO.read(new java.io.ByteArrayInputStream(Base64.getDecoder().decode(value.split(",",2)[1])));
        assertThat(decoded.getWidth()).isEqualTo(256);assertThat(decoded.getHeight()).isEqualTo(128);
        var result=login(username,password);assertThat(result.getBody()).containsEntry("avatar",value);
        var cookie=new HttpHeaders();cookie.setContentType(MediaType.APPLICATION_JSON);cookie.set(HttpHeaders.COOKIE,result.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";")[0]);
        assertThat(http.postForEntity("/api/auth/refresh",new HttpEntity<>(Map.of(),cookie),Map.class).getBody()).containsEntry("avatar",value);
        ReflectionTestUtils.invokeMethod(bootstrap,"initialize");assertThat(me(token).getBody()).containsEntry("avatar",value);
        assertThat(json("/api/account/avatar",HttpMethod.DELETE,Map.of()).getBody()).containsEntry("avatar","");
    }
    @Test void rejectsSpoofedOversizedAndUnboundedImages() throws Exception {
        assertThat(avatar("<svg onload='alert(1)'></svg>".getBytes()).getStatusCode().value()).isEqualTo(400);
        assertThat(avatar(new byte[512*1024+1]).getStatusCode().value()).isEqualTo(400);
        assertThat(avatar(new byte[2*1024*1024]).getStatusCode().value()).isEqualTo(413);
        assertThat(avatar(png(5000,1)).getStatusCode().value()).isEqualTo(400);
        assertThat(me(token).getBody().get("avatar")).isEqualTo(original.get("avatar")==null ? "" : "data:image/png;base64,"+Base64.getEncoder().encodeToString((byte[])original.get("avatar")));
    }
}
