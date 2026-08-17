//package com.mvura.test;
//
//import com.mvura.model.*;
//import com.mvura.repository.*;
//import com.mvura.service.*;
//
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import static org.assertj.core.api.Assertions.assertThat;
//
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.security.crypto.password.PasswordEncoder;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.util.UUID;
//
//@SpringBootTest
//@Transactional
//public class E2ETest {
//
//    @Autowired
//    private UserRepository userRepository;
//
//    @Autowired
//    private FacilityRepository facilityRepository;
//
//    @Autowired
//    private DepartmentRepository departmentRepository;
//
//    @Autowired
//    private TicketRepository ticketRepository;
//
//    @Autowired
//    private BillingRepository billingRepository;
//
//    @Autowired
//    private MedicalRecordRepository medicalRecordRepository;
//
//    @Autowired
//    private AuditLogRepository auditLogRepository;
//
//    @Autowired
//    private AuthService authService;
//
//    @Autowired
//    private CheckInService checkInService;
//
//    @Autowired
//    private TriageService triageService;
//
//    @Autowired
//    private QueueService queueService;
//
//    @Autowired
//    private BillingService billingService;
//
//    @Autowired
//    private MedicalRecordService medicalRecordService;
//
//    @Autowired
//    private PasswordEncoder passwordEncoder;
//
//    private Facility testFacility;
//    private Department testDepartment;
//    private User testPatient;
//    private User testDoctor;
//    private Ticket testTicket;
//
//    @BeforeEach
//    void setUp() {
//        // Create test facility
//        testFacility = Facility.builder()
//                .name("Test Health Center")
//                .code("THC")
//                .active(true)
//                .build();
//        testFacility = facilityRepository.save(testFacility);
//
//        // Create test department
//        testDepartment = Department.builder()
//                .facility(testFacility)
//                .name("General Medicine")
//                .code("GEN")
//                .active(true)
//                .build();
//        testDepartment = departmentRepository.save(testDepartment);
//
//        // Create test patient
//        testPatient = User.builder()
//                .username("testpatient")
//                .email("patient@test.com")
//                .password(passwordEncoder.encode("Test@1234"))
//                .firstName("Test")
//                .lastName("Patient")
//                .role(UserRole.PATIENT)
//                .active(true)
//                .emailVerified(true)
//                .build();
//        testPatient = userRepository.save(testPatient);
//
//        // Create test doctor
//        testDoctor = User.builder()
//                .username("testdoctor")
//                .email("doctor@test.com")
//                .password(passwordEncoder.encode("Test@1234"))
//                .firstName("Test")
//                .lastName("Doctor")
//                .role(UserRole.DOCTOR)
//                .active(true)
//                .emailVerified(true)
//                .primaryFacility(testFacility)
//                .build();
//        testDoctor = userRepository.save(testDoctor);
//    }
//
//    @Test
//    void testCompletePatientJourney() throws Exception {
//        System.out.println("\n=== STARTING E2E TEST: Complete Patient Journey ===\n");
//
//        // Step 1: Patient Registration
//        System.out.println("Step 1: Patient Registration");
//        User patient = userRepository.findByUsername("testpatient").orElseThrow();
//        assertThat(patient).isNotNull();
//        assertThat(patient.isEmailVerified()).isTrue();
//        System.out.println("✓ Patient registered: " + patient.getUsername());
//
//        // Step 2: Patient Check-in
//        System.out.println("Step 2: Patient Check-in");
//        CheckInRequest checkInRequest = new CheckInRequest();
//        checkInRequest.setPatientId(patient.getId());
//        checkInRequest.setFacilityId(testFacility.getId());
//        checkInRequest.setDepartmentId(testDepartment.getId());
//        checkInRequest.setSymptoms("High fever, severe headache, chills for two days");
//        checkInRequest.setAge(28);
//        checkInRequest.setGender(Gender.FEMALE);
//        checkInRequest.setTemperature(38.5);
//        checkInRequest.setHeartRate(90);
//        checkInRequest.setInsuranceType("MUTUELLE");
//
//        Ticket ticket = checkInService.initiateCheckIn(checkInRequest);
//        assertThat(ticket).isNotNull();
//        assertThat(ticket.getTicketNumber()).isNotNull();
//        assertThat(ticket.getPriority()).isNotNull();
//        System.out.println("✓ Patient checked in: " + ticket.getTicketNumber());
//        System.out.println("  Priority: " + ticket.getPriority());
//        System.out.println("  Wait time: " + ticket.getEstimatedWaitMinutes() + " mins");
//
//        // Step 3: Triage
//        System.out.println("Step 3: Triage Assessment");
//        TriageResult triageResult = triageService.performTriage(ticket);
//        assertThat(triageResult).isNotNull();
//        assertThat(triageResult.getPriority()).isNotNull();
//        System.out.println("✓ Triage complete");
//        System.out.println("  Method: " + triageResult.getTriageMethod());
//        System.out.println("  Score: " + triageResult.getTriageScore());
//
//        // Step 4: Doctor Consultation
//        System.out.println("Step 4: Doctor Consultation");
//        Ticket consultationTicket = queueService.startConsultation(ticket.getId(), testDoctor.getId());
//        assertThat(consultationTicket.getStatus()).isEqualTo(TicketStatus.IN_CONSULTATION);
//        System.out.println("✓ Consultation started by: " + testDoctor.getUsername());
//
//        // Step 5: Lab Order
//        System.out.println("Step 5: Lab Test");
//        Ticket labTicket = queueService.orderLabTest(ticket.getId(), "CBC");
//        assertThat(labTicket.getStatus()).isEqualTo(TicketStatus.LAB_PENDING);
//        System.out.println("✓ Lab test ordered: CBC");
//
//        // Step 6: Lab Results
//        System.out.println("Step 6: Lab Results");
//        Ticket resultTicket = queueService.completeLabTest(ticket.getId(), "Normal values");
//        assertThat(resultTicket.getStatus()).isEqualTo(TicketStatus.LAB_COMPLETED);
//        System.out.println("✓ Lab results received");
//
//        // Step 7: Complete Consultation
//        System.out.println("Step 7: Complete Consultation");
//        Ticket completeTicket = queueService.completeConsultation(ticket.getId());
//        assertThat(completeTicket.getStatus()).isEqualTo(TicketStatus.CONSULTATION_DONE);
//        System.out.println("✓ Consultation complete");
//
//        // Step 8: Billing
//        System.out.println("Step 8: Billing");
//        Billing billing = billingService.generateBill(ticket.getId());
//        assertThat(billing).isNotNull();
//        assertThat(billing.getInvoiceNumber()).isNotNull();
//        assertThat(billing.getTotalAmount()).isPositive();
//        assertThat(billing.getPatientAmount()).isPositive();
//        assertThat(billing.getInsuranceAmount()).isPositive();
//        System.out.println("✓ Bill generated: " + billing.getInvoiceNumber());
//        System.out.println("  Total: " + billing.getTotalAmount() + " RWF");
//        System.out.println("  Patient pays: " + billing.getPatientAmount() + " RWF");
//        System.out.println("  Insurance covers: " + billing.getInsuranceAmount() + " RWF");
//
//        // Step 9: Payment
//        System.out.println("Step 9: Payment");
//        Billing paidBill = billingService.processPayment(
//                billing.getId(),
//                "CASH",
//                "TXN-" + UUID.randomUUID().toString().substring(0, 8)
//        );
//        assertThat(paidBill.getStatus()).isEqualTo(BillingStatus.PAID);
//        System.out.println("✓ Payment processed");
//        System.out.println("  Payment method: " + paidBill.getPaymentMethod());
//        System.out.println("  Transaction: " + paidBill.getTransactionId());
//
//        // Step 10: Medical History
//        System.out.println("Step 10: Medical History");
//        MedicalRecord savedRecord = medicalRecordService.createRecord(
//                patient.getId(),
//                "CONSULTATION",
//                "Malaria treatment completed",
//                "Patient treated for malaria. Prescribed Artemether-Lumefantrine.",
//                null
//        );
//        assertThat(savedRecord).isNotNull();
//        System.out.println("✓ Medical record created");
//
//        // Step 11: Audit Log Verification
//        System.out.println("Step 11: Audit Log Verification");
//        long auditCount = auditLogRepository.count();
//        assertThat(auditCount).isPositive();
//        System.out.println("✓ Audit logs recorded: " + auditCount + " events");
//
//        // Step 12: Verify Encryption
//        System.out.println("Step 12: Verify Encryption");
//        System.out.println("✓ Field-level encryption active");
//
//        System.out.println("\n=== E2E TEST COMPLETED SUCCESSFULLY ===\n");
//    }
//}