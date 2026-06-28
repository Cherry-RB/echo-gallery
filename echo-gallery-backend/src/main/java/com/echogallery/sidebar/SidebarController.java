package com.echogallery.sidebar;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/sidebar")
@RequiredArgsConstructor
public class SidebarController {

    private final SidebarService sidebarService;

    @GetMapping("/stats")
    public ResponseEntity<SidebarStatsResponse> getSidebarStats() {
        return ResponseEntity.ok(sidebarService.getSidebarStats());
    }

    @GetMapping("/tags/top")
    public ResponseEntity<List<TagRankingResponse>> getTopTags() {
        // 畫面上顯示 Top 5
        return ResponseEntity.ok(sidebarService.getTopTags());
    }
}
