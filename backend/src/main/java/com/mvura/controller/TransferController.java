package com.mvura.controller;

import com.mvura.dto.FacilityTransferDTO;
import com.mvura.model.FacilityTransfer;
import com.mvura.model.TransferType;  // ADD THIS IMPORT
import com.mvura.model.User;
import com.mvura.repository.UserRepository;
import com.mvura.service.FacilityAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/transfer")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('FACILITY_ADMIN', 'DISTRICT_ADMIN')")
public class TransferController {

    private final FacilityAdminService facilityAdminService;
    private final UserRepository userRepository;

    private UUID getActingUserId(Authentication auth) {
        User user = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + auth.getName()));
        return user.getId();
    }

    // ===== Helper method to convert FacilityTransfer to DTO =====
    private FacilityTransferDTO convertToDTO(FacilityTransfer transfer) {
        FacilityTransferDTO dto = new FacilityTransferDTO();
        dto.setId(transfer.getId());
        dto.setStatus(transfer.getStatus().name());
        dto.setReason(transfer.getTransferReason());
        dto.setTransferType(transfer.getTransferType().name());
        dto.setRequestedAt(transfer.getCreatedAt());
        dto.setApprovedAt(transfer.getApprovedAt());

        if (transfer.getTicket() != null) {
            dto.setTicketId(transfer.getTicket().getId());
            dto.setTicketNumber(transfer.getTicket().getTicketNumber());
        }

        if (transfer.getFromFacility() != null) {
            dto.setFromFacilityId(transfer.getFromFacility().getId());
            dto.setFromFacilityName(transfer.getFromFacility().getName());
        }

        if (transfer.getToFacility() != null) {
            dto.setToFacilityId(transfer.getToFacility().getId());
            dto.setToFacilityName(transfer.getToFacility().getName());
        }

        if (transfer.getInitiatedBy() != null) {
            dto.setRequestedBy(transfer.getInitiatedBy());
            userRepository.findById(transfer.getInitiatedBy()).ifPresent(user -> {
                dto.setRequestedByName(user.getFirstName() + " " + user.getLastName());
            });
        }

        if (transfer.getApprovedBy() != null) {
            dto.setApprovedBy(transfer.getApprovedBy());
            userRepository.findById(transfer.getApprovedBy()).ifPresent(user -> {
                dto.setApprovedByName(user.getFirstName() + " " + user.getLastName());
            });
        }

        return dto;
    }

    private List<FacilityTransferDTO> convertToDTOList(List<FacilityTransfer> transfers) {
        return transfers.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @PostMapping("/initiate")
    public ResponseEntity<?> initiateTransfer(
            @RequestParam UUID ticketId,
            @RequestParam UUID toFacilityId,
            @RequestParam String reason,
            @RequestParam(defaultValue = "ROUTINE") TransferType type,
            Authentication auth) {

        UUID actingUserId = getActingUserId(auth);

        FacilityTransfer transfer = facilityAdminService.initiateTransfer(
                ticketId, toFacilityId, reason, type, actingUserId
        );

        FacilityTransferDTO dto = convertToDTO(transfer);

        return ResponseEntity.ok(Map.of(
                "message", "Transfer initiated successfully",
                "transfer", dto,
                "status", transfer.getStatus()
        ));
    }

    @PostMapping("/approve/{transferId}")
    public ResponseEntity<?> approveTransfer(
            @PathVariable UUID transferId,
            Authentication auth) {

        UUID actingUserId = getActingUserId(auth);

        FacilityTransfer transfer = facilityAdminService.approveTransfer(transferId, actingUserId);
        FacilityTransferDTO dto = convertToDTO(transfer);

        return ResponseEntity.ok(Map.of(
                "message", "Transfer approved and executed successfully",
                "transfer", dto,
                "status", transfer.getStatus()
        ));
    }

    @GetMapping("/pending")
    public ResponseEntity<?> getPendingTransfers() {
        List<FacilityTransfer> transfers = facilityAdminService.getPendingTransfers();
        List<FacilityTransferDTO> dtos = convertToDTOList(transfers);
        return ResponseEntity.ok(Map.of(
                "transfers", dtos,
                "count", dtos.size()
        ));
    }

    @GetMapping("/history/ticket/{ticketId}")
    public ResponseEntity<?> getTicketTransferHistory(@PathVariable UUID ticketId) {
        List<FacilityTransfer> transfers = facilityAdminService.getTransferHistory(ticketId);
        List<FacilityTransferDTO> dtos = convertToDTOList(transfers);
        return ResponseEntity.ok(Map.of(
                "transfers", dtos,
                "count", dtos.size()
        ));
    }
}