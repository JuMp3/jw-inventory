package com.gp.jwinventory.controller;

import com.gp.jwinventory.exceptions.BusinessException;
import com.gp.jwinventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.Map;

@Controller
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("openInventoryId", inventoryService.findOpenSessionId().orElse(null));
        model.addAttribute("sessions", inventoryService.listClosedView());
        return "inventory/list";
    }

    @PostMapping("/start")
    public String start(Principal principal, RedirectAttributes redirectAttributes) {
        try {
            Long id = inventoryService.start(principal.getName());
            return "redirect:/inventory/" + id;
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/inventory";
        }
    }

    @GetMapping("/{id}")
    public String session(@PathVariable Long id, Model model) {
        model.addAttribute("inventory", inventoryService.getSessionView(id));
        return "inventory/session";
    }

    /** Empty counted value clears the count. */
    @PostMapping("/{id}/count")
    @ResponseBody
    public ResponseEntity<?> count(@PathVariable Long id, @RequestParam Long publicationId,
                                   @RequestParam(required = false) Integer counted, Principal principal) {
        try {
            return ResponseEntity.ok(inventoryService.saveCount(id, publicationId, counted, principal.getName()));
        } catch (BusinessException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/close")
    public String close(@PathVariable Long id, Principal principal, RedirectAttributes redirectAttributes) {
        try {
            inventoryService.close(id, principal.getName());
            redirectAttributes.addFlashAttribute("success", "Inventario chiuso: quantità aggiornate");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/inventory/" + id;
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            inventoryService.cancel(id);
            redirectAttributes.addFlashAttribute("success", "Inventario annullato");
            return "redirect:/inventory";
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/inventory/" + id;
        }
    }
}
