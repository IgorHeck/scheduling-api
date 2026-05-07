package com.scheduling.api.scheduling.service;

import com.scheduling.api.company.model.Company;
import com.scheduling.api.company.service.CompanyService;
import com.scheduling.api.exception.ResourceNotFoundException;
import com.scheduling.api.scheduling.dto.ScheduleBlockRequest;
import com.scheduling.api.scheduling.dto.ScheduleRequest;
import com.scheduling.api.scheduling.model.Schedule;
import com.scheduling.api.scheduling.model.ScheduleBlock;
import com.scheduling.api.scheduling.repository.ScheduleBlockRepository;
import com.scheduling.api.scheduling.repository.ScheduleRepository;
import com.scheduling.api.user.model.User;
import com.scheduling.api.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final ScheduleBlockRepository blockRepository;
    private final CompanyService companyService;
    private final UserService userService;

    public Schedule create(ScheduleRequest req) {
        Company company = companyService.findCompanyById(req.getCompanyId());
        User professional = userService.findUserById(req.getProfessionalId());

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

        return scheduleRepository.save(schedule);
    }

    public Schedule update(Long id, ScheduleRequest req) {
        Schedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Grade não encontrada: " + id));

        schedule.setStartTime(req.getStartTime());
        schedule.setEndTime(req.getEndTime());
        schedule.setLunchStart(req.getLunchStart());
        schedule.setLunchEnd(req.getLunchEnd());
        schedule.setSlotDurationMinutes(req.getSlotDurationMinutes());

        return scheduleRepository.save(schedule);
    }

    public void delete(Long id) {
        Schedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Grade não encontrada: " + id));
        schedule.setActive(false);
        scheduleRepository.save(schedule);
    }

    public ScheduleBlock createBlock(ScheduleBlockRequest req) {
        Company company = companyService.findCompanyById(req.getCompanyId());
        ScheduleBlock block = ScheduleBlock.builder()
                .company(company)
                .startAt(LocalDateTime.from(req.getStartAt()))
                .endAt(LocalDateTime.from(req.getEndAt()))
                .reason(req.getReason())
                .build();
        return blockRepository.save(block);
    }
}