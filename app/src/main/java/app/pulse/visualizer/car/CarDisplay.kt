package app.pulse.visualizer.car

import android.app.Presentation
import android.content.Context
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowManager
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.pulse.visualizer.R
import kotlin.math.abs

/**
 * Mostra a mesma interface Compose do celular na tela do Android Auto: cria uma tela virtual
 * sobre a superficie do carro, abre nela uma Presentation com a UI e repassa os toques do carro
 * como eventos de toque comuns.
 */
class CarDisplay(
    private val context: Context,
    /** [margins]: quanto de cada lado os botoes do Android Auto cobrem, em pixels. */
    private val content: @Composable (margins: Rect) -> Unit,
) : SurfaceCallback {
    private val owner = Owner()
    private val main = Handler(Looper.getMainLooper())
    private var display: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var area = Rect()
    private val margins = mutableStateOf(Rect())
    private var width = 0
    private var height = 0
    private var density = 1f
    private var screenStarted = false

    /** Chamado pela tela do carro quando ela fica visivel ou some. */
    fun setStarted(started: Boolean) {
        screenStarted = started
        syncLifecycle()
    }

    fun destroy() {
        release()
        owner.registry.currentState = Lifecycle.State.DESTROYED
        owner.store.clear()
    }

    override fun onSurfaceAvailable(c: SurfaceContainer) {
        release()
        val surface = c.surface ?: return
        width = c.width
        height = c.height
        density = (if (c.dpi > 0) c.dpi else 160) / 160f
        setArea(Rect(0, 0, c.width, c.height))
        val dm = context.getSystemService(DisplayManager::class.java)
        val vd = dm.createVirtualDisplay("Pulse Android Auto", c.width, c.height, c.dpi, surface, 0)
        display = vd

        val p = Presentation(context, vd.display, R.style.Theme_Pulse)
        p.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            decorView.setViewTreeLifecycleOwner(owner)
            decorView.setViewTreeSavedStateRegistryOwner(owner)
            decorView.setViewTreeViewModelStoreOwner(owner)
        }
        val view = ComposeView(p.context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { content(margins.value) }
        }
        p.setContentView(view)
        runCatching { p.show() }.onFailure { release(); return }
        presentation = p
        syncLifecycle()
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        if (!visibleArea.isEmpty) setArea(visibleArea)
    }

    private fun setArea(r: Rect) {
        area = Rect(r)
        margins.value = Rect(
            r.left.coerceAtLeast(0), r.top.coerceAtLeast(0),
            (width - r.right).coerceAtLeast(0), (height - r.bottom).coerceAtLeast(0),
        )
    }

    override fun onStableAreaChanged(stableArea: Rect) = Unit

    override fun onSurfaceDestroyed(c: SurfaceContainer) {
        release()
        syncLifecycle()
    }

    // ---- Toques do carro -> toques na interface ----

    override fun onClick(x: Float, y: Float) {
        endDrag()
        val t = SystemClock.uptimeMillis()
        send(MotionEvent.ACTION_DOWN, t, t, x, y)
        send(MotionEvent.ACTION_UP, t, t + 40, x, y)
    }

    private var dragging = false
    private var dragDown = 0L
    private var px = 0f
    private var py = 0f
    private val endDragRunnable = Runnable { endDrag() }

    /** O carro informa so o deslocamento; o arraste comeca num ponto que faz sentido para a direcao. */
    override fun onScroll(distanceX: Float, distanceY: Float) {
        val now = SystemClock.uptimeMillis()
        if (!dragging) {
            val vertical = abs(distanceY) > abs(distanceX)
            // Vertical: rola o painel de ajustes (fica a direita). Horizontal: troca de estilo no meio.
            px = if (vertical) area.right - 230f * density else area.exactCenterX()
            py = area.exactCenterY()
            dragging = true
            dragDown = now
            send(MotionEvent.ACTION_DOWN, dragDown, now, px, py)
        }
        px -= distanceX
        py -= distanceY
        send(MotionEvent.ACTION_MOVE, dragDown, now, px, py)
        main.removeCallbacks(endDragRunnable)
        main.postDelayed(endDragRunnable, 150)
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
        if (dragging) {
            endDrag()
            return
        }
        // Gesto rapido sem arraste antes: simula um deslize curto na mesma direcao.
        val steps = 6
        val dx = velocityX * 0.12f / steps
        val dy = velocityY * 0.12f / steps
        repeat(steps) { onScroll(-dx, -dy) }
        endDrag()
    }

    private fun endDrag() {
        main.removeCallbacks(endDragRunnable)
        if (!dragging) return
        dragging = false
        send(MotionEvent.ACTION_UP, dragDown, SystemClock.uptimeMillis(), px, py)
    }

    private fun send(action: Int, down: Long, time: Long, x: Float, y: Float) {
        val view = presentation?.window?.decorView ?: return
        val ev = MotionEvent.obtain(down, time, action, x.coerceIn(0f, width - 1f), y.coerceIn(0f, height - 1f), 0)
        ev.source = InputDevice.SOURCE_TOUCHSCREEN
        view.dispatchTouchEvent(ev)
        ev.recycle()
    }

    // ---- Ciclo de vida ----

    private fun release() {
        endDrag()
        presentation?.let { runCatching { it.dismiss() } }
        presentation = null
        display?.release()
        display = null
    }

    private fun syncLifecycle() {
        if (owner.registry.currentState == Lifecycle.State.DESTROYED) return
        owner.registry.currentState = when {
            presentation != null && screenStarted -> Lifecycle.State.RESUMED
            else -> Lifecycle.State.CREATED
        }
    }

    private class Owner : SavedStateRegistryOwner, ViewModelStoreOwner {
        val registry = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        val store = ViewModelStore()

        init {
            saved.performAttach()
            saved.performRestore(null)
            registry.currentState = Lifecycle.State.CREATED
        }

        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
        override val viewModelStore: ViewModelStore get() = store
    }
}
