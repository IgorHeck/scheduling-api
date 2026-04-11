package com.scheduling.api.company.service;

import com.scheduling.api.company.dto.*;
import com.scheduling.api.company.model.Company;
import com.scheduling.api.company.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;

    public CompanyResponse create(CompanyRequest req) {
        Company c = Company.builder()
                .name(req.getName())
                .description(req.getDescription())
                .address(req.getAddress())
                .phone(req.getPhone())
                .allowClienteBooking(true)
                .active(true)
                .build();
        return toResponse(companyRepository.save(c));
    }

    public List<CompanyResponse> findAll() {
        return companyRepository.findByActiveTrue().stream().map(this::toResponse).toList();
    }

    public CompanyResponse findById(Long id) {
        return toResponse(findCompanyById(id));
    }

    public CompanyResponse update(Long id,CompanyRequest req) {
        Company c =findCompanyById(id);
        c.setName(req.getName());
        c.setDescription(req.getDescription());
        c.setAddress(req.getAddress());
        c.setPhone(req.getPhone());
        return toResponse(companyRepository.save(c));
    }

    public CompanyResponse updateSettings(Long id, CompanySettingsRequest req) {
        Company c = findCompanyById(id);
        if (req.getAllowClientBooking() != null) c.setAllowClienteBooking(req.getAllowClientBooking());
        if (req.getActive() != null) c.setActive(req.getActive());
        return toResponse(companyRepository.save(c));
    }

    public Company findCompanyById(Long id) {
        return companyRepository.findById(id).orElse(null);
    }

    private CompanyResponse toResponse(Company c) {
        return  CompanyResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .description(c.getDescription())
                .phone(c.getPhone())
                .address(c.getAddress())
                .allowCLientBooking(c.isAllowClienteBooking())
                .active(c.isActive())
                .createdAt(c.getCreatedAt())
                .build();
    }


}
