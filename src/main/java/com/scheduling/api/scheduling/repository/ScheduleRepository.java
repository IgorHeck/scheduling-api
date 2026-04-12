package com.scheduling.api.scheduling.repository;

import com.scheduling.api.scheduling.model.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
    List<Schedule> findByCompanyIdAndActiveTrue(Long companyId);
}
