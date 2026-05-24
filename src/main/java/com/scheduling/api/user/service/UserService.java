package com.scheduling.api.user.service;

import com.scheduling.api.company.model.Company;
import com.scheduling.api.company.service.CompanyService;
import com.scheduling.api.exception.BusinessException;
import com.scheduling.api.exception.ResourceNotFoundException;
import com.scheduling.api.user.dto.AssignCompanyRequest;
import com.scheduling.api.user.dto.CreateManagerRequest;
import com.scheduling.api.user.dto.UpdateUserRequest;
import com.scheduling.api.user.dto.UserResponse;
import com.scheduling.api.user.model.Role;
import com.scheduling.api.user.model.User;
import com.scheduling.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CompanyService companyService;

    public UserResponse createManager(CreateManagerRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new BusinessException("Email já cadastrado");
        }
        Company company = req.getCompanyId() != null
                ? companyService.findCompanyById(req.getCompanyId())
                : null;
        User user = User.builder()
                .name(req.getName())
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .phone(req.getPhone())
                .role(Role.MANAGER)
                .active(true)
                .company(company)
                .build();
        return toResponse(userRepository.save(user));
    }

    public UserResponse findById(Long id) {
        return toResponse(findUserById(id));
    }

    public List<UserResponse> findAll() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

    public UserResponse update(Long id, UpdateUserRequest req) {
        User user = findUserById(id);
        if (req.getName() != null) user.setName(req.getName());
        if (req.getPhone() != null) user.setPhone(req.getPhone());
        return toResponse(userRepository.save(user));
    }

    public UserResponse assignCompany(Long userId, AssignCompanyRequest req) {
        User user = findUserById(userId);
        if (req.getCompanyId() != null) {
            Company company = companyService.findCompanyById(req.getCompanyId());
            user.setCompany(company);
        } else {
            user.setCompany(null);
        }
        return toResponse(userRepository.save(user));
    }

    public void deactivate(Long id) {
        User user = findUserById(id);
        user.setActive(false);
        userRepository.save(user);
    }

    public User findUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado: " + id));
    }

    private UserResponse toResponse(User u) {
        return UserResponse.builder()
                .id(u.getId())
                .name(u.getName())
                .email(u.getEmail())
                .phone(u.getPhone())
                .role(u.getRole())
                .active(u.isActive())
                .companyId(u.getCompany() != null ? u.getCompany().getId() : null)
                .companyName(u.getCompany() != null ? u.getCompany().getName() : null)
                .createdAt(u.getCreatedAt())
                .build();
    }
}