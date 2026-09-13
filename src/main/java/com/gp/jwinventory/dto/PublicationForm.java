package com.gp.jwinventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PublicationForm {

    @NotBlank(message = "Il codice è obbligatorio")
    @Size(max = 50, message = "Massimo 50 caratteri")
    private String code;

    @NotBlank(message = "Il titolo è obbligatorio")
    @Size(max = 255, message = "Massimo 255 caratteri")
    private String title;

    /** Initial number of copies; used only on creation (afterwards quantity changes are movements). */
    @Min(value = 0, message = "La quantità non può essere negativa")
    private Integer quantity;
}
