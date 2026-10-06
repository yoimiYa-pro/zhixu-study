package cn.study.controller;

import cn.study.dto.ModelSelection.*;
import cn.study.service.ModelSelectionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai-models")
public class ModelSelectionController {
    private final ModelSelectionService models;
    public ModelSelectionController(ModelSelectionService models) { this.models=models; }
    @GetMapping public View catalog() { return models.catalog(); }
    @PostMapping public View select(@Valid @RequestBody Choice choice) { return models.select(choice); }
    @GetMapping("/connections") public List<Connection> connections() { return models.connections(); }
    @PostMapping("/connections") public View add(@Valid @RequestBody ConnectionInput input) { return models.save(null,input); }
    @PutMapping("/connections/{id}") public View edit(@PathVariable UUID id,@Valid @RequestBody ConnectionInput input) { return models.save(id,input); }
    @DeleteMapping("/connections/{id}") public View remove(@PathVariable UUID id,@Valid @RequestBody Revision input) { return models.remove(id,input); }
}
