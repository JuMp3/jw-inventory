package com.gp.jwinventory.controller;

import com.gp.jwinventory.dto.UserForm;
import com.gp.jwinventory.enums.Role;
import com.gp.jwinventory.exceptions.BusinessException;
import com.gp.jwinventory.service.CongregationService;
import com.gp.jwinventory.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;

@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class UserController {

    private static final List<Role> ASSIGNABLE_ROLES = Arrays.stream(Role.values()).filter(Role::isAssignable).toList();

    private final UserService userService;
    private final CongregationService congregationService;

    @GetMapping
    public String list(Principal principal, Model model) {
        model.addAttribute("users", userService.listManageableView(principal.getName()));
        return "admin/users";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", new UserForm());
        return formView(model, null);
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("form") UserForm form, BindingResult binding, Principal principal,
                         Model model, RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            return formView(model, null);
        }
        try {
            userService.create(form, principal.getName());
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return formView(model, null);
        }
        redirectAttributes.addFlashAttribute("success", "Utente " + form.getFullName().trim() + " creato");
        return "redirect:/admin/users";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Principal principal, Model model) {
        model.addAttribute("form", userService.getForm(id, principal.getName()));
        return formView(model, id);
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") UserForm form, BindingResult binding,
                         Principal principal, Model model, RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            return formView(model, id);
        }
        try {
            userService.update(id, form, principal.getName());
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return formView(model, id);
        }
        redirectAttributes.addFlashAttribute("success", "Utente " + form.getFullName().trim() + " aggiornato");
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, Principal principal, RedirectAttributes redirectAttributes) {
        try {
            userService.delete(id, principal.getName());
            redirectAttributes.addFlashAttribute("success", "Utente eliminato");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    private String formView(Model model, Long userId) {
        model.addAttribute("userId", userId);
        model.addAttribute("roles", ASSIGNABLE_ROLES);
        model.addAttribute("congregations", congregationService.listView());
        return "admin/user-form";
    }
}
