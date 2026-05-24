package com.scheduling.api.scheduling.service;

import com.scheduling.api.company.model.Company;
import com.scheduling.api.company.service.CompanyService;
import com.scheduling.api.exception.ResourceNotFoundException;
import com.scheduling.api.scheduling.dto.ScheduleBlockRequest;
import com.scheduling.api.scheduling.dto.ScheduleBlockResponse;
import com.scheduling.api.scheduling.dto.ScheduleRequest;
import com.scheduling.api.scheduling.dto.ScheduleResponse;
import com.scheduling.api.scheduling.model.Schedule;
import com.scheduling.api.scheduling.model.ScheduleBlock;
import com.scheduling.api.scheduling.repository.ScheduleBlockRepository;
import com.scheduling.api.scheduling.repository.ScheduleRepository;
import com.scheduling.api.user.model.Role;
import com.scheduling.api.user.model.User;
import com.scheduling.api.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final ScheduleBlockRepository blockRepository;
    private final CompanyService companyService;
    private final UserService userService;

    // ── Grades de horário ────────────────────────────────────────────────────

    @CacheEvict(value = "slots", allEntries = true)
    public ScheduleResponse create(ScheduleRequest req, User currentUser) {
        assertCompanyAccess(currentUser, req.getCompanyId());
        Company company      = companyService.findCompanyById(req.getCompanyId());
        User    professional = userService.findUserById(req.getProfessionalId());

        Schedule schedule = Schedule.builder()
                .company(company)
                .professional(professional)
                .dayOfWeek(req.getDayOfWeek())
                .startTime(req.getStartTime())
                .endTime(req.getEndTime())
                .lunchStart(req.getLunchStart())
                .lunchEnd(req.getLunchEnd())
                .slotDurationMinutes(req.getSlotDurationMinutes())
                .active(true)
                .build();

        return toResponse(scheduleRepository.save(schedule));
    }

    @CacheEvict(value = "slots", allEntries = true)
    public ScheduleResponse update(Long id, ScheduleRequest req, User currentUser) {
        Schedule schedule = findScheduleById(id);
        assertCompanyAccess(currentUser, schedule.getCompany().getId());

        schedule.setStartTime(req.getStartTime());
        schedule.setEndTime(req.getEndTime());
        schedule.setLunchStart(req.getLunchStart());
        schedule.setLunchEnd(req.getLunchEnd());
        schedule.setSlotDurationMinutes(req.getSlotDurationMinutes());

        return toResponse(scheduleRepository.save(schedule));
    }

    public List<ScheduleResponse> findByCompany(Long companyId) {
        return scheduleRepository.findByCompanyIdAndActiveTrue(companyId)
                .stream().map(this::toResponse).toList();
    }

    @CacheEvict(value = "slots", allEntries = true)
    public void delete(Long id, User currentUser) {
        Schedule schedule = findScheduleById(id);
        assertCompanyAccess(currentUser, schedule.getCompany().getId());
        schedule.setActive(false);
        scheduleRepository.save(schedule);
    }

    // ── Bloqueios ────────────────────────────────────────────────────────────

    @CacheEvict(value = "slots", allEntries = true)
    public ScheduleBlockResponse createBlock(ScheduleBlockRequest req, User currentUser) {
        assertCompanyAccess(currentUser, req.getCompanyId());
        Company company = companyService.findCompanyById(req.getCompanyId());
        ScheduleBlock block = ScheduleBlock.builder()
                .company(company)
                .startAt(LocalDateTime.from(req.getStartAt()))
                .endAt(LocalDateTime.from(req.getEndAt()))
                .reason(req.getReason())
                .build();
        return toBlockResponse(blockRepository.save(block));
    }

    public List<ScheduleBlockResponse> findBlocks(Long companyId, User currentUser) {
        assertCompanyAccess(currentUser, companyId);
        return blockRepository.findByCompanyId(companyId)
                .stream().map(this::toBlockResponse).toList();
    }

    @CacheEvict(value = "slots", allEntries = true)
    public void deleteBlock(Long id, User currentUser) {
        ScheduleBlock block = blockRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bloqueio não encontrado: " + id));
        assertCompanyAccess(currentUser, block.getCompany().getId());
        blockRepository.deleteById(id);
    }

    // ── Guarda de autorização ────────────────────────────────────────────────

    /**
     * ADMIN → passa sempre.
     * MANAGER → companyId deve coincidir com a empresa vinculada ao manager.
     */
    private void assertCompanyAccess(User currentUser, Long companyId) {
        if (currentUser.getRole() == Role.ADMIN) return;
        if (currentUser.getRole() == Role.MANAGER) {
            Long managerCompany = currentUser.getCompany() != null
                    ? currentUser.getCompany().getId() : null;
            if (!companyId.equals(managerCompany)) {
                throw new AccessDeniedException(
                        "Acesso negado: você só pode gerenciar recursos da sua empresa.");
            }
            return;
        }
        throw new AccessDeniedException("Acesso negado.");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Schedule findScheduleById(Long id) {
        return scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Grade não encontrada: " + id));
    }

    private ScheduleBlockResponse toBlockResponse(ScheduleBlock b) {
        return ScheduleBlockResponse.builder()
                .id(b.getId())
                .startAt(b.getStartAt())
                .endAt(b.getEndAt())
                .reason(b.getReason())
                .build();
    }

    public ScheduleResponse toResponse(Schedule s) {
        return ScheduleResponse.builder()
                .id(s.getId())
                .dayOfWeek(s.getDayOfWeek() != null ? s.getDayOfWeek().name() : null)
                .startTime(s.getStartTime() != null ? s.getStartTime().toString() : null)
                .endTime(s.getEndTime() != null ? s.getEndTime().toString() : null)
                .lunchStart(s.getLunchStart() != null ? s.getLunchStart().toString() : null)
                .lunchEnd(s.getLunchEnd() != null ? s.getLunchEnd().toString() : null)
                .slotDurationMinutes(s.getSlotDurationMinutes())
                .active(s.isActive())
                .professional(s.getProfessional() != null
                        ? ScheduleResponse.ProfessionalDto.builder()
                                .id(s.getProfessional().getId())
                                .name(s.getProfessional().getName())
                                .build()
                        : null)
                .build();
    }
}
