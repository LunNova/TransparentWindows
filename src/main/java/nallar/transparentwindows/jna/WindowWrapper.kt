package nallar.transparentwindows.jna

import com.sun.jna.platform.win32.*
import com.sun.jna.ptr.ByteByReference
import com.sun.jna.ptr.IntByReference
import nallar.transparentwindows.TransparentWindows

class WindowWrapper internal constructor(internal var hwnd: WinDef.HWND) {
	internal var visible = 0 // 0 invisible, 1 transparent, 2 on top
	internal var rect: WinDef.RECT? = null
	internal var title: String = ""

	internal constructor(hwnd: WinDef.HWND, rect: WinDef.RECT, title: String) : this(hwnd) {
		this.rect = rect
		this.title = title
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other == null || javaClass != other.javaClass) return false

		val that = other as WindowWrapper?

		return hwnd == that!!.hwnd

	}

	internal val isTransparent: Boolean
		get() {
			val data = User32Fast.GetWindowLongPtrA(hwnd, WinUser.GWL_EXSTYLE)
			return data.toInt() and WinUser.WS_EX_LAYERED == WinUser.WS_EX_LAYERED
		}

	internal fun setTransparent(enable: Boolean): Boolean {
		if (!enable && !isTransparent) {
			return true
		}
		val data = User32Fast.GetWindowLongPtrA(hwnd, WinUser.GWL_EXSTYLE)
		User32Fast.SetWindowLongPtrA(hwnd, WinUser.GWL_EXSTYLE, BaseTSD.LONG_PTR((data.toInt() or WinUser.WS_EX_LAYERED).toLong()))
		return true
	}

	internal val alpha: Int
		get() {
			val alpha = ByteByReference()
			val attr = IntByReference()
			User32Fast.GetLayeredWindowAttributes(hwnd, IntByReference(), alpha, attr)
			if (attr.value and WinUser.LWA_ALPHA == WinUser.LWA_ALPHA) {
				return (alpha.value.toInt() and 0xFF)
			}
			return 255
		}

	internal fun setInvisible(enabled: Boolean) {
		// TODO
	}

	internal fun setAlpha(value: Int): Boolean {
		if (value < 255 && !setTransparent(true)) {
			return false
		}
		val alpha = alpha
		if (alpha != 255 && alpha != TransparentWindows.forceFullTrans && alpha != TransparentWindows.foreInactiveTrans && alpha != TransparentWindows.activeTrans && alpha != TransparentWindows.backTrans) {
			TransparentWindows.debugPrint { title + " alpha is " + alpha }
			return false
		}
		setInvisible(value == 0)
		if (alpha != value)
			User32Fast.SetLayeredWindowAttributes(hwnd, 0, value.toByte(), WinUser.LWA_ALPHA)

		TransparentWindows.debugPrint { "Set $title to $value" }
		TransparentWindows.debugPrint { Win32Exception(Kernel32.INSTANCE.GetLastError()).message!! }

		if (value <= TransparentWindows.foreInactiveTrans)
			User32Fast.SetWindowAccent(hwnd)

		if (value >= 255)
			setTransparent(false)
		return true
	}

	override fun hashCode(): Int {
		return hwnd.hashCode()
	}

	override fun toString(): String {
		return String.format("%s : \"%s\"", rect, title)
	}

	internal fun alphaForVisibility(): Int {
		when (visible) {
			0 -> return TransparentWindows.backTrans
			1 -> return TransparentWindows.foreInactiveTrans
			2 -> return TransparentWindows.activeTrans
			3 -> return TransparentWindows.forceFullTrans
		}
		throw RuntimeException("unexpected visible " + visible)
	}
}
