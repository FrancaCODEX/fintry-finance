package com.fintry.finance;
import com.fintry.shared.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.UUID;
@Entity @Table(name="financial_accounts") @Getter @Setter
public class FinancialAccount extends BaseEntity {
    @Column(nullable=false) private UUID companyId;
    @Column(nullable=false, length=100) private String name;
    @Column(nullable=false, precision=14, scale=2) private BigDecimal initialBalance;
}
