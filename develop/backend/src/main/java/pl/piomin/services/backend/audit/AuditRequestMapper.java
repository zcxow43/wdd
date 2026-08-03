package pl.piomin.services.backend.audit;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Fully generic persistence for {@code audit_request} — no consumer-specific logic here.
 */
@Mapper
public interface AuditRequestMapper {

    List<AuditRequest> findAll(@Param("entityType") String entityType,
                                @Param("status") String status,
                                @Param("actionType") String actionType);

    AuditRequest findById(@Param("id") Long id);

    AuditRequest findPendingByEntity(@Param("entityType") String entityType,
                                      @Param("entityId") Long entityId);

    void insert(AuditRequest auditRequest);

    void update(AuditRequest auditRequest);
}
