package com.gp.jwinventory.controller;

import com.gp.jwinventory.dto.ImageUpload;
import com.gp.jwinventory.dto.PublicationEditView;
import com.gp.jwinventory.dto.PublicationForm;
import com.gp.jwinventory.dto.QuantityResult;
import com.gp.jwinventory.exceptions.BusinessException;
import com.gp.jwinventory.service.InventoryService;
import com.gp.jwinventory.service.PublicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.security.Principal;
import java.time.Duration;
import java.util.Map;

@Controller
@RequestMapping("/publications")
@RequiredArgsConstructor
public class PublicationController {

    private final PublicationService publicationService;
    private final InventoryService inventoryService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("publications", publicationService.listView());
        model.addAttribute("openInventoryId", inventoryService.findOpenSessionId().orElse(null));
        return "publications/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", new PublicationForm());
        return "publications/form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("form") PublicationForm form, BindingResult binding,
                         @RequestParam(value = "image", required = false) MultipartFile image,
                         Principal principal, Model model, RedirectAttributes redirectAttributes) throws IOException {
        if (binding.hasErrors()) {
            return "publications/form";
        }
        try {
            publicationService.create(form, toUpload(image), principal.getName());
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return "publications/form";
        }
        redirectAttributes.addFlashAttribute("success", "Pubblicazione " + form.getCode().trim() + " aggiunta");
        return "redirect:/publications";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        PublicationEditView view = publicationService.getEditView(id);
        model.addAttribute("form", view.form());
        return editView(model, view);
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") PublicationForm form,
                         BindingResult binding,
                         @RequestParam(value = "image", required = false) MultipartFile image,
                         @RequestParam(defaultValue = "false") boolean removeImage,
                         Model model, RedirectAttributes redirectAttributes) throws IOException {
        if (binding.hasErrors()) {
            return editView(model, publicationService.getEditView(id));
        }
        try {
            publicationService.update(id, form, toUpload(image), removeImage);
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return editView(model, publicationService.getEditView(id));
        }
        redirectAttributes.addFlashAttribute("success", "Pubblicazione " + form.getCode().trim() + " aggiornata");
        return "redirect:/publications";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        publicationService.delete(id);
        redirectAttributes.addFlashAttribute("success", "Pubblicazione eliminata");
        return "redirect:/publications";
    }

    /** action: inc (+1, entrata), dec (-1, uscita), set (exact value). */
    @PostMapping("/{id}/quantity")
    @ResponseBody
    public ResponseEntity<?> quantity(@PathVariable Long id, @RequestParam String action,
                                      @RequestParam(required = false) Integer value, Principal principal) {
        try {
            QuantityResult result = switch (action) {
                case "inc" -> publicationService.adjustQuantity(id, 1, principal.getName());
                case "dec" -> publicationService.adjustQuantity(id, -1, principal.getName());
                case "set" -> publicationService.setQuantity(id, value, principal.getName());
                default -> throw new BusinessException("Azione non valida");
            };
            return ResponseEntity.ok(result);
        } catch (BusinessException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable Long id) {
        return publicationService.getImage(id)
                .map(img -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(img.contentType()))
                        // URL carries a version parameter, so a long cache is safe
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                        .body(img.data()))
                .orElse(ResponseEntity.notFound().build());
    }

    private static String editView(Model model, PublicationEditView view) {
        model.addAttribute("publicationId", view.id());
        model.addAttribute("imageUrl", view.imageUrl());
        return "publications/form";
    }

    private static ImageUpload toUpload(MultipartFile file) throws IOException {
        return file == null || file.isEmpty() ? null : new ImageUpload(file.getBytes());
    }
}
