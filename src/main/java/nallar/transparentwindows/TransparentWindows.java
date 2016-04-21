package nallar.transparentwindows;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.BaseTSD.LONG_PTR;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.ptr.ByteByReference;
import com.sun.jna.ptr.IntByReference;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class TransparentWindows {
	static int forceFullTrans = 255;
	static int activeTrans = 255;
	static int foreInactiveTrans = 40;
	static int backTrans = 0;
	static HWND lastActive;
	static Thread mainThread;

	private static void debugPrint(String s) {
		System.out.println(s);
	}

	public static void systemTray() {
		SystemTray tray = SystemTray.getSystemTray();
		Image image;
		image = Toolkit.getDefaultToolkit().getImage(TransparentWindows.class.getResource("/icon.png"));
		ActionListener listener = e -> {
			mainThread.interrupt();
			try {
				Thread.sleep(200);
			} catch (InterruptedException e1) {
			}
			clearTransparencies();
			System.exit(0);
		};
		PopupMenu popup = new PopupMenu();
		MenuItem defaultItem = new MenuItem("Exit");
		defaultItem.addActionListener(listener);
		popup.add(defaultItem);
		TrayIcon trayIcon = new TrayIcon(image, "Transparent Windows", popup);
		trayIcon.addActionListener(listener);
		trayIcon.setImageAutoSize(true);
		try {
			tray.add(trayIcon);
		} catch (AWTException e) {
			e.printStackTrace();
		}
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void start() {
				tray.remove(trayIcon);
			}
		});
	}

	public static void clearTransparencies() {
		for (WindowWrapper windowWrapper : getWindows()) {
			windowWrapper.setAlpha(255);
		}
	}

	public static void setTransparencies(HWND active) {
		List<WindowWrapper> windows = getWindows();

		RECT area = new RECT();

		for (WindowWrapper w : windows) {
			RECT inner = w.rect;
			area.left = Math.min(area.left, inner.left);
			area.top = Math.min(area.top, inner.top);
			area.right = Math.max(area.right, inner.right);
			area.bottom = Math.max(area.bottom, inner.bottom);
		}

		WindowOccluder windowOccluder = new WindowOccluder(area);
		windowOccluder.occlude(windows);

		WindowWrapper activeWindowWrapper = null;

		if (active != null) {
			activeWindowWrapper = new WindowWrapper(active);
			if (windows.remove(activeWindowWrapper)) {
				lastActive = active;
			} else {
				activeWindowWrapper = null;
			}
		}

		if (activeWindowWrapper == null && lastActive != null) {
			activeWindowWrapper = new WindowWrapper(lastActive);
			if (!windows.remove(activeWindowWrapper)) {
				activeWindowWrapper = null;
			}
		}

		if (activeWindowWrapper != null) {
			activeWindowWrapper.setAlpha(255);
		}

		for (WindowWrapper windowWrapper : windows) {
			/*if (windowWrapper.title.contains(" - IntelliJ IDEA") || windowWrapper.title.contains(" - paint.net 4.")) {
				windowWrapper.visible = 2;
			}*/
			//if (windowWrapper.title.contains(" - Google Chrome")) {
			int style = User32Fast.GetWindowLongPtrA(windowWrapper.hwnd, WinUser.GWL_STYLE).intValue();
			if ((style & 0x00C00000L) != 0x00C00000L) {
				windowWrapper.visible = 3;
			}

			windowWrapper.setAlpha(windowWrapper.alphaForVisiblity());
		}

	}

	private static boolean titleValid(HWND hWnd, RECT r, String title) {
		String exe = User32Fast.GetWindowExe(hWnd);
		debugPrint("Title found " + title + " for " + hWnd + " of process " + User32Fast.GetWindowThreadProcessId(hWnd)
			+ " with exe " + exe);

		if (title.isEmpty() && exe.equals("C:\\Windows\\explorer.exe")) {
			// Explorer-derp window?
			int height = r.bottom - r.top;
			debugPrint("Found likely explorer derp: " + height);
		}

		switch (title) {
			case "":
			case "Program Manager":
				User32.INSTANCE.InvalidateRect(hWnd, null, true);
				com.sun.jna.platform.win32.WinDef.DWORD f = new WinDef.DWORD(0x1 | 0x2 | 0x4 | 0x100 | 0x200 | 0x80);
				User32.INSTANCE.RedrawWindow(hWnd, null, null, f);
				User32.INSTANCE.UpdateWindow(hWnd);

				return false;
		}
		return true;
	}

	private static List<WindowWrapper> getWindows() {
		final User32 user32 = User32.INSTANCE;
		final List<WindowWrapper> windows = new ArrayList<>();
		final List<HWND> order = new ArrayList<>();
		HWND top = user32.GetTopWindow(new HWND(new Pointer(0)));
		HWND lastTop = null;
		while (top != null && !top.equals(lastTop)) {
			lastTop = top;
			order.add(top);
			top = user32.GetWindow(top, User32.GW_HWNDNEXT);
		}
		user32.EnumWindows((hWnd, lParam) -> {
			if (user32.IsWindowVisible(hWnd)) {
				RECT r = new RECT();
				user32.GetWindowRect(hWnd, r);
				if (r.left > -32000) {     // minimized
					byte[] buffer = new byte[1024];
					user32.GetWindowTextA(hWnd, buffer, buffer.length);
					String title = Native.toString(buffer);
					if (!titleValid(hWnd, r, title)) {
						return true;
					}

					// workaround - rects oversized?

					int shrinkFactor = 20;

					r.left = r.left + shrinkFactor;
					r.top = r.top + shrinkFactor;
					r.right = r.right - shrinkFactor;
					r.bottom = r.bottom - shrinkFactor;

					if (r.left > r.right) {
						r.left = r.right - 1;
					}

					if (r.top > r.bottom) {
						r.top = r.bottom - 1;
					}

					windows.add(new WindowWrapper(hWnd, r, title));
				}
			}
			return true;
		}, new Pointer(0));
		Collections.sort(windows, (o1, o2) -> order.indexOf(o2.hwnd) - order.indexOf(o1.hwnd));
		return windows;
	}

	public static void main(String[] args) {
		mainThread = new Thread() {
			HWND lastActive = null;

			@Override
			public void run() {
				while (true) {
					try {
						Thread.sleep(150);
					} catch (InterruptedException e) {
						return;
					}
					HWND active = User32Fast.GetForegroundWindow();
					if (Objects.equals(active, lastActive)) {
						continue;
					}
					lastActive = active;
					setTransparencies(active);
				}
			}
		};
		mainThread.start();
		systemTray();

	}

	public interface User32 extends com.sun.jna.platform.win32.User32 {
		User32 INSTANCE = (User32) Native.loadLibrary("user32", User32.class);
		int GW_HWNDNEXT = 2;

		boolean EnumWindows(WinUser.WNDENUMPROC lpEnumFunc, Pointer arg);

		int GetWindowTextA(HWND hWnd, byte[] lpString, int nMaxCount);

		boolean IsWindowVisible(HWND hWnd);

		boolean GetWindowRect(HWND hWnd, RECT r);

		HWND GetTopWindow(HWND hWnd);

		HWND GetWindow(HWND hWnd, int flag);
	}

	public interface Psapi extends com.sun.jna.win32.StdCallLibrary {
		Psapi INSTANCE = (Psapi) Native.loadLibrary("psapi", Psapi.class);

		int GetModuleFileNameExA(WinNT.HANDLE process, Pointer hModule, byte[] lpString, int nMaxCount);
	}

	public static class User32Fast {
		static {
			Native.register("User32");
		}

		public static native LONG_PTR SetWindowLongPtrA(HWND hWnd, int index, LONG_PTR newLong);

		public static native LONG_PTR GetWindowLongPtrA(HWND hWnd, int nIndex);

		public static native int GetWindowThreadProcessId(HWND hwnd, IntByReference pid);

		public static int GetWindowThreadProcessId(HWND hWnd) {
			IntByReference pid = new IntByReference();
			GetWindowThreadProcessId(hWnd, pid);
			return pid.getValue();
		}

		public static String GetWindowExe(HWND hWnd) {
			int pid = GetWindowThreadProcessId(hWnd);
			WinNT.HANDLE process = Kernel32.INSTANCE.OpenProcess(0x1000, false, pid);
			byte[] exePathname = new byte[512];
			int result = Psapi.INSTANCE.GetModuleFileNameExA(process, new Pointer(0), exePathname, 512);
			return Native.toString(exePathname).substring(0, result);
		}

		public static native boolean SetLayeredWindowAttributes(HWND hwnd, int crKey, byte bAlpha, int dwFlags);

		public static native boolean GetLayeredWindowAttributes(HWND hwnd, IntByReference pcrKey, ByteByReference pbAlpha, IntByReference pdwFlags);

		public static native HWND GetForegroundWindow();
	}

	private static class WindowOccluder {
		private final short[] screen;
		private final int width;
		private final int height;
		private final int xOffset;
		private final int yOffset;

		public WindowOccluder(RECT area) {
			debugPrint("Area " + area);
			xOffset = -area.left;
			yOffset = -area.top;
			width = area.right - area.left;
			height = area.bottom - area.top;
			screen = new short[(area.bottom - area.top) * (area.right - area.left)];
		}

		private int lookup(int x, int y) {
			return (y + yOffset) * width + x + xOffset;
		}

		public void occlude(List<WindowWrapper> windows) {
			if (windows.size() >= 254) {
				throw new IllegalArgumentException("More than 253 windows not supported");
			}
			for (int id = 0; id < windows.size(); id++) {
				debugPrint("occlude " + id + " is " + windows.get(id));
				WindowWrapper windowWrapper = windows.get(id);
				occlude(windowWrapper, (short) (id + 1));
			}
			BitSet visibleSet = new BitSet();
			for (short id : screen) {
				if (id != 0) {
					visibleSet.set(id);
				}
			}

			for (int i = visibleSet.nextSetBit(0); i != -1; i = visibleSet.nextSetBit(i + 1)) {
				if (i == 0) {
					continue;
				}

				windows.get(i - 1).visible = 1;
				debugPrint("occlude " + (i - 1) + " visible " + windows.get(i - 1));
			}
		}

		public void occlude(WindowWrapper w, short id) {
			RECT r = w.rect;
			for (int y = r.top; y < r.bottom; y++) {
				for (int x = r.left; x < r.right; x++) {
					screen[lookup(x, y)] = id;
				}
			}
		}
	}

	public static class WindowWrapper {
		public int visible = 0; // 0 invisible, 1 transparent, 2 on top
		HWND hwnd;
		RECT rect;
		String title;

		public WindowWrapper(HWND hwnd) {
			if (hwnd == null) {
				throw new NullPointerException();
			}
			this.hwnd = hwnd;
		}

		public WindowWrapper(HWND hwnd, RECT rect, String title) {
			this(hwnd);
			this.rect = rect;
			this.title = title;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			WindowWrapper that = (WindowWrapper) o;

			return hwnd.equals(that.hwnd);

		}

		public boolean isTransparent() {
			LONG_PTR data = User32Fast.GetWindowLongPtrA(hwnd, WinUser.GWL_EXSTYLE);
			return (data.intValue() & WinUser.WS_EX_LAYERED) == WinUser.WS_EX_LAYERED;
		}

		public boolean setTransparent(boolean enable) {
			if (!enable && !isTransparent()) {
				return true;
			}
			LONG_PTR data = User32Fast.GetWindowLongPtrA(hwnd, WinUser.GWL_EXSTYLE);
			User32Fast.SetWindowLongPtrA(hwnd, WinUser.GWL_EXSTYLE, new LONG_PTR(data.intValue() | WinUser.WS_EX_LAYERED));
			return true;
		}

		public int getAlpha() {
			ByteByReference alpha = new ByteByReference();
			IntByReference attr = new IntByReference();
			User32Fast.GetLayeredWindowAttributes(hwnd, new IntByReference(), alpha, attr);
			if ((attr.getValue() & WinUser.LWA_ALPHA) == WinUser.LWA_ALPHA) {
				return alpha.getValue() & 0xFF;
			}
			return 255;
		}

		public void setInvisible(boolean enabled) {
			// TODO
		}

		public boolean setAlpha(int value) {
			if (value < 255 && !setTransparent(true)) {
				return false;
			}
			int alpha = getAlpha();
			if (alpha != forceFullTrans && alpha != foreInactiveTrans && alpha != activeTrans && alpha != backTrans) {
				System.out.println(title + " alpha is " + alpha);
				return false;
			}
			setInvisible(value == 0);
			if (getAlpha() != value)
				User32Fast.SetLayeredWindowAttributes(hwnd, 0, (byte) value, WinUser.LWA_ALPHA);

			debugPrint("Set " + title + " to " + value);
			debugPrint(new Win32Exception(Kernel32.INSTANCE.GetLastError()).getMessage());

			if (value >= 255)
				setTransparent(false);
			return true;
		}

		@Override
		public int hashCode() {
			return hwnd.hashCode();
		}

		public String toString() {
			return String.format("%s : \"%s\"", rect, title);
		}

		public int alphaForVisiblity() {
			switch (visible) {
				case 0:
					return backTrans;
				case 1:
					return foreInactiveTrans;
				case 2:
					return activeTrans;
				case 3:
					return forceFullTrans;
			}
			throw new RuntimeException("unexpected visible " + visible);
		}
	}
}
