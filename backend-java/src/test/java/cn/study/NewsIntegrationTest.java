package cn.study;
import cn.study.dto.NewsModels.*;
import cn.study.service.NewsService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import static org.assertj.core.api.Assertions.*;

class NewsIntegrationTest extends ApiIntegrationBase {
    @Autowired NewsService news;
    @Test void sourcesArePreservedAndDuplicateArticleDoesNotRepeatMaterials() {
        var input=Map.of("title","仅供测试的原始材料","content","测试来源中的原文内容","source","测试文档","sourceUrl","https://source.example/article");
        var first=request("/api/current-affairs",HttpMethod.POST,input,Map.class);
        assertThat(first.getStatusCode().value()).isEqualTo(201);
        assertThat(first.getBody().get("sourceUnverified")).isEqualTo(true);
        assertThat(first.getBody().get("publishTime")).isNull();
        UUID id=UUID.fromString(first.getBody().get("id").toString());
        assertThat(request("/api/current-affairs",HttpMethod.POST,input,Map.class).getBody().get("id")).isEqualTo(id.toString());
        var empty=List.<String>of();var material=new Material("测试素材","仅测试材料内容","民生","案例",List.of("测试"));
        var analysis=new Analysis("测试摘要",empty,empty,empty,empty,empty,empty,Map.of(),new Essay("",empty,empty,empty,empty,empty,empty),List.of(material));
        assertThat(news.apply(id,1,analysis)).isTrue();assertThat(news.apply(id,1,analysis)).isTrue();
        assertThat(db.count("select count(*) from essay_materials")).isEqualTo(1);
        assertThat(db.one("select * from essay_materials").get("sourceUrl")).isEqualTo("https://source.example/article");
        assertThat(db.one("select * from current_affairs where id=?",id).get("source")).isEqualTo("测试文档");
        assertThat(news.apply(id,99,analysis)).isFalse();
        assertThat(request("/api/current-affairs",HttpMethod.POST,Map.of("title","bad","content","body","source","manual","sourceUrl","javascript:alert(1)"),Map.class).getStatusCode().value()).isEqualTo(400);
    }
    @Test void tagsPersistFilterAndManualClassificationSurvivesAiAnalysis() {
        var first=request("/api/current-affairs",HttpMethod.POST,Map.of("title","科技创新与生态保护","content","真实录入内容用于隔离测试","source","来源甲","sourceUrl","https://source.example/tech","classification",Map.of("tags",List.of("科技","生态文明"))),Map.class).getBody();
        UUID firstId=UUID.fromString(first.get("id").toString());
        var second=request("/api/current-affairs",HttpMethod.POST,Map.of("title","经济产业政策","content","经济材料原文","source","来源乙","sourceUrl","https://source.example/economy"),Map.class).getBody();
        UUID secondId=UUID.fromString(second.get("id").toString());
        assertThat(first.get("tags")).isEqualTo(List.of("科技","生态文明"));
        assertThat(second.get("tags")).isEqualTo(List.of("经济"));
        var filtered=request("/api/current-affairs?tag=科技&source=来源甲&q=创新",HttpMethod.GET,null,List.class).getBody();
        assertThat(filtered).hasSize(1);
        assertThat(((Map<?,?>)filtered.getFirst()).get("sourceUrl")).isEqualTo("https://source.example/tech");
        assertThat(request("/api/current-affairs?tag=虚构标签",HttpMethod.GET,null,Map.class).getStatusCode().value()).isEqualTo(400);
        assertThat(request("/api/current-affairs/"+firstId+"/tags",HttpMethod.PATCH,Map.of("tags",List.of("不支持的标签")),Map.class).getStatusCode().value()).isEqualTo(400);
        var empty=List.<String>of();
        var analysis=new Analysis("原文摘要",empty,empty,empty,empty,empty,empty,Map.of(),new Essay("",empty,empty,empty,empty,empty,empty),List.of(),List.of("国际"));
        assertThat(news.apply(firstId,1,analysis)).isTrue();
        assertThat(news.get(firstId).get("tags")).isEqualTo(List.of("科技","生态文明"));
        assertThat(news.apply(secondId,1,analysis)).isTrue();
        assertThat(news.get(secondId).get("tags")).isEqualTo(List.of("国际"));
        var overview=request("/api/current-affairs/overview",HttpMethod.GET,null,Map.class).getBody();
        assertThat(((Number)overview.get("total")).intValue()).isEqualTo(2);
        assertThat(((Number)overview.get("analyzed")).intValue()).isEqualTo(2);
        assertThat((List<?>)overview.get("sources")).hasSize(2);
        assertThat((List<?>)overview.get("tags")).hasSize(3);
        assertThat(request("/api/current-affairs/"+firstId+"/tags",HttpMethod.PATCH,Map.of("tags",List.of()),Map.class).getStatusCode().value()).isEqualTo(200);
        assertThat(news.apply(firstId,1,analysis)).isTrue();
        assertThat(news.get(firstId).get("tags")).isEqualTo(List.of());
        assertThat(request("/api/current-affairs?tag=待分类",HttpMethod.GET,null,List.class).getBody()).hasSize(1);
        assertThat(request("/api/current-affairs?date="+LocalDate.now().minusDays(1),HttpMethod.GET,null,List.class).getBody()).isEmpty();
        assertThat(request("/api/current-affairs?q=%25_%27",HttpMethod.GET,null,List.class).getBody()).isEmpty();
        assertThat(db.count("select count(*) from current_affairs")).isEqualTo(2);
    }
}
