package com.mahitotsu.arachne.samples.delivery.orderservice.infrastructure;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;

public interface DeliveryOrderJdbcRepository extends ListCrudRepository<DeliveryOrderAggregate, String> {

    Optional<DeliveryOrderAggregate> findTopByCustomerIdOrderByCreatedAtDesc(String customerId);

    List<DeliveryOrderAggregate> findByCustomerIdOrderByCreatedAtDesc(String customerId);
}