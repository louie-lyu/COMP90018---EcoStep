# On Windows, `firebase emulators:exec` can leave the Firestore emulator JVM running after it
# exits, which blocks port 8080 on the next run. This stops only that emulator process.
# Usage (from functions/):  powershell -ExecutionPolicy Bypass -File scripts\stop-orphaned-emulators.ps1

Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
    Where-Object { $_.CommandLine -like '*cloud-firestore-emulator*' } |
    ForEach-Object {
        Stop-Process -Id $_.ProcessId -Confirm:$false
        Write-Host "Stopped Firestore emulator (PID $($_.ProcessId))"
    }
