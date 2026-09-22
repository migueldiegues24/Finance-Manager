package com.miguel.financemanager.config;

import com.miguel.financemanager.exception.RegistrationDisabledException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Interruptor do registo público, lido no arranque (REGISTRATION_ENABLED,
// por omissão true). Desligado, o POST /api/auth/register é recusado num
// interceptor, antes de o corpo ser lido e validado, por isso dá sempre 403
// e não chega ao AuthService. O login e as contas existentes não mudam.
@Configuration
public class RegistrationConfig implements WebMvcConfigurer {

    @Getter
    @Value("${registration.enabled:true}")
    private boolean registrationEnabled;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if (!registrationEnabled && "POST".equals(request.getMethod())) {
                    throw new RegistrationDisabledException();
                }
                return true;
            }
        }).addPathPatterns("/api/auth/register");
    }
}
