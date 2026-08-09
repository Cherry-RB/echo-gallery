package com.echogallery.card;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@ValidCardContent
public abstract class CardContentRequest {

    @NotBlank(message = "卡片類型不可為空")
    @Pattern(regexp = "note|link", message = "卡片類型必須是 note 或 link")
    private String type;

    @NotBlank(message = "卡片標題不可為空")
    @Size(max = 255, message = "卡片標題不可超過 255 個字元")
    private String title;

    @Size(max = 2048, message = "封面圖片網址不可超過 2048 個字元")
    private String coverImageUrl;

    @Size(max = 2048, message = "來源網址不可超過 2048 個字元")
    private String url;

    @Size(max = 600, message = "簡介不可超過 600 個字元")
    private String summary;

    private String content;

    @Size(max = 300, message = "推薦原因不可超過 300 個字元")
    private String reason;

    @Size(max = 10, message = "每張卡片最多只能有 10 個標籤")
    private List<@NotNull(message = "標籤不可為 null") String> tags;

    @Min(value = 1, message = "回流間隔至少為 1 天")
    @Max(value = 365, message = "回流間隔最多為 365 天")
    private Integer intervalDays;
}
