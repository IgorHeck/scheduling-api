package com.scheduling.api.scheduling.model;

import com.scheduling.api.company.model.Company;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDateTime;

@Entity
@Table(name = "schedule_blocks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "company_id")
    private Company company;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    private String reason;

}
