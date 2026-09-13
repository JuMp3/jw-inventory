package com.gp.jwinventory.controller;

import com.gp.jwinventory.BaseTest;
import com.gp.jwinventory.dto.NotificationForm;
import com.gp.jwinventory.dto.PublicationForm;
import com.gp.jwinventory.entity.User;
import com.gp.jwinventory.enums.Role;
import com.gp.jwinventory.enums.ScheduleType;
import com.gp.jwinventory.service.CustomUserDetailsService;
import com.gp.jwinventory.service.InventoryService;
import com.gp.jwinventory.service.NotificationService;
import com.gp.jwinventory.service.PublicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Renders every Thymeleaf page with realistic data, so template errors fail the build. */
@AutoConfigureMockMvc
@Transactional
class PageRenderingTest extends BaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PublicationService publicationService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private NotificationService notificationService;

    private User superAdmin;
    private User member;
    private Long publicationId;
    private Long closedInventoryId;
    private Long openInventoryId;
    private Long adminNotificationId;

    @BeforeEach
    void setUp() {
        superAdmin = superAdmin();
        member = newUser("member-pages@test.com", Role.USER, newCongregation("Cong Pagine"));

        PublicationForm publication = new PublicationForm();
        publication.setCode("page-lff");
        publication.setTitle("Vivi felice per sempre");
        publication.setQuantity(4);
        publicationId = publicationService.create(publication, null, superAdmin.getEmail());
        publicationService.adjustQuantity(publicationId, -1, member.getEmail());

        closedInventoryId = inventoryService.start(member.getEmail());
        inventoryService.saveCount(closedInventoryId, publicationId, 2, member.getEmail());
        inventoryService.close(closedInventoryId, member.getEmail());
        openInventoryId = inventoryService.start(member.getEmail());

        NotificationForm notification = new NotificationForm();
        notification.setName("Promemoria inventario");
        notification.setSubject("Inventario entro il 10");
        notification.setBody("Testo");
        notification.setRecipients("a@test.com");
        notification.setScheduleType(ScheduleType.MONTHLY);
        notification.setDayOfMonth(5);
        notification.setSendTime(LocalTime.of(9, 0));
        adminNotificationId = notificationService.create(notification, superAdmin.getEmail());
    }

    private UserRequestPostProcessor asSuperAdmin() {
        return user(superAdmin.getEmail()).authorities(CustomUserDetailsService.authoritiesFor(Role.SUPER_ADMIN));
    }

    private UserRequestPostProcessor asMember() {
        return user(member.getEmail()).authorities(CustomUserDetailsService.authoritiesFor(Role.USER));
    }

    @Test
    void publicationPages_render() throws Exception {
        mockMvc.perform(get("/publications").with(asSuperAdmin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vivi felice per sempre")))
                .andExpect(content().string(containsString("Inventario in corso")));
        mockMvc.perform(get("/publications/new").with(asSuperAdmin())).andExpect(status().isOk());
        mockMvc.perform(get("/publications/{id}/edit", publicationId).with(asSuperAdmin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("page-lff")));
    }

    @Test
    void publicationList_forUser_hidesAdminActions() throws Exception {
        mockMvc.perform(get("/publications").with(asMember()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Nuova pubblicazione"))));
    }

    @Test
    void inventoryPages_render() throws Exception {
        mockMvc.perform(get("/inventory").with(asMember()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Continua il conteggio")));
        mockMvc.perform(get("/inventory/{id}", openInventoryId).with(asMember()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chiudi inventario")));
        mockMvc.perform(get("/inventory/{id}", closedInventoryId).with(asMember()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chiuso")));
    }

    @Test
    void movementPage_renders() throws Exception {
        mockMvc.perform(get("/movements").with(asMember()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Uscita")))
                .andExpect(content().string(containsString("Inventario")));
        mockMvc.perform(get("/movements").param("publicationId", publicationId.toString()).with(asMember()))
                .andExpect(status().isOk());
    }

    @Test
    void notificationPages_render() throws Exception {
        mockMvc.perform(get("/notifications").with(asSuperAdmin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Il giorno 5 di ogni mese alle 09:00")));
        mockMvc.perform(get("/notifications/new").with(asMember())).andExpect(status().isOk());
        mockMvc.perform(get("/notifications/{id}", adminNotificationId).with(asSuperAdmin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Invia ora")));
        mockMvc.perform(get("/notifications/{id}", adminNotificationId).with(asMember()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("puoi solo visualizzarla")))
                .andExpect(content().string(not(containsString("Invia ora"))));
    }

    @Test
    void administrationPages_render() throws Exception {
        mockMvc.perform(get("/admin/users").with(asSuperAdmin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(member.getEmail())));
        mockMvc.perform(get("/admin/users/new").with(asSuperAdmin())).andExpect(status().isOk());
        mockMvc.perform(get("/admin/users/{id}/edit", member.getId()).with(asSuperAdmin())).andExpect(status().isOk());
        mockMvc.perform(get("/superadmin/congregations").with(asSuperAdmin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cong Pagine")));
    }

    @Test
    void profilePage_renders() throws Exception {
        mockMvc.perform(get("/profile").with(asMember()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Aggiorna password")));
        mockMvc.perform(get("/profile").with(asSuperAdmin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SUPERADMIN_PASSWORD")));
    }
}
