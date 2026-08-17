package com.mvura.repository;

import com.mvura.model.FacilityTransfer;
import com.mvura.model.TransferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FacilityTransferRepository extends JpaRepository<FacilityTransfer, UUID> {

    List<FacilityTransfer> findByTicketId(UUID ticketId);

    List<FacilityTransfer> findByFromFacilityId(UUID facilityId);

    List<FacilityTransfer> findByToFacilityId(UUID facilityId);

    List<FacilityTransfer> findByStatus(TransferStatus status);

    @Query("SELECT ft FROM FacilityTransfer ft WHERE ft.fromFacility.id = :facilityId AND ft.status = 'PENDING'")
    List<FacilityTransfer> findPendingTransfersFromFacility(@Param("facilityId") UUID facilityId);

    @Query("SELECT ft FROM FacilityTransfer ft WHERE ft.toFacility.id = :facilityId AND ft.status = 'PENDING'")
    List<FacilityTransfer> findPendingTransfersToFacility(@Param("facilityId") UUID facilityId);

    @Query("SELECT COUNT(ft) FROM FacilityTransfer ft WHERE ft.ticket.id = :ticketId")
    long countTransfersForTicket(@Param("ticketId") UUID ticketId);
}