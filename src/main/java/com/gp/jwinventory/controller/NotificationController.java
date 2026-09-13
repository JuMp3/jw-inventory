package com.gp.jwinventory.controller;

import com.gp.jwinventory.dto.NotificationEditView;
import com.gp.jwinventory.dto.NotificationForm;
import com.gp.jwinventory.dto.NotificationView;
import com.gp.jwinventory.enums.ScheduleType;
import com.gp.jwinventory.exceptions.BusinessException;
import com.gp.jwinventory.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private static final Map<Integer, String> DAYS_OF_WEEK = new LinkedHashMap<>();

    static {
        for (DayOfWeek day : DayOfWeek.values()) {
            String name = day.getDisplayName(TextStyle.FULL, Locale.ITALIAN);
            DAYS_OF_WEEK.put(day.getValue(), name.substring(0, 1).toUpperCase(Locale.ITALIAN) + name.substring(1));
        }
    }

    private final NotificationService notificationService;

    @GetMapping
    public String list(Principal principal, Model model) {
        model.addAttribute("notifications", notificationService.listVisibleView(principal.getName()));
        return "notifications/list";
    }

    @GetMapping("/new")
    public String newForm(Principal principal, Model model) {
        model.addAttribute("form", new NotificationForm());
        return formView(model, null, principal);
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("form") NotificationForm form, BindingResult binding,
                         Principal principal, Model model, RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            return formView(model, null, principal);
        }
        try {
            notificationService.create(form, principal.getName());
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return formView(model, null, principal);
        }
        redirectAttributes.addFlashAttribute("success", "Notifica \"" + form.getName().trim() + "\" creata");
        return "redirect:/notifications";
    }

    @GetMapping("/{id}")
    public String edit(@PathVariable Long id, Principal principal, Model model) {
        NotificationEditView editView = notificationService.getEditView(id, principal.getName());
        model.addAttribute("form", editView.form());
        return formView(model, editView.view(), principal);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") NotificationForm form,
                         BindingResult binding, Principal principal, Model model,
                         RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            return formView(model, notificationService.getEditView(id, principal.getName()).view(), principal);
        }
        try {
            notificationService.update(id, form, principal.getName());
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return formView(model, notificationService.getEditView(id, principal.getName()).view(), principal);
        }
        redirectAttributes.addFlashAttribute("success", "Notifica \"" + form.getName().trim() + "\" salvata");
        return "redirect:/notifications";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, Principal principal, RedirectAttributes redirectAttributes) {
        notificationService.delete(id, principal.getName());
        redirectAttributes.addFlashAttribute("success", "Notifica eliminata");
        return "redirect:/notifications";
    }

    @PostMapping("/{id}/send-now")
    public String sendNow(@PathVariable Long id, Principal principal, RedirectAttributes redirectAttributes) {
        try {
            notificationService.sendNow(id, principal.getName());
            redirectAttributes.addFlashAttribute("success", "Email di prova inviata");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/notifications/" + id;
    }

    private String formView(Model model, NotificationView view, Principal principal) {
        model.addAttribute("view", view);
        model.addAttribute("editable", view == null || view.editable());
        model.addAttribute("recipientOptions", notificationService.listRecipientOptionsView(principal.getName()));
        model.addAttribute("scheduleTypes", ScheduleType.values());
        model.addAttribute("daysOfWeek", DAYS_OF_WEEK);
        return "notifications/form";
    }
}
