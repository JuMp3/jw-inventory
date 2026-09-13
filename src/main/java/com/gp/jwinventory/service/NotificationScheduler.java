package com.gp.jwinventory.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polls for due notifications. Row locks in dispatchDue keep concurrent instances from double-sending. */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.notifications.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class NotificationScheduler {

    private final NotificationService notificationService;

    @Scheduled(fixedDelayString = "${app.notifications.poll-interval-ms:60000}",
            initialDelayString = "${app.notifications.poll-interval-ms:60000}")
    public void dispatch() {
        int processed = notificationService.dispatchDue();
        if (processed > 0) {
            log.info("Processed {} scheduled notification(s)", processed);
        }
    }
}
