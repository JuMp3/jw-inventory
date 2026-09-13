package com.gp.jwinventory.service;

import com.gp.jwinventory.dto.NotificationEditView;
import com.gp.jwinventory.dto.NotificationForm;
import com.gp.jwinventory.dto.NotificationView;
import com.gp.jwinventory.dto.RecipientOption;
import com.gp.jwinventory.entity.ScheduledNotification;
import com.gp.jwinventory.entity.User;
import com.gp.jwinventory.enums.NotificationScope;
import com.gp.jwinventory.enums.Role;
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
 *   <li>The SuperAdmin sees and edits every notification.</li>
 *   <li>SUPER_ADMIN-scope notifications (created by the SuperAdmin) are visible only to the SuperAdmin.</li>
 *   <li>ADMIN-scope notifications are shared within the creator's congregation: its admins edit
 *       them, its users see them read-only.</li>
 *   <li>USER-scope notifications are private: only their owner sees and edits them
 *       (admins and other users do not see them).</li>
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
        List<ScheduledNotification> notifications = actor.getRole() == Role.SUPER_ADMIN
                ? notificationRepository.findAllOrderByName()
                : notificationRepository.findVisibleTo(actor.getId(), actor.getCongregation().getId());
        return notifications.stream()
                .map(n -> toView(n, canEdit(actor, n)))
                .toList();
    }

    @Transactional(readOnly = true)
    public NotificationEditView getEditView(Long id, String actorEmail) {
        User actor = userService.requireActor(actorEmail);
        ScheduledNotification notification = requireVisible(id, actor);
        return new NotificationEditView(toForm(notification), toView(notification, canEdit(actor, notification)));
    }

    /** Users the actor can pick as recipients (see {@link UserService#recipientCandidates}). */
    @Transactional(readOnly = true)
    public List<RecipientOption> listRecipientOptionsView(String actorEmail) {
        User actor = userService.requireActor(actorEmail);
        return userService.recipientCandidates(actor).stream()
                .map(u -> new RecipientOption(u.getEmail(), u.getFullName(), u.getCongregationName()))
                .toList();
    }

    @Transactional
    public Long create(NotificationForm form, String actorEmail) {
        User actor = userService.requireActor(actorEmail);
        LocalDateTime now = LocalDateTime.now(clock);
        ScheduledNotification notification = ScheduledNotification.builder()
                .scope(switch (actor.getRole()) {
                    case SUPER_ADMIN -> NotificationScope.SUPER_ADMIN;
                    case ADMIN -> NotificationScope.ADMIN;
                    case USER -> NotificationScope.USER;
                })
                .congregation(actor.getCongregation())
                .owner(actor)
                .ownerName(actor.getFullName())
                .createdAt(now)
                .build();
        apply(notification, form, now, actor);
        return notificationRepository.save(notification).getId();
    }

    @Transactional
    public void update(Long id, NotificationForm form, String actorEmail) {
        User actor = userService.requireActor(actorEmail);
        apply(requireEditable(id, actor), form, LocalDateTime.now(clock), actor);
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

    private void apply(ScheduledNotification notification, NotificationForm form, LocalDateTime now, User actor) {
        List<String> recipients = parseRecipients(form.getRecipients());
        if (recipients.isEmpty()) {
            throw new BusinessException("Indica almeno un destinatario");
        }
        if (recipients.size() > MAX_RECIPIENTS) {
            throw new BusinessException("Massimo " + MAX_RECIPIENTS + " destinatari");
        }
        requireSelectableRecipients(recipients, notification, actor);
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

    /**
     * Recipients must be users the actor can pick. Recipients already saved on the notification
     * are kept even if outside the actor's scope (e.g. added by the SuperAdmin to a congregation's
     * notification), so its admins can still save it.
     */
    private void requireSelectableRecipients(List<String> recipients, ScheduledNotification notification, User actor) {
        Set<String> allowed = new HashSet<>();
        userService.recipientCandidates(actor).forEach(u -> allowed.add(u.getEmail()));
        if (notification.getRecipients() != null) {
            allowed.addAll(parseRecipients(notification.getRecipients()));
        }
        for (String email : recipients) {
            if (!allowed.contains(email)) {
                throw new BusinessException("Destinatario non selezionabile: " + email + " (scegli un utente esistente)");
            }
        }
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
        if (!canView(actor, notification)) {
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

    private static boolean canView(User actor, ScheduledNotification notification) {
        if (actor.getRole() == Role.SUPER_ADMIN) {
            return true;
        }
        return switch (notification.getScope()) {
            case SUPER_ADMIN -> false;
            case ADMIN -> sameCongregation(actor, notification);
            case USER -> isOwner(actor, notification);
        };
    }

    private static boolean canEdit(User actor, ScheduledNotification notification) {
        if (actor.getRole() == Role.SUPER_ADMIN) {
            return true;
        }
        return switch (notification.getScope()) {
            case SUPER_ADMIN -> false;
            case ADMIN -> actor.getRole() == Role.ADMIN && sameCongregation(actor, notification);
            case USER -> isOwner(actor, notification);
        };
    }

    private static boolean sameCongregation(User actor, ScheduledNotification notification) {
        return actor.getCongregation() != null && notification.getCongregation() != null
                && notification.getCongregation().getId().equals(actor.getCongregation().getId());
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
                n.getCongregation() != null ? n.getCongregation().getName() : null, n.getOwnerName(), n.getNextRunAt(), n.getLastSentAt(), n.getLastError(), editable);
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
