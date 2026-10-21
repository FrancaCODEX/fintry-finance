package com.fintry.commerce;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProposalItemRepository extends JpaRepository<ProposalItem, UUID> {
  List<ProposalItem> findByProposalIdOrderBySkuAsc(UUID proposal);
}
