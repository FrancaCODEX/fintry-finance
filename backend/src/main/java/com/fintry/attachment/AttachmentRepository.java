package com.fintry.attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.*;
public interface AttachmentRepository extends JpaRepository<EntryAttachment,UUID> {
    @org.springframework.data.jpa.repository.Query("select coalesce(sum(a.byteCount),0) from EntryAttachment a where a.companyId=:company")
    long subscriptionBytes(@org.springframework.data.repository.query.Param("company") UUID company);
    interface Info {
        UUID getId(); String getFilename(); String getContentType(); long getByteCount();
        String getUploadedByName(); LocalDateTime getCreatedAt();
    }
    List<Info> findByCompanyIdAndEntryIdOrderByCreatedAtDescIdDesc(UUID companyId,UUID entryId);
    long countByCompanyIdAndEntryId(UUID companyId,UUID entryId);
    Optional<EntryAttachment> findByIdAndEntryIdAndCompanyId(UUID id,UUID entryId,UUID companyId);
}
