package com.example.notesWeb.dtos.TodoListDto.notificatePopup;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ReminderDelay { //async & scheduling infrastructure
    private UUID todoID;
    private Instant trigger;
}
