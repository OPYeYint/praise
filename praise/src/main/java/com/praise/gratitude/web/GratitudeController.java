package com.praise.gratitude.web;

import com.praise.gratitude.service.GratitudeEntryService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/gratitudes")
public class GratitudeController {

    private final GratitudeEntryService service;

    public GratitudeController(GratitudeEntryService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<GratitudeResponse> create(@Valid @RequestBody GratitudeRequest request) {
        GratitudeResponse saved = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(saved.id())
                .toUri();
        return ResponseEntity.created(location).body(saved);
    }

    @GetMapping
    public List<GratitudeResponse> list() {
        return service.list();
    }

    @GetMapping("/random")
    public ResponseEntity<GratitudeResponse> random() {
        return service.findRandom()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{id}")
    public GratitudeResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public GratitudeResponse update(@PathVariable Long id, @Valid @RequestBody GratitudeRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
