package com.gp.jwinventory.service;

import com.gp.jwinventory.dto.MovementView;
import com.gp.jwinventory.entity.Publication;
import com.gp.jwinventory.entity.StockMovement;
import com.gp.jwinventory.entity.User;
import com.gp.jwinventory.enums.MovementType;
import com.gp.jwinventory.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MovementService {

    static final int PAGE_SIZE = 50;

    private final StockMovementRepository movementRepository;
    private final Clock clock;

    /**
     * Service-internal: sets the new quantity on a (locked, managed) publication and logs the
     * movement. No-op when the quantity does not change.
     */
    @Transactional
    public void applyQuantity(Publication publication, int newQuantity, MovementType type, User actor) {
        int delta = newQuantity - publication.getQuantity();
        if (delta == 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        publication.setQuantity(newQuantity);
        publication.setUpdatedAt(now);
        movementRepository.save(StockMovement.builder()
                .publication(publication)
                .type(type)
                .delta(delta)
                .quantityAfter(newQuantity)
                .userName(actor.getFullName())
                .congregationName(actor.getCongregationName())
                .createdAt(now)
                .build());
    }

    @Transactional(readOnly = true)
    public Page<MovementView> listView(Long publicationId, int page) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE);
        Page<StockMovement> movements = publicationId == null
                ? movementRepository.findAllByOrderByCreatedAtDescIdDesc(pageable)
                : movementRepository.findByPublicationIdOrderByCreatedAtDescIdDesc(publicationId, pageable);
        return movements.map(m -> new MovementView(m.getCreatedAt(), m.getPublication().getId(),
                m.getPublication().getCode(), m.getPublication().getTitle(), m.getType(), m.getDelta(),
                m.getQuantityAfter(), m.getUserName(), m.getCongregationName()));
    }
}
