package io.github.kmadsdev.cardengine.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    boolean existsByCpf(String cpf);

    Optional<Customer> findByCpf(String cpf);
}
