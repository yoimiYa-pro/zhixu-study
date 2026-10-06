package cn.study;

import cn.study.security.JwtTokens;
import io.jsonwebtoken.JwtException;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class JwtTokensTest {
    @Test void rejectsTamperingWrongSignaturesAndExpiredTokens() {
        String secret=UUID.randomUUID()+"-"+UUID.randomUUID();
        Clock now=Clock.fixed(Instant.parse("2026-10-02T01:00:00Z"),ZoneId.of("Asia/Shanghai"));
        var original=new JwtTokens(secret,30,now);
        String token=original.issue();
        assertThat(original.verify(token)).endsWith("0001");
        var pieces=token.split("\\."); pieces[1]="A"+pieces[1].substring(1);
        assertThatThrownBy(()->original.verify(String.join(".",pieces))).isInstanceOf(JwtException.class);
        assertThatThrownBy(()->new JwtTokens(UUID.randomUUID()+"-"+UUID.randomUUID(),30,now).verify(token)).isInstanceOf(JwtException.class);
        assertThatThrownBy(()->new JwtTokens(secret,30,Clock.offset(now,Duration.ofMinutes(31))).verify(token)).isInstanceOf(JwtException.class);
    }
}
