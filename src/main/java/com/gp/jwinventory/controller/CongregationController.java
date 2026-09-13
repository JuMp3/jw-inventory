package com.gp.jwinventory.controller;

import com.gp.jwinventory.exceptions.BusinessException;
import com.gp.jwinventory.service.CongregationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/superadmin/congregations")
@RequiredArgsConstructor
public class CongregationController {

    private static final String REDIRECT = "redirect:/superadmin/congregations";

    private final CongregationService congregationService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("congregations", congregationService.listView());
        return "superadmin/congregations";
    }

    @PostMapping
    public String create(@RequestParam String name, RedirectAttributes redirectAttributes) {
        try {
            congregationService.create(name);
            redirectAttributes.addFlashAttribute("success", "Congregazione " + name.trim() + " creata");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return REDIRECT;
    }

    @PostMapping("/{id}/rename")
    public String rename(@PathVariable Long id, @RequestParam String name, RedirectAttributes redirectAttributes) {
        try {
            congregationService.rename(id, name);
            redirectAttributes.addFlashAttribute("success", "Congregazione rinominata");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return REDIRECT;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            congregationService.delete(id);
            redirectAttributes.addFlashAttribute("success", "Congregazione eliminata");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return REDIRECT;
    }
}
