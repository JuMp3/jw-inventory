package com.gp.jwinventory.service;

import com.gp.jwinventory.dto.*;
import com.gp.jwinventory.entity.Publication;
import com.gp.jwinventory.entity.PublicationImage;
import com.gp.jwinventory.entity.User;
import com.gp.jwinventory.enums.MovementType;
import com.gp.jwinventory.exceptions.BusinessException;
import com.gp.jwinventory.exceptions.NotFoundException;
import com.gp.jwinventory.repository.PublicationImageRepository;
import com.gp.jwinventory.repository.PublicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PublicationService {

    static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;

    private final PublicationRepository publicationRepository;
    private final PublicationImageRepository imageRepository;
    private final MovementService movementService;
    private final UserService userService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<PublicationView> listView() {
        return publicationRepository.findAllByOrderByCodeAsc().stream()
                .map(p -> new PublicationView(p.getId(), p.getCode(), p.getTitle(), p.getQuantity(), imageUrl(p)))
                .toList();
    }

    @Transactional(readOnly = true)
    public PublicationEditView getEditView(Long id) {
        Publication publication = find(id);
        PublicationForm form = new PublicationForm();
        form.setCode(publication.getCode());
        form.setTitle(publication.getTitle());
        return new PublicationEditView(publication.getId(), form, imageUrl(publication));
    }

    @Transactional
    public Long create(PublicationForm form, ImageUpload image, String actorEmail) {
        User actor = userService.requireActor(actorEmail);
        String code = form.getCode().trim();
        if (publicationRepository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException("Esiste già una pubblicazione con il codice " + code);
        }
        int initialQuantity = form.getQuantity() == null ? 0 : form.getQuantity();
        if (initialQuantity < 0) {
            throw new BusinessException("La quantità non può essere negativa");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        Publication publication = publicationRepository.save(Publication.builder()
                .code(code)
                .title(form.getTitle().trim())
                .quantity(0)
                .createdAt(now)
                .updatedAt(now)
                .build());
        movementService.applyQuantity(publication, initialQuantity, MovementType.SET, actor);
        if (image != null) {
            storeImage(publication, image);
        }
        return publication.getId();
    }

    @Transactional
    public void update(Long id, PublicationForm form, ImageUpload image, boolean removeImage) {
        Publication publication = find(id);
        String code = form.getCode().trim();
        if (publicationRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new BusinessException("Esiste già una pubblicazione con il codice " + code);
        }
        publication.setCode(code);
        publication.setTitle(form.getTitle().trim());
        publication.setUpdatedAt(LocalDateTime.now(clock));
        if (image != null) {
            storeImage(publication, image);
        } else if (removeImage && publication.getImageUpdatedAt() != null) {
            imageRepository.deleteById(id);
            publication.setImageUpdatedAt(null);
        }
    }

    /** Also removes (DB cascade) the image, the movement history and the inventory counts. */
    @Transactional
    public void delete(Long id) {
        publicationRepository.delete(find(id));
    }

    @Transactional
    public QuantityResult adjustQuantity(Long id, int delta, String actorEmail) {
        User actor = userService.requireActor(actorEmail);
        Publication publication = publicationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Pubblicazione non trovata"));
        int newQuantity = publication.getQuantity() + delta;
        if (newQuantity < 0) {
            throw new BusinessException("Non ci sono copie di " + publication.getCode() + " da far uscire");
        }
        movementService.applyQuantity(publication, newQuantity, delta > 0 ? MovementType.IN : MovementType.OUT, actor);
        return new QuantityResult(id, publication.getQuantity());
    }

    @Transactional
    public QuantityResult setQuantity(Long id, Integer quantity, String actorEmail) {
        if (quantity == null) {
            throw new BusinessException("Indica il numero di copie");
        }
        if (quantity < 0) {
            throw new BusinessException("La quantità non può essere negativa");
        }
        User actor = userService.requireActor(actorEmail);
        Publication publication = publicationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Pubblicazione non trovata"));
        movementService.applyQuantity(publication, quantity, MovementType.SET, actor);
        return new QuantityResult(id, publication.getQuantity());
    }

    @Transactional(readOnly = true)
    public Optional<ImageData> getImage(Long id) {
        return imageRepository.findById(id).map(img -> new ImageData(img.getContentType(), img.getData()));
    }

    static String imageUrl(Publication publication) {
        return publication.getImageUpdatedAt() == null ? null
                : "/publications/" + publication.getId() + "/image?v="
                + publication.getImageUpdatedAt().toEpochSecond(ZoneOffset.UTC);
    }

    /** Detects the image type from its magic bytes (the client-declared type is not trusted). */
    static String detectImageType(byte[] data) {
        if (data == null || data.length < 12) {
            return null;
        }
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if ((data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return "image/png";
        }
        if (data[0] == 'G' && data[1] == 'I' && data[2] == 'F' && data[3] == '8') {
            return "image/gif";
        }
        if (data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private void storeImage(Publication publication, ImageUpload image) {
        if (image.data().length > MAX_IMAGE_BYTES) {
            throw new BusinessException("L'immagine supera i 2 MB");
        }
        String contentType = detectImageType(image.data());
        if (contentType == null) {
            throw new BusinessException("Formato immagine non supportato (usa JPEG, PNG, WebP o GIF)");
        }
        imageRepository.save(PublicationImage.builder()
                .publicationId(publication.getId())
                .contentType(contentType)
                .data(image.data())
                .build());
        publication.setImageUpdatedAt(LocalDateTime.now(clock));
    }

    private Publication find(Long id) {
        return publicationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Pubblicazione non trovata"));
    }
}
