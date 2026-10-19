package com.fintry.attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.*;
public interface AttachmentRepository extends JpaRepository<EntryAttachment,UUID> {
    interface Info {
        UUID getId(); String getFilename(); String getContentType(); long getByteCount();
        String getUploadedByName(); LocalDateTime getCreatedAt();
    }
    List<Info> findByCompanyIdAndEntryIdOrderByCreatedAtDescIdDesc(UUID companyId,UUID entryId);
    long countByCompanyIdAndEntryId(UUID companyId,UUID entryId);
    Optional<EntryAttachment> findByIdAndEntryIdAndCompanyId(UUID id,UUID entryId,UUID companyId);
}
