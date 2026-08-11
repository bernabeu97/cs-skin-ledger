package com.cs.skinledger.web;

import com.cs.skinledger.dto.WatchlistCreateRequest;
import com.cs.skinledger.dto.WatchlistResponse;
import com.cs.skinledger.service.WatchlistService;
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
@RequestMapping("/api/watchlist")
@RequiredArgsConstructor
public class WatchlistController {
    private final WatchlistService watchlistService;

    @GetMapping
    public List<WatchlistResponse> list() {
        return watchlistService.list();
    }

    @PostMapping
    public WatchlistResponse create(@Valid @RequestBody WatchlistCreateRequest request) {
        return watchlistService.create(request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        watchlistService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
