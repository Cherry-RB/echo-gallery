package com.echogallery.work;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateWorkRequest extends WorkContentRequest {

    @NotNull(message = "作品狀態不可為空")
    private WorkStatus status;
}
