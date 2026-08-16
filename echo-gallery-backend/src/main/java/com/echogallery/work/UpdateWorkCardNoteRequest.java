package com.echogallery.work;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateWorkCardNoteRequest {

    @Size(max = 1000, message = "素材備註不可超過 1000 個字")
    private String note;
}
