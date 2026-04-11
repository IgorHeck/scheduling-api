package com.scheduling.api.company.repository;

import com.scheduling.api.company.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    List<Company> findByActiveTrue();
}
