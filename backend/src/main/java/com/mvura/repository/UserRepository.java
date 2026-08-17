package com.mvura.repository;

import com.mvura.model.User;
import com.mvura.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    // ===== BASIC FINDERS =====
    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.username = :username AND u.active = true")
    Optional<User> findActiveByUsername(@Param("username") String username);

    // ===== UPDATED: Include primaryDepartment in fetch =====
    @Query("SELECT DISTINCT u FROM User u " +
            "LEFT JOIN FETCH u.primaryFacility " +
            "LEFT JOIN FETCH u.primaryDepartment " +
            "LEFT JOIN FETCH u.departments " +
            "WHERE u.id = :id")
    Optional<User> findByIdWithDepartments(@Param("id") UUID id);

    // ===== COUNTING METHODS =====
    @Query("SELECT COUNT(u) FROM User u WHERE u.primaryFacility.id = :facilityId AND u.role = :role AND u.active = true")
    long countActiveByFacilityAndRole(@Param("facilityId") UUID facilityId, @Param("role") String role);

    // ===== FIXED: Use facilities (ManyToMany) instead of primaryFacility =====
    @Query("SELECT COUNT(u) FROM User u JOIN u.facilities f WHERE f.id = :facilityId AND u.role = :role")
    long countByFacilityIdAndRole(@Param("facilityId") UUID facilityId, @Param("role") UserRole role);

    // ===== FIXED: Use facilities (ManyToMany) instead of primaryFacility =====
    @Query("SELECT u FROM User u JOIN u.facilities f WHERE f.id = :facilityId AND u.role IN :roles")
    List<User> findByFacilityIdAndRoleIn(@Param("facilityId") UUID facilityId, @Param("roles") List<UserRole> roles);

    // ===== FIXED: Use facilities (ManyToMany) instead of primaryFacility =====
    @Query("SELECT u FROM User u JOIN u.facilities f WHERE f.id = :facilityId AND u.role IN ('DOCTOR', 'STAFF', 'FACILITY_ADMIN')")
    List<User> findStaffByFacility(@Param("facilityId") UUID facilityId);

    @Query("SELECT u FROM User u WHERE u.role = 'DOCTOR' AND u.active = true")
    List<User> findAllActiveDoctors();

    // ===== FIXED: Use facilities (ManyToMany) instead of primaryFacility =====
    @Query("SELECT u FROM User u JOIN u.facilities f WHERE f.id = :facilityId AND u.role = 'DOCTOR' AND u.active = true")
    List<User> findActiveDoctorsByFacility(@Param("facilityId") UUID facilityId);

    // ===== DEPARTMENT-DOCTOR METHODS =====
    @Query("SELECT u FROM User u JOIN u.departments d WHERE d.id = :departmentId AND u.role = 'DOCTOR' AND u.active = true")
    List<User> findDoctorsByDepartment(@Param("departmentId") UUID departmentId);

    @Query("SELECT COUNT(u) FROM User u JOIN u.departments d WHERE d.id = :departmentId AND u.role = 'DOCTOR' AND u.active = true")
    long countDoctorsByDepartment(@Param("departmentId") UUID departmentId);

    // ===== ALL USERS METHODS =====
    @Query("SELECT u FROM User u ORDER BY u.createdAt DESC")
    List<User> findAllOrderByCreatedAtDesc();

    @Query("SELECT u FROM User u WHERE u.role = :role ORDER BY u.createdAt DESC")
    List<User> findByRoleOrderByCreatedAtDesc(@Param("role") UserRole role);

    @Query("SELECT u FROM User u WHERE u.active = true AND u.role = 'PATIENT'")
    List<User> findAllActivePatients();

    // ===== SEARCH METHODS =====
    @Query("SELECT u FROM User u WHERE LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%'))")
    List<User> searchByName(@Param("search") String search);

    @Query("SELECT u FROM User u WHERE LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))")
    List<User> searchByEmail(@Param("search") String search);

    @Query("SELECT COUNT(DISTINCT u) FROM User u JOIN u.facilities f WHERE f.id = :facilityId AND u.role IN :roles")
    long countByFacilityIdAndRoles(@Param("facilityId") UUID facilityId, @Param("roles") List<UserRole> roles);
}