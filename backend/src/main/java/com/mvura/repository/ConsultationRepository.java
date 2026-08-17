package com.mvura.repository;

import com.mvura.model.Consultation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConsultationRepository extends JpaRepository<Consultation, UUID> {

    Optional<Consultation> findByTicketId(UUID ticketId);

    @Query("SELECT c FROM Consultation c WHERE c.ticket.id = :ticketId ORDER BY c.createdAt DESC")
    List<Consultation> findAllByTicketId(@Param("ticketId") UUID ticketId);
}