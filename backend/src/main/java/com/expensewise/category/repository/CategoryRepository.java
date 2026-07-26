package com.expensewise.category.repository;

import com.expensewise.category.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    @Query("select c from Category c where c.userId is null or c.userId = :userId")
    Page<Category> findVisibleToUser(@Param("userId") Long userId, Pageable pageable);

    @Query("select c from Category c where (c.userId is null or c.userId = :userId) and c.type = :type")
    Page<Category> findVisibleToUserByType(@Param("userId") Long userId, @Param("type") String type, Pageable pageable);

    boolean existsByUserIdAndNameAndType(Long userId, String name, String type);

    boolean existsByUserIdAndNameAndTypeAndIdNot(Long userId, String name, String type, Long id);
}
