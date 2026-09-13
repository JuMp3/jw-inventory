package com.gp.jwinventory.service;

import com.gp.jwinventory.BaseTest;
import com.gp.jwinventory.dto.NotificationForm;
import com.gp.jwinventory.entity.Congregation;
import com.gp.jwinventory.entity.ScheduledNotification;
import com.gp.jwinventory.entity.User;
import com.gp.jwinventory.enums.NotificationScope;
import com.gp.jwinventory.enums.Role;
import com.gp.jwinventory.enums.ScheduleType;
import com.gp.jwinventory.exceptions.BusinessException;
import com.gp.jwinventory.exceptions.NotFoundException;
import com.gp.jwinventory.repository.ScheduledNotificationRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailSendException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Transactional
class NotificationServiceTest extends BaseTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ScheduledNotificationRepository notificationRepository;

    private User northAdmin;
    private User southAdmin;
    private User northUser;
    private User southUser;

    @BeforeEach
    void setUp() {
        Congregation north = newCongregation("Nord");
        Congregation south = newCongregation("Sud");
        northAdmin = newUser("admin-n@test.com", Role.ADMIN, north);
        southAdmin = newUser("admin-s@test.com", Role.ADMIN, south);
        northUser = newUser("user-n@test.com", Role.USER, north);
        southUser = newUser("user-s@test.com", Role.USER, south);
        doReturn(new MimeMessage((Session) null)).when(mailSender).createMimeMessage();
    }

    private static NotificationForm monthlyForm(String name) {
        NotificationForm form = new NotificationForm();
        form.setName(name);
        form.setSubject("Inventario entro il 10");
        form.setBody("Ricordati di fare l'inventario.\nGrazie");
        form.setRecipients("A@test.com; b@test.com\nA@test.com");
        form.setScheduleType(ScheduleType.MONTHLY);
        form.setDayOfMonth(5);
        form.setSendTime(LocalTime.of(9, 0));
        return form;
    }

    @Test
    void create_byAdmin_isAdminScoped_normalizedAndScheduled() {
        Long id = notificationService.create(monthlyForm("Promemoria"), northAdmin.getEmail());

        ScheduledNotification n = notificationRepository.findById(id).orElseThrow();
        assertThat(n.getScope()).isEqualTo(NotificationScope.ADMIN);
        assertThat(n.getRecipients()).isEqualTo("a@test.com, b@test.com");
        assertThat(n.getNextRunAt()).isAfter(LocalDateTime.now().minusMinutes(1));
        assertThat(n.getNextRunAt().getDayOfMonth()).isEqualTo(5);
    }

    @Test
    void visibility_adminNotificationsAreGlobal_userNotificationsArePrivate() {
        Long adminId = notificationService.create(monthlyForm("Admin"), northAdmin.getEmail());
        Long northUserId = notificationService.create(monthlyForm("Personale N"), northUser.getEmail());
        Long southUserId = notificationService.create(monthlyForm("Personale S"), southUser.getEmail());

        // admins (any congregation) see and edit ADMIN notifications, never the users' ones
        assertThat(notificationService.listVisibleView(southAdmin.getEmail()))
                .extracting(v -> v.id(), v -> v.editable())
                .containsExactly(org.assertj.core.groups.Tuple.tuple(adminId, true));

        // a user sees admin notifications read-only plus its own, not other users'
        assertThat(notificationService.listVisibleView(northUser.getEmail()))
                .extracting(v -> v.id(), v -> v.editable())
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(adminId, false),
                        org.assertj.core.groups.Tuple.tuple(northUserId, true));

        assertThatThrownBy(() -> notificationService.getEditView(southUserId, northUser.getEmail()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> notificationService.getEditView(northUserId, northAdmin.getEmail()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void user_cannotModifyAdminNotification() {
        Long adminId = notificationService.create(monthlyForm("Admin"), northAdmin.getEmail());

        assertThatThrownBy(() -> notificationService.update(adminId, monthlyForm("Hack"), northUser.getEmail()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> notificationService.delete(adminId, northUser.getEmail()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> notificationService.sendNow(adminId, northUser.getEmail()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void anotherAdmin_canEditAdminNotification() {
        Long adminId = notificationService.create(monthlyForm("Admin"), northAdmin.getEmail());

        notificationService.update(adminId, monthlyForm("Rinominata"), southAdmin.getEmail());

        assertThat(notificationRepository.findById(adminId).orElseThrow().getName()).isEqualTo("Rinominata");
    }

    @Test
    void invalidRecipient_isRejected() {
        NotificationForm form = monthlyForm("Errata");
        form.setRecipients("ok@test.com, not-an-email");

        assertThatThrownBy(() -> notificationService.create(form, northUser.getEmail()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not-an-email");
    }

    @Test
    void onceInThePast_isRejectedWhenEnabled_butAllowedWhenDisabled() {
        NotificationForm form = monthlyForm("Passata");
        form.setScheduleType(ScheduleType.ONCE);
        form.setRunDate(LocalDate.now().minusDays(1));

        assertThatThrownBy(() -> notificationService.create(form, northUser.getEmail()))
                .isInstanceOf(BusinessException.class);

        form.setEnabled(false);
        Long id = notificationService.create(form, northUser.getEmail());
        assertThat(notificationRepository.findById(id).orElseThrow().getNextRunAt()).isNull();
    }

    @Test
    void dispatchDue_sendsEmail_andMovesToNextOccurrence() {
        Long id = notificationService.create(monthlyForm("Mensile"), northAdmin.getEmail());
        ScheduledNotification n = notificationRepository.findById(id).orElseThrow();
        n.setNextRunAt(LocalDateTime.now().minusMinutes(1));

        assertThat(notificationService.dispatchDue()).isEqualTo(1);

        verify(mailSender).send(any(MimeMessage.class));
        assertThat(n.getLastSentAt()).isNotNull();
        assertThat(n.getLastError()).isNull();
        assertThat(n.getNextRunAt()).isAfter(LocalDateTime.now());
        assertThat(n.isEnabled()).isTrue();
    }

    @Test
    void dispatchDue_onceNotification_isDisabledAfterSending() {
        NotificationForm form = monthlyForm("Una volta");
        form.setScheduleType(ScheduleType.ONCE);
        form.setRunDate(LocalDate.now().plusDays(1));
        Long id = notificationService.create(form, northUser.getEmail());
        ScheduledNotification n = notificationRepository.findById(id).orElseThrow();
        n.setRunDate(LocalDate.now().minusDays(1));
        n.setNextRunAt(LocalDateTime.now().minusDays(1));

        notificationService.dispatchDue();

        verify(mailSender).send(any(MimeMessage.class));
        assertThat(n.isEnabled()).isFalse();
        assertThat(n.getNextRunAt()).isNull();
    }

    @Test
    void dispatchDue_sendFailure_recordsErrorAndStillReschedules() {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(MimeMessage.class));
        Long id = notificationService.create(monthlyForm("Fallita"), northAdmin.getEmail());
        ScheduledNotification n = notificationRepository.findById(id).orElseThrow();
        n.setNextRunAt(LocalDateTime.now().minusMinutes(1));

        notificationService.dispatchDue();

        assertThat(n.getLastError()).contains("SMTP down");
        assertThat(n.getLastSentAt()).isNull();
        assertThat(n.getNextRunAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void dispatchDue_ignoresDisabledAndFutureNotifications() {
        Long id = notificationService.create(monthlyForm("Futura"), northAdmin.getEmail());
        NotificationForm disabled = monthlyForm("Spenta");
        disabled.setEnabled(false);
        notificationService.create(disabled, northAdmin.getEmail());

        assertThat(notificationService.dispatchDue()).isZero();
        verify(mailSender, never()).send(any(MimeMessage.class));
        assertThat(notificationRepository.findById(id).orElseThrow().getLastSentAt()).isNull();
    }
}
