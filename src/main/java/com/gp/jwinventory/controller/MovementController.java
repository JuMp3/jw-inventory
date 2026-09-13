package com.gp.jwinventory.controller;

import com.gp.jwinventory.service.MovementService;
import com.gp.jwinventory.service.PublicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class MovementController {

    private final MovementService movementService;
    private final PublicationService publicationService;

    @GetMapping("/movements")
    public String list(@RequestParam(required = false) Long publicationId,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("movements", movementService.listView(publicationId, page));
        model.addAttribute("publications", publicationService.listView());
        model.addAttribute("selectedPublicationId", publicationId);
        return "movements/list";
    }
}
