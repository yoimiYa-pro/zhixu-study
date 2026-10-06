package cn.study;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.flyway.schemas=integration_test", "spring.flyway.default-schema=integration_test",
    "spring.datasource.hikari.schema=integration_test", "scheduler.enabled=false", "ai.worker-enabled=false"
})
class HealthTest {
    @Autowired TestRestTemplate http;
    @Autowired cn.study.repository.Db db;
    @Test void healthEndpointIsLive() {
        var response = http.getForEntity("/actuator/health", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("UP");
    }
    @Test void publicLivenessEndpointIdentifiesTheService() {
        var response = http.getForEntity("/api/health", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("backend-java", "1.0.0", "UP");
    }
    @Test void migrationsCreateAllBusinessTables() {
        assertThat(db.count("select count(*) from information_schema.tables where table_schema='integration_test'"))
            .isGreaterThanOrEqualTo(17);
        assertThat(db.rows("select 1 as ready from questions limit 1")).isNotNull();
    }
    @Test void repositorySerializesJsonAndTimestampsAtTheBoundary() {
        var result = db.one("select now() as created_at, '{}'::jsonb as analysis_json, gen_random_uuid() as id");
        assertThat(result.get("createdAt")).isInstanceOf(String.class);
        assertThat(result.get("analysis")).isInstanceOf(java.util.Map.class);
        assertThat(result.get("id")).isInstanceOf(String.class);
    }
}
