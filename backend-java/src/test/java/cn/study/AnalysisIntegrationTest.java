package cn.study;

import cn.study.dto.QuestionAnalysis;
import cn.study.repository.Db;
import cn.study.service.*;
import cn.study.tasks.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import static org.assertj.core.api.Assertions.*;

class AnalysisIntegrationTest extends ApiIntegrationBase {
    @Autowired QuestionService questions;
    @Autowired AnalysisResultService results;
    @Autowired Validator validator;
    private QuestionAnalysis valid() {
        return new QuestionAnalysis("资料分析",List.of("增长率","基期量"),"中等","计算错误","增长量为20，基期量100，增长率20%。","20/100=20%",List.of("分母应是基期量"),List.of("求增长量","除以基期量"),"增长量除以基期量",List.of("基期量"),List.of(new QuestionAnalysis.ReviewSuggestion(1,"巩固概念")));
    }
    private void process(UUID question,String response,int status) throws Exception {
        db.update("delete from ai_tasks where kind<>'ANALYZE_QUESTION'");
        String token=UUID.randomUUID().toString();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/ai/question/analyze",exchange->{
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8);
            int code=exchange.getRequestHeaders().getFirst("Authorization").equals("Bearer "+token)?status:401;
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(code,bytes.length);
            exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        try {
            var client=new AiClient("http://127.0.0.1:"+server.getAddress().getPort(),token,5,validator);
            var worker=new TaskWorker(db,List.of(new QuestionAnalysisHandler(questions,client,results)),1,false,300);
            assertThat(worker.processOne()).isTrue();
        } finally { server.stop(0); }
    }
    @Test void validatedAnalysisExtractsKnowledgeAndPreservesConfirmedReason() throws Exception {
        UUID id=createQuestion();request("/api/questions/"+id+"/mistake",HttpMethod.PATCH,Map.of("reason","审题错误"),Map.class);
        process(id,db.json(valid()),200);
        var question=questions.get(id);
        assertThat(question.get("aiStatus")).isEqualTo("COMPLETED");
        assertThat(question.get("mistakeReason")).isEqualTo("审题错误");
        assertThat(question.get("analysis")).isInstanceOf(Map.class);
        assertThat((List)question.get("knowledgePoints")).contains("基期量");
        var task=db.one("select * from ai_tasks where kind='ANALYZE_QUESTION' and reference_id=?",id);
        assertThat(task.get("progressDone")).isEqualTo(1);
        assertThat(task.get("progressTotal")).isEqualTo(1);
        assertThat(task.get("progressLabel")).isEqualTo("已完成");
    }
    @Test void invalidOutputAndNetworkFailureDoNotAlterCoreQuestion() throws Exception {
        UUID id=createQuestion();process(id,db.json(valid()).replace("中等","未知难度"),200);
        assertThat(questions.get(id).get("aiStatus")).isEqualTo("FAILED");
        assertThat(questions.get(id).get("analysis")).isNull();
        var task=db.one("select id from ai_tasks where kind='ANALYZE_QUESTION' and reference_id=?",id);
        assertThat(request("/api/ai-tasks/"+task.get("id")+"/retry",HttpMethod.POST,Map.of(),Map.class).getStatusCode().value()).isEqualTo(200);
        var reset=db.one("select * from ai_tasks where id=?",UUID.fromString(task.get("id").toString()));
        assertThat(reset.get("progressDone")).isEqualTo(0);
        assertThat(reset.get("progressTotal")).isNull();
        assertThat(reset.get("progressLabel")).isEqualTo("等待处理");
        process(id,"{}",503);
        assertThat(questions.get(id).get("content")).isNotNull();
        assertThat(db.one("select error_code from ai_tasks where reference_id=?",id).get("errorCode")).isEqualTo("AI_UNAVAILABLE");
    }
    @Test void obsoleteAnalysisCannotOverwriteEditedQuestion() {
        UUID id=createQuestion();assertThat(results.apply(id,0,valid())).isFalse();
        assertThat(questions.get(id).get("analysis")).isNull();
    }
}
