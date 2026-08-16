package com.echogallery.work;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class WorkContentRequest {

    @NotBlank(message = "作品標題不可為空")
    @Size(max = 255, message = "作品標題不可超過 255 個字")
    private String title;

    @Size(max = 5000, message = "作品說明不可超過 5000 個字")
    private String description;

    @Size(max = 2048, message = "外部連結不可超過 2048 個字")
    @ValidOptionalHttpUrl
    private String externalUrl;
}
