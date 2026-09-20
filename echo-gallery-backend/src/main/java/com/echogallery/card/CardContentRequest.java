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

    @NotBlank(message = "卡片類型為必填")
    @Pattern(regexp = "note|link", message = "卡片類型只能是 note 或 link")
    private String type;

    @NotBlank(message = "卡片標題為必填")
    private String title;

    @Size(max = 2048, message = "封面圖片網址不可超過 2048 個字元")
    private String coverImageUrl;

    @Size(max = 2048, message = "來源網址不可超過 2048 個字元")
    private String url;

    private String summary;

    private String content;

    private String reason;

    @Size(max = 10, message = "每張卡片最多只能有 10 個標籤")
    private List<@NotNull(message = "標籤不可為 null") String> tags;

    @Min(value = 1, message = "回流天數至少為 1 天")
    @Max(value = 365, message = "回流天數最多為 365 天")
    private Integer intervalDays;
}
