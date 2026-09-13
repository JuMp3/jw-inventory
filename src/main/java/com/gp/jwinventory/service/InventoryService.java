package com.gp.jwinventory.service;

import com.gp.jwinventory.dto.CountResult;
import com.gp.jwinventory.dto.InventoryRowView;
import com.gp.jwinventory.dto.InventorySessionView;
import com.gp.jwinventory.dto.InventorySummaryView;
import com.gp.jwinventory.entity.InventoryItem;
import com.gp.jwinventory.entity.InventorySession;
import com.gp.jwinventory.entity.Publication;
import com.gp.jwinventory.entity.User;
import com.gp.jwinventory.enums.InventoryStatus;
import com.gp.jwinventory.enums.MovementType;
import com.gp.jwinventory.exceptions.BusinessException;
import com.gp.jwinventory.exceptions.NotFoundException;
import com.gp.jwinventory.repository.InventoryItemRepository;
import com.gp.jwinventory.repository.InventorySessionRepository;
import com.gp.jwinventory.repository.PublicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Monthly inventory. One session can be open at a time (shared library): users record the
 * copies they physically count; closing the session sets each counted publication's
 * quantity to the counted value (logged as an INVENTORY movement). Uncounted publications
 * are left untouched.
 */
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventorySessionRepository sessionRepository;
    private final InventoryItemRepository itemRepository;
    private final PublicationRepository publicationRepository;
    private final MovementService movementService;
    private final UserService userService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Optional<Long> findOpenSessionId() {
        return sessionRepository.findByStatus(InventoryStatus.OPEN).map(InventorySession::getId);
    }

    @Transactional(readOnly = true)
    public List<InventorySummaryView> listClosedView() {
        return sessionRepository.findByStatusOrderByClosedAtDesc(InventoryStatus.CLOSED).stream()
                .map(session -> {
                    List<InventoryItem> items = itemRepository.findBySessionId(session.getId());
                    int differences = (int) items.stream()
                            .filter(i -> !Objects.equals(i.getExpectedQuantity(), i.getCountedQuantity()))
                            .count();
                    return new InventorySummaryView(session.getId(), session.getStartedAt(), session.getStartedBy(),
                            session.getClosedAt(), session.getClosedBy(), items.size(), differences);
                })
                .toList();
    }

    @Transactional
    public Long start(String actorEmail) {
        User actor = userService.requireActor(actorEmail);
        if (sessionRepository.findByStatus(InventoryStatus.OPEN).isPresent()) {
            throw new BusinessException("C'è già un inventario in corso");
        }
        return sessionRepository.save(InventorySession.builder()
                .status(InventoryStatus.OPEN)
                .startedAt(LocalDateTime.now(clock))
                .startedBy(actor.getFullName())
                .build()).getId();
    }

    @Transactional(readOnly = true)
    public InventorySessionView getSessionView(Long id) {
        InventorySession session = find(id);
        List<InventoryItem> items = itemRepository.findBySessionId(id);
        List<InventoryRowView> rows;
        if (session.getStatus() == InventoryStatus.OPEN) {
            Map<Long, InventoryItem> byPublication = items.stream()
                    .collect(Collectors.toMap(i -> i.getPublication().getId(), Function.identity()));
            rows = publicationRepository.findAllByOrderByCodeAsc().stream()
                    .map(p -> {
                        InventoryItem item = byPublication.get(p.getId());
                        return InventoryRowView.of(p.getId(), p.getCode(), p.getTitle(),
                                PublicationService.imageUrl(p), p.getQuantity(),
                                item == null ? null : item.getCountedQuantity());
                    })
                    .toList();
        } else {
            rows = items.stream()
                    .sorted(Comparator.comparing(i -> i.getPublication().getCode(), String.CASE_INSENSITIVE_ORDER))
                    .map(i -> InventoryRowView.of(i.getPublication().getId(), i.getPublication().getCode(),
                            i.getPublication().getTitle(), PublicationService.imageUrl(i.getPublication()),
                            i.getExpectedQuantity(), i.getCountedQuantity()))
                    .toList();
        }
        long counted = rows.stream().filter(r -> r.counted() != null).count();
        long differences = rows.stream().filter(r -> r.difference() != null && r.difference() != 0).count();
        return new InventorySessionView(session.getId(), session.getStatus(), session.getStartedAt(),
                session.getStartedBy(), session.getClosedAt(), session.getClosedBy(), rows, counted, differences);
    }

    /** counted == null removes a previous count. */
    @Transactional
    public CountResult saveCount(Long sessionId, Long publicationId, Integer counted, String actorEmail) {
        User actor = userService.requireActor(actorEmail);
        InventorySession session = requireOpen(sessionId);
        Publication publication = publicationRepository.findById(publicationId)
                .orElseThrow(() -> new NotFoundException("Pubblicazione non trovata"));
        Optional<InventoryItem> existing = itemRepository.findBySessionIdAndPublicationId(sessionId, publicationId);
        if (counted == null) {
            existing.ifPresent(itemRepository::delete);
            return new CountResult(publicationId, null, publication.getQuantity(), null);
        }
        if (counted < 0) {
            throw new BusinessException("Il numero di copie non può essere negativo");
        }
        InventoryItem item = existing.orElseGet(() -> InventoryItem.builder()
                .session(session)
                .publication(publication)
                .build());
        item.setCountedQuantity(counted);
        item.setCountedBy(actor.getFullName());
        item.setCountedAt(LocalDateTime.now(clock));
        itemRepository.save(item);
        return new CountResult(publicationId, counted, publication.getQuantity(), counted - publication.getQuantity());
    }

    @Transactional
    public void close(Long sessionId, String actorEmail) {
        User actor = userService.requireActor(actorEmail);
        InventorySession session = requireOpen(sessionId);
        for (InventoryItem item : itemRepository.findBySessionIdOrderById(sessionId)) {
            // re-read under row lock: quantities may have moved since the count was entered
            Publication publication = publicationRepository.findByIdForUpdate(item.getPublication().getId())
                    .orElseThrow(() -> new NotFoundException("Pubblicazione non trovata"));
            item.setExpectedQuantity(publication.getQuantity());
            movementService.applyQuantity(publication, item.getCountedQuantity(), MovementType.INVENTORY, actor);
        }
        session.setStatus(InventoryStatus.CLOSED);
        session.setClosedAt(LocalDateTime.now(clock));
        session.setClosedBy(actor.getFullName());
    }

    @Transactional
    public void cancel(Long sessionId) {
        sessionRepository.delete(requireOpen(sessionId));
    }

    private InventorySession requireOpen(Long id) {
        InventorySession session = find(id);
        if (session.getStatus() != InventoryStatus.OPEN) {
            throw new BusinessException("L'inventario è già chiuso");
        }
        return session;
    }

    private InventorySession find(Long id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Inventario non trovato"));
    }
}
