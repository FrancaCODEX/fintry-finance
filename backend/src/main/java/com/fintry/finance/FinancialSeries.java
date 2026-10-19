package com.fintry.finance;

import com.fintry.shared.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity @Table(name="financial_series") @Getter @Setter
public class FinancialSeries extends BaseEntity {
    public enum Kind { INSTALLMENT, RECURRING }
    @Column(nullable=false) private UUID companyId;
    @Column(nullable=false) private UUID requestId;
    @Column(nullable=false,length=64) private String fingerprint;
    @Column(nullable=false) private String description;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=12) private Kind kind;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=10) private FinancialEntry.Type type;
    @Column(nullable=false) private int occurrences;
    @Column(nullable=false,precision=18,scale=2) private BigDecimal totalAmount;
    @Column(nullable=false) private LocalDate firstDueDate;
    @Column(nullable=false) private LocalDate lastDueDate;
    @Column(nullable=false) private boolean stopped;
}
