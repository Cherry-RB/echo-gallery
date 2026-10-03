package com.echogallery.overview;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/overview")
@RequiredArgsConstructor
public class OverviewController {

    private final OverviewService overviewService;

    @GetMapping("/current")
    public ResponseEntity<OverviewResponse.OverviewCurrentResponse> getCurrentOverview() {
        return ResponseEntity.ok(overviewService.getCurrentOverview());
    }

    @GetMapping("/recent")
    public ResponseEntity<OverviewRecentResponse> getRecentOverview(
            @RequestParam(name = "periodDays", defaultValue = "30") int periodDays) {
        return ResponseEntity.ok(overviewService.getRecentOverview(periodDays));
    }

    @GetMapping("/card-return")
    public ResponseEntity<CardReturnOverviewResponse> getCardReturnOverview() {
        return ResponseEntity.ok(overviewService.getCardReturnOverview());
    }
}
