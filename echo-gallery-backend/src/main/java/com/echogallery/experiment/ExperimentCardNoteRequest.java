package com.echogallery.experiment;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ExperimentCardNoteRequest {

    @Size(max = 1000, message = "種植備註不可超過 1000 字")
    private String note;
}
