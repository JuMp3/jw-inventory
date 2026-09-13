package com.gp.jwinventory.service;

import com.gp.jwinventory.dto.CongregationView;
import com.gp.jwinventory.entity.Congregation;
import com.gp.jwinventory.exceptions.BusinessException;
import com.gp.jwinventory.exceptions.NotFoundException;
import com.gp.jwinventory.repository.CongregationRepository;
import com.gp.jwinventory.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CongregationService {

    private static final int MAX_NAME_LENGTH = 120;

    private final CongregationRepository congregationRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<CongregationView> listView() {
        return congregationRepository.findAllByOrderByNameAsc().stream()
                .map(c -> new CongregationView(c.getId(), c.getName(), userRepository.countByCongregationId(c.getId())))
                .toList();
    }

    @Transactional
    public Long create(String name) {
        String normalized = validateName(name);
        if (congregationRepository.existsByNameIgnoreCase(normalized)) {
            throw new BusinessException("Esiste già una congregazione con questo nome");
        }
        return congregationRepository.save(Congregation.builder()
                .name(normalized)
                .createdAt(LocalDateTime.now(clock))
                .build()).getId();
    }

    @Transactional
    public void rename(Long id, String name) {
        Congregation congregation = find(id);
        String normalized = validateName(name);
        if (congregationRepository.existsByNameIgnoreCaseAndIdNot(normalized, id)) {
            throw new BusinessException("Esiste già una congregazione con questo nome");
        }
        congregation.setName(normalized);
    }

    @Transactional
    public void delete(Long id) {
        Congregation congregation = find(id);
        if (userRepository.countByCongregationId(id) > 0) {
            throw new BusinessException("Impossibile eliminare: la congregazione ha ancora degli utenti");
        }
        congregationRepository.delete(congregation);
    }

    private Congregation find(Long id) {
        return congregationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Congregazione non trovata"));
    }

    private static String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException("Il nome è obbligatorio");
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new BusinessException("Il nome può avere al massimo " + MAX_NAME_LENGTH + " caratteri");
        }
        return trimmed;
    }
}
