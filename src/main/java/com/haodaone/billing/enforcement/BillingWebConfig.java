package com.haodaone.billing.enforcement;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers the read-only enforcement for inactive subscriptions on every /api/** handler. */
@Configuration
public class BillingWebConfig implements WebMvcConfigurer {

    private final SubscriptionEnforcementInterceptor interceptor;

    public BillingWebConfig(SubscriptionEnforcementInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/**");
    }
}
