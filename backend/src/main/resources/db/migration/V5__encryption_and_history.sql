-- Create consultations table with encrypted fields
CREATE TABLE consultations (
                               id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               ticket_id UUID NOT NULL REFERENCES tickets(id),
                               doctor_id UUID NOT NULL REFERENCES users(id),
                               diagnosis TEXT, -- Encrypted
                               notes TEXT, -- Encrypted
                               prescription TEXT, -- Encrypted
                               lab_orders TEXT, -- Encrypted
                               lab_results TEXT, -- Encrypted
                               symptoms TEXT, -- Encrypted
                               diagnosis_code VARCHAR(20), -- Not encrypted for indexing
                               follow_up_date TIMESTAMP,
                               started_at TIMESTAMP,
                               completed_at TIMESTAMP,
                               created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                               updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create medical records table
CREATE TABLE medical_records (
                                 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                 patient_id UUID NOT NULL REFERENCES users(id),
                                 record_type VARCHAR(50) NOT NULL,
                                 record_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                 summary TEXT, -- Encrypted
                                 details TEXT, -- Encrypted
                                 metadata JSONB,
                                 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Add indexes
CREATE INDEX idx_consultations_ticket_id ON consultations(ticket_id);
CREATE INDEX idx_consultations_doctor_id ON consultations(doctor_id);
CREATE INDEX idx_medical_records_patient_id ON medical_records(patient_id);
CREATE INDEX idx_medical_records_record_type ON medical_records(record_type);
CREATE INDEX idx_medical_records_record_date ON medical_records(record_date);

-- RLS for consultations
ALTER TABLE consultations ENABLE ROW LEVEL SECURITY;
ALTER TABLE medical_records ENABLE ROW LEVEL SECURITY;

-- RLS Policies for consultations
CREATE POLICY consultations_facility_isolation ON consultations
    USING (ticket_id IN (SELECT id FROM tickets WHERE facility_id = current_setting('app.current_facility_id')::UUID));

-- RLS Policies for medical records
CREATE POLICY medical_records_patient_isolation ON medical_records
    USING (patient_id = current_setting('app.current_patient_id')::UUID);

-- Function to create medical record from consultation
CREATE OR REPLACE FUNCTION create_medical_record_from_consultation()
RETURNS TRIGGER AS $$
BEGIN
INSERT INTO medical_records (patient_id, record_type, summary, details, record_date)
SELECT
    t.patient_id,
    'CONSULTATION',
    CASE
        WHEN NEW.diagnosis IS NOT NULL THEN 'Diagnosis: ' || NEW.diagnosis
        ELSE 'Consultation completed'
        END,
    'Consultation on ' || NEW.started_at || '. Notes: ' || COALESCE(NEW.notes, 'No notes') ||
    CASE
        WHEN NEW.prescription IS NOT NULL THEN '. Prescription: ' || NEW.prescription
        ELSE ''
        END,
    NEW.completed_at
FROM tickets t
WHERE t.id = NEW.ticket_id;

RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_create_medical_record
    AFTER UPDATE OF completed_at ON consultations
    FOR EACH ROW
    WHEN (NEW.completed_at IS NOT NULL)
    EXECUTE FUNCTION create_medical_record_from_consultation();