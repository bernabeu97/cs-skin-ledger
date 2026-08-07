package com.cs.skinledger.web;

import com.cs.skinledger.dto.AlertCreateRequest;
import com.cs.skinledger.dto.AlertResponse;
import com.cs.skinledger.service.AlertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    public List<AlertResponse> list() {
        return alertService.list();
    }

    @PostMapping
    public AlertResponse create(@Valid @RequestBody AlertCreateRequest req) {
        return alertService.create(req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        alertService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reset")
    public AlertResponse reset(@PathVariable Long id) {
        return alertService.reset(id);
    }
}