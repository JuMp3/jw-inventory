package com.gp.jwinventory.dto;

import com.gp.jwinventory.enums.ScheduleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class NotificationForm {

    @NotBlank(message = "Il nome è obbligatorio")
    @Size(max = 150, message = "Massimo 150 caratteri")
    private String name;

    @NotBlank(message = "L'oggetto è obbligatorio")
    @Size(max = 255, message = "Massimo 255 caratteri")
    private String subject;

    @NotBlank(message = "Il testo è obbligatorio")
    @Size(max = 10000, message = "Massimo 10000 caratteri")
    private String body;

    /** Email addresses separated by comma, semicolon, space or new line. */
    @NotBlank(message = "Indica almeno un destinatario")
    private String recipients;

    @NotNull(message = "Seleziona la frequenza")
    private ScheduleType scheduleType = ScheduleType.MONTHLY;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate runDate;

    private Integer dayOfWeek;

    private Integer dayOfMonth;

    @NotNull(message = "Indica l'orario di invio")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime sendTime = LocalTime.of(9, 0);

    private boolean enabled = true;
}
