package com.wdd.backend.audit;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuditRequestMapper {

    List<AuditRequest> findAll(@Param("entityType") String entityType,
                                @Param("status") String status,
                                @Param("actionType") String actionType);

    Optional<AuditRequest> findById(Long id);

    Optional<AuditRequest> findPendingByEntity(@Param("entityType") String entityType,
                                                @Param("entityId") Long entityId);

    int insert(AuditRequest auditRequest);

    int update(AuditRequest auditRequest);
}
