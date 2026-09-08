package io.github.immaghzbad.aetherst.shared.core

import io.github.immaghzbad.aetherst.shared.data.LogRepository

object NetworkHealer {

    private const val TAG = "Heal"
    private val virtualGatewayPrefixes = listOf("198.18.", "10.98.")

    fun heal() {
        if (!Elevation.isElevated()) return
        runCatching { killOrphanHelpers() }
        runCatching { removeStaleVirtualRoutes() }.onFailure {
            LogRepository.w("[$TAG] route sweep failed: ${it.localizedMessage}")
        }
        runCatching { stripOrphanVirtualAdapterIps() }
        runCatching { resetAdapterDns("AetherST") }
        runCatching {
            ProcessBuilder("ipconfig", "/flushdns").redirectErrorStream(true).start().waitFor()
        }
    }

    fun removeStaleVirtualRoutes(): Int {
        val proc = ProcessBuilder("route", "print", "-4").redirectErrorStream(true).start()
        val lines = proc.inputStream.bufferedReader().readLines()
        proc.waitFor()

        var removed = 0
        for (line in lines) {
            val cols = line.trim().split(Regex("\\s+"))
            if (cols.size < 3) continue
            val destination = cols[0]
            val netmask = cols[1]
            val gateway = cols[2]
            val isStale =
                ((destination == "0.0.0.0" || destination == "128.0.0.0") && netmask == "128.0.0.0") ||
                    (destination == "0.0.0.0" && netmask == "0.0.0.0")
            if (!isStale) continue
            if (virtualGatewayPrefixes.none { gateway.startsWith(it) }) continue
            runCatching {
                ProcessBuilder("route", "delete", destination, "mask", netmask, gateway)
                    .redirectErrorStream(true).start().waitFor()
                removed++
                LogRepository.i("[$TAG] removed stale route $destination mask $netmask via $gateway")
            }
        }
        return removed
    }

    private fun stripOrphanVirtualAdapterIps() {
        val script = "Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue | " +
            "Where-Object { (\$_.InterfaceDescription -like '*TAP-*' -or \$_.InterfaceDescription -like '*Wintun*') -and " +
            "(\$_.IPAddress -like '10.98.*' -or \$_.IPAddress -like '198.18.*') } | " +
            "Remove-NetIPAddress -Confirm:\$false -ErrorAction SilentlyContinue"
        val proc = ProcessBuilder("powershell", "-NoProfile", "-Command", script)
            .redirectErrorStream(true).start()
        proc.waitFor()
    }

    private fun killOrphanHelpers() {
        val script = "\$targets = Get-CimInstance Win32_Process -Filter \"Name='pwsh.exe' OR Name='powershell.exe'\" | " +
            "Where-Object { \$_.CommandLine -like '*tun-helper.ps1*' }; " +
            "foreach (\$x in \$targets) { Stop-Process -Id \$x.ProcessId -Force -ErrorAction SilentlyContinue }"
        ProcessBuilder("powershell", "-NoProfile", "-Command", script)
            .redirectErrorStream(true).start().waitFor()
    }

    private fun resetAdapterDns(alias: String) {
        val script = "if (Get-NetAdapter -Name '$alias' -ErrorAction SilentlyContinue) { " +
            "Set-DnsClientServerAddress -InterfaceAlias '$alias' -ResetServerAddresses }"
        ProcessBuilder("powershell", "-NoProfile", "-Command", script)
            .redirectErrorStream(true).start().waitFor()
    }
}
