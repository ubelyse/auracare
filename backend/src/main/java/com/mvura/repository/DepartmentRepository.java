package com.mvura.repository;

import com.mvura.model.Department;
import com.mvura.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    // ===== QUERY METHODS =====

    @Query("SELECT d FROM Department d WHERE d.facility.id = :facilityId AND d.active = true")
    List<Department> findActiveByFacility(@Param("facilityId") UUID facilityId);

    @Query("SELECT d FROM Department d WHERE d.facility.id = :facilityId AND d.code = :code")
    Optional<Department> findByFacilityIdAndCode(@Param("facilityId") UUID facilityId, @Param("code") String code);

    @Query("SELECT d FROM Department d WHERE d.facility.id = :facilityId")
    List<Department> findByFacilityId(@Param("facilityId") UUID facilityId);

    @Query(value = "SELECT * FROM departments WHERE facility_id = :facilityId AND is_active = true", nativeQuery = true)
    List<Department> findActiveByFacilityNative(@Param("facilityId") UUID facilityId);

    @Query(value = "SELECT COUNT(*) FROM departments WHERE facility_id = :facilityId", nativeQuery = true)
    int countDepartmentsByFacility(@Param("facilityId") UUID facilityId);

    @Query("SELECT d FROM Department d LEFT JOIN FETCH d.doctors WHERE d.id = :departmentId")
    Optional<Department> findByIdWithDoctors(@Param("departmentId") UUID departmentId);

    @Query("SELECT u FROM User u JOIN u.departments d WHERE d.id = :departmentId AND u.role = 'DOCTOR' AND u.active = true")
    List<User> findAvailableDoctorsByDepartment(@Param("departmentId") UUID departmentId);

    @Query("SELECT COUNT(u) FROM User u JOIN u.departments d WHERE d.id = :departmentId AND u.role = 'DOCTOR' AND u.active = true")
    long countDoctorsByDepartment(@Param("departmentId") UUID departmentId);

    @Query("SELECT COUNT(u) > 0 FROM User u JOIN u.departments d WHERE u.id = :doctorId AND d.id = :departmentId")
    boolean doctorBelongsToDepartment(@Param("doctorId") UUID doctorId, @Param("departmentId") UUID departmentId);
}