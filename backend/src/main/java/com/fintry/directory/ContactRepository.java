package com.fintry.directory;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface ContactRepository extends JpaRepository<Contact,UUID>, JpaSpecificationExecutor<Contact> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Contact c where c.id=:id and c.companyId=:company")
    Optional<Contact> lock(@Param("id") UUID id,@Param("company") UUID company);
}
