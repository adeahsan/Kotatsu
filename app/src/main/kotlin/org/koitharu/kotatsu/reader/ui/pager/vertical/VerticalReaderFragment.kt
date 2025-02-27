package org.koitharu.kotatsu.reader.ui.pager.vertical

import android.os.Build
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.os.NetworkState
import org.koitharu.kotatsu.core.prefs.ReaderAnimation
import org.koitharu.kotatsu.core.ui.list.lifecycle.PagerLifecycleDispatcher
import org.koitharu.kotatsu.core.util.ext.doOnPageChanged
import org.koitharu.kotatsu.core.util.ext.findCurrentViewHolder
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.core.util.ext.recyclerView
import org.koitharu.kotatsu.core.util.ext.resetTransformations
import org.koitharu.kotatsu.databinding.FragmentReaderStandardBinding
import org.koitharu.kotatsu.reader.domain.PageLoader
import org.koitharu.kotatsu.reader.ui.ReaderState
import org.koitharu.kotatsu.reader.ui.pager.BaseReaderAdapter
import org.koitharu.kotatsu.reader.ui.pager.BaseReaderFragment
import org.koitharu.kotatsu.reader.ui.pager.ReaderPage
import org.koitharu.kotatsu.reader.ui.pager.standard.NoAnimPageTransformer
import org.koitharu.kotatsu.reader.ui.pager.standard.PageHolder
import org.koitharu.kotatsu.reader.ui.pager.standard.PagerEventSupplier
import org.koitharu.kotatsu.reader.ui.pager.standard.PagesAdapter
import javax.inject.Inject
import kotlin.math.absoluteValue
import kotlin.math.sign

@AndroidEntryPoint
class VerticalReaderFragment : BaseReaderFragment<FragmentReaderStandardBinding>(),
	View.OnGenericMotionListener {

	@Inject
	lateinit var networkState: NetworkState

	@Inject
	lateinit var pageLoader: PageLoader

	private var pagerLifecycleDispatcher: PagerLifecycleDispatcher? = null

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	) = FragmentReaderStandardBinding.inflate(inflater, container, false)

	override fun onViewBindingCreated(
		binding: FragmentReaderStandardBinding,
		savedInstanceState: Bundle?,
	) {
		super.onViewBindingCreated(binding, savedInstanceState)
		with(binding.pager) {
			orientation = ViewPager2.ORIENTATION_VERTICAL
			adapter = readerAdapter
			offscreenPageLimit = 2
			doOnPageChanged(::notifyPageChanged)
			setOnGenericMotionListener(this@VerticalReaderFragment)
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				recyclerView?.defaultFocusHighlightEnabled = false
			}
			PagerEventSupplier(this).attach()
			pagerLifecycleDispatcher = PagerLifecycleDispatcher(this).also {
				registerOnPageChangeCallback(it)
			}
		}

		viewModel.pageAnimation.observe(viewLifecycleOwner) {
			val transformer = when (it) {
				ReaderAnimation.NONE -> NoAnimPageTransformer()
				ReaderAnimation.DEFAULT -> null
				ReaderAnimation.ADVANCED -> VerticalPageAnimTransformer()
			}
			binding.pager.setPageTransformer(transformer)
			if (transformer == null) {
				binding.pager.recyclerView?.children?.forEach { view ->
					view.resetTransformations()
				}
			}
		}
	}

	override fun onDestroyView() {
		pagerLifecycleDispatcher = null
		requireViewBinding().pager.adapter = null
		super.onDestroyView()
	}

	override fun onZoomIn() {
		(viewBinding?.pager?.findCurrentViewHolder() as? PageHolder)?.onZoomIn()
	}

	override fun onZoomOut() {
		(viewBinding?.pager?.findCurrentViewHolder() as? PageHolder)?.onZoomOut()
	}

	override fun onGenericMotion(v: View?, event: MotionEvent): Boolean {
		if (event.source and InputDevice.SOURCE_CLASS_POINTER != 0) {
			if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
				val axisValue = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
				val withCtrl = event.metaState and KeyEvent.META_CTRL_MASK != 0
				if (!withCtrl) {
					switchPageBy(-axisValue.sign.toInt())
					return true
				}
			}
		}
		return false
	}

	override suspend fun onPagesChanged(pages: List<ReaderPage>, pendingState: ReaderState?) =
		coroutineScope {
			val items = launch {
				requireAdapter().setItems(pages)
				yield()
				pagerLifecycleDispatcher?.invalidate()
			}
			if (pendingState != null) {
				val position = pages.indexOfFirst {
					it.chapterId == pendingState.chapterId && it.index == pendingState.page
				}
				items.join()
				if (position != -1) {
					requireViewBinding().pager.setCurrentItem(position, false)
					notifyPageChanged(position)
				} else {
					Snackbar.make(requireView(), R.string.not_found_404, Snackbar.LENGTH_SHORT)
						.show()
				}
			} else {
				items.join()
			}
		}

	override fun onCreateAdapter() = PagesAdapter(
		lifecycleOwner = viewLifecycleOwner,
		loader = pageLoader,
		settings = viewModel.readerSettings,
		networkState = networkState,
		exceptionResolver = exceptionResolver,
	)

	override fun switchPageBy(delta: Int) {
		with(requireViewBinding().pager) {
			setCurrentItem(currentItem + delta, isAnimationEnabled())
		}
	}

	override fun switchPageTo(position: Int, smooth: Boolean) {
		with(requireViewBinding().pager) {
			setCurrentItem(
				position,
				smooth && isAnimationEnabled() && (currentItem - position).absoluteValue < SMOOTH_SCROLL_LIMIT,
			)
		}
	}

	override fun getCurrentState(): ReaderState? = viewBinding?.run {
		val adapter = pager.adapter as? BaseReaderAdapter<*>
		val page = adapter?.getItemOrNull(pager.currentItem) ?: return@run null
		ReaderState(
			chapterId = page.chapterId,
			page = page.index,
			scroll = 0,
		)
	}

	private fun notifyPageChanged(page: Int) {
		viewModel.onCurrentPageChanged(page)
	}

	companion object {

		const val SMOOTH_SCROLL_LIMIT = 3
	}
}
