package com.scheduling.api.user.repository;

import com.scheduling.api.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    /**
     * Retorna todos os ADMIN ativos + todos os MANAGER ativos da empresa informada.
     * Usado pelo NotificationService para disparar SSE ao criar agendamentos.
     */
    @Query("SELECT u FROM User u WHERE u.active = true AND " +
           "(u.role = 'ADMIN' OR (u.role = 'MANAGER' AND u.company.id = :companyId))")
    List<User> findActiveStaffForCompany(@Param("companyId") Long companyId);
}
