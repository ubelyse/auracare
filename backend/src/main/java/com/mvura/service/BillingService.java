package com.mvura.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvura.converter.EncryptedStringConverter;
import com.mvura.dto.BillingDTO;
import com.mvura.model.*;
import com.mvura.repository.BillingRepository;
import com.mvura.repository.ConsultationRepository;
import com.mvura.repository.ServicePricingRepository;
import com.mvura.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingService {

    private final BillingRepository billingRepository;
    private final TicketRepository ticketRepository;
    private final ConsultationRepository consultationRepository;
    private final ServicePricingRepository servicePricingRepository;
    private final InsuranceCalculatorService insuranceCalculatorService;
    private final AuditService auditService;
    private final SseService sseService;
    private final ObjectMapper objectMapper;
    private final EncryptedStringConverter encryptedStringConverter;

    // ===== CONVERSION METHODS =====

    public BillingDTO convertToDTO(Billing billing) {
        BillingDTO dto = new BillingDTO();
        dto.setId(billing.getId());
        dto.setInvoiceNumber(billing.getInvoiceNumber());
        dto.setStatus(billing.getStatus().name());
        dto.setTotalAmount(billing.getTotalAmount());
        dto.setPatientAmount(billing.getPatientAmount());
        dto.setInsuranceAmount(billing.getInsuranceAmount());
        dto.setPaymentMethod(billing.getPaymentMethod());
        dto.setTransactionId(billing.getTransactionId());

        dto.setIssuedAt(billing.getIssuedAt());
        dto.setCreatedAt(billing.getCreatedAt());
        dto.setPaidAt(billing.getPaidAt());

        if (billing.getInsuranceType() != null) {
            dto.setInsuranceType(billing.getInsuranceType().name());
        }

        if (billing.getPatient() != null) {
            dto.setPatientId(billing.getPatient().getId());
            dto.setPatientName(billing.getPatient().getFirstName() + " " + billing.getPatient().getLastName());
        }

        if (billing.getFacility() != null) {
            dto.setFacilityId(billing.getFacility().getId());
            dto.setFacilityName(billing.getFacility().getName());
        }

        if (billing.getTicket() != null) {
            dto.setTicketId(billing.getTicket().getId());
        }

        return dto;
    }

    public List<BillingDTO> convertToDTOList(List<Billing> billings) {
        return billings.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    // ===== ORIGINAL BILL GENERATION (kept for backward compatibility) =====

    @Transactional
    public Billing generateBill(UUID ticketId, String serviceCode) {
        log.info("========================================");
        log.info("📋 Generating bill for ticket: {}", ticketId);
        log.info("========================================");

        // 1. Get ticket
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> {
                    log.error("❌ Ticket not found: {}", ticketId);
                    return new RuntimeException("Ticket not found");
                });

        log.info("📌 Ticket Number: {}", ticket.getTicketNumber());
        log.info("📌 Ticket Status: {}", ticket.getStatus());

        // 2. Validate ticket status
        if (ticket.getStatus() != TicketStatus.CONSULTATION_DONE &&
                ticket.getStatus() != TicketStatus.PAYMENT_PENDING) {
            throw new RuntimeException("Ticket must be in CONSULTATION_DONE or PAYMENT_PENDING state to generate bill");
        }

        // ===== 3. CHECK IF BILL ALREADY EXISTS =====
        List<Billing> existingBills = billingRepository.findByTicketId(ticketId);
        if (!existingBills.isEmpty()) {
            Billing existingBill = existingBills.get(0);
            log.info("ℹ️ Bill already exists for ticket {}: {}", ticketId, existingBill.getInvoiceNumber());
            return existingBill;
        }

        // ===== 4. GET PATIENT =====
        User patient = ticket.getPatient();
        UUID facilityId = ticket.getFacility().getId();

        log.info("👤 Patient: {} {}", patient.getFirstName(), patient.getLastName());
        log.info("🏥 Facility ID: {}", facilityId);

        // ===== 5. GET INSURANCE TYPE =====
        InsuranceType insuranceType = getInsuranceType(ticket, patient);
        log.info("🛡️ Final Insurance Type: {}", insuranceType);

        // ===== 6. GET THE MOST RECENT CONSULTATION =====
        List<Consultation> consultations = consultationRepository.findAllByTicketId(ticketId);
        if (consultations.isEmpty()) {
            log.error("❌ No consultation found for ticket: {}", ticketId);
            throw new RuntimeException("No consultation found for ticket: " + ticketId);
        }

        Consultation consultation = consultations.get(0); // Most recent consultation
        log.info("📋 Consultation ID: {}", consultation.getId());
        log.info("📋 Consultation Created At: {}", consultation.getCreatedAt());
        log.info("📋 Total consultations for this ticket: {}", consultations.size());

        // ===== 7. CHECK LAB ORDERS DIRECTLY =====
        String labOrdersRaw = consultation.getLabOrders();
        log.info("🔐 Raw Lab Orders (from consultation): '{}'", labOrdersRaw);
        log.info("🔐 Is null? {}", labOrdersRaw == null);
        log.info("🔐 Length: {}", labOrdersRaw != null ? labOrdersRaw.length() : 0);

        // ===== 8. DECRYPT LAB ORDERS =====
        String decryptedLabOrders = decryptLabOrders(labOrdersRaw);
        log.info("🔓 Decrypted Lab Orders: '{}'", decryptedLabOrders);

        // ===== 9. GET CONSULTATION PRICING =====
        ServicePricing consultationPricing = findPricing("CONSULTATION", facilityId);
        log.info("💰 Consultation Pricing: {} - Base: {} RWF",
                consultationPricing.getServiceName(), consultationPricing.getBasePrice());

        // ===== 10. GET INSURANCE-SPECIFIC PRICE FOR CONSULTATION =====
        BigDecimal consultationPrice = getPriceForInsurance(consultationPricing, insuranceType);
        log.info("💵 Consultation price for {}: {} RWF (Base: {} RWF)",
                insuranceType, consultationPrice, consultationPricing.getBasePrice());

        // ===== 11. PARSE DECRYPTED LAB ORDERS =====
        List<String> labOrderCodes = parseLabOrders(decryptedLabOrders);
        log.info("🔬 Parsed lab codes: {}", labOrderCodes);

        // ===== 12. BUILD BILL ITEMS =====
        List<BillItem> billItems = new ArrayList<>();
        BigDecimal patientTotal = BigDecimal.ZERO;
        BigDecimal originalTotal = BigDecimal.ZERO;

        // Add consultation
        BillItem consultationItem = BillItem.builder()
                .serviceCode(consultationPricing.getServiceCode())
                .serviceName(consultationPricing.getServiceName())
                .category("CONSULTATION")
                .amount(consultationPrice)
                .originalPrice(consultationPricing.getBasePrice())
                .insuranceType(insuranceType.name())
                .build();
        billItems.add(consultationItem);
        patientTotal = patientTotal.add(consultationPrice);
        originalTotal = originalTotal.add(consultationPricing.getBasePrice());
        log.info("✅ Added consultation: {} RWF", consultationPrice);

        // Add lab tests
        if (labOrderCodes.isEmpty()) {
            log.warn("⚠️ No lab codes found to add to bill");
        } else {
            for (String labCode : labOrderCodes) {
                try {
                    log.info("🔍 Looking for pricing for lab: {}", labCode);
                    ServicePricing labPricing = findPricing(labCode, facilityId);
                    BigDecimal labPrice = getPriceForInsurance(labPricing, insuranceType);

                    BillItem labItem = BillItem.builder()
                            .serviceCode(labPricing.getServiceCode())
                            .serviceName(labPricing.getServiceName())
                            .category("LAB")
                            .amount(labPrice)
                            .originalPrice(labPricing.getBasePrice())
                            .insuranceType(insuranceType.name())
                            .build();
                    billItems.add(labItem);
                    patientTotal = patientTotal.add(labPrice);
                    originalTotal = originalTotal.add(labPricing.getBasePrice());
                    log.info("✅ Added lab: {} - {} RWF (Base: {} RWF)",
                            labCode, labPrice, labPricing.getBasePrice());
                } catch (Exception e) {
                    log.warn("⚠️ Lab service not found for code: {}, skipping. Error: {}", labCode, e.getMessage());
                }
            }
        }

        // ===== 13. CALCULATE TOTALS =====
        log.info("📊 Original Total: {} RWF", originalTotal);
        log.info("📊 Patient Pays: {} RWF", patientTotal);
        // FIX: Calculate insurance amount
        BigDecimal insuranceAmount = originalTotal.subtract(patientTotal);
        log.info("📊 Insurance Pays: {} RWF", insuranceAmount);

        // ===== 14. GENERATE INVOICE NUMBER =====
        String invoiceNumber = generateInvoiceNumber(ticket.getFacility());
        log.info("📄 Invoice Number: {}", invoiceNumber);

        // ===== 15. CREATE BILLING RECORD =====
        Billing billing = Billing.builder()
                .ticket(ticket)
                .patient(patient)
                .facility(ticket.getFacility())
                .invoiceNumber(invoiceNumber)
                .serviceCode(consultationPricing.getServiceCode())
                .serviceName(consultationPricing.getServiceName())
                .serviceCategory("CONSULTATION")
                .totalAmount(originalTotal)
                .patientAmount(patientTotal)
                .insuranceAmount(insuranceAmount)  // FIXED: Now uses calculated value
                .paidAmount(BigDecimal.ZERO)
                .insuranceType(insuranceType)
                .coPayPercentage(getCoPayPercentage(consultationPricing, insuranceType))
                .status(BillingStatus.PENDING)
                .issuedAt(LocalDateTime.now())
                .dueDate(LocalDateTime.now().plusDays(7))
                .items(buildItemsJson(billItems))
                .build();

        Billing saved = billingRepository.save(billing);
        log.info("✅ Billing record saved with ID: {}", saved.getId());

        // ===== 16. UPDATE TICKET STATUS =====
        ticket.setStatus(TicketStatus.PAYMENT_PENDING);
        ticketRepository.save(ticket);
        log.info("🔄 Ticket status updated to: PAYMENT_PENDING");

        // ===== 17. SEND SSE UPDATE =====
        try {
            sseService.sendTicketUpdate(ticket);
            log.info("📡 SSE update sent for ticket: {}", ticket.getTicketNumber());
        } catch (Exception e) {
            log.warn("⚠️ Failed to send SSE update: {}", e.getMessage());
        }

        // ===== 18. AUDIT LOG =====
        auditService.logAction(
                "BILL_GENERATED",
                "BILLING",
                saved.getId().toString(),
                patient.getUsername(),
                null,
                null,
                Map.of(
                        "invoiceNumber", invoiceNumber,
                        "originalTotal", originalTotal,
                        "patientAmount", patientTotal,
                        "insuranceAmount", insuranceAmount,  // FIXED
                        "insuranceType", insuranceType.name(),
                        "labCount", labOrderCodes.size(),
                        "ticketNumber", ticket.getTicketNumber()
                )
        );

        log.info("========================================");
        log.info("✅ BILL GENERATED SUCCESSFULLY");
        log.info("📄 Invoice: {}", invoiceNumber);
        log.info("🛡️ Insurance: {}", insuranceType);
        log.info("💰 Original Total: {} RWF", originalTotal);
        log.info("💵 Patient Pays: {} RWF", patientTotal);
        log.info("🏦 Insurance Pays: {} RWF", insuranceAmount);  // FIXED
        log.info("📋 Labs Included: {}", labOrderCodes.size());
        if (!labOrderCodes.isEmpty()) {
            log.info("📋 Lab Codes: {}", labOrderCodes);
        }
        log.info("========================================");

        return saved;
    }

    // ===== NEW OVERLOADED METHOD: Generate bill with existing consultation =====

    @Transactional
    public Billing generateBill(UUID ticketId, String serviceCode, Consultation consultation) {
        if (consultation == null) {
            // Fallback to the original method
            return generateBill(ticketId, serviceCode);
        }

        // Use the passed consultation instead of querying again
        return generateBillWithConsultation(ticketId, serviceCode, consultation);
    }

    // ===== PRIVATE METHOD: Generate bill using passed consultation =====

    private Billing generateBillWithConsultation(UUID ticketId, String serviceCode, Consultation consultation) {
        log.info("========================================");
        log.info("📋 Generating bill for ticket: {}", ticketId);
        log.info("========================================");

        // 1. Get ticket
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> {
                    log.error("❌ Ticket not found: {}", ticketId);
                    return new RuntimeException("Ticket not found");
                });

        log.info("📌 Ticket Number: {}", ticket.getTicketNumber());
        log.info("📌 Ticket Status: {}", ticket.getStatus());

        // 2. Validate ticket status
        if (ticket.getStatus() != TicketStatus.CONSULTATION_DONE &&
                ticket.getStatus() != TicketStatus.PAYMENT_PENDING) {
            throw new RuntimeException("Ticket must be in CONSULTATION_DONE or PAYMENT_PENDING state to generate bill");
        }

        // 3. Check if bill already exists
        List<Billing> existingBills = billingRepository.findByTicketId(ticketId);
        if (!existingBills.isEmpty()) {
            Billing existingBill = existingBills.get(0);
            log.info("ℹ️ Bill already exists for ticket {}: {}", ticketId, existingBill.getInvoiceNumber());
            return existingBill;
        }

        // 4. Get patient
        User patient = ticket.getPatient();
        UUID facilityId = ticket.getFacility().getId();

        log.info("👤 Patient: {} {}", patient.getFirstName(), patient.getLastName());
        log.info("🏥 Facility ID: {}", facilityId);

        // 5. Get insurance type
        InsuranceType insuranceType = getInsuranceType(ticket, patient);
        log.info("🛡️ Final Insurance Type: {}", insuranceType);

        // 6. USE THE PASSED CONSULTATION instead of querying
        log.info("📋 Using passed consultation ID: {}", consultation.getId());
        log.info("📋 Consultation Created At: {}", consultation.getCreatedAt());

        // 7. Check lab orders directly from the consultation
        String labOrdersRaw = consultation.getLabOrders();
        log.info("🔐 Raw Lab Orders (from consultation): '{}'", labOrdersRaw);
        log.info("🔐 Is null? {}", labOrdersRaw == null);
        log.info("🔐 Length: {}", labOrdersRaw != null ? labOrdersRaw.length() : 0);

        // 8. Decrypt lab orders
        String decryptedLabOrders = decryptLabOrders(labOrdersRaw);
        log.info("🔓 Decrypted Lab Orders: '{}'", decryptedLabOrders);

        // 9. Get consultation pricing
        ServicePricing consultationPricing = findPricing("CONSULTATION", facilityId);
        log.info("💰 Consultation Pricing: {} - Base: {} RWF",
                consultationPricing.getServiceName(), consultationPricing.getBasePrice());

        // 10. Get insurance-specific price for consultation
        BigDecimal consultationPrice = getPriceForInsurance(consultationPricing, insuranceType);
        log.info("💵 Consultation price for {}: {} RWF (Base: {} RWF)",
                insuranceType, consultationPrice, consultationPricing.getBasePrice());

        // 11. Parse decrypted lab orders
        List<String> labOrderCodes = parseLabOrders(decryptedLabOrders);
        log.info("🔬 Parsed lab codes: {}", labOrderCodes);

        // 12. Build bill items
        List<BillItem> billItems = new ArrayList<>();
        BigDecimal patientTotal = BigDecimal.ZERO;
        BigDecimal originalTotal = BigDecimal.ZERO;

        // Add consultation
        BillItem consultationItem = BillItem.builder()
                .serviceCode(consultationPricing.getServiceCode())
                .serviceName(consultationPricing.getServiceName())
                .category("CONSULTATION")
                .amount(consultationPrice)
                .originalPrice(consultationPricing.getBasePrice())
                .insuranceType(insuranceType.name())
                .build();
        billItems.add(consultationItem);
        patientTotal = patientTotal.add(consultationPrice);
        originalTotal = originalTotal.add(consultationPricing.getBasePrice());
        log.info("✅ Added consultation: {} RWF", consultationPrice);

        // Add lab tests
        if (labOrderCodes.isEmpty()) {
            log.warn("⚠️ No lab codes found to add to bill");
        } else {
            for (String labCode : labOrderCodes) {
                try {
                    log.info("🔍 Looking for pricing for lab: {}", labCode);
                    ServicePricing labPricing = findPricing(labCode, facilityId);
                    BigDecimal labPrice = getPriceForInsurance(labPricing, insuranceType);

                    BillItem labItem = BillItem.builder()
                            .serviceCode(labPricing.getServiceCode())
                            .serviceName(labPricing.getServiceName())
                            .category("LAB")
                            .amount(labPrice)
                            .originalPrice(labPricing.getBasePrice())
                            .insuranceType(insuranceType.name())
                            .build();
                    billItems.add(labItem);
                    patientTotal = patientTotal.add(labPrice);
                    originalTotal = originalTotal.add(labPricing.getBasePrice());
                    log.info("✅ Added lab: {} - {} RWF (Base: {} RWF)",
                            labCode, labPrice, labPricing.getBasePrice());
                } catch (Exception e) {
                    log.warn("⚠️ Lab service not found for code: {}, skipping. Error: {}", labCode, e.getMessage());
                }
            }
        }

        // 13. Calculate totals
        log.info("📊 Original Total: {} RWF", originalTotal);
        log.info("📊 Patient Pays: {} RWF", patientTotal);
        // FIX: Calculate insurance amount
        BigDecimal insuranceAmount = originalTotal.subtract(patientTotal);
        log.info("📊 Insurance Pays: {} RWF", insuranceAmount);

        // 14. Generate invoice number
        String invoiceNumber = generateInvoiceNumber(ticket.getFacility());
        log.info("📄 Invoice Number: {}", invoiceNumber);

        // 15. Create billing record
        Billing billing = Billing.builder()
                .ticket(ticket)
                .patient(patient)
                .facility(ticket.getFacility())
                .invoiceNumber(invoiceNumber)
                .serviceCode(consultationPricing.getServiceCode())
                .serviceName(consultationPricing.getServiceName())
                .serviceCategory("CONSULTATION")
                .totalAmount(originalTotal)
                .patientAmount(patientTotal)
                .insuranceAmount(insuranceAmount)  // FIXED: Now uses calculated value
                .paidAmount(BigDecimal.ZERO)
                .insuranceType(insuranceType)
                .coPayPercentage(getCoPayPercentage(consultationPricing, insuranceType))
                .status(BillingStatus.PENDING)
                .issuedAt(LocalDateTime.now())
                .dueDate(LocalDateTime.now().plusDays(7))
                .items(buildItemsJson(billItems))
                .build();

        Billing saved = billingRepository.save(billing);
        log.info("✅ Billing record saved with ID: {}", saved.getId());

        // 16. Update ticket status
        ticket.setStatus(TicketStatus.PAYMENT_PENDING);
        ticketRepository.save(ticket);
        log.info("🔄 Ticket status updated to: PAYMENT_PENDING");

        // 17. Send SSE update
        try {
            sseService.sendTicketUpdate(ticket);
            log.info("📡 SSE update sent for ticket: {}", ticket.getTicketNumber());
        } catch (Exception e) {
            log.warn("⚠️ Failed to send SSE update: {}", e.getMessage());
        }

        // 18. Audit log
        auditService.logAction(
                "BILL_GENERATED",
                "BILLING",
                saved.getId().toString(),
                patient.getUsername(),
                null,
                null,
                Map.of(
                        "invoiceNumber", invoiceNumber,
                        "originalTotal", originalTotal,
                        "patientAmount", patientTotal,
                        "insuranceAmount", insuranceAmount,  // FIXED
                        "insuranceType", insuranceType.name(),
                        "labCount", labOrderCodes.size(),
                        "ticketNumber", ticket.getTicketNumber()
                )
        );

        log.info("========================================");
        log.info("✅ BILL GENERATED SUCCESSFULLY");
        log.info("📄 Invoice: {}", invoiceNumber);
        log.info("🛡️ Insurance: {}", insuranceType);
        log.info("💰 Original Total: {} RWF", originalTotal);
        log.info("💵 Patient Pays: {} RWF", patientTotal);
        log.info("🏦 Insurance Pays: {} RWF", insuranceAmount);  // FIXED
        log.info("📋 Labs Included: {}", labOrderCodes.size());
        if (!labOrderCodes.isEmpty()) {
            log.info("📋 Lab Codes: {}", labOrderCodes);
        }
        log.info("========================================");

        return saved;
    }

    // ===== PREVIEW BILL WITHOUT SAVING =====

    @Transactional(readOnly = true)
    public Map<String, Object> previewBill(UUID ticketId, String serviceCode) {
        log.info("📋 Previewing bill for ticket: {}", ticketId);

        // 1. Get ticket
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> {
                    log.error("❌ Ticket not found: {}", ticketId);
                    return new RuntimeException("Ticket not found");
                });

        if (ticket.getStatus() != TicketStatus.CONSULTATION_DONE &&
                ticket.getStatus() != TicketStatus.PAYMENT_PENDING) {
            throw new RuntimeException("Ticket must be in CONSULTATION_DONE or PAYMENT_PENDING state to preview bill");
        }

        // 2. Get patient
        User patient = ticket.getPatient();
        UUID facilityId = ticket.getFacility().getId();

        // 3. Get insurance type
        InsuranceType insuranceType = getInsuranceType(ticket, patient);

        // 4. Get consultation
        Consultation consultation = consultationRepository.findByTicketId(ticketId)
                .orElseThrow(() -> {
                    log.error("❌ Consultation not found for ticket: {}", ticketId);
                    return new RuntimeException("Consultation not found for ticket: " + ticketId);
                });

        // 5. Decrypt lab orders
        String decryptedLabOrders = decryptLabOrders(consultation.getLabOrders());
        log.info("🔓 Decrypted Lab Orders: '{}'", decryptedLabOrders);

        // 6. Parse lab orders
        List<String> labOrderCodes = parseLabOrders(decryptedLabOrders);
        log.info("🔬 Parsed lab codes: {}", labOrderCodes);

        // 7. Get consultation pricing
        ServicePricing consultationPricing = findPricing("CONSULTATION", facilityId);
        BigDecimal consultationPrice = getPriceForInsurance(consultationPricing, insuranceType);

        // 8. Build preview items
        List<Map<String, Object>> billItems = new ArrayList<>();
        BigDecimal patientTotal = BigDecimal.ZERO;
        BigDecimal originalTotal = BigDecimal.ZERO;

        // Add consultation
        Map<String, Object> consultationItem = new HashMap<>();
        consultationItem.put("serviceCode", consultationPricing.getServiceCode());
        consultationItem.put("serviceName", consultationPricing.getServiceName());
        consultationItem.put("category", "CONSULTATION");
        consultationItem.put("amount", consultationPrice);
        consultationItem.put("originalPrice", consultationPricing.getBasePrice());
        consultationItem.put("insuranceType", insuranceType.name());
        billItems.add(consultationItem);
        patientTotal = patientTotal.add(consultationPrice);
        originalTotal = originalTotal.add(consultationPricing.getBasePrice());

        // Add labs
        for (String labCode : labOrderCodes) {
            try {
                ServicePricing labPricing = findPricing(labCode, facilityId);
                BigDecimal labPrice = getPriceForInsurance(labPricing, insuranceType);

                Map<String, Object> labItem = new HashMap<>();
                labItem.put("serviceCode", labPricing.getServiceCode());
                labItem.put("serviceName", labPricing.getServiceName());
                labItem.put("category", "LAB");
                labItem.put("amount", labPrice);
                labItem.put("originalPrice", labPricing.getBasePrice());
                labItem.put("insuranceType", insuranceType.name());
                billItems.add(labItem);
                patientTotal = patientTotal.add(labPrice);
                originalTotal = originalTotal.add(labPricing.getBasePrice());
            } catch (Exception e) {
                log.warn("⚠️ Lab service not found for code: {}, skipping", labCode);
            }
        }

        // Calculate insurance amount for preview
        BigDecimal insuranceAmount = originalTotal.subtract(patientTotal);

        // 9. Build preview response
        Map<String, Object> preview = new HashMap<>();
        preview.put("ticketNumber", ticket.getTicketNumber());
        preview.put("patientName", patient.getFirstName() + " " + patient.getLastName());
        preview.put("insuranceType", insuranceType.name());
        preview.put("originalTotal", originalTotal);
        preview.put("patientAmount", patientTotal);
        preview.put("insuranceAmount", insuranceAmount);  // Added
        preview.put("items", billItems);
        preview.put("labCount", labOrderCodes.size());
        preview.put("willLabsBeIncluded", labOrderCodes.size() > 0);
        preview.put("labCodes", labOrderCodes);
        preview.put("message", labOrderCodes.isEmpty() ?
                "⚠️ No lab orders found. Bill will only include consultation." :
                "✅ Bill will include consultation + " + labOrderCodes.size() + " lab(s)");

        log.info("📋 Preview complete: Labs included: {}, Total items: {}",
                labOrderCodes.size(), billItems.size());

        return preview;
    }

    // ===== HELPER: Decrypt lab orders =====

    private String decryptLabOrders(String encryptedLabOrders) {
        if (encryptedLabOrders == null || encryptedLabOrders.isEmpty()) {
            log.info("🔐 No lab orders to decrypt (null or empty)");
            return null;
        }

        try {
            String decrypted = encryptedStringConverter.convertToEntityAttribute(encryptedLabOrders);
            log.info("🔓 Successfully decrypted lab orders: '{}'", decrypted);
            return decrypted;
        } catch (Exception e) {
            log.error("❌ Failed to decrypt lab orders: {}", e.getMessage(), e);
            return encryptedLabOrders;
        }
    }

    // ===== HELPER: Get insurance type =====

    private InsuranceType getInsuranceType(Ticket ticket, User patient) {
        if (ticket.getInsuranceType() != null && !ticket.getInsuranceType().isEmpty()) {
            try {
                InsuranceType type = InsuranceType.valueOf(ticket.getInsuranceType().toUpperCase());
                log.info("✅ Using insurance from TICKET: {}", type);
                return type;
            } catch (IllegalArgumentException e) {
                log.warn("⚠️ Invalid insurance type on ticket: {}", ticket.getInsuranceType());
            }
        }

        InsuranceType patientType = patient.getInsuranceType() != null ?
                patient.getInsuranceType() : InsuranceType.UNINSURED;
        log.info("✅ Using insurance from PATIENT PROFILE: {}", patientType);
        return patientType;
    }

    // ===== HELPER: Get insurance-specific price =====

    private BigDecimal getPriceForInsurance(ServicePricing pricing, InsuranceType insuranceType) {
        BigDecimal price = switch (insuranceType) {
            case MUTUELLE -> pricing.getMutuellePrice() != null ? pricing.getMutuellePrice() : pricing.getBasePrice();
            case RSSB -> pricing.getRssbPrice() != null ? pricing.getRssbPrice() : pricing.getBasePrice();
            case MMI -> pricing.getMmiPrice() != null ? pricing.getMmiPrice() : pricing.getBasePrice();
            case PRIVATE -> pricing.getBasePrice();
            case UNINSURED -> pricing.getBasePrice();
            default -> pricing.getBasePrice();
        };
        return price;
    }

    // ===== HELPER: Get co-pay percentage =====

    private BigDecimal getCoPayPercentage(ServicePricing pricing, InsuranceType insuranceType) {
        return switch (insuranceType) {
            case MUTUELLE -> pricing.getMutuelleCoPayPercent() != null ?
                    pricing.getMutuelleCoPayPercent() : BigDecimal.valueOf(10);
            case RSSB -> pricing.getRssbCoPayPercent() != null ?
                    pricing.getRssbCoPayPercent() : BigDecimal.valueOf(15);
            case MMI -> pricing.getMmiCoPayPercent() != null ?
                    pricing.getMmiCoPayPercent() : BigDecimal.valueOf(15);
            case PRIVATE -> BigDecimal.valueOf(100);
            case UNINSURED -> BigDecimal.valueOf(100);
            default -> BigDecimal.valueOf(100);
        };
    }

    // ===== PARSE LAB ORDERS =====

    private List<String> parseLabOrders(String labOrders) {
        log.info("🔍 Parsing lab orders: '{}'", labOrders);

        if (labOrders == null || labOrders.isEmpty()) {
            log.info("📋 Lab orders is null or empty, returning empty list");
            return new ArrayList<>();
        }

        try {
            // Clean the string - remove extra spaces
            String cleaned = labOrders.trim();
            log.info("📋 Cleaned lab orders: '{}'", cleaned);

            List<String> result = new ArrayList<>();

            // Check if it's a JSON array format
            if (cleaned.startsWith("[")) {
                log.info("📋 Detected JSON array format");
                // Remove brackets and quotes
                cleaned = cleaned.replace("[", "").replace("]", "").replace("\"", "").trim();
                if (!cleaned.isEmpty()) {
                    String[] items = cleaned.split(",");
                    for (String item : items) {
                        String trimmed = item.trim();
                        if (!trimmed.isEmpty()) {
                            result.add(trimmed);
                        }
                    }
                }
            }
            // Check if it's comma-separated
            else if (cleaned.contains(",")) {
                log.info("📋 Detected comma-separated format");
                String[] items = cleaned.split(",");
                for (String item : items) {
                    String trimmed = item.trim();
                    if (!trimmed.isEmpty()) {
                        result.add(trimmed);
                    }
                }
            }
            // Single item
            else {
                log.info("📋 Detected single lab order");
                result.add(cleaned);
            }

            // Extract service codes from "Name (CODE)" format
            List<String> extractedCodes = new ArrayList<>();
            for (String item : result) {
                log.info("📋 Processing lab item: '{}'", item);

                // Try to extract code from "Name (CODE)" format
                if (item.contains("(") && item.contains(")")) {
                    int start = item.indexOf("(");
                    int end = item.indexOf(")");
                    if (start != -1 && end != -1 && start < end) {
                        String code = item.substring(start + 1, end).trim();
                        log.info("📋 Extracted code: '{}' from '{}'", code, item);
                        extractedCodes.add(code);
                    } else {
                        // If extraction fails, use the whole string
                        log.info("📋 Could not extract code, using whole string: '{}'", item);
                        extractedCodes.add(item);
                    }
                } else {
                    // If no parentheses, use the whole string
                    log.info("📋 No parentheses found, using whole string: '{}'", item);
                    extractedCodes.add(item);
                }
            }

            log.info("📋 Final extracted lab codes: {}", extractedCodes);
            return extractedCodes;

        } catch (Exception e) {
            log.error("❌ Failed to parse lab orders: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    // ===== FIND PRICING WITH FALLBACK =====

    private ServicePricing findPricing(String serviceCode, UUID facilityId) {
        log.debug("🔍 Looking for pricing: serviceCode={}", serviceCode);

        // Try 1: Exact match
        Optional<ServicePricing> pricing = servicePricingRepository.findByServiceCode(serviceCode);
        if (pricing.isPresent()) {
            return pricing.get();
        }

        // Try 2: Case-insensitive
        List<ServicePricing> allPricing = servicePricingRepository.findAll();
        for (ServicePricing p : allPricing) {
            if (p.getServiceCode() != null && p.getServiceCode().equalsIgnoreCase(serviceCode)) {
                return p;
            }
            if (p.getServiceName() != null && p.getServiceName().equalsIgnoreCase(serviceCode)) {
                return p;
            }
        }

        // Try 3: By category
        List<ServicePricing> categoryPricing = servicePricingRepository.findByCategory(serviceCode.toUpperCase());
        if (!categoryPricing.isEmpty()) {
            return categoryPricing.get(0);
        }

        // Try 4: Default for CONSULTATION
        if ("CONSULTATION".equalsIgnoreCase(serviceCode)) {
            log.warn("⚠️ No CONSULTATION pricing found, creating default");
            ServicePricing defaultPricing = new ServicePricing();
            defaultPricing.setServiceCode("CONSULTATION");
            defaultPricing.setServiceName("General Consultation");
            defaultPricing.setCategory("CONSULTATION");
            defaultPricing.setBasePrice(BigDecimal.valueOf(5000));
            defaultPricing.setMutuelleCoPayPercent(BigDecimal.valueOf(10));
            defaultPricing.setRssbCoPayPercent(BigDecimal.valueOf(15));
            defaultPricing.setMmiCoPayPercent(BigDecimal.valueOf(15));
            defaultPricing.setActive(true);
            defaultPricing.calculatePrices();
            return defaultPricing;
        }

        throw new RuntimeException("Service pricing not found for: " + serviceCode);
    }

    // ===== BUILD ITEMS JSON =====

    private String buildItemsJson(List<BillItem> items) {
        try {
            List<Map<String, Object>> jsonItems = new ArrayList<>();
            for (BillItem item : items) {
                Map<String, Object> map = new HashMap<>();
                map.put("serviceCode", item.getServiceCode());
                map.put("serviceName", item.getServiceName());
                map.put("category", item.getCategory());
                map.put("amount", item.getAmount());
                if (item.getOriginalPrice() != null) {
                    map.put("originalPrice", item.getOriginalPrice());
                }
                if (item.getInsuranceType() != null) {
                    map.put("insuranceType", item.getInsuranceType());
                }
                jsonItems.add(map);
            }
            return objectMapper.writeValueAsString(jsonItems);
        } catch (Exception e) {
            log.error("Failed to serialize billing items", e);
            return "[]";
        }
    }

    // ===== BILL ITEM INNER CLASS =====

    @lombok.Data
    @lombok.Builder
    public static class BillItem {
        private String serviceCode;
        private String serviceName;
        private String category;
        private BigDecimal amount;
        private BigDecimal originalPrice;
        private String insuranceType;
    }

    // ===== PAYMENT PROCESSING =====

    @Transactional
    public Billing processPayment(UUID billingId, String paymentMethod, String transactionId) {
        log.info("Processing payment for bill: {}", billingId);

        Billing billing = billingRepository.findById(billingId)
                .orElseThrow(() -> new RuntimeException("Billing record not found"));

        if (billing.getStatus() == BillingStatus.PAID) {
            throw new RuntimeException("Bill already paid");
        }

        billing.setStatus(BillingStatus.PAID);
        billing.setPaymentMethod(paymentMethod);
        billing.setTransactionId(transactionId);
        billing.setPaymentReference(UUID.randomUUID().toString());
        billing.setPaidAt(LocalDateTime.now());
        billing.setPaidAmount(billing.getPatientAmount());

        Billing saved = billingRepository.save(billing);

        Ticket ticket = billing.getTicket();
        if (ticket != null) {
            ticket.setStatus(TicketStatus.DISCHARGED);
            ticket.setActive(false);
            ticketRepository.save(ticket);
            try {
                sseService.sendTicketUpdate(ticket);
            } catch (Exception e) {
                log.warn("Failed to send SSE update: {}", e.getMessage());
            }
        }

        auditService.logAction(
                "PAYMENT_PROCESSED",
                "BILLING",
                billingId.toString(),
                billing.getPatient().getUsername(),
                null,
                null,
                Map.of(
                        "invoiceNumber", billing.getInvoiceNumber(),
                        "amount", billing.getPatientAmount(),
                        "method", paymentMethod,
                        "transactionId", transactionId
                )
        );

        return saved;
    }

    // ===== VOID BILL =====

    @Transactional
    public Billing voidBill(UUID billingId, String reason) {
        Billing billing = billingRepository.findById(billingId)
                .orElseThrow(() -> new RuntimeException("Billing record not found"));

        if (billing.getStatus() == BillingStatus.PAID) {
            throw new RuntimeException("Cannot void a paid bill. Process refund instead.");
        }

        billing.setStatus(BillingStatus.CANCELLED);
        Billing saved = billingRepository.save(billing);

        auditService.logAction(
                "BILL_VOIDED",
                "BILLING",
                billingId.toString(),
                billing.getPatient().getUsername(),
                null,
                null,
                Map.of("reason", reason)
        );

        return saved;
    }

    // ===== QUERY METHODS =====

    public Billing getBill(UUID billingId) {
        return billingRepository.findById(billingId)
                .orElseThrow(() -> new RuntimeException("Bill not found"));
    }

    public List<Billing> getPatientBills(UUID patientId) {
        return billingRepository.findByPatientId(patientId);
    }

    public List<Billing> getFacilityBills(UUID facilityId) {
        return billingRepository.findByFacilityId(facilityId);
    }

    public List<Billing> getPatientPendingBills(UUID patientId) {
        return billingRepository.findByPatientIdAndStatus(patientId, BillingStatus.PENDING);
    }

    // ===== FINANCIAL SUMMARY METHODS =====

    public Map<String, Object> getFacilityFinancialSummary(UUID facilityId) {
        Map<String, Object> summary = new HashMap<>();

        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startOfMonth = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
            LocalDateTime startOfDay = now.withHour(0).withMinute(0).withSecond(0);

            BigDecimal monthRevenue = billingRepository.sumPaidAmountByFacilityAndDateRange(facilityId, startOfMonth, now);
            BigDecimal todayRevenue = billingRepository.sumPaidAmountByFacilityAndDateRange(facilityId, startOfDay, now);
            long pendingBills = billingRepository.countByFacilityIdAndStatus(facilityId, BillingStatus.PENDING);
            long totalBills = billingRepository.countByFacilityId(facilityId);

            summary.put("facilityId", facilityId);
            summary.put("monthRevenue", monthRevenue != null ? monthRevenue : BigDecimal.ZERO);
            summary.put("todayRevenue", todayRevenue != null ? todayRevenue : BigDecimal.ZERO);
            summary.put("pendingBills", pendingBills);
            summary.put("totalBills", totalBills);
            summary.put("currency", "RWF");
        } catch (Exception e) {
            log.warn("Could not get facility financial summary: {}", e.getMessage());
            summary.put("error", "Unable to generate summary");
        }

        return summary;
    }

    public List<Map<String, Object>> getInsuranceClaimsSummary() {
        List<Map<String, Object>> claims = new ArrayList<>();

        try {
            List<Object[]> results = billingRepository.getInsuranceClaimsSummary();
            for (Object[] result : results) {
                Map<String, Object> claim = new HashMap<>();
                claim.put("insuranceType", result[0]);
                claim.put("totalAmount", result[1]);
                claims.add(claim);
            }
        } catch (Exception e) {
            log.warn("Could not get insurance claims summary: {}", e.getMessage());
        }

        return claims;
    }

    // ===== AGING REPORT METHODS =====

    public List<Object[]> getAgingReport(UUID facilityId) {
        try {
            return billingRepository.getAgingReport(facilityId);
        } catch (Exception e) {
            log.error("Failed to get aging report: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public Map<String, Object> getAgingReportFormatted(UUID facilityId) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> agingData = new ArrayList<>();
        BigDecimal totalOutstanding = BigDecimal.ZERO;

        try {
            List<Object[]> results = billingRepository.getAgingReport(facilityId);

            for (Object[] row : results) {
                Map<String, Object> categoryData = new HashMap<>();
                String category = (String) row[0];
                Long count = (Long) row[1];
                BigDecimal amount = (BigDecimal) row[2];

                categoryData.put("category", category);
                categoryData.put("count", count);
                categoryData.put("totalOutstanding", amount);
                agingData.add(categoryData);

                if (amount != null) {
                    totalOutstanding = totalOutstanding.add(amount);
                }
            }

            result.put("facilityId", facilityId);
            result.put("agingData", agingData);
            result.put("totalOutstanding", totalOutstanding);
            result.put("generatedAt", LocalDateTime.now());

        } catch (Exception e) {
            log.error("Failed to get aging report: {}", e.getMessage());
            result.put("error", "Unable to generate aging report");
            result.put("agingData", new ArrayList<>());
        }

        return result;
    }

    // ===== HELPER METHODS =====

    private String generateInvoiceNumber(Facility facility) {
        String prefix = facility.getCode() + "-INV-";
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        java.security.SecureRandom random = new java.security.SecureRandom();
        int suffix = 1000 + random.nextInt(9000);
        return prefix + date + "-" + suffix;
    }
}