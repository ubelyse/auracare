-- Create facility transfers table
CREATE TABLE facility_transfers (
                                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                    ticket_id UUID NOT NULL REFERENCES tickets(id),
                                    from_facility_id UUID NOT NULL REFERENCES facilities(id),
                                    to_facility_id UUID NOT NULL REFERENCES facilities(id),
                                    from_department_id UUID NOT NULL REFERENCES departments(id),
                                    to_department_id UUID NOT NULL REFERENCES departments(id),
                                    transfer_reason TEXT,
                                    transfer_type VARCHAR(20) NOT NULL,
                                    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                                    initiated_by UUID REFERENCES users(id),
                                    approved_by UUID REFERENCES users(id),
                                    approved_at TIMESTAMP,
                                    completed_at TIMESTAMP,
                                    notes TEXT,
                                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Add indexes
CREATE INDEX idx_facility_transfers_ticket_id ON facility_transfers(ticket_id);
CREATE INDEX idx_facility_transfers_from_facility ON facility_transfers(from_facility_id);
CREATE INDEX idx_facility_transfers_to_facility ON facility_transfers(to_facility_id);
CREATE INDEX idx_facility_transfers_status ON facility_transfers(status);
CREATE INDEX idx_facility_transfers_created_at ON facility_transfers(created_at);

-- RLS for facility transfers
ALTER TABLE facility_transfers ENABLE ROW LEVEL SECURITY;

CREATE POLICY facility_transfers_facility_isolation ON facility_transfers
    USING (
        from_facility_id = current_setting('app.current_facility_id')::UUID
        OR to_facility_id = current_setting('app.current_facility_id')::UUID
    );

-- Function to get facility metrics
CREATE OR REPLACE FUNCTION get_facility_metrics(p_facility_id UUID)
RETURNS JSONB AS $$
DECLARE
result JSONB;
    active_patients INTEGER;
    staff_count INTEGER;
    avg_wait_minutes NUMERIC;
BEGIN
    -- Count active patients
SELECT COUNT(*) INTO active_patients
FROM tickets
WHERE facility_id = p_facility_id
  AND active = true
  AND status NOT IN ('DISCHARGED', 'CANCELLED');

-- Count staff
SELECT COUNT(*) INTO staff_count
FROM users
WHERE facility_id = p_facility_id
  AND role IN ('DOCTOR', 'STAFF')
  AND active = true;

-- Average wait time
SELECT COALESCE(AVG(estimated_wait_minutes), 0) INTO avg_wait_minutes
FROM tickets
WHERE facility_id = p_facility_id
  AND status IN ('CHECKED_IN', 'TRIAGED');

-- Build JSON result
SELECT jsonb_build_object(
               'facilityId', p_facility_id,
               'activePatients', active_patients,
               'staffCount', staff_count,
               'avgWaitMinutes', ROUND(avg_wait_minutes),
               'updatedAt', CURRENT_TIMESTAMP
       ) INTO result;

RETURN result;
END;
$$ LANGUAGE plpgsql;