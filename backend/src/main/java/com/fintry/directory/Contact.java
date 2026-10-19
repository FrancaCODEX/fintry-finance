package com.fintry.directory;

import com.fintry.shared.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity @Table(name="contacts") @Getter @Setter
public class Contact extends BaseEntity {
    public enum Kind { CUSTOMER, SUPPLIER, BOTH }
    @Column(nullable=false) private UUID companyId;
    @Column(nullable=false, length=120) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=10) private Kind kind;
    @Column(length=14) private String document;
    private String email;
    @Column(length=24) private String phone;
    @Column(length=1000) private String notes;
    @Column(nullable=false) private boolean active = true;
}
