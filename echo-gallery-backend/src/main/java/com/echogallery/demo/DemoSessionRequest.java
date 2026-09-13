package com.echogallery.demo;

import jakarta.validation.constraints.NotBlank;

public record DemoSessionRequest(@NotBlank(message = "請選擇 Demo 內容庫") String library) {
}
