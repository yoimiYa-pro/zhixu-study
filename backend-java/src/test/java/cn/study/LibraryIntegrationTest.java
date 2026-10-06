package cn.study;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import static org.assertj.core.api.Assertions.*;
class LibraryIntegrationTest extends ApiIntegrationBase {
    @Test void vocabularyFlagsAndMaterialVersionsArePersistent() {
        var word=new LinkedHashMap<String,Object>(Map.of("word","循序渐进","kind","成语","definition","按一定的次序逐步前进","synonyms",List.of("按部就班"),"antonyms",List.of("急于求成"),"example","测试例句"));
        var saved=request("/api/idioms",HttpMethod.POST,word,Map.class);assertThat(saved.getStatusCode().value()).isEqualTo(201);
        String id=saved.getBody().get("id").toString();
        assertThat(request("/api/idioms/"+id,HttpMethod.PATCH,Map.of("favorite",true,"mastered",true),Map.class).getBody().get("mastered")).isEqualTo(true);
        assertThat(request("/api/idioms/today",HttpMethod.GET,null,List.class).getBody()).isEmpty();
        assertThat(request("/api/idioms",HttpMethod.POST,word,Map.class).getStatusCode().value()).isEqualTo(409);
        var material=Map.of("title","仅测试的案例","content","测试素材库 CRUD","category","基层治理","kind","案例","tags",List.of("测试"));
        var result=request("/api/essay-materials",HttpMethod.POST,material,Map.class);assertThat(result.getStatusCode().value()).isEqualTo(201);
        String materialId=result.getBody().get("id").toString();assertThat(result.getBody().get("sourceUnverified")).isEqualTo(true);
        assertThat(request("/api/essay-materials/"+materialId,HttpMethod.PUT,material,Map.class).getBody().get("revision")).isEqualTo(2);
        assertThat(request("/api/essay-materials?category=基层治理",HttpMethod.GET,null,Map.class).getBody().get("total")).isEqualTo(1);
        assertThat(request("/api/essay-materials/"+materialId,HttpMethod.DELETE,null,Void.class).getStatusCode().value()).isEqualTo(204);
        assertThat(db.count("select count(*) from ai_tasks where kind='DELETE_DOCUMENT'")).isEqualTo(1);
    }
}
