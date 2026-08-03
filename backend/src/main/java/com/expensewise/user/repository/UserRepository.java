package com.expensewise.user.repository;

import com.expensewise.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("select u.active from User u where u.id = :id")
    Optional<Boolean> findActiveById(@Param("id") Long id);

    long countByActive(boolean active);

    long countByRole(String role);

    // Projected to just the timestamp — admin-dashboard month bucketing is
    // done in Java (see AdminDashboardService), same "small demo dataset,
    // boring code over a DB-specific timezone function" precedent as
    // BudgetService.sumExpenses.
    @Query("select u.createdAt from User u")
    List<Instant> findAllCreatedAt();

    List<User> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // The cast(:search as string) is load-bearing, not decorative: when
    // :search is null, Postgres can't infer the parameter's type from the
    // polymorphic concat(...) context and defaults to bytea, so
    // lower(...) blows up with "function lower(bytea) does not exist" —
    // even though that branch is never actually reached at null. The cast
    // pins the type so the query plans regardless of whether search is set.
    @Query("""
            select u from User u
            where (:search is null
                or lower(u.email) like lower(concat('%', cast(:search as string), '%'))
                or lower(u.fullName) like lower(concat('%', cast(:search as string), '%')))
              and (:role is null or u.role = :role)
              and (:active is null or u.active = :active)
            """)
    Page<User> searchAdmin(@Param("search") String search,
                            @Param("role") String role,
                            @Param("active") Boolean active,
                            Pageable pageable);
}
