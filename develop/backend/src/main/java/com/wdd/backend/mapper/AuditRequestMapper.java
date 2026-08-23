package com.wdd.backend.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.wdd.backend.dto.AuditRequest;

@Mapper
public interface AuditRequestMapper {

    List<AuditRequest> findAll(@Param("status") String status, @Param("entityType") String entityType,
            @Param("brandId") Long brandId, @Param("entityId") Long entityId);

    AuditRequest findById(@Param("id") Long id);

    /** The at-most-one-PENDING-per-target check, mirrored by {@code uk_audit_request_pending}. */
    AuditRequest findPending(@Param("entityType") String entityType, @Param("entityId") Long entityId);

    int insert(AuditRequest request);

    int updateResolved(@Param("id") Long id, @Param("status") String status, @Param("reviewedBy") String reviewedBy,
            @Param("reviewedAt") LocalDateTime reviewedAt, @Param("reviewComment") String reviewComment,
            @Param("applyError") String applyError);

    /** Separate, immediately-committed write so a rolled-back apply's reason survives. */
    int updateApplyError(@Param("id") Long id, @Param("applyError") String applyError);
}
