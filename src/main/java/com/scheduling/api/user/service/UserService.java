package com.scheduling.api.user.service;

import com.scheduling.api.exception.ResourceNotFoundException;
import com.scheduling.api.user.dto.UpdateUserRequest;
import com.scheduling.api.user.dto.UserResponse;
import com.scheduling.api.user.model.User;
import com.scheduling.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

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
                .createdAt(u.getCreatedAt())
                .build();
    }
}