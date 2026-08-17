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

    // ===== USE @Query FOR ALL METHODS =====

    // ===== USE 'active' NOT 'isActive' =====
    @Query("SELECT d FROM Department d WHERE d.facility.id = :facilityId AND d.active = true")
    List<Department> findActiveByFacility(@Param("facilityId") UUID facilityId);

    @Query("SELECT d FROM Department d WHERE d.facility.id = :facilityId AND d.code = :code")
    Optional<Department> findByFacilityIdAndCode(@Param("facilityId") UUID facilityId, @Param("code") String code);

    @Query("SELECT d FROM Department d WHERE d.facility.id = :facilityId")
    List<Department> findByFacilityId(@Param("facilityId") UUID facilityId);

    // ===== NATIVE QUERY AS BACKUP =====
    @Query(value = "SELECT * FROM departments WHERE facility_id = :facilityId AND is_active = true", nativeQuery = true)
    List<Department> findActiveByFacilityNative(@Param("facilityId") UUID facilityId);

    // ===== COUNT METHOD FOR DEBUGGING =====
    @Query(value = "SELECT COUNT(*) FROM departments WHERE facility_id = :facilityId", nativeQuery = true)
    int countDepartmentsByFacility(@Param("facilityId") UUID facilityId);

    // Get department with doctors
    @Query("SELECT d FROM Department d LEFT JOIN FETCH d.doctors WHERE d.id = :departmentId")
    Optional<Department> findByIdWithDoctors(@Param("departmentId") UUID departmentId);

    // Get available doctors for a department
    @Query("SELECT u FROM User u JOIN u.departments d WHERE d.id = :departmentId AND u.role = 'DOCTOR' AND u.active = true")
    List<User> findAvailableDoctorsByDepartment(@Param("departmentId") UUID departmentId);

    // Count doctors in department
    @Query("SELECT COUNT(u) FROM User u JOIN u.departments d WHERE d.id = :departmentId AND u.role = 'DOCTOR' AND u.active = true")
    long countDoctorsByDepartment(@Param("departmentId") UUID departmentId);

    // FIX: direct membership check used by CheckInService instead of
    // doctor.hasDepartment(departmentId). That in-memory check relied on
    // doctor.getDepartments() being fully initialized, which depended on
    // getting back the exact entity instance that findByIdWithDepartments()
    // fetch-joined -- if Hibernate instead returned an already-managed
    // instance from earlier in the persistence context (e.g. from Spring
    // Security's UserDetailsService loading the same user during auth),
    // the collection could appear empty even though the doctor_departments
    // row genuinely existed. This query asks Postgres directly and cannot
    // be fooled by entity/session state.
    @Query("SELECT COUNT(u) > 0 FROM User u JOIN u.departments d WHERE u.id = :doctorId AND d.id = :departmentId")
    boolean doctorBelongsToDepartment(@Param("doctorId") UUID doctorId, @Param("departmentId") UUID departmentId);
}