package com.fintry.sample;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SampleRunRepository extends JpaRepository<SampleRun, UUID> {}
