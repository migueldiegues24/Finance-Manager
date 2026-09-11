package com.miguel.financemanager.service;

import com.miguel.financemanager.entity.User;
import com.miguel.financemanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;

    public User getCurrentUser() {
        // O JwtAuthenticationFilter já validou o token e colocou o email
        // (subject do JWT) como "name" da autenticação no contexto.
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Utilizador autenticado não encontrado"));
    }
}
