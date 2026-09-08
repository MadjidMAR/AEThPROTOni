package io.github.immaghzbad.aetherst.shared.desktop

import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.Image
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import io.github.immaghzbad.aetherst.shared.desktop.TrayState
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.JWindow
import javax.swing.SwingConstants
import javax.swing.border.EmptyBorder

class TrayActions(
    val onShowWindow: () -> Unit,
    val onToggleConnection: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenRouting: () -> Unit,
    val onOpenLogs: () -> Unit = {},
    val onExit: () -> Unit
)

object AetherTray {
    private var trayIcon: TrayIcon? = null
    private var toggleItem: MenuItem? = null
    private var traySupportedCache: Boolean? = null
    private var popupWindow: JWindow? = null
    private var toggleButton: JButton? = null
    private var isConnectedState: Boolean = false

    fun isSupported(): Boolean {
        traySupportedCache?.let { return it }
        val supported = try {
            !GraphicsEnvironment.isHeadless() && SystemTray.isSupported()
        } catch (_: Exception) {
            false
        } catch (_: UnsatisfiedLinkError) {
            false
        }
        traySupportedCache = supported
        return supported
    }

    fun isInstalled(): Boolean = trayIcon != null

    fun uninstall() {
        runCatching {
            trayIcon?.let { SystemTray.getSystemTray().remove(it) }
            trayIcon = null
            toggleItem = null
            hidePopup()
        }
    }

    fun install(actions: TrayActions): Boolean {
        if (!isSupported()) return false
        if (trayIcon != null) return true
        return runCatching {
            val tray = SystemTray.getSystemTray()
            val toggle = MenuItem("Connect")
            toggleItem = toggle
            val icon = TrayIcon(createIcon(), "AetherST Tunnel", null).apply {
                isImageAutoSize = true
            }

            icon.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2 && e.button == MouseEvent.BUTTON1) {
                        hidePopup()
                        actions.onShowWindow()
                    }
                }

                override fun mouseReleased(e: MouseEvent) {
                    if (e.button == MouseEvent.BUTTON3 || e.isPopupTrigger) {
                        showCustomPopup(e, actions)
                    } else if (e.button == MouseEvent.BUTTON1 && e.clickCount == 1) {
                        hidePopup()
                        actions.onShowWindow()
                    }
                }

                override fun mousePressed(e: MouseEvent) {
                    if (e.isPopupTrigger) {
                        showCustomPopup(e, actions)
                    }
                }
            })

            tray.add(icon)
            trayIcon = icon
            true
        }.getOrDefault(false)
    }

    fun setConnectionState(connected: Boolean) {
        isConnectedState = connected
        runCatching {
            toggleItem?.label = if (connected) "Disconnect" else "Connect"
            toggleButton?.text = if (connected) "  Disconnect" else "  Connect"
            trayIcon?.toolTip = if (connected) "AetherST Tunnel - Connected" else "AetherST Tunnel"
        }
    }

    fun updateStatus(status: String) {
        runCatching {
            trayIcon?.toolTip = status
        }
    }

    private fun showCustomPopup(e: MouseEvent, actions: TrayActions) {
        try {
            hidePopup()
            val window = JWindow()
            window.isAlwaysOnTop = true
            window.type = java.awt.Window.Type.POPUP
            val content = JPanel()
            content.layout = BoxLayout(content, BoxLayout.Y_AXIS)
            content.background = Color(0x1C1C1E)
            content.border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color(0x3A3A3C), 1, true),
                EmptyBorder(8, 8, 8, 8)
            )

            val header = JPanel()
            header.layout = BoxLayout(header, BoxLayout.X_AXIS)
            header.background = Color(0x1C1C1E)
            header.border = EmptyBorder(6, 8, 10, 8)
            header.maximumSize = Dimension(220, 40)
            header.alignmentX = java.awt.Component.CENTER_ALIGNMENT
            val dot = JLabel("●")
            dot.foreground = if (isConnectedState) Color(0x34C759) else Color(0x8E8E93)
            dot.font = Font("Segoe UI", Font.BOLD, 10)
            val title = JLabel("  AetherST Tunnel")
            title.foreground = Color.WHITE
            title.font = Font("Segoe UI", Font.BOLD, 13)
            header.add(dot)
            header.add(title)
            header.add(Box.createHorizontalGlue())
            val closeBtn = JButton("X")
            closeBtn.foreground = Color(0x8E8E93)
            closeBtn.background = Color(0x2C2C2E)
            closeBtn.isContentAreaFilled = true
            closeBtn.isOpaque = true
            closeBtn.isFocusPainted = false
            closeBtn.isBorderPainted = false
            closeBtn.font = Font("Segoe UI", Font.BOLD, 12)
            closeBtn.maximumSize = Dimension(24, 24)
            closeBtn.preferredSize = Dimension(24, 24)
            closeBtn.minimumSize = Dimension(24, 24)
            closeBtn.border = BorderFactory.createLineBorder(Color(0x3A3A3C), 1, true)
            closeBtn.addActionListener { hidePopup() }
            closeBtn.addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(me: MouseEvent) {
                    closeBtn.background = Color(0x3A3A3C)
                    closeBtn.foreground = Color.WHITE
                }
                override fun mouseExited(me: MouseEvent) {
                    closeBtn.background = Color(0x2C2C2E)
                    closeBtn.foreground = Color(0x8E8E93)
                }
            })
            header.add(closeBtn)
            content.add(header)

            val sep1 = JSeparator(SwingConstants.HORIZONTAL)
            sep1.foreground = Color(0x2C2C2E)
            sep1.maximumSize = Dimension(220, 1)
            sep1.alignmentX = java.awt.Component.CENTER_ALIGNMENT
            content.add(sep1)
            content.add(Box.createVerticalStrut(6))

            fun addButton(text: String, color: Color = Color.WHITE, bg: Color = Color(0x2C2C2E), onClick: () -> Unit) {
                val btn = JButton(text)
                btn.foreground = color
                btn.background = bg
                btn.isContentAreaFilled = true
                btn.isOpaque = true
                btn.isFocusPainted = false
                btn.isBorderPainted = false
                btn.font = Font("Segoe UI", Font.PLAIN, 12)
                btn.horizontalAlignment = SwingConstants.LEFT
                btn.maximumSize = Dimension(204, 36)
                btn.preferredSize = Dimension(204, 36)
                btn.alignmentX = java.awt.Component.CENTER_ALIGNMENT
                btn.border = BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Color(0x3A3A3C), 0),
                    EmptyBorder(6, 12, 6, 12)
                )
                btn.addMouseListener(object : MouseAdapter() {
                    override fun mouseEntered(me: MouseEvent) {
                        btn.background = Color(0x3A3A3C)
                    }
                    override fun mouseExited(me: MouseEvent) {
                        btn.background = bg
                    }
                })
                btn.addActionListener {
                    hidePopup()
                    onClick()
                }
                content.add(btn)
                content.add(Box.createVerticalStrut(4))
                if (text.contains("Connect") || text.contains("Disconnect")) {
                    toggleButton = btn
                }
            }

            addButton("  Open Dashboard") { actions.onShowWindow() }
            addButton(if (isConnectedState) "  Disconnect" else "  Connect") { actions.onToggleConnection() }
            content.add(Box.createVerticalStrut(2))
            val sep2 = JSeparator(SwingConstants.HORIZONTAL)
            sep2.foreground = Color(0x2C2C2E)
            sep2.maximumSize = Dimension(220, 1)
            sep2.alignmentX = java.awt.Component.CENTER_ALIGNMENT
            content.add(sep2)
            content.add(Box.createVerticalStrut(6))
            addButton("  Settings") {
                TrayState.requestSettings()
                actions.onOpenSettings()
            }
            addButton("  Logs") {
                actions.onOpenLogs()
            }
            content.add(Box.createVerticalStrut(2))
            val sep3 = JSeparator(SwingConstants.HORIZONTAL)
            sep3.foreground = Color(0x2C2C2E)
            sep3.maximumSize = Dimension(220, 1)
            sep3.alignmentX = java.awt.Component.CENTER_ALIGNMENT
            content.add(sep3)
            content.add(Box.createVerticalStrut(6))
            addButton("  Exit", color = Color(0xFF3B30)) { actions.onExit() }

            window.contentPane.add(content)
            window.pack()
            val size = window.size
            var x = e.xOnScreen - size.width
            var y = e.yOnScreen - size.height - 4
            try {
                val ge = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
                if (x < ge.x) x = ge.x + 4
                if (y < ge.y) y = ge.y + 4
                if (x + size.width > ge.x + ge.width) x = ge.x + ge.width - size.width - 4
                if (y + size.height > ge.y + ge.height) y = e.yOnScreen - size.height - 4
            } catch (_: Exception) {}
            window.setLocation(x, y)
            window.isVisible = true
            window.addWindowFocusListener(object : java.awt.event.WindowFocusListener {
                override fun windowGainedFocus(we: java.awt.event.WindowEvent) {}
                override fun windowLostFocus(we: java.awt.event.WindowEvent) {
                    hidePopup()
                }
            })
            popupWindow = window
        } catch (_: Exception) {}
    }

    private fun hidePopup() {
        try {
            popupWindow?.isVisible = false
            popupWindow?.dispose()
        } catch (_: Exception) {}
        popupWindow = null
    }

    private fun createIcon(): Image {
        runCatching {
            val bytes = AetherTray::class.java.classLoader
                ?.getResourceAsStream("icon.png")
                ?.use { it.readBytes() }
            if (bytes != null && bytes.isNotEmpty()) {
                return Toolkit.getDefaultToolkit().createImage(bytes)
            }
        }
        val size = 16
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.color = java.awt.Color(0x007AFF)
        g.fillRoundRect(1, 1, size - 2, size - 2, 6, 6)
        g.color = java.awt.Color.WHITE
        g.drawString("A", 4, 12)
        g.dispose()
        return image
    }
}
