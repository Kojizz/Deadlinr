package com.deadlinr.app.service;

import com.deadlinr.app.dto.UserResponse;
import com.deadlinr.app.entity.User;
import com.deadlinr.app.exception.ResourceNotFoundException;
import com.deadlinr.app.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserResponse getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
        return UserResponse.from(user);
    }
}
