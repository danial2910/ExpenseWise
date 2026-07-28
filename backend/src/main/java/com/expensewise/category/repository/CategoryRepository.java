package com.expensewise.category.repository;

import com.expensewise.category.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    @Query("select c from Category c where c.userId is null or c.userId = :userId")
    Page<Category> findVisibleToUser(@Param("userId") Long userId, Pageable pageable);

    @Query("select c from Category c where (c.userId is null or c.userId = :userId) and c.type = :type")
    Page<Category> findVisibleToUserByType(@Param("userId") Long userId, @Param("type") String type, Pageable pageable);

    /**
     * Unpaged variant used by BudgetService to enumerate every category the
     * budgets screen must show a row for (including ones with no budget set
     * yet) — the paged variant above exists for the categories list screen.
     */
    @Query("select c from Category c where (c.userId is null or c.userId = :userId) and c.type = :type order by c.name")
    List<Category> findAllVisibleToUserByType(@Param("userId") Long userId, @Param("type") String type);

    boolean existsByUserIdAndNameAndType(Long userId, String name, String type);

    boolean existsByUserIdAndNameAndTypeAndIdNot(Long userId, String name, String type, Long id);
}
