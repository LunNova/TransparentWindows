package nallar.transparentwindows

import com.sun.jna.platform.win32.WinDef
import nallar.transparentwindows.jna.WindowWrapper
import java.util.*

class WindowOccluder {
	companion object {
		private val maxWindows = 0xFE
	}
	private var screen: ByteArray = ByteArray(0)
	private var width: Int = 0
	private var xOffset: Int = 0
	private var yOffset: Int = 0
	private var size: Int = 0

	init {
		screen = ByteArray(0)
	}

	fun setArea(windows: List<WindowWrapper>) {
		val area = WinDef.RECT()
		for (w in windows) {
			val inner = w.rect!!
			area.left = Math.min(area.left, inner.left)
			area.top = Math.min(area.top, inner.top)
			area.right = Math.max(area.right, inner.right)
			area.bottom = Math.max(area.bottom, inner.bottom)
		}
		setArea(area)
	}

	fun setArea(area: WinDef.RECT) {
		val width = area.right - area.left
		val height = area.bottom - area.top

		val requiredSize = (height) * (width)
		if (requiredSize > 16588800)
			throw Exception("Area too large")

		if (requiredSize > screen.size)
			try {
				screen = ByteArray(requiredSize)
			} catch (oom: OutOfMemoryError) {
				println("oom when requesting $requiredSize, previous size ${screen.size}")
				throw oom
			}
		else
			Arrays.fill(screen, 0, requiredSize, 0)

		this.width = width
		size = requiredSize
		xOffset = -area.left
		yOffset = -area.top
	}

	private fun lookup(x: Int, y: Int): Int {
		return (y + yOffset) * width + x + xOffset
	}

	internal fun occlude(windows: List<WindowWrapper>) {
		if (windows.size >= maxWindows) {
			throw IllegalArgumentException("More than $maxWindows windows not supported")
		}
		setArea(windows)
		for (id in windows.indices) {
			TransparentWindows.debugPrint { "occlude " + id + " is " + windows[id] }
			val windowWrapper = windows[id]
			occlude(windowWrapper, (id + 1).toByte())
		}
		val visibleSet = BitSet()

		val screen = screen
		val size = size
		for (i in 0..size - 1) {
			val x = screen[i]
			if (x != 0.toByte()) {
				visibleSet.set(x.toInt() and 0xFF)
			}
		}

		var i = visibleSet.nextSetBit(0)
		while (i != -1) {
			if (i == 0) {
				i = visibleSet.nextSetBit(i + 1)
				continue
			}

			val pos = i - 1
			windows[pos].visible = 1
			TransparentWindows.debugPrint { "occlude " + (pos) + " visible " + windows[pos] }
			i = visibleSet.nextSetBit(i + 1)
		}
	}

	internal fun occlude(w: WindowWrapper, id: Byte) {
		val r = w.rect!!
		for (y in r.top..r.bottom - 1) {
			for (x in r.left..r.right - 1) {
				screen[lookup(x, y)] = id
			}
		}
	}
}
