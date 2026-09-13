package com.gp.jwinventory.service;

import com.gp.jwinventory.dto.PasswordChangeForm;
import com.gp.jwinventory.dto.UserForm;
import com.gp.jwinventory.dto.UserView;
import com.gp.jwinventory.entity.Congregation;
import com.gp.jwinventory.entity.User;
import com.gp.jwinventory.enums.NotificationScope;
import com.gp.jwinventory.enums.Role;
import com.gp.jwinventory.exceptions.BusinessException;
import com.gp.jwinventory.exceptions.NotFoundException;
import com.gp.jwinventory.repository.CongregationRepository;
import com.gp.jwinventory.repository.ScheduledNotificationRepository;
import com.gp.jwinventory.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * User management scoping: the SuperAdmin manages users of every congregation, an ADMIN
 * only those of its own congregation. Nobody can create, edit or delete the SuperAdmin.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final CongregationRepository congregationRepository;
    private final ScheduledNotificationRepository notificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    /** Service-internal: resolves the authenticated user (runs in the caller's transaction). */
    @Transactional(readOnly = true)
    public User requireActor(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AccessDeniedException("Utente non trovato"));
    }

    @Transactional(readOnly = true)
    public UserView getProfileView(String email) {
        return toView(requireActor(email));
    }

    @Transactional(readOnly = true)
    public List<UserView> listManageableView(String actorEmail) {
        User actor = requireAdmin(actorEmail);
        List<User> users = actor.getRole() == Role.SUPER_ADMIN
                ? userRepository.findAllByOrderByFullNameAsc()
                : userRepository.findByCongregationIdOrderByFullNameAsc(actor.getCongregation().getId());
        return users.stream().map(UserService::toView).toList();
    }

    /**
     * Service-internal: users that can be picked as notification recipients by the actor.
     * SuperAdmin: every user; anyone else: the users of its own congregation.
     */
    @Transactional(readOnly = true)
    public List<User> recipientCandidates(User actor) {
        return actor.getRole() == Role.SUPER_ADMIN
                ? userRepository.findAllByOrderByFullNameAsc()
                : userRepository.findByCongregationIdOrderByFullNameAsc(actor.getCongregation().getId());
    }

    @Transactional(readOnly = true)
    public UserForm getForm(Long id, String actorEmail) {
        User target = requireManageable(id, requireAdmin(actorEmail));
        UserForm form = new UserForm();
        form.setFullName(target.getFullName());
        form.setEmail(target.getEmail());
        form.setRole(target.getRole());
        form.setCongregationId(target.getCongregation().getId());
        form.setEnabled(target.isEnabled());
        return form;
    }

    @Transactional
    public Long create(UserForm form, String actorEmail) {
        User actor = requireAdmin(actorEmail);
        requireAssignableRole(form.getRole());
        String email = normalizeEmail(form.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("Esiste già un utente con questa email");
        }
        validatePassword(form.getPassword());
        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(form.getPassword()))
                .fullName(form.getFullName().trim())
                .role(form.getRole())
                .congregation(resolveCongregation(actor, form.getCongregationId()))
                .enabled(form.isEnabled())
                .createdAt(LocalDateTime.now(clock))
                .build();
        return userRepository.save(user).getId();
    }

    @Transactional
    public void update(Long id, UserForm form, String actorEmail) {
        User actor = requireAdmin(actorEmail);
        User target = requireManageable(id, actor);
        requireAssignableRole(form.getRole());
        String email = normalizeEmail(form.getEmail());
        if (userRepository.existsByEmailAndIdNot(email, id)) {
            throw new BusinessException("Esiste già un utente con questa email");
        }
        if (target.getId().equals(actor.getId()) && (form.getRole() != target.getRole() || !form.isEnabled())) {
            throw new BusinessException("Non puoi cambiare il tuo ruolo o disattivare il tuo account");
        }
        target.setEmail(email);
        target.setFullName(form.getFullName().trim());
        target.setRole(form.getRole());
        target.setCongregation(resolveCongregation(actor, form.getCongregationId()));
        target.setEnabled(form.isEnabled());
        if (StringUtils.hasText(form.getPassword())) {
            validatePassword(form.getPassword());
            target.setPassword(passwordEncoder.encode(form.getPassword()));
        }
    }

    @Transactional
    public void delete(Long id, String actorEmail) {
        User actor = requireAdmin(actorEmail);
        User target = requireManageable(id, actor);
        if (target.getId().equals(actor.getId())) {
            throw new BusinessException("Non puoi eliminare il tuo account");
        }
        // personal notifications die with their owner; ADMIN ones survive (owner_id ON DELETE SET NULL)
        notificationRepository.deleteByScopeAndOwnerId(NotificationScope.USER, target.getId());
        userRepository.delete(target);
    }

    @Transactional
    public void changeOwnPassword(String email, PasswordChangeForm form) {
        User user = requireActor(email);
        if (user.getRole() == Role.SUPER_ADMIN) {
            throw new BusinessException("La password del Super Admin è gestita dalla configurazione (SUPERADMIN_PASSWORD)");
        }
        if (!passwordEncoder.matches(form.getCurrentPassword(), user.getPassword())) {
            throw new BusinessException("La password attuale non è corretta");
        }
        if (!Objects.equals(form.getNewPassword(), form.getConfirmPassword())) {
            throw new BusinessException("Le due password non coincidono");
        }
        validatePassword(form.getNewPassword());
        user.setPassword(passwordEncoder.encode(form.getNewPassword()));
    }

    private User requireAdmin(String actorEmail) {
        User actor = requireActor(actorEmail);
        if (!actor.getRole().isAdmin()) {
            throw new AccessDeniedException("Operazione riservata agli amministratori");
        }
        return actor;
    }

    private User requireManageable(Long id, User actor) {
        User target = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Utente non trovato"));
        if (target.getRole() == Role.SUPER_ADMIN) {
            throw new AccessDeniedException("Il Super Admin non può essere modificato né eliminato");
        }
        if (actor.getRole() != Role.SUPER_ADMIN
                && !target.getCongregation().getId().equals(actor.getCongregation().getId())) {
            throw new AccessDeniedException("L'utente appartiene a un'altra congregazione");
        }
        return target;
    }

    private Congregation resolveCongregation(User actor, Long congregationId) {
        if (actor.getRole() != Role.SUPER_ADMIN) {
            return actor.getCongregation();
        }
        if (congregationId == null) {
            throw new BusinessException("Seleziona una congregazione");
        }
        return congregationRepository.findById(congregationId)
                .orElseThrow(() -> new BusinessException("Congregazione non valida"));
    }

    private static void requireAssignableRole(Role role) {
        if (role == null || !role.isAssignable()) {
            throw new BusinessException("Ruolo non assegnabile");
        }
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessException("La password deve avere almeno " + MIN_PASSWORD_LENGTH + " caratteri");
        }
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static UserView toView(User user) {
        return new UserView(user.getId(), user.getEmail(), user.getFullName(), user.getRole(),
                user.getCongregationName(), user.isEnabled());
    }
}
