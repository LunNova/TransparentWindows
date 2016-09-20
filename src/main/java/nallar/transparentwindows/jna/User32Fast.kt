package nallar.transparentwindows.jna

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.*
import com.sun.jna.ptr.ByteByReference
import com.sun.jna.ptr.IntByReference
import nallar.transparentwindows.TransparentWindows
import java.awt.Color

object User32Fast {
	init {
		Native.register("User32")
	}

	external fun FindWindowA(className: String, windowName: String?): WinDef.HWND?

	external fun GetWindow(hWnd: WinDef.HWND?, flag: Int): WinDef.HWND?

	external fun GetTopWindow(hWnd: WinDef.HWND): WinDef.HWND?

	external fun GetWindowRect(hWnd: WinDef.HWND, r: WinDef.RECT): Boolean

	external fun IsWindowVisible(hWnd: WinDef.HWND): Boolean

	external fun GetWindowTextA(hWnd: WinDef.HWND, lpString: ByteArray, nMaxCount: Int): Int

	external fun EnumWindows(lpEnumFunc: WinUser.WNDENUMPROC, arg: Pointer?): Boolean

	external fun SetWindowLongPtrA(hWnd: WinDef.HWND, index: Int, newLong: BaseTSD.LONG_PTR): BaseTSD.LONG_PTR

	external fun GetWindowLongPtrA(hWnd: WinDef.HWND, nIndex: Int): BaseTSD.LONG_PTR

	external fun GetWindowThreadProcessId(hWnd: WinDef.HWND, pid: IntByReference): Int

	external fun SetLayeredWindowAttributes(hWnd: WinDef.HWND, crKey: Int, bAlpha: Byte, dwFlags: Int): Boolean

	external fun GetLayeredWindowAttributes(hWnd: WinDef.HWND, pcrKey: IntByReference, pbAlpha: ByteByReference, pdwFlags: IntByReference): Boolean

	external fun GetForegroundWindow(): WinDef.HWND?

	external fun SetWindowCompositionAttribute(hWnd: WinDef.HWND, compositionAttribute: CompositionAttribute): Int

	open class CompositionAttribute() : Structure() {
		@JvmField
		var attribute: Int = 19
		@JvmField
		var data: AccentPolicy.ByReference? = null
		@JvmField
		var sizeOfData: Int = 0

		// WCA attribute = 19 accent style
		override fun getFieldOrder(): MutableList<Any?>? {
			return mutableListOf(
				"attribute",
				"data",
				"sizeOfData"
			)
		}
	}

	open class AccentPolicy() : Structure() {
		@JvmField var accentState: Int = 0
		@JvmField var accentFlags: Int = 0
		@JvmField var gradientColor: Int = 0
		@Suppress("unused") // Required for object layout to match (JNA)
		@JvmField var animationId: Int = 0

		class ByReference : AccentPolicy(), Structure.ByReference
		/*state:
		ACCENT_DISABLED = 0,
        ACCENT_ENABLE_GRADIENT = 1,
        ACCENT_ENABLE_TRANSPARENTGRADIENT = 2,
        ACCENT_ENABLE_BLURBEHIND = 3,
        ACCENT_INVALID_STATE = 4*/

		override fun getFieldOrder(): MutableList<Any?>? {
			return mutableListOf(
				"accentState",
				"accentFlags",
				"gradientColor",
				"animationId"
			)
		}
	}

	fun SetWindowAccent(hWnd: WinDef.HWND) {
		val policy = AccentPolicy.ByReference()
		policy.accentState = 3 or 2
		policy.accentFlags = 2
		policy.gradientColor = Color(12, 12, 12, 154).rgb
		val attr = CompositionAttribute()
		attr.attribute = 19
		attr.data = policy
		attr.sizeOfData = policy.size()
		val e = SetWindowCompositionAttribute(hWnd, attr)
		TransparentWindows.debugPrint { Win32Exception(Kernel32.INSTANCE.GetLastError()).message!! }
		TransparentWindows.debugPrint { e.toString() + " " + attr.attribute }
	}

	fun GetWindowThreadProcessId(hWnd: WinDef.HWND): Int {
		val pid = IntByReference()
		GetWindowThreadProcessId(hWnd, pid)
		return pid.value
	}

	fun GetWindowExe(hWnd: WinDef.HWND): String {
		val pid = GetWindowThreadProcessId(hWnd)
		val process = Kernel32.INSTANCE.OpenProcess(0x1000, false, pid) ?: return "unknown.exe"
		val exePathname = ByteArray(512)
		val result = Psapi.INSTANCE.GetModuleFileNameExA(process, Pointer(0), exePathname, 512)
		return Native.toString(exePathname).substring(0, result)
	}

	private interface Psapi : com.sun.jna.win32.StdCallLibrary {

		fun GetModuleFileNameExA(process: WinNT.HANDLE, hModule: Pointer, lpString: ByteArray, nMaxCount: Int): Int

		companion object {
			val INSTANCE = Native.loadLibrary("psapi", Psapi::class.java) as Psapi
		}
	}
}
