package com.gp.jwinventory.service;

import com.gp.jwinventory.BaseTest;
import com.gp.jwinventory.dto.ImageUpload;
import com.gp.jwinventory.dto.PublicationForm;
import com.gp.jwinventory.entity.StockMovement;
import com.gp.jwinventory.entity.User;
import com.gp.jwinventory.enums.MovementType;
import com.gp.jwinventory.enums.Role;
import com.gp.jwinventory.exceptions.BusinessException;
import com.gp.jwinventory.repository.StockMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class PublicationServiceTest extends BaseTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};

    @Autowired
    private PublicationService publicationService;

    @Autowired
    private StockMovementRepository movementRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = newUser("user-pub@test.com", Role.USER, newCongregation("Cong Pubblicazioni"));
    }

    private static PublicationForm form(String code, Integer quantity) {
        PublicationForm form = new PublicationForm();
        form.setCode(code);
        form.setTitle("Titolo " + code);
        form.setQuantity(quantity);
        return form;
    }

    @Test
    void create_withInitialQuantity_recordsSetMovement() {
        Long id = publicationService.create(form(" lff ", 12), null, user.getEmail());

        assertThat(publicationRepository.findById(id)).get()
                .satisfies(p -> {
                    assertThat(p.getCode()).isEqualTo("lff");
                    assertThat(p.getQuantity()).isEqualTo(12);
                });
        List<StockMovement> movements = movementRepository.findByPublicationIdOrderByIdAsc(id);
        assertThat(movements).singleElement().satisfies(m -> {
            assertThat(m.getType()).isEqualTo(MovementType.SET);
            assertThat(m.getDelta()).isEqualTo(12);
            assertThat(m.getUserName()).isEqualTo("user-pub");
            assertThat(m.getCongregationName()).isEqualTo("Cong Pubblicazioni");
        });
    }

    @Test
    void create_withoutQuantity_startsAtZeroWithoutMovements() {
        Long id = publicationService.create(form("bh", null), null, user.getEmail());

        assertThat(publicationRepository.findById(id).orElseThrow().getQuantity()).isZero();
        assertThat(movementRepository.findByPublicationIdOrderByIdAsc(id)).isEmpty();
    }

    @Test
    void create_duplicateCodeIgnoringCase_isRejected() {
        newPublication("LFF", 1);

        assertThatThrownBy(() -> publicationService.create(form("lff", 0), null, user.getEmail()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void adjustQuantity_incrementAndDecrement_logInAndOut() {
        Long id = newPublication("t1", 1).getId();

        assertThat(publicationService.adjustQuantity(id, 1, user.getEmail()).quantity()).isEqualTo(2);
        assertThat(publicationService.adjustQuantity(id, -1, user.getEmail()).quantity()).isEqualTo(1);

        assertThat(movementRepository.findByPublicationIdOrderByIdAsc(id))
                .extracting(StockMovement::getType, StockMovement::getDelta, StockMovement::getQuantityAfter)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(MovementType.IN, 1, 2),
                        org.assertj.core.groups.Tuple.tuple(MovementType.OUT, -1, 1));
    }

    @Test
    void adjustQuantity_belowZero_isRejected() {
        Long id = newPublication("t2", 0).getId();

        assertThatThrownBy(() -> publicationService.adjustQuantity(id, -1, user.getEmail()))
                .isInstanceOf(BusinessException.class);
        assertThat(movementRepository.findByPublicationIdOrderByIdAsc(id)).isEmpty();
    }

    @Test
    void setQuantity_exactValue_logsDelta() {
        Long id = newPublication("t3", 5).getId();

        assertThat(publicationService.setQuantity(id, 2, user.getEmail()).quantity()).isEqualTo(2);

        assertThat(movementRepository.findByPublicationIdOrderByIdAsc(id)).singleElement()
                .satisfies(m -> {
                    assertThat(m.getType()).isEqualTo(MovementType.SET);
                    assertThat(m.getDelta()).isEqualTo(-3);
                });
    }

    @Test
    void setQuantity_sameValue_recordsNoMovement() {
        Long id = newPublication("t4", 5).getId();

        publicationService.setQuantity(id, 5, user.getEmail());

        assertThat(movementRepository.findByPublicationIdOrderByIdAsc(id)).isEmpty();
    }

    @Test
    void setQuantity_negativeOrMissing_isRejected() {
        Long id = newPublication("t5", 5).getId();

        assertThatThrownBy(() -> publicationService.setQuantity(id, -1, user.getEmail()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> publicationService.setQuantity(id, null, user.getEmail()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void create_withPngImage_storesImageAndExposesVersionedUrl() {
        Long id = publicationService.create(form("img", 0), new ImageUpload(PNG), user.getEmail());

        assertThat(publicationService.getImage(id)).get()
                .satisfies(img -> assertThat(img.contentType()).isEqualTo("image/png"));
        assertThat(publicationService.listView())
                .filteredOn(p -> p.id().equals(id)).singleElement()
                .satisfies(p -> assertThat(p.imageUrl()).startsWith("/publications/" + id + "/image?v="));
    }

    @Test
    void update_removeImage_clearsIt() {
        Long id = publicationService.create(form("img2", 0), new ImageUpload(PNG), user.getEmail());

        publicationService.update(id, form("img2", null), null, true);

        assertThat(publicationService.getImage(id)).isEmpty();
        assertThat(publicationService.getEditView(id).imageUrl()).isNull();
    }

    @Test
    void create_withNonImageFile_isRejected() {
        byte[] text = "this is not an image at all".getBytes();

        assertThatThrownBy(() -> publicationService.create(form("bad", 0), new ImageUpload(text), user.getEmail()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Formato immagine");
    }

    @Test
    void create_withTooLargeImage_isRejected() {
        byte[] large = Arrays.copyOf(PNG, PublicationService.MAX_IMAGE_BYTES + 1);

        assertThatThrownBy(() -> publicationService.create(form("big", 0), new ImageUpload(large), user.getEmail()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2 MB");
    }

    @Test
    void detectImageType_recognizesSupportedFormatsByMagicBytes() {
        assertThat(PublicationService.detectImageType(PNG)).isEqualTo("image/png");
        assertThat(PublicationService.detectImageType(
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0})).isEqualTo("image/jpeg");
        assertThat(PublicationService.detectImageType("GIF89a......".getBytes())).isEqualTo("image/gif");
        assertThat(PublicationService.detectImageType("RIFF\0\0\0\0WEBPVP8 ".getBytes())).isEqualTo("image/webp");
        assertThat(PublicationService.detectImageType("<svg xmlns=...>".getBytes())).isNull();
    }
}
