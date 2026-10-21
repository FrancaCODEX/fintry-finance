package com.fintry.subscription;

public interface SubscriptionRepository
    extends org.springframework.data.jpa.repository.JpaRepository<
        SaasSubscription, java.util.UUID> {}
