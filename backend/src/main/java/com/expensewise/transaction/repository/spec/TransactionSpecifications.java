package com.expensewise.transaction.repository.spec;

import com.expensewise.transaction.entity.Transaction;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/**
 * Static Specification factories composed by the service with
 * Specification.where(...).and(...) — kept as small, single-purpose
 * predicates rather than combinatorial findBy... repository methods, so
 * the filter set (type, category, date range, search) can grow without
 * repository method explosion.
 */
public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    public static Specification<Transaction> ownedBy(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("userId"), userId);
    }

    public static Specification<Transaction> hasType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    public static Specification<Transaction> hasCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("categoryId"), categoryId);
    }

    public static Specification<Transaction> dateFrom(LocalDate from) {
        if (from == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("transactionDate"), from);
    }

    public static Specification<Transaction> dateTo(LocalDate to) {
        if (to == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("transactionDate"), to);
    }

    public static Specification<Transaction> descriptionContains(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String pattern = "%" + search.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("description")), pattern);
    }
}
