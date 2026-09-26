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

    @GetMapping
    public ResponseEntity<OverviewResponse> getOverview(
            @RequestParam(name = "periodDays", defaultValue = "30") int periodDays) {
        return ResponseEntity.ok(overviewService.getOverview(periodDays));
    }

    @GetMapping("/card-return")
    public ResponseEntity<CardReturnOverviewResponse> getCardReturnOverview() {
        return ResponseEntity.ok(overviewService.getCardReturnOverview());
    }
}
