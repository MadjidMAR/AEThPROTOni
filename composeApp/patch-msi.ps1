param(
    [string]$msiPath
)
Write-Host "Patching MSI: $msiPath"
$installer = New-Object -ComObject WindowsInstaller.Installer
try {
    $database = $installer.OpenDatabase($msiPath, 1)
} catch {
    Write-Host "Failed to open database: $_"
    exit 1
}
try {
    $sql = "INSERT INTO CustomAction (Action, Type, Source, Target) VALUES ('CleanupAppData', 50, 'SystemFolder', 'cmd.exe /c rmdir /S /Q ""%APPDATA%\\AetherST-Tunnel""')"
    $view = $database.OpenView($sql)
    $view.Execute()
    $view.Close()
    Write-Host "Inserted CustomAction"
} catch {
    Write-Host "CustomAction insert failed (maybe exists): $_"
}
try {
    $sql2 = "INSERT INTO InstallExecuteSequence (Action, Condition, Sequence) VALUES ('CleanupAppData', 'REMOVE=""ALL""', 3501)"
    $view2 = $database.OpenView($sql2)
    $view2.Execute()
    $view2.Close()
    Write-Host "Inserted InstallExecuteSequence"
} catch {
    Write-Host "Sequence insert failed: $_"
}
try {
    $sql3 = "INSERT INTO CustomAction (Action, Type, Source, Target) VALUES ('CleanupTempLogs', 50, 'SystemFolder', 'cmd.exe /c rmdir /S /Q ""%TEMP%\\AetherST""')"
    $view3 = $database.OpenView($sql3)
    $view3.Execute()
    $view3.Close()
    Write-Host "Inserted CleanupTempLogs"
} catch {
    Write-Host "CleanupTempLogs insert failed: $_"
}
try {
    $sql4 = "INSERT INTO InstallExecuteSequence (Action, Condition, Sequence) VALUES ('CleanupTempLogs', 'REMOVE=""ALL""', 3502)"
    $view4 = $database.OpenView($sql4)
    $view4.Execute()
    $view4.Close()
    Write-Host "Inserted TempLogs Sequence"
} catch {
    Write-Host "TempLogs Sequence failed: $_"
}
$database.Commit()
[System.Runtime.InteropServices.Marshal]::ReleaseComObject($installer) | Out-Null
Write-Host "Patch completed"
