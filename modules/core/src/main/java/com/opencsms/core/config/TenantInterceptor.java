package com.opencsms.core.config;

import com.opencsms.core.tenant.TenantContext;
import com.opencsms.domain.base.BaseEntity;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.CallbackException;
import org.hibernate.Interceptor;
import org.hibernate.type.Type;

/**
 * Hibernate interceptor to automatically set tenant ID on entity creation.
 */
@Slf4j
public class TenantInterceptor implements Interceptor {
    
    @Override
    public boolean onSave(Object entity, Object id, Object[] state, String[] propertyNames, Type[] types) {
        if (entity instanceof BaseEntity) {
            return setTenantId(state, propertyNames);
        }
        return false;
    }
    
    @Override
    public boolean onFlushDirty(Object entity, Object id, Object[] currentState, Object[] previousState,
                                String[] propertyNames, Type[] types) {
        if (entity instanceof BaseEntity) {
            // Validate tenant ID hasn't changed
            for (int i = 0; i < propertyNames.length; i++) {
                if ("tenantId".equals(propertyNames[i])) {
                    if (previousState[i] != null && !previousState[i].equals(currentState[i])) {
                        throw new CallbackException("Tenant ID cannot be changed after entity creation");
                    }
                }
            }
        }
        return false;
    }
    
    private boolean setTenantId(Object[] state, String[] propertyNames) {
        if (!TenantContext.hasTenant()) {
            log.warn("No tenant context available when saving entity");
            return false;
        }
        
        String tenantId = TenantContext.getCurrentTenant();
        
        for (int i = 0; i < propertyNames.length; i++) {
            if ("tenantId".equals(propertyNames[i])) {
                if (state[i] == null) {
                    state[i] = tenantId;
                    log.trace("Set tenant ID to {} for new entity", tenantId);
                    return true;
                } else if (!state[i].equals(tenantId)) {
                    throw new CallbackException(
                        String.format("Entity tenant ID (%s) doesn't match current context (%s)", 
                                    state[i], tenantId)
                    );
                }
            }
        }
        
        return false;
    }
}