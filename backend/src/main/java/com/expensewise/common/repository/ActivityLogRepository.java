package com.expensewise.common.repository;

import com.expensewise.common.entity.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    Page<ActivityLog> findByUserIdAndActionInOrderByCreatedAtDesc(Long userId, List<String> actions, Pageable pageable);
}
