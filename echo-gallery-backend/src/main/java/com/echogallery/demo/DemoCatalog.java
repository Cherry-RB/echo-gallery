package com.echogallery.demo;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class DemoCatalog {
    private final Map<String, Library> libraries;
    public DemoCatalog(ObjectMapper objectMapper) {
        try (var input = new ClassPathResource("demo/demo-libraries.json").getInputStream()) {
            libraries = objectMapper.readValue(input, new TypeReference<Map<String, Library>>() {});
        } catch (IOException exception) { throw new IllegalStateException("無法讀取 Demo 測資檔", exception); }
    }
    public Library library(DemoLibrary library) {
        Library result = libraries.get(library.getKey());
        if (result == null || result.cards() == null || result.cards().size() != 15) throw new IllegalStateException("Demo 測資檔不完整：" + library.getKey());
        return result;
    }
    public record Library(String workTitle, List<Entry> cards) {}
    public record Entry(String type, String title, String url, String summary, String reason, List<String> tags, Integer intervalDays, Integer dayOffset, String content, String growthStatus, Integer snoozeCount, Boolean archived, String workStatus) {}
}
