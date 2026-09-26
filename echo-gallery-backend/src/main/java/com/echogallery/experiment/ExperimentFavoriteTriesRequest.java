package com.echogallery.experiment;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExperimentFavoriteTriesRequest(
        @Size(max = 3, message = "最多保存 3 種常用試法")
        List<@NotBlank(message = "常用試法不可留白") @Size(max = 500, message = "試法不可超過 500 字") String> favoriteTries) {
}
