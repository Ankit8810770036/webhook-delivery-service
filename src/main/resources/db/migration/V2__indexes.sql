-- V2__indexes.sql
-- Optimized indexes for high-throughput DB-level claiming and tenant-scoped lookups

-- Primary index for worker claim query (status = 'PENDING', next_attempt_at <= now, locked_until check)
CREATE INDEX idx_deliveries_due_claim ON deliveries (status, next_attempt_at, locked_until);

-- Multi-tenant delivery pagination and filtering at database level
CREATE INDEX idx_deliveries_tenant_endpoint_created ON deliveries (tenant_id, endpoint_id, created_at DESC);
CREATE INDEX idx_deliveries_tenant_event ON deliveries (tenant_id, event_id);
CREATE INDEX idx_deliveries_status ON deliveries (status);

-- Endpoint lookup per tenant
CREATE INDEX idx_endpoints_tenant_status ON endpoints (tenant_id, status);

-- Delivery attempts lookup for audit / visibility
CREATE INDEX idx_attempts_delivery_created ON delivery_attempts (delivery_id, created_at DESC);
