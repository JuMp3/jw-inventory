package com.gp.jwinventory.config;

import com.gp.jwinventory.BaseTest;
import com.gp.jwinventory.enums.Role;
import com.gp.jwinventory.service.CustomUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Route authorization. The filter chain ends with denyAll(): unregistered routes fail closed. */
@AutoConfigureMockMvc
@Transactional
class SecurityConfigTest extends BaseTest {

    @Autowired
    private MockMvc mockMvc;

    private static UserRequestPostProcessor as(String email, Role role) {
        return user(email).authorities(CustomUserDetailsService.authoritiesFor(role));
    }

    @Test
    void anonymous_isRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/publications"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void loginPage_isPublic() throws Exception {
        mockMvc.perform(get("/login")).andExpect(status().isOk());
    }

    @Test
    void unmappedRoute_isDeniedEvenWhenAuthenticated() throws Exception {
        mockMvc.perform(get("/some-unmapped-route").with(as("sa@test.com", Role.SUPER_ADMIN)))
                .andExpect(status().isForbidden());
    }

    @Test
    void user_canListPublications_butNotManageThem() throws Exception {
        mockMvc.perform(get("/publications").with(as("u@test.com", Role.USER))).andExpect(status().isOk());
        mockMvc.perform(get("/publications/new").with(as("u@test.com", Role.USER))).andExpect(status().isForbidden());
        mockMvc.perform(get("/publications/1/edit").with(as("u@test.com", Role.USER))).andExpect(status().isForbidden());
        mockMvc.perform(post("/publications/1/delete").with(csrf()).with(as("u@test.com", Role.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void user_cannotManageUsersOrCancelInventory() throws Exception {
        mockMvc.perform(get("/admin/users").with(as("u@test.com", Role.USER))).andExpect(status().isForbidden());
        mockMvc.perform(post("/inventory/1/cancel").with(csrf()).with(as("u@test.com", Role.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlySuperAdmin_managesCongregations() throws Exception {
        mockMvc.perform(get("/superadmin/congregations").with(as("a@test.com", Role.ADMIN)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/superadmin/congregations").with(as("sa@test.com", Role.SUPER_ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    void quantityEndpoint_updatesAndReturnsJson() throws Exception {
        var congregation = newCongregation("Cong Security");
        var member = newUser("member@test.com", Role.USER, congregation);
        Long id = newPublication("sec-1", 0).getId();

        mockMvc.perform(post("/publications/{id}/quantity", id).param("action", "inc")
                        .with(csrf()).with(as(member.getEmail(), Role.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(1));

        mockMvc.perform(post("/publications/{id}/quantity", id).param("action", "set").param("value", "-3")
                        .with(csrf()).with(as(member.getEmail(), Role.USER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void postWithoutCsrf_isForbidden() throws Exception {
        mockMvc.perform(post("/publications/1/quantity").param("action", "inc").with(as("u@test.com", Role.USER)))
                .andExpect(status().isForbidden());
    }
}
