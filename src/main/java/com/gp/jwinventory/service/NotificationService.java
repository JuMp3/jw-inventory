package com.gp.jwinventory.service;

import com.gp.jwinventory.dto.NotificationEditView;
import com.gp.jwinventory.dto.NotificationForm;
import com.gp.jwinventory.dto.NotificationView;
import com.gp.jwinventory.entity.ScheduledNotification;
import com.gp.jwinventory.entity.User;
import com.gp.jwinventory.enums.NotificationScope;
import com.gp.jwinventory.exceptions.BusinessException;
import com.gp.jwinventory.exceptions.NotFoundException;
import com.gp.jwinventory.repository.ScheduledNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Visibility and edit rules:
 * <ul>
 *   <li>ADMIN-scope notifications (created by ADMIN/SuperAdmin) are shared: every admin can
 *       edit them, every user sees them read-only.</li>
 *   <li>USER-scope notifications are private: only their owner sees and edits them
 *       (admins do not see them).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    static final int MAX_RECIPIENTS = 50;
    private static final int MAX_ERROR_LENGTH = 1000;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final ScheduledNotificationRepository notificationRepository;
    private final UserService userService;
    private final EmailService emailService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<NotificationView> listVisibleView(String actorEmail) {
        User actor = userService.requireActor(actorEmail);
        return notificationRepository.findVisibleTo(actor.getId()).stream()
                .map(n -> toView(n, canEdit(actor, n)))
                .toList();
    }

    @Transactional(readOnly = true)
    public NotificationEditView getEditView(Long id, String actorEmail) {
        User actor = userService.requireActor(actorEmail);
        ScheduledNotification notification = requireVisible(id, actor);
        return new NotificationEditView(toForm(notification), toView(notification, canEdit(actor, notification)));
    }

    @Transactional
    public Long create(NotificationForm form, String actorEmail) {
        User actor = userService.requireActor(actorEmail);
        LocalDateTime now = LocalDateTime.now(clock);
        ScheduledNotification notification = ScheduledNotification.builder()
                .scope(actor.getRole().isAdmin() ? NotificationScope.ADMIN : NotificationScope.USER)
                .owner(actor)
                .ownerName(actor.getFullName())
                .createdAt(now)
                .build();
        apply(notification, form, now);
        return notificationRepository.save(notification).getId();
    }

    @Transactional
    public void update(Long id, NotificationForm form, String actorEmail) {
        User actor = userService.requireActor(actorEmail);
        apply(requireEditable(id, actor), form, LocalDateTime.now(clock));
    }

    @Transactional
    public void delete(Long id, String actorEmail) {
        User actor = userService.requireActor(actorEmail);
        notificationRepository.delete(requireEditable(id, actor));
    }

    /** Immediate test send; does not change the schedule. */
    @Transactional
    public void sendNow(Long id, String actorEmail) {
        User actor = userService.requireActor(actorEmail);
        ScheduledNotification notification = requireEditable(id, actor);
        try {
            send(notification);
        } catch (RuntimeException e) {
            log.warn("Test send of notification {} failed", id, e);
            throw new BusinessException("Invio non riuscito: " + e.getMessage());
        }
    }

    /**
     * Sends every due notification and moves it to its next occurrence. A failed send is
     * recorded in lastError and not retried (the next occurrence is scheduled anyway).
     * Occurrences missed while the app was down are sent once at the next run.
     */
    @Transactional
    public int dispatchDue() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<ScheduledNotification> due = notificationRepository.findDueForUpdate(now);
        for (ScheduledNotification notification : due) {
            try {
                send(notification);
                notification.setLastSentAt(now);
                notification.setLastError(null);
            } catch (RuntimeException e) {
                log.error("Scheduled notification {} failed", notification.getId(), e);
                notification.setLastError(truncate(e.getMessage()));
            }
            Optional<LocalDateTime> next = ruleOf(notification).nextAfter(now);
            notification.setNextRunAt(next.orElse(null));
            if (next.isEmpty()) {
                notification.setEnabled(false);
            }
        }
        return due.size();
    }

    static List<String> parseRecipients(String raw) {
        if (raw == null) {
            return List.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String token : raw.split("[,;\\s]+")) {
            if (token.isBlank()) {
                continue;
            }
            String email = token.trim().toLowerCase(Locale.ROOT);
            if (!EMAIL.matcher(email).matches()) {
                throw new BusinessException("Indirizzo email non valido: " + token.trim());
            }
            result.add(email);
        }
        return new ArrayList<>(result);
    }

    private void send(ScheduledNotification notification) {
        emailService.send(parseRecipients(notification.getRecipients()), notification.getSubject(),
                notification.getBody());
    }

    private void apply(ScheduledNotification notification, NotificationForm form, LocalDateTime now) {
        List<String> recipients = parseRecipients(form.getRecipients());
        if (recipients.isEmpty()) {
            throw new BusinessException("Indica almeno un destinatario");
        }
        if (recipients.size() > MAX_RECIPIENTS) {
            throw new BusinessException("Massimo " + MAX_RECIPIENTS + " destinatari");
        }
        ScheduleRule rule = validateRule(form);

        notification.setName(form.getName().trim());
        notification.setSubject(form.getSubject().trim());
        notification.setBody(form.getBody());
        notification.setRecipients(String.join(", ", recipients));
        notification.setScheduleType(rule.type());
        notification.setRunDate(rule.runDate());
        notification.setDayOfWeek(rule.dayOfWeek());
        notification.setDayOfMonth(rule.dayOfMonth());
        notification.setSendTime(rule.sendTime());
        notification.setEnabled(form.isEnabled());
        notification.setNextRunAt(form.isEnabled()
                ? rule.nextAfter(now).orElseThrow(() -> new BusinessException("La data e l'ora di invio sono già passate"))
                : null);
        notification.setUpdatedAt(now);
    }

    private static ScheduleRule validateRule(NotificationForm form) {
        if (form.getScheduleType() == null || form.getSendTime() == null) {
            throw new BusinessException("Indica frequenza e orario di invio");
        }
        return switch (form.getScheduleType()) {
            case ONCE -> {
                if (form.getRunDate() == null) {
                    throw new BusinessException("Indica la data di invio");
                }
                yield new ScheduleRule(form.getScheduleType(), form.getRunDate(), null, null, form.getSendTime());
            }
            case DAILY -> new ScheduleRule(form.getScheduleType(), null, null, null, form.getSendTime());
            case WEEKLY -> {
                if (form.getDayOfWeek() == null || form.getDayOfWeek() < 1 || form.getDayOfWeek() > 7) {
                    throw new BusinessException("Seleziona il giorno della settimana");
                }
                yield new ScheduleRule(form.getScheduleType(), null, form.getDayOfWeek(), null, form.getSendTime());
            }
            case MONTHLY -> {
                if (form.getDayOfMonth() == null || form.getDayOfMonth() < 1 || form.getDayOfMonth() > 31) {
                    throw new BusinessException("Il giorno del mese deve essere tra 1 e 31");
                }
                yield new ScheduleRule(form.getScheduleType(), null, null, form.getDayOfMonth(), form.getSendTime());
            }
        };
    }

    private ScheduledNotification requireVisible(Long id, User actor) {
        ScheduledNotification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Notifica non trovata"));
        if (notification.getScope() != NotificationScope.ADMIN && !isOwner(actor, notification)) {
            throw new NotFoundException("Notifica non trovata");
        }
        return notification;
    }

    private ScheduledNotification requireEditable(Long id, User actor) {
        ScheduledNotification notification = requireVisible(id, actor);
        if (!canEdit(actor, notification)) {
            throw new AccessDeniedException("Notifica in sola lettura");
        }
        return notification;
    }

    private static boolean canEdit(User actor, ScheduledNotification notification) {
        return actor.getRole().isAdmin()
                ? notification.getScope() == NotificationScope.ADMIN || isOwner(actor, notification)
                : notification.getScope() == NotificationScope.USER && isOwner(actor, notification);
    }

    private static boolean isOwner(User actor, ScheduledNotification notification) {
        return notification.getOwner() != null && notification.getOwner().getId().equals(actor.getId());
    }

    private static ScheduleRule ruleOf(ScheduledNotification n) {
        return new ScheduleRule(n.getScheduleType(), n.getRunDate(), n.getDayOfWeek(), n.getDayOfMonth(), n.getSendTime());
    }

    private static NotificationView toView(ScheduledNotification n, boolean editable) {
        return new NotificationView(n.getId(), n.getName(), n.getSubject(), n.getRecipients(),
                n.getRecipients().split(",").length, ruleOf(n).describe(), n.isEnabled(), n.getScope(),
                n.getOwnerName(), n.getNextRunAt(), n.getLastSentAt(), n.getLastError(), editable);
    }

    private static NotificationForm toForm(ScheduledNotification n) {
        NotificationForm form = new NotificationForm();
        form.setName(n.getName());
        form.setSubject(n.getSubject());
        form.setBody(n.getBody());
        form.setRecipients(n.getRecipients());
        form.setScheduleType(n.getScheduleType());
        form.setRunDate(n.getRunDate());
        form.setDayOfWeek(n.getDayOfWeek());
        form.setDayOfMonth(n.getDayOfMonth());
        form.setSendTime(n.getSendTime());
        form.setEnabled(n.isEnabled());
        return form;
    }

    private static String truncate(String message) {
        String value = message == null ? "Errore sconosciuto" : message;
        return value.length() > MAX_ERROR_LENGTH ? value.substring(0, MAX_ERROR_LENGTH) : value;
    }
}
