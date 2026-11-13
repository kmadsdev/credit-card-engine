package io.github.kmadsdev.cardengine.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CardAccountRepository extends JpaRepository<CardAccount, UUID> {
}
