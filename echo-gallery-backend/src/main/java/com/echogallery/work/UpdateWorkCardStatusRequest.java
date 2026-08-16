package com.echogallery.work;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateWorkCardStatusRequest {

    @NotNull(message = "作品素材狀態不可為空")
    private WorkCardStatus status;
}
