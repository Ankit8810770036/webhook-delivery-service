#!/bin/bash
# demo.sh - Automated 5-Minute Evaluation Demo Script for Webhook Delivery Service

set -e

BASE_URL="http://localhost:8090"
TENANT_A="tenant-alpha"
TENANT_B="tenant-beta"

echo -e "\n========================================================"
echo "⚡ RELIABLE WEBHOOK DELIVERY SERVICE - 5-MINUTE LIVE DEMO"
echo -e "========================================================\n"

# 1. Health Check
echo "1. Checking System Health & Actuator Status..."
curl -s "$BASE_URL/actuator/health"
echo ""

# 2. Register Endpoint
echo -e "\n2. Registering Webhook Endpoint for Tenant Alpha..."
EP_RES=$(curl -s -X POST "$BASE_URL/api/v1/endpoints" \
  -H "X-Tenant-Id: $TENANT_A" \
  -H "Content-Type: application/json" \
  -d '{"url":"http://localhost:8090/api/demo/sink","eventTypes":["invoice.paid"]}')
echo "$EP_RES"
EP_ID=$(echo "$EP_RES" | grep -o '"id":"[^"]*' | cut -d'"' -f4)

# 3. Test Ping
echo -e "\n3. Testing Inbound Self-Test Ping..."
curl -s -X POST "$BASE_URL/api/v1/endpoints/$EP_ID/test" \
  -H "X-Tenant-Id: $TENANT_A"
echo ""

# 4. Ingest Event
echo -e "\n4. Ingesting Producer Event (invoice.paid)..."
EVT_ID="evt_$(date +%s)"
EVT_RES=$(curl -s -X POST "$BASE_URL/api/v1/events" \
  -H "X-Tenant-Id: $TENANT_A" \
  -H "Content-Type: application/json" \
  -d "{\"eventId\":\"$EVT_ID\",\"type\":\"invoice.paid\",\"payload\":{\"amount\":299.99,\"currency\":\"USD\"}}")
echo "$EVT_RES"
INTERNAL_EVT_ID=$(echo "$EVT_RES" | grep -o '"id":"[^"]*' | cut -d'"' -f4)

# 5. Check Deliveries
echo -e "\n5. Querying Deliveries for Event $INTERNAL_EVT_ID..."
sleep 2
curl -s "$BASE_URL/api/v1/events/$INTERNAL_EVT_ID/deliveries" \
  -H "X-Tenant-Id: $TENANT_A"
echo ""

# 6. Idempotency Test
echo -e "\n6. Re-submitting Duplicate Event ($EVT_ID)..."
curl -s -X POST "$BASE_URL/api/v1/events" \
  -H "X-Tenant-Id: $TENANT_A" \
  -H "Content-Type: application/json" \
  -d "{\"eventId\":\"$EVT_ID\",\"type\":\"invoice.paid\",\"payload\":{\"amount\":299.99,\"currency\":\"USD\"}}"
echo ""

# 7. Multi-Tenancy Cross-Access Rejection
echo -e "\n7. Cross-Tenant Access Attempt (Tenant Beta accessing Tenant Alpha's Endpoint)..."
curl -s -w "\nHTTP Status: %{http_code}\n" -X GET "$BASE_URL/api/v1/endpoints/$EP_ID" \
  -H "X-Tenant-Id: $TENANT_B"

echo -e "\n========================================================"
echo "🎉 DEMO COMPLETE! Dashboard: http://localhost:8090"
echo -e "========================================================\n"
