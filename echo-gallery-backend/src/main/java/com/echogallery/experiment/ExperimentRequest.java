package com.echogallery.experiment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ExperimentRequest {

    @NotBlank(message = "請填寫實驗主題名稱")
    @Size(max = 255, message = "實驗主題名稱不可超過 255 字")
    private String title;

    @Size(max = 5000, message = "實驗主題說明不可超過 5000 字")
    private String description;

    @Size(max = 2000, message = "假設不可超過 2000 字")
    private String hypothesis;

    private ExperimentThemeColor themeColor;
}
