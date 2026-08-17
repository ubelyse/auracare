-- Create billing table
CREATE TABLE billing (
                         id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                         ticket_id UUID NOT NULL REFERENCES tickets(id),
                         patient_id UUID NOT NULL REFERENCES users(id),
                         facility_id UUID NOT NULL REFERENCES facilities(id),
                         invoice_number VARCHAR(50) NOT NULL UNIQUE,
                         total_amount DECIMAL(12,0) NOT NULL,
                         insurance_amount DECIMAL(12,0),
                         patient_amount DECIMAL(12,0),
                         insurance_type VARCHAR(20),
                         co_pay_percentage DECIMAL(5,2),
                         status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                         payment_method VARCHAR(30),
                         transaction_id VARCHAR(100),
                         payment_reference VARCHAR(100),
                         items JSONB,
                         issued_at TIMESTAMP,
                         due_date TIMESTAMP,
                         paid_at TIMESTAMP,
                         created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                         updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create service_pricing table
CREATE TABLE service_pricing (
                                 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                 service_code VARCHAR(50) NOT NULL UNIQUE,
                                 service_name VARCHAR(200) NOT NULL,
                                 category VARCHAR(50) NOT NULL,
                                 base_price DECIMAL(12,0) NOT NULL,
                                 mutuelle_price DECIMAL(12,0),
                                 rssb_price DECIMAL(12,0),
                                 private_price DECIMAL(12,0),
                                 description TEXT,
                                 active BOOLEAN DEFAULT true,
                                 facility_id UUID REFERENCES facilities(id),
                                 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                 updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create insurance_providers table
CREATE TABLE insurance_providers (
                                     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                     code VARCHAR(20) NOT NULL UNIQUE,
                                     name VARCHAR(100) NOT NULL,
                                     patient_co_pay_percentage DECIMAL(5,2) NOT NULL,
                                     max_coverage_amount DECIMAL(12,0),
                                     active BOOLEAN DEFAULT true,
                                     contact_email VARCHAR(100),
                                     contact_phone VARCHAR(20),
                                     requirements JSONB,
                                     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                     updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Add indexes
CREATE INDEX idx_billing_ticket_id ON billing(ticket_id);
CREATE INDEX idx_billing_patient_id ON billing(patient_id);
CREATE INDEX idx_billing_facility_id ON billing(facility_id);
CREATE INDEX idx_billing_status ON billing(status);
CREATE INDEX idx_billing_invoice_number ON billing(invoice_number);
CREATE INDEX idx_billing_issued_at ON billing(issued_at);
CREATE INDEX idx_billing_paid_at ON billing(paid_at);

CREATE INDEX idx_service_pricing_code ON service_pricing(service_code);
CREATE INDEX idx_service_pricing_category ON service_pricing(category);
CREATE INDEX idx_service_pricing_facility ON service_pricing(facility_id);

-- Insert default insurance providers
INSERT INTO insurance_providers (id, code, name, patient_co_pay_percentage, active) VALUES
                                                                                        ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'MUTUELLE', 'Mutuelle de Santé', 10.00, true),
                                                                                        ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'RSSB', 'RSSB', 15.00, true),
                                                                                        ('cccccccc-cccc-cccc-cccc-cccccccccccc', 'PRIVATE', 'Private Insurance', 100.00, true),
                                                                                        ('dddddddd-dddd-dddd-dddd-dddddddddddd', 'NONE', 'No Insurance', 100.00, true);

-- Insert default service pricing
INSERT INTO service_pricing (id, service_code, service_name, category, base_price, mutuelle_price, rssb_price, private_price) VALUES
                                                                                                                                  ('11111111-1111-1111-1111-111111111111', 'CONSULTATION', 'General Medical Consultation', 'CONSULTATION', 30000, 25000, 27000, 30000),
                                                                                                                                  ('22222222-2222-2222-2222-222222222222', 'CONSULTATION_SPECIALIST', 'Specialist Consultation', 'CONSULTATION', 50000, 40000, 45000, 50000),
                                                                                                                                  ('33333333-3333-3333-3333-333333333333', 'LAB_CBC', 'Complete Blood Count', 'LAB', 15000, 12000, 13500, 15000),
                                                                                                                                  ('44444444-4444-4444-4444-444444444444', 'LAB_MALARIA', 'Malaria Test', 'LAB', 5000, 4000, 4500, 5000),
                                                                                                                                  ('55555555-5555-5555-5555-555555555555', 'LAB_URINE', 'Urinalysis', 'LAB', 8000, 6500, 7200, 8000),
                                                                                                                                  ('66666666-6666-6666-6666-666666666666', 'PROCEDURE_MINOR', 'Minor Procedure', 'PROCEDURE', 25000, 20000, 22500, 25000),
                                                                                                                                  ('77777777-7777-7777-7777-777777777777', 'MEDICATION_GENERIC', 'Generic Medication', 'MEDICATION', 5000, 4000, 4500, 5000);

-- RLS for billing
ALTER TABLE billing ENABLE ROW LEVEL SECURITY;
ALTER TABLE service_pricing ENABLE ROW LEVEL SECURITY;
ALTER TABLE insurance_providers ENABLE ROW LEVEL SECURITY;

-- RLS Policies
CREATE POLICY billing_patient_isolation ON billing
    USING (patient_id = current_setting('app.current_patient_id')::UUID);

CREATE POLICY billing_facility_isolation ON billing
    USING (facility_id = current_setting('app.current_facility_id')::UUID);