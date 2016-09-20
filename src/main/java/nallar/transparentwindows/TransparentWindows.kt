package nallar.transparentwindows

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.RECT
import com.sun.jna.platform.win32.WinUser
import nallar.transparentwindows.jna.User32Fast
import nallar.transparentwindows.jna.WindowWrapper
import java.awt.*
import java.awt.event.ActionListener
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JOptionPane

object TransparentWindows {
	private val mainThread = Thread.currentThread()
	private val POLLING_MS = 100L
	private val mainThreadRun = AtomicReference<() -> Unit>()
	private var lastActive: HWND? = null
	private var trayIcon: TrayIcon? = null
	private val windowOccluder: WindowOccluder = WindowOccluder()
	val forceFullTrans = 255
	var activeTrans = 225
	var foreInactiveTrans = 225
	var backTrans = 0

	inline fun debugPrint(@Suppress("UNUSED_PARAMETER") s: () -> String) {
		//println(s())
	}

	private fun exit() {
		runInMainThread {
			clearTransparencies()
			if (trayIcon != null)
				SystemTray.getSystemTray().remove(trayIcon)
			System.exit(0)
		}
		mainThread.interrupt()
		Thread.sleep(POLLING_MS)
	}

	private fun setForeInactiveTrans(s: String) {
		val t = s.toInt()
		runInMainThread { clearTransparencies(); foreInactiveTrans = t; }
	}

	private fun runInMainThread(r: () -> Unit) {
		while (!mainThreadRun.compareAndSet(null, r)) {
			Thread.sleep(POLLING_MS)
		}
	}

	private fun setupSystemTray() {
		val image = Toolkit.getDefaultToolkit().getImage(TransparentWindows::class.java.getResource("/icon.png"))
		val listener = ActionListener { e -> exit() }
		val popup = PopupMenu()
		val setForeInactiveTransItem = MenuItem("Set Inactive Transparency")
		setForeInactiveTransItem.addActionListener { setForeInactiveTrans(JOptionPane.showInputDialog("Set transparency level", foreInactiveTrans)) }
		popup.add(setForeInactiveTransItem)
		val defaultItem = MenuItem("Exit")
		defaultItem.addActionListener(listener)
		popup.add(defaultItem)
		trayIcon = TrayIcon(image, "Transparent Windows", popup)
		trayIcon!!.addActionListener(listener)
		trayIcon!!.isImageAutoSize = true
		SystemTray.getSystemTray().add(trayIcon!!)

		Runtime.getRuntime().addShutdownHook(Thread { exit(); })
	}

	private fun clearTransparencies() {
		for (windowWrapper in windows) {
			windowWrapper.setAlpha(forceFullTrans)
		}

		taskBar?.setAlpha(foreInactiveTrans)

		lastActive = null
	}

	private fun setTransparencies(active: HWND?) {
		val tb = taskBar
		if (tb != null) {
			tb.setAlpha(foreInactiveTrans)
			User32Fast.SetWindowAccent(tb.hwnd)
		}

		val windows = windows
		windowOccluder.occlude(windows)

		var activeWindowWrapper: WindowWrapper? = null

		if (active != null) {
			activeWindowWrapper = WindowWrapper(active)
			if (windows.remove(activeWindowWrapper)) {
				lastActive = active
			} else {
				activeWindowWrapper = null
			}
		}

		/*if (activeWindowWrapper == null && lastActive != null) {
            activeWindowWrapper = new WindowWrapper(lastActive);
            if (!windows.remove(activeWindowWrapper)) {
                activeWindowWrapper = null;
            }
        }*/

		if (activeWindowWrapper != null) {
			activeWindowWrapper.setAlpha(forceFullTrans)
		}

		for (windowWrapper in windows) {
			/*if (windowWrapper.title.contains(" - IntelliJ IDEA") || windowWrapper.title.contains(" - paint.net 4.")) {
                windowWrapper.visible = 2;
            }*/
			//if (windowWrapper.title.contains(" - Google Chrome")) {
			val style = User32Fast.GetWindowLongPtrA(windowWrapper.hwnd, WinUser.GWL_STYLE).toLong()
			// Check if it has a title bar
			if (style and 0x00C00000L != 0x00C00000L) {
				windowWrapper.visible = 3
			}

			if (windowWrapper.visible == 1 && style and 0x8L == 0x8L) {
				// it's an always on top window
				windowWrapper.visible = 2
			}

			windowWrapper.setAlpha(windowWrapper.alphaForVisibility())
		}
	}

	private fun isValidWindowForTransparency(hWnd: HWND, r: RECT, title: String): Boolean {
		if (title.contains("Planetside2 v")) {
			return false
		}

		val exe = User32Fast.GetWindowExe(hWnd)

		val exeLast = exe.substring(exe.lastIndexOf('\\') + 1)

		when (exeLast) {
			"Planetside2_x64.exe", "OBS.exe", "osu!.exe", "unknown.exe" -> return false
		}

		debugPrint {
			"Title found " + title + " for " + hWnd + " of process " +
				User32Fast.GetWindowThreadProcessId(hWnd) + " with exe " + exe
		}

		if (title.isEmpty() && exe == "C:\\Windows\\explorer.exe") {
			// Explorer-derp window?
			val height = r.bottom - r.top
			if (height > 900) {
				debugPrint { "Found likely explorer derp: $height $hWnd" }
				User32.INSTANCE.DestroyWindow(hWnd)
				User32.INSTANCE.CloseWindow(hWnd)
			}
		}

		when (title) {
			"", "Program Manager" -> {
				User32.INSTANCE.InvalidateRect(hWnd, null, true)
				val f = WinDef.DWORD((0x1 or 0x2 or 0x4 or 0x100 or 0x200 or 0x80).toLong())
				User32.INSTANCE.RedrawWindow(hWnd, null, null, f)

				return false
			}
		}
		return true
	}

	val windowsList: MutableList<WindowWrapper> = mutableListOf()
	val windowOrder: MutableList<HWND?> = mutableListOf()
	private val windows: MutableList<WindowWrapper>
		get() {
			val windows = windowsList
			val order = windowOrder
			windows.clear()
			order.clear()
			var top: HWND? = User32Fast.GetTopWindow(HWND(Pointer(0)))!!
			var lastTop: HWND? = null
			while (top != lastTop) {
				lastTop = top
				order.add(top)
				top = User32Fast.GetWindow(top, User32.GW_HWNDNEXT)
			}
			User32Fast.EnumWindows(WinUser.WNDENUMPROC { hWnd, lParam ->
				if (hWnd != null && User32Fast.IsWindowVisible(hWnd)) {
					val r = RECT()
					User32Fast.GetWindowRect(hWnd, r)
					if (r.left > -32000) {
						val buffer = ByteArray(1024)
						User32Fast.GetWindowTextA(hWnd, buffer, buffer.size)
						val title = Native.toString(buffer)
						if (!isValidWindowForTransparency(hWnd, r, title)) {
							return@WNDENUMPROC true
						}

						// workaround - rects oversized?
						val shrinkFactor = 20

						r.left = r.left + shrinkFactor
						r.top = r.top + shrinkFactor
						r.right = r.right - shrinkFactor
						r.bottom = r.bottom - shrinkFactor

						if (r.left > r.right) {
							r.left = r.right - 1
						}

						if (r.top > r.bottom) {
							r.top = r.bottom - 1
						}

						windows.add(WindowWrapper(hWnd, r, title))
					}
				}
				true
			}, null)
			Collections.sort(windows) { o1, o2 -> order.indexOf(o2.hwnd) - order.indexOf(o1.hwnd) }
			return windows
		}

	fun start() {
		setupSystemTray()

		var lastForeground: HWND? = null
		while (true) {
			try {
				Thread.sleep(POLLING_MS)
			} catch (ignored: InterruptedException) {
			}
			try {
				mainThreadRun.getAndSet(null)?.invoke()

				val foreground = User32Fast.GetForegroundWindow()
				if (foreground == lastForeground)
					continue

				lastForeground = foreground
				setTransparencies(foreground)
			} catch (t: Throwable) {
				t.printStackTrace()
			}
		}
	}

	private val taskBar: WindowWrapper?
		get() {
			return WindowWrapper(User32Fast.FindWindowA("Shell_TrayWnd", null) ?: return null)
		}

}
