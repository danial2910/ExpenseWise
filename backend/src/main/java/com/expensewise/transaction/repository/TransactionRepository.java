package com.expensewise.transaction.repository;

import com.expensewise.transaction.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    // Projected to just the timestamp — see UserRepository.findAllCreatedAt
    // for why month-bucketing is done in Java rather than in SQL.
    @Query("select t.createdAt from Transaction t")
    List<Instant> findAllCreatedAt();
}
