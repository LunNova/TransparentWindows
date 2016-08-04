package nallar.transparentwindows.jna

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.*
import com.sun.jna.ptr.ByteByReference
import com.sun.jna.ptr.IntByReference

object User32Fast {
	init {
		Native.register("User32")
	}

	external fun FindWindowA(className: String, windowName: String?): WinDef.HWND?

	external fun GetWindow(hWnd: WinDef.HWND, flag: Int): WinDef.HWND?

	external fun GetTopWindow(hWnd: WinDef.HWND): WinDef.HWND?

	external fun GetWindowRect(hWnd: WinDef.HWND, r: WinDef.RECT): Boolean

	external fun IsWindowVisible(hWnd: WinDef.HWND): Boolean

	external fun GetWindowTextA(hWnd: WinDef.HWND, lpString: ByteArray, nMaxCount: Int): Int

	external fun EnumWindows(lpEnumFunc: WinUser.WNDENUMPROC, arg: Pointer): Boolean

	external fun SetWindowLongPtrA(hWnd: WinDef.HWND, index: Int, newLong: BaseTSD.LONG_PTR): BaseTSD.LONG_PTR

	external fun GetWindowLongPtrA(hWnd: WinDef.HWND, nIndex: Int): BaseTSD.LONG_PTR

	external fun GetWindowThreadProcessId(hwnd: WinDef.HWND, pid: IntByReference): Int

	external fun SetLayeredWindowAttributes(hwnd: WinDef.HWND, crKey: Int, bAlpha: Byte, dwFlags: Int): Boolean

	external fun GetLayeredWindowAttributes(hwnd: WinDef.HWND, pcrKey: IntByReference, pbAlpha: ByteByReference, pdwFlags: IntByReference): Boolean

	external fun GetForegroundWindow(): WinDef.HWND?

	fun GetWindowThreadProcessId(hWnd: WinDef.HWND): Int {
		val pid = IntByReference()
		GetWindowThreadProcessId(hWnd, pid)
		return pid.value
	}

	fun GetWindowExe(hWnd: WinDef.HWND): String {
		val pid = GetWindowThreadProcessId(hWnd)
		val process = Kernel32.INSTANCE.OpenProcess(0x1000, false, pid)
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
