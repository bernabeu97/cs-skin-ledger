package com.cs.skinledger.web;

import com.cs.skinledger.domain.Item;
import com.cs.skinledger.dto.ItemDto;
import com.cs.skinledger.dto.ItemImportResult;
import com.cs.skinledger.repository.ItemRepository;
import com.cs.skinledger.service.ItemImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemRepository itemRepository;
    private final ItemImportService itemImportService;

    @GetMapping("/search")
    public List<ItemDto> search(@RequestParam String q, @RequestParam(defaultValue = "50") int limit) {
        int capped = Math.min(Math.max(limit, 1), 100);
        return itemRepository.search(q.trim(), PageRequest.of(0, capped)).stream()
                .map(ItemDto::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ItemDto get(@PathVariable Long id) {
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("饰品不存在: " + id));
        return ItemDto.from(item);
    }

    @PostMapping("/import")
    public ItemImportResult importItems(@RequestParam(defaultValue = "work/csgoapi") String dir) throws IOException {
        return itemImportService.importFromDirectory(Path.of(dir));
    }
}