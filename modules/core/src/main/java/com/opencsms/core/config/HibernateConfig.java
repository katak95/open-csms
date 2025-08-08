package com.opencsms.core.config;

import com.opencsms.core.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Hibernate configuration for multi-tenant support.
 */
@Configuration
@Slf4j
public class HibernateConfig {
    
    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer() {
        return hibernateProperties -> {
            hibernateProperties.put("hibernate.session_factory.interceptor", TenantInterceptor.class.getName());
        };
    }
    
    /**
     * Aspect to automatically enable tenant filter for repository operations.
     */
    @Aspect
    @Component
    @RequiredArgsConstructor
    public static class TenantFilterAspect {
        
        private final EntityManager entityManager;
        
        @Before("@annotation(org.springframework.transaction.annotation.Transactional) || " +
                "@within(org.springframework.stereotype.Repository)")
        public void enableTenantFilter() {
            if (TenantContext.hasTenant()) {
                String tenantId = TenantContext.getCurrentTenant();
                log.trace("Enabling tenant filter for tenant: {}", tenantId);
                
                Session session = entityManager.unwrap(Session.class);
                session.enableFilter("tenantFilter")
                    .setParameter("tenantId", tenantId);
            }
        }
        
        @After("@annotation(org.springframework.transaction.annotation.Transactional) || " +
               "@within(org.springframework.stereotype.Repository)")
        public void disableTenantFilter() {
            Session session = entityManager.unwrap(Session.class);
            session.disableFilter("tenantFilter");
            log.trace("Disabled tenant filter");
        }
    }
}