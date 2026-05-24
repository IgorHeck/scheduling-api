package com.scheduling.api.company.service;

import com.scheduling.api.company.dto.*;
import com.scheduling.api.company.model.Company;
import com.scheduling.api.company.repository.CompanyRepository;
import com.scheduling.api.exception.BusinessException;
import com.scheduling.api.exception.ResourceNotFoundException;
import com.scheduling.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;

    @Value("${app.upload-path:./uploads/logos}")
    private String uploadPath;

    public CompanyResponse create(CompanyRequest req, String creatorEmail) {
        Company c = Company.builder()
                .name(req.getName())
                .description(req.getDescription())
                .address(req.getAddress())
                .phone(req.getPhone())
                .allowClienteBooking(true)
                .active(true)
                .build();
        Company saved = companyRepository.save(c);

        // Vincula o criador à empresa automaticamente
        userRepository.findByEmail(creatorEmail).ifPresent(user -> {
            user.setCompany(saved);
            userRepository.save(user);
        });

        return toResponse(saved);
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

    public CompanyResponse uploadLogo(Long companyId, MultipartFile file) {
        Company company = findCompanyById(companyId);

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException("Apenas arquivos de imagem são permitidos (JPEG, PNG, WebP...).");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new BusinessException("Imagem muito grande. Limite: 5 MB.");
        }

        String original = file.getOriginalFilename();
        String ext = (original != null && original.contains("."))
                ? original.substring(original.lastIndexOf('.')).toLowerCase()
                : ".jpg";

        String filename = "company-" + companyId + ext;

        try {
            Path dir = Paths.get(uploadPath);
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), dir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException("Erro ao salvar a imagem: " + e.getMessage());
        }

        company.setLogoUrl("/uploads/logos/" + filename);
        return toResponse(companyRepository.save(company));
    }

    public Company findCompanyById(Long id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa não encontrada: " + id));
    }

    private CompanyResponse toResponse(Company c) {
        return  CompanyResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .description(c.getDescription())
                .phone(c.getPhone())
                .address(c.getAddress())
                .logoUrl(c.getLogoUrl())
                .allowClientBooking(c.isAllowClienteBooking())
                .active(c.isActive())
                .createdAt(c.getCreatedAt())
                .build();
    }


}
