package com.mahitotsu.arachne.samples.delivery.customerservice.infrastructure;

import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;

public interface CustomerAccountJdbcRepository extends ListCrudRepository<CustomerAccountAggregate, String> {

    Optional<CustomerAccountAggregate> findByLoginId(String loginId);
}