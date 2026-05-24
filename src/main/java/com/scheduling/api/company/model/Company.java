package com.scheduling.api.company.model;

import com.scheduling.api.user.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.validator.internal.IgnoreForbiddenApisErrors;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "companies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false)
    private String name;

    private String description;

    private String address;

    private String phone;

    @Column(length = 500)
    private String logoUrl;

    @Column(nullable = false)
    private boolean allowClienteBooking = true;

    @Column(nullable = false)
    private boolean active = true;

    @ManyToOne
    @JoinColumn(name = "owner_id")
    private User owner;

    @OneToMany(mappedBy = "company")
    private List<User> members;

    @CreationTimestamp
    private LocalDateTime createdAt;


}
