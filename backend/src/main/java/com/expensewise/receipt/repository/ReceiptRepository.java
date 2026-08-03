package com.expensewise.receipt.repository;

import com.expensewise.receipt.entity.Receipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReceiptRepository extends JpaRepository<Receipt, Long> {

    Optional<Receipt> findByTransactionId(Long transactionId);

    List<Receipt> findByTransactionIdIn(Collection<Long> transactionIds);
}
