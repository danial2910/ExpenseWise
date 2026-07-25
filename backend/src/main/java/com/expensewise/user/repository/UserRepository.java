package com.expensewise.user.repository;

import com.expensewise.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("select u.active from User u where u.id = :id")
    Optional<Boolean> findActiveById(@Param("id") Long id);

    @Query("""
            select u from User u
            where lower(u.email) like lower(concat('%', :search, '%'))
               or lower(u.fullName) like lower(concat('%', :search, '%'))
            """)
    Page<User> search(@Param("search") String search, Pageable pageable);
}
