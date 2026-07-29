package pl.piomin.services.backend.audit;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuditRequestMapper {

    List<AuditRequest> findAll(@Param("entityType") String entityType,
                                @Param("status") String status,
                                @Param("actionType") String actionType);

    AuditRequest findById(@Param("id") Long id);

    AuditRequest findPendingByEntity(@Param("entityType") String entityType, @Param("entityId") Long entityId);

    int insert(AuditRequest auditRequest);

    int update(AuditRequest auditRequest);

    int deleteById(@Param("id") Long id);

    // Used only by tests to reset the audit_request table between runs.
    List<Long> findAllIds();
}
