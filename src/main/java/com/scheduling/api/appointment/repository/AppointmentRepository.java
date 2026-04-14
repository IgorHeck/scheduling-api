package com.scheduling.api.appointment.repository;

import com.scheduling.api.appointment.model.Appointment;
import com.scheduling.api.appointment.model.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findByClientIdOrderByStartAtDesc(Long clientId);

    List<Appointment> findByCompanyIdAndStartAtBetweenOrderByStartAt (Long companyId, LocalDateTime start, LocalDateTime end);

    List<Appointment> findByCompanyIdAndStatus(Long companyId, AppointmentStatus status);

    @Query("SELECT a FROM Appointment a WHERE a.professional.id = :profId " +
            "AND a.status NOT IN ('CANCELLED') " +
            "AND a.startAt < :end AND a.endAt > :start")
    List<Appointment> findConflics(Long profId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT FUNCTION('DATE', a.startAt) as day, COUNT(a) as total " +
            "FROM Appointment a WHERE a.company.id = :companyId " +
            "AND a.startAt BETWEEN :start AND :end " +
            "AND a.status NOT IN ('CANCELLED') " +
            "GROUP BY FUNCTION('DATE', a.startAt)")
    List<Object[]> countByDayInMonth(Long companyId, LocalDateTime start, LocalDateTime end);

}
