package cn.study.tasks;

import cn.study.repository.Db;
import cn.study.service.AiClient;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class DeleteDocumentHandler implements TaskHandler {
    private final AiClient ai;
    public DeleteDocumentHandler(AiClient ai) { this.ai=ai; }
    public String kind() { return "DELETE_DOCUMENT"; }
    public Object handle(Map<String,Object> task) { return ai.post("/ai/documents/delete",Map.of("entityId",task.get("referenceId"),"entityType",((Map<?,?>)task.get("payload")).get("entityType")),Map.class); }
}
