package com.gp.jwinventory.dto;

import com.gp.jwinventory.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserForm {

    @NotBlank(message = "Il nome è obbligatorio")
    @Size(max = 150, message = "Massimo 150 caratteri")
    private String fullName;

    @NotBlank(message = "L'email è obbligatoria")
    @Email(message = "Email non valida")
    private String email;

    @NotNull(message = "Seleziona un ruolo")
    private Role role;

    /** Used only when the SuperAdmin manages users; an ADMIN always works in its own congregation. */
    private Long congregationId;

    private boolean enabled = true;

    /** Required on creation; on update, blank means "keep the current password". */
    private String password;
}
