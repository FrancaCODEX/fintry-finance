package com.fintry.directory;

import com.fintry.shared.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity @Table(name="financial_categories") @Getter @Setter
public class FinancialCategory extends BaseEntity {
    public enum Kind { INCOME, EXPENSE, BOTH }
    @Column(nullable=false) private UUID companyId;
    @Column(nullable=false,length=80) private String name;
    @Column(nullable=false,length=80) private String nameKey;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=10) private Kind kind;
    @Column(nullable=false) private boolean active = true;
}
