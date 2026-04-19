package com.example.notesWeb.exception.kafka.handleStaging;

import com.example.notesWeb.dtos.NoteDto.NoteUpdateEvent;
import com.example.notesWeb.model.takeNotes.StagedUpdate;
import com.example.notesWeb.repository.noteRepo.StagingRepo;
import com.example.notesWeb.service.takeNotes.TaskNoteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class noteRetryScheduler {
    private final StagingRepo stagingRepo;
    private final TaskNoteService taskNoteService;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    //Run 10s times once
    @Scheduled(fixedDelay = 10000)
    public void retryFailedUpdates() {
        List<StagedUpdate> failedEvents = stagingRepo.findAll(
                PageRequest.of(0, 50, Sort.by("id").ascending())
        ).getContent();

        if (failedEvents.isEmpty()) return;
        log.info("Trying again {} failed the comment...", failedEvents.size());

        for (StagedUpdate stagedUpdate : failedEvents) {
            if (stagedUpdate.getRetryCount() >= 5) {
                log.error("Note {} has retried more than 5 times. Stop processing to check manually!", stagedUpdate.getNoteID());
                continue;
            }
            processRetry(stagedUpdate);
        }
    }

    private void processRetry (StagedUpdate stagedUpdate) {
        try {
            //Parse JSON from rawPayload back to the original Object
            NoteUpdateEvent event = objectMapper.readValue(
                    stagedUpdate.getRawPayload(), NoteUpdateEvent.class);
            taskNoteService.updateNote(event);
            stagingRepo.delete(stagedUpdate);
            log.info("Note: {} has been successfully recovered", stagedUpdate.getNoteID());

            messagingTemplate.convertAndSendToUser(
                    event.getUsername(),
                    "/queue/note-updates",
                    "Your Notes have been updated successfully!"
            );
        } catch (Exception e) {
            //Increase retry_count
            stagedUpdate.setRetryCount(stagedUpdate.getRetryCount() + 1);
            stagedUpdate.setErrorReason("Retry failed: " + e.getMessage());
            stagingRepo.save(stagedUpdate);
            log.warn("Unable to recover Note: {}. Attempt: {}", stagedUpdate.getNoteID(), stagedUpdate.getRetryCount());
        }
    }
}
