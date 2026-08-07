package com.echogallery.tag;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagDto {
    @NotBlank
    private Long id;
    @NotBlank
    private String name;

    private Long cardCount;

    public TagDto(String name, Long cardCount){
        this.name = name;
        this.cardCount = cardCount;
    }
}
