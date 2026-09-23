package com.miguel.financemanager.support;

import com.miguel.financemanager.security.AuthRateLimiter;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

// Os testes de integração partilham o contexto Spring e, com ele, os
// contadores do AuthRateLimiter; como todos os pedidos do MockMvc vêm do
// mesmo IP, os registos e logins de uns contariam para os limites dos
// outros. Este listener (registado em META-INF/spring.factories) repõe os
// contadores antes de cada teste.
public class AuthRateLimiterResetListener extends AbstractTestExecutionListener {

    @Override
    public void beforeTestMethod(TestContext testContext) {
        if (testContext.hasApplicationContext()) {
            testContext.getApplicationContext()
                    .getBeanProvider(AuthRateLimiter.class)
                    .ifAvailable(AuthRateLimiter::reset);
        }
    }
}
