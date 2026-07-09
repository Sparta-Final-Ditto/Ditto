# Ditto load test - user seeding script
# Creates test accounts in user_service and exports UUIDs to CSV

$userCount = 100  # change as needed: 100 / 300 / 600 / 1000

Write-Host "=== Step 1: Creating $userCount test accounts ==="
$successCount = 0
$failCount = 0

1..$userCount | ForEach-Object {
    $i = $_ - 1
    $body = @{
        email     = "loadtest$i@test.com"
        password  = "password1234"
        nickname  = "tester$i"
        gender    = "MALE"
        birthdate = "2000-01-01"
    } | ConvertTo-Json

    try {
        Invoke-RestMethod -Uri "http://localhost:8081/api/v1/auth/signup" `
            -Method Post -ContentType "application/json" -Body $body | Out-Null
        $successCount++
    } catch {
        $failCount++
        Write-Host "FAIL: loadtest$i - $($_.Exception.Message)"
    }
}

Write-Host "=== Signup done: success=$successCount fail=$failCount ==="

Write-Host "=== Step 2: Exporting UUIDs from DB to CSV ==="
$sql = "COPY (SELECT id, email FROM users WHERE email LIKE 'loadtest%' ORDER BY email) TO STDOUT WITH CSV HEADER"
$outputPath = "$PSScriptRoot\..\data\user_loadtest_final.csv"
$sql | docker exec -i postgres-match psql -U postgres -d ditto_user > $outputPath

Write-Host "=== Done: $outputPath ==="
Get-Content $outputPath | Select-Object -First 5
Write-Host "Total lines (including header):"
(Get-Content $outputPath).Count
