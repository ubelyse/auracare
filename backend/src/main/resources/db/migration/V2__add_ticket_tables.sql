-- Create ticket table
CREATE TABLE tickets (
                         id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                         ticket_number VARCHAR(50) NOT NULL UNIQUE,
                         patient_id UUID NOT NULL REFERENCES users(id),
                         facility_id UUID NOT NULL REFERENCES facilities(id),
                         department_id UUID NOT NULL REFERENCES departments(id),
                         status VARCHAR(30) NOT NULL,
                         priority VARCHAR(20) NOT NULL,
                         symptoms TEXT,
                         sanitized_symptoms TEXT,
                         age INTEGER,
                         gender VARCHAR(10),
                         is_pregnant BOOLEAN,
                         temperature DECIMAL(4,1),
                         heart_rate INTEGER,
                         blood_pressure_systolic INTEGER,
                         blood_pressure_diastolic INTEGER,
                         insurance_type VARCHAR(50),
                         triage_score INTEGER,
                         triage_method VARCHAR(20),
                         ai_confidence DECIMAL(3,2),
                         estimated_wait_minutes INTEGER,
                         queue_position INTEGER,
                         checked_in_at TIMESTAMP,
                         triaged_at TIMESTAMP,
                         consultation_started_at TIMESTAMP,
                         consultation_completed_at TIMESTAMP,
                         assigned_doctor_id UUID REFERENCES users(id),
                         active BOOLEAN DEFAULT true,
                         created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                         updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Add indexes
CREATE INDEX idx_tickets_patient_id ON tickets(patient_id);
CREATE INDEX idx_tickets_facility_id ON tickets(facility_id);
CREATE INDEX idx_tickets_department_id ON tickets(department_id);
CREATE INDEX idx_tickets_status ON tickets(status);
CREATE INDEX idx_tickets_priority ON tickets(priority);
CREATE INDEX idx_tickets_ticket_number ON tickets(ticket_number);
CREATE INDEX idx_tickets_active ON tickets(active) WHERE active = true;
CREATE INDEX idx_tickets_created_at ON tickets(created_at);

-- Add RLS for tickets
ALTER TABLE tickets ENABLE ROW LEVEL SECURITY;

-- RLS Policies for tickets
CREATE POLICY tickets_facility_isolation ON tickets
    USING (facility_id = current_setting('app.current_facility_id')::UUID);

-- Create function to get queue position
CREATE OR REPLACE FUNCTION get_queue_position(
    p_facility_id UUID,
    p_department_id UUID,
    p_ticket_id UUID
) RETURNS INTEGER AS $$
DECLARE
pos INTEGER;
BEGIN
SELECT COUNT(*)::INTEGER INTO pos
FROM tickets
WHERE facility_id = p_facility_id
  AND department_id = p_department_id
  AND active = true
  AND status NOT IN ('DISCHARGED', 'CANCELLED')
  AND created_at <= (SELECT created_at FROM tickets WHERE id = p_ticket_id);

RETURN pos;
END;
$$ LANGUAGE plpgsql;

-- Create trigger to update queue position
CREATE OR REPLACE FUNCTION update_queue_positions()
RETURNS TRIGGER AS $$
BEGIN
    -- Update queue positions for all active tickets in this department
UPDATE tickets
SET queue_position = (
    SELECT COUNT(*)
    FROM tickets t2
    WHERE t2.facility_id = NEW.facility_id
      AND t2.department_id = NEW.department_id
      AND t2.active = true
      AND t2.status NOT IN ('DISCHARGED', 'CANCELLED')
      AND t2.created_at <= tickets.created_at
)
WHERE facility_id = NEW.facility_id
  AND department_id = NEW.department_id
  AND active = true
  AND status NOT IN ('DISCHARGED', 'CANCELLED');

RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_queue_positions
    AFTER INSERT OR UPDATE OF status ON tickets
    FOR EACH ROW
    EXECUTE FUNCTION update_queue_positions();