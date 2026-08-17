package com.mvura.repository;

import com.mvura.model.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, UUID> {

    @Query("SELECT COUNT(l) FROM LoginAttempt l WHERE l.username = :username AND l.attemptTime > :since AND l.success = false")
    long countFailedAttemptsSince(@Param("username") String username, @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(l) FROM LoginAttempt l WHERE l.ipAddress = :ip AND l.attemptTime > :since AND l.success = false")
    long countFailedAttemptsByIpSince(@Param("ip") String ip, @Param("since") LocalDateTime since);

    List<LoginAttempt> findByUsernameOrderByAttemptTimeDesc(String username);

    @Query("SELECT COUNT(l) FROM LoginAttempt l WHERE l.username = :username AND l.success = false AND l.attemptTime > :cutoff")
    long countFailedAttempts(@Param("username") String username, @Param("cutoff") LocalDateTime cutoff);
}