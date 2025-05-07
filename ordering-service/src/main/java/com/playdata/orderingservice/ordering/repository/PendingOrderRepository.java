package com.playdata.orderingservice.ordering.repository;

import com.playdata.orderingservice.ordering.entity.PendingOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PendingOrderRepository extends JpaRepository<PendingOrder, Long> {

}
