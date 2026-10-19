package com.fintry.attachment;
import com.fintry.shared.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;
@Entity @Table(name="entry_attachments") @Getter @Setter
public class EntryAttachment extends BaseEntity {
    @Column(nullable=false) private UUID companyId;
    @Column(nullable=false) private UUID entryId;
    @Column(nullable=false,length=120) private String filename;
    @Column(nullable=false,length=40) private String contentType;
    @Column(nullable=false) private long byteCount;
    @Column(nullable=false) private UUID uploadedBy;
    @Column(nullable=false,length=120) private String uploadedByName;
    @Column(nullable=false,columnDefinition="bytea") private byte[] content;
}
