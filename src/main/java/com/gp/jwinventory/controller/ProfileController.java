package com.gp.jwinventory.controller;

import com.gp.jwinventory.dto.PasswordChangeForm;
import com.gp.jwinventory.exceptions.BusinessException;
import com.gp.jwinventory.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;

    @GetMapping
    public String profile(Principal principal, Model model) {
        model.addAttribute("passwordForm", new PasswordChangeForm());
        return profileView(principal, model);
    }

    @PostMapping("/password")
    public String changePassword(@Valid @ModelAttribute("passwordForm") PasswordChangeForm form, BindingResult binding,
                                 Principal principal, Model model, RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            return profileView(principal, model);
        }
        try {
            userService.changeOwnPassword(principal.getName(), form);
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return profileView(principal, model);
        }
        redirectAttributes.addFlashAttribute("success", "Password aggiornata");
        return "redirect:/profile";
    }

    private String profileView(Principal principal, Model model) {
        model.addAttribute("user", userService.getProfileView(principal.getName()));
        return "profile";
    }
}
