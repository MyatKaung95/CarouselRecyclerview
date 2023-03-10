package com.jackandphantom.carouselrecyclerview

import android.animation.Animator
import android.animation.ValueAnimator
import android.graphics.Rect
import android.os.Parcel
import android.os.Parcelable
import android.util.SparseArray
import android.util.SparseBooleanArray
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

class CarouselLayoutManager constructor(
    isLoop: Boolean,
    isItem3D: Boolean,
    ratio: Float,
    flat: Boolean,
    alpha: Boolean,
    isScrollingEnabled: Boolean,
    @RecyclerView.Orientation orientation: Int
) : RecyclerView.LayoutManager() {

    /**
     * We are supposing that all the items in recyclerview will have same size
     * Decorated child view width
     * */
    private var mItemDecoratedWidth: Int = 0

    /** Decorated child view height */
    private var mItemDecoratedHeight: Int = 0

    /** Initially position of an item (x coordinates) */
    private var mStartX = 0

    /** Initially position of an item (y coordinates) */
    private var mStartY = 0

    /** items Sliding offset */
    private var mOffsetAll = 0

    /** interval ratio is how much portion of a view will show */
    private var intervalRatio = 0.5f

    /** Cached all required items in rect object (left, top, right, bottom) */
    private val mAllItemsFrames = SparseArray<Rect>()

    /** Cache those items which are currently attached to the screen */
    private val mHasAttachedItems = SparseBooleanArray()

    /** animator animate the items in layout manager */
    private var animator:ValueAnimator?= null

    /** Store recycler so that we can use it in [scrollToPosition]*/
    private lateinit var recycler: RecyclerView.Recycler

    /** Store state so that we use in scrolling [scrollToPosition] */
    private lateinit var state: RecyclerView.State

    /** set infinite loop of items in the layout manager if true */
    private var mInfinite = false

    /** set tilt of items in layout manager if true */
    private var is3DItem = false

    /** set flat each item if true */
    private var isFlat = false

    /** set alpha based on the position of items in layout manager if true */
    private var isAlpha = false

    /** interface for the selected item or middle item in the layout manager */
    private var mSelectedListener: OnSelected? = null

    /** selected position of layout manager (center item)*/
    private var selectedPosition: Int = 0

    /** Previous item which was in center in layout manager */
    private var mLastSelectedPosition: Int = 0

    /** Use for restore scrolling in recyclerview at the time of orientation change*/
    private var isOrientationChange = false

    /** set to enable/disable scrolling in recyclerview */
    private var isScrollingEnabled = true

    /** Set the orientation of carousel */
    @RecyclerView.Orientation
    private var orientation: Int = RecyclerView.HORIZONTAL
        set(value) {
            when(value) {
                RecyclerView.HORIZONTAL -> mStartY = 0
                RecyclerView.VERTICAL -> mStartX = 0
            }
            field = value
        }

    /** Initialize all the attribute from the constructor and also apply some conditions */
    init {
        this.mInfinite = isLoop
        this.is3DItem = isItem3D
        this.isAlpha = alpha
        this.isScrollingEnabled = isScrollingEnabled
        this.orientation = orientation
        if (ratio in 0f..1f) this.intervalRatio = ratio
        isFlat = flat
        if (isFlat) intervalRatio = 1.1f
    }

    companion object {
        /**Item moves to right */
        private const val SCROLL_TO_RIGHT = 1

        /**Items moves to left */
        private const val SCROLL_TO_LEFT = 2

        /**Item moves to up */
        private const val SCROLL_TO_TOP = 3

        /**Item moves to bottom */
        private const val SCROLL_TO_BOTTOM = 4

        private const val MAX_RECT_COUNT = 100

    }

    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams {
        return RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.WRAP_CONTENT,
            RecyclerView.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {
        if (state == null || recycler == null)
            return

        /** check item count and pre layout state of the layout manager*/
        if (state.itemCount <= 0 || state.isPreLayout) {
            mOffsetAll = 0
            return
        }

        mAllItemsFrames.clear()
        mHasAttachedItems.clear()

        val scrap = recycler.getViewForPosition(0)
        addView(scrap)
        measureChildWithMargins(scrap, 0, 0)

        mItemDecoratedWidth = getDecoratedMeasuredWidth(scrap)
        mItemDecoratedHeight = getDecoratedMeasuredHeight(scrap)

        when (orientation) {
            RecyclerView.HORIZONTAL -> {
                mStartX = ((getHorizontalSpace() - mItemDecoratedWidth) * 1.0f / 2).roundToInt()
            }
            RecyclerView.VERTICAL -> {
                mStartY = ((getVerticalSpace() - mItemDecoratedHeight) * 1.0f / 2).roundToInt()
            }
        }

        var offset = getStartXOrY()

        //Start from the center of the recyclerview
        //Save only specific item position
        var i = 0
        while (i < itemCount && i < MAX_RECT_COUNT) {
            var frame = mAllItemsFrames[i]
            if (frame == null) {
                frame = Rect()
            }
            setFrameCoordinates(frame, offset)
            mAllItemsFrames.put(i, frame)
            mHasAttachedItems.put(i, false)
            offset += getIntervalDistance()
            i++
        }

        detachAndScrapAttachedViews(recycler)

        if (isOrientationChange && selectedPosition != 0) {
            isOrientationChange = false
            mOffsetAll = calculatePositionOffset(selectedPosition)
            onSelectedCallback()
        }

        layoutItems(recycler, state, getScrollToLeftOrTopDirection())
        this.recycler = recycler
        this.state = state
    }

    override fun canScrollHorizontally(): Boolean {

        return isScrollingEnabled && (orientation == RecyclerView.HORIZONTAL)
    }

    override fun canScrollVertically(): Boolean {

        return isScrollingEnabled && (orientation == RecyclerView.VERTICAL)
    }
    override fun scrollHorizontallyBy(
        dx: Int,
        recycler: RecyclerView.Recycler?,
        state: RecyclerView.State?
    ): Int = handleScrollBy(dx, recycler, state)

    override fun scrollVerticallyBy(
        dy: Int,
        recycler: RecyclerView.Recycler?,
        state: RecyclerView.State?
    ): Int = handleScrollBy(dy, recycler, state)

    private fun handleScrollBy(
        dxy: Int,
        recycler: RecyclerView.Recycler?,
        state: RecyclerView.State?
    ): Int {
        if (animator != null && animator!!.isRunning) {
            animator?.cancel()
        }

        if (recycler == null || state == null) return 0

        var travel = dxy

        if (!mInfinite) {
            if (dxy + mOffsetAll < 0) {
                travel = - mOffsetAll
            }else if (dxy + mOffsetAll > maxOffset()) {
                travel = (maxOffset() - mOffsetAll)
            }
        }
        mOffsetAll += travel
        layoutItems(
            recycler,
            state,
            if (dxy > 0) getScrollToLeftOrTopDirection() else getScrollToRightOrBottomDirection()
        )
        return travel
    }

    private fun layoutItems(
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State,
        scrollToDirection: Int
    ) {

        if (state.isPreLayout) return
        //calculate current display area for showing views

        val displayFrames = createRect()
        var position = 0
        for (index in 0 until childCount) {
            val child = getChildAt(index) ?: break
            position = if (child.tag != null) {
                //get position from tag class define later
                val tag = checkTAG(child.tag)
                tag!!.pos
            }else {
                getPosition(child)
            }

            val rect = getFrame(position)

            //Now check item is in the display area, if not recycle that item
            if (!Rect.intersects(displayFrames, rect)) {
                removeAndRecycleView(child, recycler)
                mHasAttachedItems.delete(position)
            }else {
                //Shift the item which has still in the screen
                layoutItem(child, rect)
                mHasAttachedItems.put(position, true)
            }
        }

        if (position == 0) position = centerPosition()

        //For making infinite loop for the layout manager
        var min = position - 20
        var max = position + 20

        if (!mInfinite) {
            if (min < 0) min = 0
            if (max > itemCount ) max = itemCount
        }

        for (index in min until max) {
            val rect = getFrame(index)
            //layout items area of a view if it's first inside the display area
            // and also not already on the screen
           if (Rect.intersects(displayFrames, rect) && !mHasAttachedItems.get(
                   index
               )) {
               var actualPos = index % itemCount
               if (actualPos < 0) actualPos += itemCount

               val scrap = recycler.getViewForPosition(actualPos)
               checkTAG(scrap.tag)
               scrap.tag = TAG(index)
               measureChildWithMargins(scrap, 0, 0)

               if (scrollToDirection == getScrollToRightOrBottomDirection()) {
                   addView(scrap, 0)
               } else addView(scrap)

               layoutItem(scrap, rect)
               mHasAttachedItems.put(index, true)
           }
       }

    }


    private fun layoutItem(child: View, rect: Rect) {
        when (orientation) {
            RecyclerView.HORIZONTAL -> {
                layoutDecorated(
                    child,
                    rect.left - mOffsetAll,
                    rect.top,
                    rect.right - mOffsetAll,
                    rect.bottom
                )
            }
            RecyclerView.VERTICAL -> {
                layoutDecorated(
                    child,
                    rect.left,
                    rect.top - mOffsetAll,
                    rect.right,
                    rect.bottom - mOffsetAll
                )
            }
        }

        if (!isFlat) {
            child.scaleX = computeScale(getRectLeftOrTop(rect) - mOffsetAll)
            child.scaleY = computeScale(getRectLeftOrTop(rect) - mOffsetAll)
        }

        if (is3DItem) itemRotate(child, rect)

        if (isAlpha) child.alpha = computeAlpha(getRectLeftOrTop(rect) - mOffsetAll)

    }

    /**
     * Tilt the child view, center view rotation will be 0
     * tilt others views based on the drawing order
     */
   /* private fun itemRotate(child: View, frame: Rect) {
        val itemCenter =
            (getRectLeftOrTop(frame) + getRectRightOrBottom(frame) - 2 * mOffsetAll) / 2f
        var value =
            (itemCenter - (getStartXOrY() + getItemDecoratedWidthOrHeight() / 2f)) * 1f / (itemCount * getIntervalDistance())
        value = sqrt(abs(value).toDouble()).toFloat()
        val symbol =
            if (itemCenter > getStartXOrY() + getItemDecoratedWidthOrHeight() / 2f) (-1).toFloat() else 1.toFloat()

        when (orientation) {
            RecyclerView.HORIZONTAL -> {
                child.rotationY = symbol * 50 * abs(value)
            }
            RecyclerView.VERTICAL -> {
                child.rotationX = symbol * 50 * abs(value)
            }
        }
    }*/
   /* private fun itemRotate(child: View, frame: Rect) {
        val itemCenter = (getRectLeftOrTop(frame) + getRectRightOrBottom(frame) - 2 * mOffsetAll) / 2f
        var value = (itemCenter - (getStartXOrY() + getItemDecoratedWidthOrHeight() / 2f)) * 1f / (itemCount * getIntervalDistance())
        value = sqrt(abs(value).toDouble()).toFloat()
        val symbol = if (itemCenter > getStartXOrY() + getItemDecoratedWidthOrHeight() / 2f) (-1).toFloat() else 1.toFloat()

        when (orientation) {
            RecyclerView.HORIZONTAL -> {
                child.rotation = symbol * 50 * abs(value)
            }
            RecyclerView.VERTICAL -> {
                child.rotation = symbol * 50 * abs(value)
            }
        }
    }*/
    private fun itemRotate(child: View, frame: Rect) {
        val itemCenter = (getRectLeftOrTop(frame) + getRectRightOrBottom(frame) - 2 * mOffsetAll) / 2f
        var value = (itemCenter - (getStartXOrY() + getItemDecoratedWidthOrHeight() / 2f)) * 1f / (itemCount * getIntervalDistance())
        value = sqrt(abs(value).toDouble()).toFloat()
        val symbol = if (itemCenter > getStartXOrY() + getItemDecoratedWidthOrHeight() / 2f) (-1).toFloat() else 1.toFloat()

        when (orientation) {
            RecyclerView.HORIZONTAL -> {
                child.rotation = symbol * -50 * abs(value)
                child.translationX = symbol * 400 * abs(value)
            }
            RecyclerView.VERTICAL -> {
                child.rotation = symbol * -50 * abs(value)
                child.translationY = symbol * 400 * abs(value)
            }
        }

        // Adjust item spacing for more pronounced fan effect
        val scaleFactor = 1.2f
        child.scaleX = scaleFactor - abs(value)
        child.scaleY = scaleFactor - abs(value)
    }

    /**
     * Callback method when the scroll state changes,
     * we are using this method to fix the position of middle item and this could be done with snap helper
     * @param state scroll state
     */
    override fun onScrollStateChanged(state: Int) {
        super.onScrollStateChanged(state)
        if (state == RecyclerView.SCROLL_STATE_IDLE) {
            //When scrolling stops
            fixOffsetWhenFinishOffset()
        }
    }

    /**
     * Determine the position of the center element of the layout manager based on the scrolling offset [mOffsetAll],
     * scroll to that item position with the animation
     */
    private fun fixOffsetWhenFinishOffset() {
        if (getIntervalDistance() != 0) {
            var scrollPosition = (mOffsetAll * 1.0f / getIntervalDistance()).toInt()
            val moreDxy: Float = (mOffsetAll % getIntervalDistance()).toFloat()
            if (abs(moreDxy) > getIntervalDistance() * 0.5f) {
                if (moreDxy > 0) scrollPosition++
                else scrollPosition--
            }
            val finalOffset = scrollPosition * getIntervalDistance()
            startScroll(mOffsetAll, finalOffset)
        }
    }

    /**
     * Scroll the items of the layout manager from scrolling offset [mOffsetAll] to final offset with the animation
     * @param from initial offset, beginning position for an animation
     * @param to final offset, end position for an animation
     */
    private fun startScroll(from: Int, to: Int) {
        //Start animation
        if (animator != null && animator!!.isRunning) {
            animator?.cancel()
        }
        val direction =
            if (from < to) getScrollToLeftOrTopDirection() else getScrollToRightOrBottomDirection()

        animator = ValueAnimator.ofFloat(from * 1.0f, to * 1.0f)
        animator?.duration= 500
        animator?.interpolator = DecelerateInterpolator()

        animator?.addUpdateListener { animation ->
            mOffsetAll = (animation.animatedValue as Float).roundToInt()
            layoutItems(recycler, state, direction)
        }
        animator?.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator?) {
                //do nothing
            }

            override fun onAnimationEnd(animation: Animator?) {
                onSelectedCallback()
            }

            override fun onAnimationCancel(animation: Animator?) {
                //do nothing
            }

            override fun onAnimationRepeat(animation: Animator?) {
                //do nothing
            }

        })
        animator?.start()
    }

    /**
     * Scroll to particular position, in this we are not performing any animation we just layout items to
     * specified position in the parameter
     * @param position where we need to scroll
     */
    override fun scrollToPosition(position: Int) {
        if (position < 0 || position > itemCount - 1) return
        if (!this::recycler.isInitialized || !this::state.isInitialized) {
            isOrientationChange = true
            selectedPosition = position
            requestLayout()
            return
        }
        mOffsetAll = calculatePositionOffset(position)
        layoutItems(recycler,
            state,
            if (position > selectedPosition) getScrollToLeftOrTopDirection()
            else getScrollToRightOrBottomDirection()
        )
        onSelectedCallback()

    }

    override fun smoothScrollToPosition(
        recyclerView: RecyclerView?,
        state: RecyclerView.State?,
        position: Int
    ) {
        //Loop does not support for smooth scrolling
        if (mInfinite||!this::recycler.isInitialized || !this::state.isInitialized) return
        val finalOffset = calculatePositionOffset(position)
        startScroll(mOffsetAll, finalOffset)
    }

    fun centerPosition(): Int {
        val intervalPosition = getIntervalDistance()
        if (intervalPosition == 0) return intervalPosition

        var pos = mOffsetAll / intervalPosition
        val more = mOffsetAll % intervalPosition
        if (abs(more) >= intervalPosition * 0.5f) {
            if (more >= 0) pos++
            else pos--
        }
        return pos
    }

    fun getChildActualPos(index: Int): Int {
        val child = getChildAt(index) ?: return Int.MIN_VALUE

        if (child.tag != null) {
            val tag = checkTAG(child.tag)
            if (tag != null)
            return tag.pos
        }
        return getPosition(child)
    }

    private fun checkTAG(tag: Any?): TAG? {
        return if (tag != null) {
            if (tag is TAG) {
                tag as TAG
            }else {
                throw IllegalArgumentException("You should use the set tag with the position")
            }
        }else {
            null
        }

    }

    override fun onAdapterChanged(
        oldAdapter: RecyclerView.Adapter<*>?,
        newAdapter: RecyclerView.Adapter<*>?
    ) {
        removeAllViews()
        mOffsetAll = 0
        mHasAttachedItems.clear()
        mAllItemsFrames.clear()
        mInfinite = false
        is3DItem = false
        isFlat = false
        isAlpha = false
        intervalRatio = 0.5f
    }

    fun getFirstVisiblePosition(): Int {
        val displayFrame = createRect()
        val cur: Int = centerPosition()
        var i = cur - 1
        while (true) {
            val rect = getFrame(i)
            if (getRectLeftOrTop(rect) <= getRectLeftOrTop(displayFrame)) {
                return abs(i) % itemCount
            }
            i--
        }
    }

    fun getLastVisiblePosition(): Int {
        val displayFrame = createRect()
        val cur: Int = centerPosition()
        var i = cur - 1
        while (true) {
            val rect = getFrame(i)
            if (getRectLeftOrTop(rect) <= getRectLeftOrTop(displayFrame)) {
                return abs(i) % itemCount
            }
            i++
        }
    }

    private fun onSelectedCallback() {
        //Some time interval distance is returns 0 that makes [ArithmeticException]
        val intervalDistance = getIntervalDistance()
        if (intervalDistance == 0) return

        selectedPosition = ((mOffsetAll / intervalDistance).toFloat()).roundToInt()
        if (selectedPosition < 0) selectedPosition += itemCount
        selectedPosition = abs(selectedPosition % itemCount)
        //check if the listener is implemented
        //mLastSelectedPosition keeps track of last position which will prevent simple slide and same position
        if (mSelectedListener != null && selectedPosition != mLastSelectedPosition) {
            mSelectedListener!!.onItemSelected(selectedPosition)
        }
        mLastSelectedPosition = selectedPosition
    }

    private fun getFrame(position: Int): Rect {
        var frame = mAllItemsFrames[position]
        if (frame == null) {
            frame = Rect()
            val offset = getStartXOrY() + getIntervalDistance() * position
            setFrameCoordinates(frame, offset)
            return frame
        }
        return frame
    }

    private fun computeScale(x: Int): Float {
        val startXOrY = getStartXOrY()
        var scale: Float =
            1 - abs(x - startXOrY) * 1.0f / abs(startXOrY + getItemDecoratedWidthOrHeight() / intervalRatio)

        if (scale < 0) scale = 0f
        if (scale > 1) scale = 1f
        return scale
    }

    private fun computeAlpha(x: Int): Float {
        val startXOrY = getStartXOrY()
        var alpha: Float =
            1 - abs(x - startXOrY) * 1.0f / abs(startXOrY + getItemDecoratedWidthOrHeight() / intervalRatio)

        if (alpha < 0.3f) alpha = 0f
        if (alpha > 1) alpha = 1f
        return alpha
    }

    private fun calculatePositionOffset(position: Int): Int {
        return ((getIntervalDistance() * position).toFloat()).roundToInt()
    }

    /**
     * Get the item interval
     */
    private fun getIntervalDistance(): Int {
        return (getItemDecoratedWidthOrHeight() * intervalRatio).roundToInt()
    }

    /**
     * Get the horizontal space for the layout manager
     */
    private fun getHorizontalSpace(): Int {
        return width - paddingLeft - paddingRight
    }

    /**
     * Get the vertical space and it's used for the calculate the display area for the layout manager
     */
    private fun getVerticalSpace(): Int {
        return height - paddingBottom - paddingTop
    }

    /**
     * Calculate the maximum offset
     */
    private fun maxOffset(): Int{
        return ((itemCount - 1) * getIntervalDistance())
    }

    /**
     * set the selected listener (interface)
     */
    fun setOnSelectedListener(l: OnSelected) {
        this.mSelectedListener = l
    }

    /**
     * Get the selected position or centered position
     * @return selectedPosition
     */
    internal fun getSelectedPosition() = selectedPosition

    private fun getScrollToLeftOrTopDirection(): Int = when (orientation) {
        RecyclerView.HORIZONTAL -> SCROLL_TO_LEFT
        RecyclerView.VERTICAL -> SCROLL_TO_TOP
        else -> SCROLL_TO_LEFT
    }

    private fun getScrollToRightOrBottomDirection(): Int = when (orientation) {
        RecyclerView.HORIZONTAL -> SCROLL_TO_RIGHT
        RecyclerView.VERTICAL -> SCROLL_TO_BOTTOM
        else -> SCROLL_TO_RIGHT
    }

    private fun getStartXOrY(): Int = when (orientation) {
        RecyclerView.HORIZONTAL -> mStartX
        RecyclerView.VERTICAL -> mStartY
        else -> mStartX
    }

    private fun getItemDecoratedWidthOrHeight(): Int = when (orientation) {
        RecyclerView.HORIZONTAL -> mItemDecoratedWidth
        RecyclerView.VERTICAL -> mItemDecoratedHeight
        else -> mItemDecoratedWidth
    }

    private fun createRect(): Rect = when (orientation) {
        RecyclerView.HORIZONTAL -> {
            Rect(mOffsetAll, 0, mOffsetAll + getHorizontalSpace(), getVerticalSpace())
        }
        RecyclerView.VERTICAL -> {
            Rect(0, mOffsetAll, getHorizontalSpace(), mOffsetAll + getVerticalSpace())
        }
        else -> {
            Rect(mOffsetAll, 0, mOffsetAll + getHorizontalSpace(), getVerticalSpace())
        }
    }

    private fun setFrameCoordinates(frame: Rect, offset: Int) {
        when (orientation) {
            RecyclerView.HORIZONTAL -> {
                frame.set(offset, 0, (offset + mItemDecoratedWidth), mItemDecoratedHeight)
            }
            RecyclerView.VERTICAL -> {
                frame.set(0, offset, mItemDecoratedWidth, (offset + mItemDecoratedHeight))
            }
        }
    }
    private fun getRectLeftOrTop(rect: Rect): Int = when (orientation) {
        RecyclerView.HORIZONTAL -> rect.left
        RecyclerView.VERTICAL -> rect.top
        else -> rect.left
    }

    private fun getRectRightOrBottom(rect: Rect): Int = when (orientation) {
        RecyclerView.HORIZONTAL -> rect.right
        RecyclerView.VERTICAL -> rect.bottom
        else -> rect.right
    }

    class Builder {
        private var isInfinite = false
        private var is3DItem = false
        private var intervalRation: Float = 0.5f
        private var isFlat = false
        private var isAlpha = false
        private var isScrollingEnabled = true
        @RecyclerView.Orientation
        private var orientation = RecyclerView.HORIZONTAL

        fun setIsInfinite(isInfinite: Boolean) : Builder {
            this.isInfinite = isInfinite
            return this
        }

        fun set3DItem(is3DItem: Boolean): Builder {
            this.is3DItem = is3DItem
            return this
        }

        fun setIntervalRatio(intervalRatio: Float): Builder {
            this.intervalRation = intervalRatio
            return this
        }

        fun setIsFlat(isFlat: Boolean): Builder {
            this.isFlat = isFlat
            return this
        }

        fun setIsAlpha(isAlpha: Boolean): Builder {
            this.isAlpha = isAlpha
            return this
        }

        fun setIsScrollingEnabled(isScrollingEnabled: Boolean): Builder {
            this.isScrollingEnabled = isScrollingEnabled
            return this
        }

        fun setOrientation(@RecyclerView.Orientation orientation: Int): Builder {
            this.orientation = orientation
            return this
        }

        fun build(): CarouselLayoutManager {
            return CarouselLayoutManager(
                isInfinite,
                is3DItem,
                intervalRation,
                isFlat,
                isAlpha,
                isScrollingEnabled,
                orientation
            )
        }
    }

    internal data class TAG(var pos: Int = 0)

    override fun isAutoMeasureEnabled() = true

    interface OnSelected {
        fun onItemSelected(position: Int)
    }

    override fun onSaveInstanceState(): Parcelable? {
        return SaveState(selectedPosition)
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        super.onRestoreInstanceState(state)
        if (state is SaveState) {
            isOrientationChange = true
            this.selectedPosition = state.scrollPosition
        }
    }

    class SaveState constructor(var scrollPosition:Int = 0) : Parcelable {

        constructor(parcel: Parcel) : this() {
            scrollPosition = parcel.readInt()
        }

        override fun describeContents(): Int {
            return 0
        }

        override fun writeToParcel(dest: Parcel?, flags: Int) {
            dest?.writeInt(scrollPosition)
        }

        companion object CREATOR : Parcelable.Creator<SaveState> {
            override fun createFromParcel(parcel: Parcel): SaveState {
                return SaveState(parcel)
            }

            override fun newArray(size: Int): Array<SaveState?> {
                return arrayOfNulls(size)
            }
        }

    }

}