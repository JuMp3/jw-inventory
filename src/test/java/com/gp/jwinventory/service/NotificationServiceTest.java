package com.gp.jwinventory.service;

import com.gp.jwinventory.BaseTest;
import com.gp.jwinventory.dto.NotificationForm;
import com.gp.jwinventory.dto.RecipientOption;
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
import static org.assertj.core.api.Assertions.tuple;
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
        form.setRecipients("USER-N@test.com; admin-n@test.com\nuser-n@test.com");
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
        assertThat(n.getCongregation().getName()).isEqualTo("Nord");
        assertThat(n.getRecipients()).isEqualTo("user-n@test.com, admin-n@test.com");
        assertThat(n.getNextRunAt()).isAfter(LocalDateTime.now().minusMinutes(1));
        assertThat(n.getNextRunAt().getDayOfMonth()).isEqualTo(5);
    }

    @Test
    void create_bySuperAdmin_isSuperAdminScoped_withoutCongregation() {
        Long id = notificationService.create(monthlyForm("Super"), superAdmin().getEmail());

        ScheduledNotification n = notificationRepository.findById(id).orElseThrow();
        assertThat(n.getScope()).isEqualTo(NotificationScope.SUPER_ADMIN);
        assertThat(n.getCongregation()).isNull();
    }

    @Test
    void visibility_followsRoleAndCongregation() {
        User northAdmin2 = newUser("admin-n2@test.com", Role.ADMIN, northAdmin.getCongregation());
        User northUser2 = newUser("user-n2@test.com", Role.USER, northAdmin.getCongregation());
        Long northAdminId = notificationService.create(monthlyForm("Admin N"), northAdmin.getEmail());
        NotificationForm southAdminForm = monthlyForm("Admin S");
        southAdminForm.setRecipients(southUser.getEmail());
        Long southAdminId = notificationService.create(southAdminForm, southAdmin.getEmail());
        Long superId = notificationService.create(monthlyForm("Super"), superAdmin().getEmail());
        Long northUserId = notificationService.create(monthlyForm("Personale N"), northUser.getEmail());
        NotificationForm southForm = monthlyForm("Personale S");
        southForm.setRecipients(southUser.getEmail());
        Long southUserId = notificationService.create(southForm, southUser.getEmail());

        // SuperAdmin: everything, editable
        assertThat(notificationService.listVisibleView(superAdmin().getEmail()))
                .extracting(v -> v.id(), v -> v.editable())
                .contains(tuple(northAdminId, true), tuple(southAdminId, true), tuple(superId, true),
                        tuple(northUserId, true), tuple(southUserId, true));

        // admin: only the admin notifications of its congregation, also when created by another admin
        assertThat(notificationService.listVisibleView(northAdmin2.getEmail()))
                .extracting(v -> v.id(), v -> v.editable())
                .containsExactly(tuple(northAdminId, true));

        // user: its congregation's admin notifications read-only plus its own, not other users'
        assertThat(notificationService.listVisibleView(northUser.getEmail()))
                .extracting(v -> v.id(), v -> v.editable())
                .containsExactlyInAnyOrder(tuple(northAdminId, false), tuple(northUserId, true));
        assertThat(notificationService.listVisibleView(northUser2.getEmail()))
                .extracting(v -> v.id(), v -> v.editable())
                .containsExactly(tuple(northAdminId, false));

        // direct access outside the visible set is a 404
        assertThatThrownBy(() -> notificationService.getEditView(superId, northAdmin.getEmail()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> notificationService.getEditView(southAdminId, northAdmin.getEmail()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> notificationService.getEditView(northUserId, northAdmin.getEmail()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> notificationService.getEditView(superId, northUser.getEmail()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> notificationService.getEditView(northUserId, northUser2.getEmail()))
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
    void adminOfSameCongregation_canEditAdminNotification_otherCongregationCannot() {
        Long adminId = notificationService.create(monthlyForm("Admin"), northAdmin.getEmail());
        User northAdmin2 = newUser("admin-n2@test.com", Role.ADMIN, northAdmin.getCongregation());

        notificationService.update(adminId, monthlyForm("Rinominata"), northAdmin2.getEmail());

        assertThat(notificationRepository.findById(adminId).orElseThrow().getName()).isEqualTo("Rinominata");
        assertThatThrownBy(() -> notificationService.update(adminId, monthlyForm("Hack"), southAdmin.getEmail()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> notificationService.delete(adminId, southAdmin.getEmail()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void superAdmin_canEditAnyNotification() {
        Long userId = notificationService.create(monthlyForm("Personale"), northUser.getEmail());

        notificationService.update(userId, monthlyForm("Corretta dal Super Admin"), superAdmin().getEmail());

        assertThat(notificationRepository.findById(userId).orElseThrow().getName()).isEqualTo("Corretta dal Super Admin");
    }

    @Test
    void recipientOptions_adminSeesOwnCongregation_superAdminSeesEveryone() {
        assertThat(notificationService.listRecipientOptionsView(northAdmin.getEmail()))
                .extracting(RecipientOption::email)
                .containsExactlyInAnyOrder("admin-n@test.com", "user-n@test.com");
        assertThat(notificationService.listRecipientOptionsView(superAdmin().getEmail()))
                .extracting(RecipientOption::email)
                .contains("admin-n@test.com", "user-n@test.com", "admin-s@test.com", "user-s@test.com");
    }

    @Test
    void recipientOutsideScope_isRejectedForAdmin_butAllowedForSuperAdmin() {
        NotificationForm form = monthlyForm("Cross");
        form.setRecipients("user-n@test.com, user-s@test.com");

        assertThatThrownBy(() -> notificationService.create(form, northAdmin.getEmail()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("user-s@test.com");
        assertThatThrownBy(() -> notificationService.create(form, northUser.getEmail()))
                .isInstanceOf(BusinessException.class);

        Long id = notificationService.create(form, superAdmin().getEmail());
        assertThat(notificationRepository.findById(id).orElseThrow().getRecipients())
                .isEqualTo("user-n@test.com, user-s@test.com");
    }

    @Test
    void unknownRecipient_isRejected() {
        NotificationForm form = monthlyForm("Sconosciuto");
        form.setRecipients("user-n@test.com, nobody@test.com");

        assertThatThrownBy(() -> notificationService.create(form, superAdmin().getEmail()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("nobody@test.com");
    }

    @Test
    void adminNotification_keepsRecipientsAddedBySuperAdmin() {
        Long adminId = notificationService.create(monthlyForm("Admin Nord"), northAdmin.getEmail());
        NotificationForm form = monthlyForm("Admin Nord");
        form.setRecipients("user-n@test.com, user-s@test.com");
        notificationService.update(adminId, form, superAdmin().getEmail());

        // a north admin can still save it although user-s is outside its congregation
        notificationService.update(adminId, form, northAdmin.getEmail());

        assertThat(notificationRepository.findById(adminId).orElseThrow().getRecipients())
                .isEqualTo("user-n@test.com, user-s@test.com");
    }

    @Test
    void invalidRecipient_isRejected() {
        NotificationForm form = monthlyForm("Errata");
        form.setRecipients("user-n@test.com, not-an-email");

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
