package com.echogallery.card;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

import lombok.Data;

@Data
public class CardRequest {
    private Long id;

    @NotBlank(message = "卡片類型不能為空")
    @Pattern(regexp = "note|link", message = "卡片類型必須是 note 或 link")
    private String type;         // "note" 或 "link"

    @NotBlank(message = "標題不能為空")
    @Size(max = 255, message = "標題長度不能超過 255 個字元")
    private String title;

    @Size(max = 2048, message = "封面圖片網址過長")
    private String coverImageUrl;

    @Size(max = 2048, message = "網址過長")
    private String url;
    // private String sourceType;   // "bilibili", "youtube" 等

    @Size(max = 600, message = "摘要不能超過 600 個字元")
    private String summary;

    private String content;

    @Size(max = 300, message = "原因不能超過 300 個字元")
    private String reason;
    private String[] tags;
    private Integer intervalDays;
    private Boolean isArchived;
}
