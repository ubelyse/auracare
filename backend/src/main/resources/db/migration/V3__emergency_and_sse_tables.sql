-- Add emergency and transfer columns to tickets
ALTER TABLE tickets
    ADD COLUMN IF NOT EXISTS transfer_from_facility_id UUID REFERENCES facilities(id),
    ADD COLUMN IF NOT EXISTS transfer_from_department_id UUID REFERENCES departments(id),
    ADD COLUMN IF NOT EXISTS transfer_reason TEXT,
    ADD COLUMN IF NOT EXISTS transferred_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS emergency_mode_active BOOLEAN DEFAULT false,
    ADD COLUMN IF NOT EXISTS emergency_mode_started_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS emergency_mode_ended_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS emergency_option VARCHAR(20),
    ADD COLUMN IF NOT EXISTS last_updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- Add indexes for emergency queries
CREATE INDEX idx_tickets_emergency_active ON tickets(emergency_mode_active) WHERE emergency_mode_active = true;
CREATE INDEX idx_tickets_transferred_at ON tickets(transferred_at);
CREATE INDEX idx_tickets_assigned_doctor ON tickets(assigned_doctor_id);

-- Create notification preferences table
CREATE TABLE notification_preferences (
                                          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                          user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                          email_enabled BOOLEAN DEFAULT true,
                                          sms_enabled BOOLEAN DEFAULT true,
                                          push_enabled BOOLEAN DEFAULT true,
                                          ussd_enabled BOOLEAN DEFAULT true,
                                          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                          updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                          UNIQUE(user_id)
);

-- Create notification logs
CREATE TABLE notifications (
                               id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               user_id UUID REFERENCES users(id),
                               ticket_id UUID REFERENCES tickets(id),
                               type VARCHAR(30) NOT NULL, -- EMAIL, SMS, PUSH, USSD
                               title VARCHAR(200),
                               body TEXT,
                               metadata JSONB,
                               sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                               delivered BOOLEAN DEFAULT false,
                               read_at TIMESTAMP
);

-- Create indexes
CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_ticket_id ON notifications(ticket_id);
CREATE INDEX idx_notifications_sent_at ON notifications(sent_at);
CREATE INDEX idx_notifications_delivered ON notifications(delivered) WHERE delivered = false;

-- Create function to update last_updated_at
CREATE OR REPLACE FUNCTION update_last_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.last_updated_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_ticket_last_updated
    BEFORE UPDATE ON tickets
    FOR EACH ROW
    EXECUTE FUNCTION update_last_updated_at();