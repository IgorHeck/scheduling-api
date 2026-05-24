package com.scheduling.api.appointment.repository;

import com.scheduling.api.appointment.model.Appointment;
import com.scheduling.api.appointment.model.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    // ── cliente ─────────────────────────────────────────────────────────────

    Page<Appointment> findByClientIdOrderByStartAtDesc(Long clientId, Pageable pageable);

    // ── empresa ──────────────────────────────────────────────────────────────

    Page<Appointment> findByCompanyIdAndStartAtBetweenOrderByStartAt(
            Long companyId, LocalDateTime start, LocalDateTime end, Pageable pageable);

    List<Appointment> findByCompanyIdAndStatus(Long companyId, AppointmentStatus status);

    // ── conflito de horário ──────────────────────────────────────────────────

    @Query("SELECT a FROM Appointment a WHERE a.professional.id = :profId " +
            "AND a.status NOT IN ('CANCELLED', 'COMPLETED') " +
            "AND a.startAt < :end AND a.endAt > :start")
    List<Appointment> findConflics(Long profId, LocalDateTime start, LocalDateTime end);

    // ── calendário mensal ────────────────────────────────────────────────────

    @Query("SELECT FUNCTION('DATE', a.startAt) as day, COUNT(a) as total " +
            "FROM Appointment a WHERE a.company.id = :companyId " +
            "AND a.startAt BETWEEN :start AND :end " +
            "AND a.status NOT IN ('CANCELLED') " +
            "GROUP BY FUNCTION('DATE', a.startAt)")
    List<Object[]> countByDayInMonth(Long companyId, LocalDateTime start, LocalDateTime end);

    // ── disponibilidade (usado pelo AvaliabilityService) ─────────────────────

    List<Appointment> findByCompanyIdAndStartAtBetweenOrderByStartAt(
            Long companyId, LocalDateTime start, LocalDateTime end);

    // ── conclusão automática ─────────────────────────────────────────────────

    List<Appointment> findByStatusAndEndAtBefore(AppointmentStatus status, LocalDateTime cutoff);
}
