package com.gp.jwinventory.service;

import com.gp.jwinventory.BaseTest;
import com.gp.jwinventory.dto.InventorySessionView;
import com.gp.jwinventory.entity.Publication;
import com.gp.jwinventory.entity.StockMovement;
import com.gp.jwinventory.entity.User;
import com.gp.jwinventory.enums.InventoryStatus;
import com.gp.jwinventory.enums.MovementType;
import com.gp.jwinventory.enums.Role;
import com.gp.jwinventory.exceptions.BusinessException;
import com.gp.jwinventory.repository.StockMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class InventoryServiceTest extends BaseTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private StockMovementRepository movementRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = newUser("user-inv@test.com", Role.USER, newCongregation("Cong Inventario"));
    }

    @Test
    void start_whenAlreadyOpen_isRejected() {
        inventoryService.start(user.getEmail());

        assertThatThrownBy(() -> inventoryService.start(user.getEmail()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void close_appliesCountedQuantities_andLeavesUncountedUntouched() {
        Publication missing = newPublication("inv-a", 10);
        Publication matching = newPublication("inv-b", 5);
        Publication uncounted = newPublication("inv-c", 7);
        Long sessionId = inventoryService.start(user.getEmail());

        assertThat(inventoryService.saveCount(sessionId, missing.getId(), 8, user.getEmail()).difference()).isEqualTo(-2);
        assertThat(inventoryService.saveCount(sessionId, matching.getId(), 5, user.getEmail()).difference()).isZero();

        InventorySessionView open = inventoryService.getSessionView(sessionId);
        assertThat(open.isOpen()).isTrue();
        assertThat(open.countedCount()).isEqualTo(2);
        assertThat(open.differenceCount()).isEqualTo(1);

        inventoryService.close(sessionId, user.getEmail());

        assertThat(publicationRepository.findById(missing.getId()).orElseThrow().getQuantity()).isEqualTo(8);
        assertThat(publicationRepository.findById(matching.getId()).orElseThrow().getQuantity()).isEqualTo(5);
        assertThat(publicationRepository.findById(uncounted.getId()).orElseThrow().getQuantity()).isEqualTo(7);

        assertThat(movementRepository.findByPublicationIdOrderByIdAsc(missing.getId())).singleElement()
                .satisfies(m -> {
                    assertThat(m.getType()).isEqualTo(MovementType.INVENTORY);
                    assertThat(m.getDelta()).isEqualTo(-2);
                });
        assertThat(movementRepository.findByPublicationIdOrderByIdAsc(matching.getId())).isEmpty();

        InventorySessionView closed = inventoryService.getSessionView(sessionId);
        assertThat(closed.status()).isEqualTo(InventoryStatus.CLOSED);
        assertThat(closed.closedBy()).isEqualTo("user-inv");
        assertThat(closed.rows()).hasSize(2)
                .filteredOn(r -> r.publicationId().equals(missing.getId())).singleElement()
                .satisfies(r -> {
                    assertThat(r.expected()).isEqualTo(10);
                    assertThat(r.counted()).isEqualTo(8);
                });
        assertThat(inventoryService.findOpenSessionId()).isEmpty();
        assertThat(inventoryService.listClosedView())
                .filteredOn(s -> s.id().equals(sessionId)).singleElement()
                .satisfies(s -> {
                    assertThat(s.countedCount()).isEqualTo(2);
                    assertThat(s.differenceCount()).isEqualTo(1);
                });
    }

    @Test
    void close_usesQuantityAtClosingTime_whenCopiesMovedDuringCount() {
        Publication publication = newPublication("inv-d", 4);
        Long sessionId = inventoryService.start(user.getEmail());
        inventoryService.saveCount(sessionId, publication.getId(), 4, user.getEmail());
        publication.setQuantity(6); // two copies came in after counting

        inventoryService.close(sessionId, user.getEmail());

        assertThat(publicationRepository.findById(publication.getId()).orElseThrow().getQuantity()).isEqualTo(4);
        assertThat(movementRepository.findByPublicationIdOrderByIdAsc(publication.getId()))
                .extracting(StockMovement::getDelta).containsExactly(-2);
    }

    @Test
    void saveCount_null_removesThePreviousCount() {
        Publication publication = newPublication("inv-e", 3);
        Long sessionId = inventoryService.start(user.getEmail());
        inventoryService.saveCount(sessionId, publication.getId(), 1, user.getEmail());

        inventoryService.saveCount(sessionId, publication.getId(), null, user.getEmail());

        assertThat(inventoryService.getSessionView(sessionId).rows())
                .filteredOn(r -> r.publicationId().equals(publication.getId())).singleElement()
                .satisfies(r -> assertThat(r.counted()).isNull());
    }

    @Test
    void saveCount_onClosedSession_isRejected() {
        Publication publication = newPublication("inv-f", 3);
        Long sessionId = inventoryService.start(user.getEmail());
        inventoryService.close(sessionId, user.getEmail());

        assertThatThrownBy(() -> inventoryService.saveCount(sessionId, publication.getId(), 3, user.getEmail()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void cancel_deletesTheOpenSession() {
        Long sessionId = inventoryService.start(user.getEmail());

        inventoryService.cancel(sessionId);

        assertThat(inventoryService.findOpenSessionId()).isEmpty();
    }
}
