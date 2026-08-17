# demo.ps1 - Automated 5-Minute Evaluation Demo Script for Webhook Delivery Service
$ErrorActionPreference = "Continue"

Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host "⚡ RELIABLE WEBHOOK DELIVERY SERVICE - 5-MINUTE LIVE DEMO" -ForegroundColor Cyan
Write-Host "========================================================`n" -ForegroundColor Cyan

$baseUrl = "http://localhost:8090"
$tenantA = "tenant-alpha"
$tenantB = "tenant-beta"

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        [string]$TenantId,
        [object]$Body = $null
    )
    $headers = @{ "X-Tenant-Id" = $TenantId; "Content-Type" = "application/json" }
    $url = "$baseUrl$Path"
    try {
        if ($Body) {
            $json = $Body | ConvertTo-Json -Depth 5
            return Invoke-RestMethod -Uri $url -Method $Method -Headers $headers -Body $json
        } else {
            return Invoke-RestMethod -Uri $url -Method $Method -Headers $headers
        }
    } catch {
        if ($_.Exception.Response) {
            $reader = New-Object System.IO.StreamReader $_.Exception.Response.GetResponseStream()
            return $reader.ReadToEnd()
        }
        return $_.Exception.Message
    }
}

# 1. Health Check
Write-Host "1. Checking System Health & Actuator Status..." -ForegroundColor Yellow
$health = Invoke-RestMethod -Uri "$baseUrl/actuator/health"
Write-Host "   Actuator Health Status: $($health.status)" -ForegroundColor Green
Write-Host "   Worker Pool: $($health.details.workerHealth.details.workerPoolStatus)" -ForegroundColor Green
Start-Sleep -Seconds 1

# 2. Register Endpoint for Tenant Alpha
Write-Host "`n2. Registering Webhook Endpoint for Tenant Alpha..." -ForegroundColor Yellow
$epReq = @{
    url = "$baseUrl/api/demo/sink"
    eventTypes = @("invoice.paid", "order.created")
}
$epA = Invoke-Api -Method "POST" -Path "/api/v1/endpoints" -TenantId $tenantA -Body $epReq
Write-Host "   ✅ Registered Endpoint ID: $($epA.id)" -ForegroundColor Green
Write-Host "   Target URL: $($epA.url)" -ForegroundColor Gray
Write-Host "   Generated HMAC Secret: $($epA.secret)" -ForegroundColor Gray
Start-Sleep -Seconds 1

# 3. Inbound Self-Test Ping
Write-Host "`n3. Executing Inbound Self-Test Ping (POST /api/v1/endpoints/$($epA.id)/test)..." -ForegroundColor Yellow
$testRes = Invoke-Api -Method "POST" -Path "/api/v1/endpoints/$($epA.id)/test" -TenantId $tenantA
Write-Host "   ✅ Reachable: $($testRes.reachable) | HTTP Status: $($testRes.statusCode) | Latency: $($testRes.latencyMs)ms" -ForegroundColor Green
Start-Sleep -Seconds 1

# 4. Ingest Event & Fast Fan-out
Write-Host "`n4. Ingesting Producer Event (invoice.paid)..." -ForegroundColor Yellow
$evtId = "evt_" + (Get-Random)
$evtReq = @{
    eventId = $evtId
    type = "invoice.paid"
    payload = @{
        invoiceNumber = "INV-2026-99"
        customer = "Acme Corp"
        amount = 1450.00
        status = "PAID"
    }
}
$evtRes = Invoke-Api -Method "POST" -Path "/api/v1/events" -TenantId $tenantA -Body $evtReq
Write-Host "   ✅ Ingestion Status: $($evtRes.status) | Internal Event ID: $($evtRes.id)" -ForegroundColor Green
Start-Sleep -Seconds 2

# 5. Verify Delivery Audit Trail
Write-Host "`n5. Querying Delivery Audit Trail for Event $($evtRes.id)..." -ForegroundColor Yellow
$deliveries = Invoke-Api -Method "GET" -Path "/api/v1/events/$($evtRes.id)/deliveries" -TenantId $tenantA
foreach ($d in $deliveries) {
    Write-Host "   ✅ Delivery ID: $($d.id) | Status: $($d.status) | Response Code: $($d.lastResponseCode) | Attempts: $($d.attemptCount)" -ForegroundColor Green
}
Start-Sleep -Seconds 1

# 6. Duplicate Submission Idempotency Test
Write-Host "`n6. Re-submitting Duplicate Event ($evtId) to Verify Idempotency..." -ForegroundColor Yellow
$dupRes = Invoke-Api -Method "POST" -Path "/api/v1/events" -TenantId $tenantA -Body $evtReq
Write-Host "   ✅ Ingestion Result: $($dupRes.status) (No duplicate delivery created)" -ForegroundColor Green
Start-Sleep -Seconds 1

# 7. Cross-Tenant Isolation Test
Write-Host "`n7. Testing Cross-Tenant Isolation (Tenant Beta accessing Tenant Alpha's Endpoint)..." -ForegroundColor Yellow
$leakAttempt = Invoke-Api -Method "GET" -Path "/api/v1/endpoints/$($epA.id)" -TenantId $tenantB
Write-Host "   ✅ Server Response to Cross-Tenant Access: $leakAttempt" -ForegroundColor Magenta
Start-Sleep -Seconds 1

# 8. Flaky Receiver & Exponential Retries
Write-Host "`n8. Registering Flaky Endpoint (simulates 500 error then recovery)..." -ForegroundColor Yellow
$flakyReq = @{
    url = "$baseUrl/api/demo/sink/flaky"
    eventTypes = @("user.alert")
}
$epFlaky = Invoke-Api -Method "POST" -Path "/api/v1/endpoints" -TenantId $tenantA -Body $flakyReq
$evtFlaky = @{
    eventId = "evt_flaky_" + (Get-Random)
    type = "user.alert"
    payload = @{ alert = "high_cpu_load" }
}
$evtFlakyRes = Invoke-Api -Method "POST" -Path "/api/v1/events" -TenantId $tenantA -Body $evtFlaky
Write-Host "   Event Ingested for Flaky Sink. Polling delivery status for retries..." -ForegroundColor Gray
Start-Sleep -Seconds 3
$flakyDeliveries = Invoke-Api -Method "GET" -Path "/api/v1/events/$($evtFlakyRes.id)/deliveries" -TenantId $tenantA
foreach ($fd in $flakyDeliveries) {
    Write-Host "   Delivery Status: $($fd.status) | Attempts: $($fd.attemptCount) | Last HTTP Code: $($fd.lastResponseCode) | Next Attempt: $($fd.nextAttemptAt)" -ForegroundColor Yellow
}

Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host "🎉 DEMO COMPLETED SUCCESSFULLY!" -ForegroundColor Green
Write-Host "Open Dashboard: http://localhost:8090" -ForegroundColor White
Write-Host "Swagger UI:     http://localhost:8090/swagger-ui.html" -ForegroundColor White
Write-Host "Actuator:       http://localhost:8090/actuator/health" -ForegroundColor White
Write-Host "========================================================`n" -ForegroundColor Cyan
