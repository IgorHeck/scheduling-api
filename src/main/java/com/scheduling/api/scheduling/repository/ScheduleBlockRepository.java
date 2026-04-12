package com.scheduling.api.scheduling.repository;

import com.scheduling.api.scheduling.model.ScheduleBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface ScheduleBlockRepository extends JpaRepository<ScheduleBlock, Long> {

    @Query("SELECT b FROM ScheduleBlock b WHERE b.company.id = :companyId " +
            "AND b.startAt <= :end AND b.endAt >= :start")
    List<ScheduleBlock> findOverLapping(Long companyId, LocalDateTime start, LocalDateTime end);
}
