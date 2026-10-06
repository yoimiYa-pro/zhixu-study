package cn.study.controller;
import cn.study.service.AssistantService;
import cn.study.service.ChatConversationService;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController
public class AssistantController {
    private final AssistantService assistant;private final ChatConversationService conversations;
    public AssistantController(AssistantService assistant,ChatConversationService conversations) { this.assistant=assistant;this.conversations=conversations; }
    public record Chat(@NotNull UUID turnId,@NotBlank @Size(max=3000) String query,UUID questionId,UUID conversationId) {}
    public record NewConversation(UUID questionId) {}
    public record RenameConversation(@NotBlank @Size(max=80) String title) {}
    public record Essay(@NotBlank @Size(max=300) String topic) {}
    @GetMapping("/api/chat") public List<Map<String,Object>> history(@RequestParam(required=false) UUID conversationId) { return conversations.history(conversationId); }
    @PostMapping("/api/chat") public Map<String,Object> chat(@Valid @RequestBody Chat input) { return assistant.chat(input.conversationId(),input.turnId(),input.query(),input.questionId()); }
    @GetMapping("/api/chat/conversations") public List<Map<String,Object>> conversations() { return conversations.list(); }
    @PostMapping("/api/chat/conversations") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> create(@RequestBody(required=false) NewConversation input) { return conversations.create(input==null?null:input.questionId()); }
    @GetMapping("/api/chat/conversations/{id}") public Map<String,Object> conversation(@PathVariable UUID id) { return conversations.get(id); }
    @PatchMapping("/api/chat/conversations/{id}") public Map<String,Object> rename(@PathVariable UUID id,@Valid @RequestBody RenameConversation input) { return conversations.rename(id,input.title()); }
    @DeleteMapping("/api/chat/conversations/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { conversations.delete(id); }
    @GetMapping("/api/chat/conversations/{id}/messages")
    public Map<String,Object> messages(@PathVariable UUID id,@RequestParam(required=false) UUID before,@RequestParam(defaultValue="50") int pageSize) { return conversations.messages(id,before,pageSize); }
    @PostMapping("/api/essay-assistant") public Map<String,Object> essay(@Valid @RequestBody Essay input) { return assistant.outline(input.topic()); }
}
