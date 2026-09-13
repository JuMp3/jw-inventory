package com.gp.jwinventory.service;

import com.gp.jwinventory.BaseTest;
import com.gp.jwinventory.dto.PasswordChangeForm;
import com.gp.jwinventory.dto.UserForm;
import com.gp.jwinventory.entity.Congregation;
import com.gp.jwinventory.entity.ScheduledNotification;
import com.gp.jwinventory.entity.User;
import com.gp.jwinventory.enums.NotificationScope;
import com.gp.jwinventory.enums.Role;
import com.gp.jwinventory.enums.ScheduleType;
import com.gp.jwinventory.exceptions.BusinessException;
import com.gp.jwinventory.repository.ScheduledNotificationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class UserServiceTest extends BaseTest {

    @Autowired
    private UserService userService;

    @Autowired
    private ScheduledNotificationRepository notificationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    private Congregation north;
    private Congregation south;
    private User northAdmin;

    @BeforeEach
    void setUp() {
        north = newCongregation("Nord");
        south = newCongregation("Sud");
        northAdmin = newUser("admin-nord@test.com", Role.ADMIN, north);
    }

    private static UserForm form(String email, Role role, Long congregationId) {
        UserForm form = new UserForm();
        form.setFullName("Nome " + email);
        form.setEmail(email);
        form.setRole(role);
        form.setCongregationId(congregationId);
        form.setPassword("password123");
        return form;
    }

    @Test
    void superAdmin_isBootstrappedFromConfiguration_withoutCongregation() {
        User superAdmin = superAdmin();

        assertThat(superAdmin.getEmail()).isEqualTo("superadmin@test.com");
        assertThat(superAdmin.getCongregation()).isNull();
        assertThat(passwordEncoder.matches("superadmin123", superAdmin.getPassword())).isTrue();
    }

    @Test
    void admin_createsUserInOwnCongregation_ignoringRequestedCongregation() {
        Long id = userService.create(form(" New.User@Test.com ", Role.USER, south.getId()), northAdmin.getEmail());

        User created = userRepository.findById(id).orElseThrow();
        assertThat(created.getEmail()).isEqualTo("new.user@test.com");
        assertThat(created.getCongregation().getId()).isEqualTo(north.getId());
        assertThat(passwordEncoder.matches("password123", created.getPassword())).isTrue();
    }

    @Test
    void superAdmin_createsUserInChosenCongregation() {
        Long id = userService.create(form("south-admin@test.com", Role.ADMIN, south.getId()), superAdmin().getEmail());

        assertThat(userRepository.findById(id).orElseThrow().getCongregation().getId()).isEqualTo(south.getId());
    }

    @Test
    void admin_seesAndManagesOnlyOwnCongregation() {
        User southUser = newUser("user-sud@test.com", Role.USER, south);

        assertThat(userService.listManageableView(northAdmin.getEmail()))
                .allSatisfy(u -> assertThat(u.congregationName()).isEqualTo("Nord"));
        assertThatThrownBy(() -> userService.update(southUser.getId(), form("user-sud@test.com", Role.ADMIN, null), northAdmin.getEmail()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> userService.delete(southUser.getId(), northAdmin.getEmail()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void superAdminRole_cannotBeAssigned() {
        assertThatThrownBy(() -> userService.create(form("second-sa@test.com", Role.SUPER_ADMIN, north.getId()), superAdmin().getEmail()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void superAdmin_cannotBeEditedOrDeleted() {
        Long superAdminId = superAdmin().getId();

        assertThatThrownBy(() -> userService.delete(superAdminId, superAdmin().getEmail()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> userService.getForm(superAdminId, northAdmin.getEmail()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void plainUser_cannotManageUsers() {
        User user = newUser("plain@test.com", Role.USER, north);

        assertThatThrownBy(() -> userService.listManageableView(user.getEmail()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> userService.create(form("x@test.com", Role.USER, null), user.getEmail()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void admin_cannotDeleteOrDemoteSelf() {
        assertThatThrownBy(() -> userService.delete(northAdmin.getId(), northAdmin.getEmail()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> userService.update(northAdmin.getId(), form(northAdmin.getEmail(), Role.USER, null), northAdmin.getEmail()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void admin_cannotDeactivateSelf_butCanDeactivateAndDeleteOthers() {
        UserForm self = form(northAdmin.getEmail(), Role.ADMIN, null);
        self.setPassword("");
        self.setEnabled(false);
        assertThatThrownBy(() -> userService.update(northAdmin.getId(), self, northAdmin.getEmail()))
                .isInstanceOf(BusinessException.class);
        assertThat(northAdmin.isEnabled()).isTrue();

        User other = newUser("other-admin@test.com", Role.ADMIN, north);
        UserForm otherForm = form(other.getEmail(), Role.ADMIN, null);
        otherForm.setPassword("");
        otherForm.setEnabled(false);
        userService.update(other.getId(), otherForm, northAdmin.getEmail());
        assertThat(other.isEnabled()).isFalse();

        userService.delete(other.getId(), northAdmin.getEmail());
        assertThat(userRepository.findById(other.getId())).isEmpty();
    }

    @Test
    void plainUser_cannotDeleteOrDeactivateSelf() {
        User user = newUser("self-user@test.com", Role.USER, north);
        UserForm form = form(user.getEmail(), Role.USER, null);
        form.setEnabled(false);

        assertThatThrownBy(() -> userService.delete(user.getId(), user.getEmail()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> userService.update(user.getId(), form, user.getEmail()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void update_blankPassword_keepsCurrentOne() {
        User user = newUser("keep@test.com", Role.USER, north);
        String hash = user.getPassword();
        UserForm form = form("keep@test.com", Role.ADMIN, null);
        form.setPassword("");

        userService.update(user.getId(), form, northAdmin.getEmail());

        assertThat(user.getPassword()).isEqualTo(hash);
        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void delete_removesPersonalNotifications_butKeepsAdminOnes() {
        User leavingAdmin = newUser("leaving@test.com", Role.ADMIN, north);
        Long personalId = notificationRepository.save(notification(leavingAdmin, NotificationScope.USER)).getId();
        Long sharedId = notificationRepository.save(notification(leavingAdmin, NotificationScope.ADMIN)).getId();
        // like a real request: the deletion runs without the notifications loaded in the persistence context
        entityManager.flush();
        entityManager.clear();

        userService.delete(leavingAdmin.getId(), northAdmin.getEmail());
        entityManager.flush();
        entityManager.clear();

        assertThat(notificationRepository.findById(personalId)).isEmpty();
        assertThat(notificationRepository.findById(sharedId)).get()
                .satisfies(n -> assertThat(n.getOwner()).isNull());
    }

    @Test
    void changeOwnPassword_requiresCurrentPassword() {
        User user = newUser("pwd@test.com", Role.USER, north);
        user.setPassword(passwordEncoder.encode("oldpassword"));
        PasswordChangeForm form = new PasswordChangeForm();
        form.setCurrentPassword("wrong");
        form.setNewPassword("newpassword");
        form.setConfirmPassword("newpassword");

        assertThatThrownBy(() -> userService.changeOwnPassword(user.getEmail(), form))
                .isInstanceOf(BusinessException.class);

        form.setCurrentPassword("oldpassword");
        userService.changeOwnPassword(user.getEmail(), form);
        assertThat(passwordEncoder.matches("newpassword", user.getPassword())).isTrue();
    }

    private static ScheduledNotification notification(User owner, NotificationScope scope) {
        LocalDateTime now = LocalDateTime.now();
        return ScheduledNotification.builder()
                .name("n-" + scope).subject("s").body("b").recipients("a@test.com")
                .scheduleType(ScheduleType.DAILY).sendTime(LocalTime.of(9, 0)).enabled(true)
                .scope(scope).congregation(owner.getCongregation()).owner(owner).ownerName(owner.getFullName())
                .createdAt(now).updatedAt(now)
                .build();
    }
}
