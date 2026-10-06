package cn.study.controller;
import cn.study.dto.LibraryInput.*;
import cn.study.service.LibraryService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController
public class LibraryController {
    private final LibraryService library;
    public LibraryController(LibraryService library) { this.library=library; }
    @GetMapping("/api/idioms/today") public List<Map<String,Object>> today() { return library.today(); }
    @GetMapping("/api/idioms") public Map<String,Object> idioms(@RequestParam(defaultValue="") String q,@RequestParam(required=false) Boolean favorite,@RequestParam(required=false) Boolean mastered,@RequestParam(defaultValue="1") int page) { return library.idioms(q,favorite,mastered,page); }
    @GetMapping("/api/idioms/{id}") public Map<String,Object> idiom(@PathVariable UUID id) { return library.idiom(id); }
    @PostMapping("/api/idioms") public ResponseEntity<?> create(@Valid @RequestBody Idiom input) { return ResponseEntity.status(201).body(library.createIdiom(input)); }
    @PutMapping("/api/idioms/{id}") public Map<String,Object> edit(@PathVariable UUID id,@Valid @RequestBody Idiom input) { return library.updateIdiom(id,input); }
    @PatchMapping("/api/idioms/{id}") public Map<String,Object> state(@PathVariable UUID id,@RequestBody State input) { return library.state(id,input); }
    @DeleteMapping("/api/idioms/{id}") public ResponseEntity<?> delete(@PathVariable UUID id) { library.deleteIdiom(id);return ResponseEntity.noContent().build(); }
    @GetMapping("/api/essay-materials") public Map<String,Object> materials(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String category,@RequestParam(defaultValue="") String kind,@RequestParam(defaultValue="1") int page) { return library.materials(q,category,kind,page); }
    @GetMapping("/api/essay-materials/{id}") public Map<String,Object> material(@PathVariable UUID id) { return library.material(id); }
    @PostMapping("/api/essay-materials") public ResponseEntity<?> createMaterial(@Valid @RequestBody Material input) { return ResponseEntity.status(201).body(library.saveMaterial(null,input)); }
    @PutMapping("/api/essay-materials/{id}") public Map<String,Object> editMaterial(@PathVariable UUID id,@Valid @RequestBody Material input) { return library.saveMaterial(id,input); }
    @DeleteMapping("/api/essay-materials/{id}") public ResponseEntity<?> deleteMaterial(@PathVariable UUID id) { library.deleteMaterial(id);return ResponseEntity.noContent().build(); }
}
