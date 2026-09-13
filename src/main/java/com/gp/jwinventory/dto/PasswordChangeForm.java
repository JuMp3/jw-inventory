package com.gp.jwinventory.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PasswordChangeForm {

    @NotBlank(message = "Inserisci la password attuale")
    private String currentPassword;

    @NotBlank(message = "Inserisci la nuova password")
    private String newPassword;

    @NotBlank(message = "Conferma la nuova password")
    private String confirmPassword;
}
