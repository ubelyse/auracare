-- Add audit log constraints for write-only
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS hash VARCHAR(128);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS previous_hash VARCHAR(128);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS blockchain_index INTEGER;

-- Create function to calculate hash for audit chain
CREATE OR REPLACE FUNCTION calculate_audit_hash(
    p_id UUID,
    p_username VARCHAR(50),
    p_action VARCHAR(50),
    p_resource_type VARCHAR(50),
    p_resource_id VARCHAR(50),
    p_ip_address VARCHAR(45),
    p_details JSONB,
    p_created_at TIMESTAMP,
    p_previous_hash VARCHAR(128)
) RETURNS VARCHAR(128) AS $$
DECLARE
hash_data TEXT;
BEGIN
    hash_data := COALESCE(p_id::TEXT, '') || '|' ||
                 COALESCE(p_username, '') || '|' ||
                 COALESCE(p_action, '') || '|' ||
                 COALESCE(p_resource_type, '') || '|' ||
                 COALESCE(p_resource_id, '') || '|' ||
                 COALESCE(p_ip_address, '') || '|' ||
                 COALESCE(p_details::TEXT, '') || '|' ||
                 COALESCE(p_created_at::TEXT, '') || '|' ||
                 COALESCE(p_previous_hash, '');

RETURN ENCODE(SHA256(hash_data::BYTEA), 'hex');
END;
$$ LANGUAGE plpgsql;

-- Create trigger for audit hash calculation
CREATE OR REPLACE FUNCTION audit_log_trigger()
RETURNS TRIGGER AS $$
DECLARE
prev_hash VARCHAR(128);
BEGIN
    -- Get previous hash from last record
SELECT hash INTO prev_hash
FROM audit_logs
ORDER BY blockchain_index DESC
    LIMIT 1;

-- Set blockchain index
NEW.blockchain_index := COALESCE(prev_hash, '0')::INTEGER + 1;

    -- Set previous hash
    NEW.previous_hash := prev_hash;

    -- Calculate current hash
    NEW.hash := calculate_audit_hash(
        NEW.id,
        NEW.username,
        NEW.action,
        NEW.resource_type,
        NEW.resource_id,
        NEW.ip_address,
        NEW.details::JSONB,
        NEW.created_at,
        NEW.previous_hash
    );

RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger
DROP TRIGGER IF EXISTS audit_log_trigger ON audit_logs;
CREATE TRIGGER audit_log_trigger
    BEFORE INSERT ON audit_logs
    FOR EACH ROW
    EXECUTE FUNCTION audit_log_trigger();

-- Prevent updates and deletes on audit_logs
CREATE OR REPLACE FUNCTION prevent_audit_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Audit logs are write-only. UPDATE and DELETE are prohibited.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER prevent_audit_update
    BEFORE UPDATE ON audit_logs
    FOR EACH ROW
    EXECUTE FUNCTION prevent_audit_modification();

CREATE TRIGGER prevent_audit_delete
    BEFORE DELETE ON audit_logs
    FOR EACH ROW
    EXECUTE FUNCTION prevent_audit_modification();