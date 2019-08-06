package io.rover.sdk.ui.blocks.poll.text

import android.animation.AnimatorSet
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.support.v4.view.ViewCompat
import android.support.v7.widget.AppCompatTextView
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import io.rover.sdk.data.domain.FontWeight
import io.rover.sdk.data.domain.TextPollOption
import io.rover.sdk.data.mapToFont
import io.rover.sdk.platform.create
import io.rover.sdk.platform.setBackgroundWithoutPaddingChange
import io.rover.sdk.platform.setupLayoutParams
import io.rover.sdk.platform.setupRelativeLayoutParams
import io.rover.sdk.platform.textView
import io.rover.sdk.ui.asAndroidColor
import io.rover.sdk.ui.blocks.concerns.background.BackgroundColorDrawableWrapper
import io.rover.sdk.ui.blocks.poll.PollBorderView
import io.rover.sdk.ui.blocks.poll.RoundRect
import io.rover.sdk.ui.dpAsPx

/**
 * Custom view that is "externally bound", the "bind" methods on this class are externally called
 * as opposed to other views which subscribe to a ViewModel. This means that this view and the views
 * that interact with it operate in a more MVP type approach than the other MVVM-esque views.
 */
internal class TextOptionView(context: Context?) : RelativeLayout(context) {
    private val optionTextView = textView {
        id = ViewCompat.generateViewId()
        ellipsize = TextUtils.TruncateAt.END
        maxLines = 1
        gravity = Gravity.CENTER_VERTICAL
        val marginInPixels = 16.dpAsPx(resources.displayMetrics)

        setupRelativeLayoutParams(
            width = ViewGroup.LayoutParams.WRAP_CONTENT, height = ViewGroup.LayoutParams.MATCH_PARENT,
            leftMargin = marginInPixels, rightMargin = marginInPixels
        ) {
            addRule(ALIGN_PARENT_START)
        }
    }

    private val voteIndicatorView = textView {
        visibility = View.GONE
        maxLines = 1
        gravity = Gravity.CENTER_VERTICAL
        textAlignment = View.TEXT_ALIGNMENT_TEXT_START

        val marginInPixels = 8.dpAsPx(resources.displayMetrics)

        setupRelativeLayoutParams(
            width = LayoutParams.WRAP_CONTENT,
            height = LayoutParams.MATCH_PARENT,
            leftMargin = marginInPixels
        ) {
            addRule(END_OF, optionTextView.id)
        }
    }

    private val votePercentageText = textView {
        id = ViewCompat.generateViewId()
        visibility = View.GONE
        maxLines = 1
        gravity = Gravity.CENTER_VERTICAL
        textAlignment = View.TEXT_ALIGNMENT_TEXT_END

        val marginInDp = 16.dpAsPx(resources.displayMetrics)

        setupRelativeLayoutParams(
            width = LayoutParams.WRAP_CONTENT, height = LayoutParams.MATCH_PARENT,
            leftMargin = marginInDp, rightMargin = marginInDp
        ) {
            addRule(ALIGN_PARENT_RIGHT)
        }
    }

    private val borderView = PollBorderView(this.context).apply {
        setupRelativeLayoutParams(width = ViewGroup.LayoutParams.MATCH_PARENT, height = ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private var roundRect: RoundRect? = null
    private var optionPaints: OptionPaints = OptionPaints()

    // used to set starting point for update animations
    private var currentVote = 0

    var backgroundImage: BackgroundColorDrawableWrapper? = null
        set(value) {
            field = value
            value?.let {
                setBackgroundWithoutPaddingChange(it)
            }
        }

    companion object {
        private const val RESULTS_TEXT_SCALE_FACTOR = 1.05f
        private const val RESULT_FILL_PROGRESS_DURATION = 1000L
        private const val ALPHA_DURATION = 750L
        private const val RESULT_FILL_ALPHA_DURATION = 50L
    }

    init {
        addView(optionTextView)
        addView(votePercentageText)
        addView(borderView)
        addView(voteIndicatorView)
    }

    fun initializeOptionViewLayout(optionStyle: TextPollOption) {
        val optionStyleHeight = optionStyle.height.dpAsPx(resources.displayMetrics)
        val optionMarginHeight = optionStyle.topMargin.dpAsPx(resources.displayMetrics)

        gravity = Gravity.CENTER_VERTICAL
        alpha = optionStyle.opacity.toFloat()
        setBackgroundColor(Color.TRANSPARENT)
        setupLayoutParams(
            width = ViewGroup.LayoutParams.MATCH_PARENT,
            height = optionStyleHeight,
            topMargin = optionMarginHeight
        )

        initializeViewStyle(optionStyle)
    }

    fun bindOptionView(option: TextPollOption) {
        optionTextView.run {
            text = option.text.rawValue
            textSize = option.text.font.size.toFloat()
            setTextColor(option.text.color.asAndroidColor())
            val font = option.text.font.weight.mapToFont()
            typeface = Typeface.create(font.fontFamily, font.fontStyle)
        }
    }

    private fun initializeViewStyle(optionStyle: TextPollOption) {
        val adjustedBorderRadius = if (optionStyle.border.radius.toFloat() > optionStyle.height.toFloat() / 2) {
            optionStyle.height / 2
        } else {
            optionStyle.border.radius
        }

        val adjustedOptionStyle = optionStyle.copy(border = optionStyle.border.copy(radius = adjustedBorderRadius))

        val borderRadius = adjustedOptionStyle.border.radius.dpAsPx(resources.displayMetrics).toFloat()
        val borderStrokeWidth = adjustedOptionStyle.border.width.dpAsPx(resources.displayMetrics).toFloat()
        val fillPaint = Paint().create(adjustedOptionStyle.background.color.asAndroidColor(), Paint.Style.FILL)
        val resultPaint = Paint().create(adjustedOptionStyle.resultFillColor.asAndroidColor(), Paint.Style.FILL)

        optionPaints = OptionPaints(fillPaint, resultPaint)

        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        roundRect = RoundRect(rect, borderRadius, borderStrokeWidth)
        borderView.border = adjustedOptionStyle.border
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        roundRect = roundRect?.copy(rectF = RectF(0f, 0f, width.toFloat(), height.toFloat()))
    }

    private var resultRect: RectF? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        roundRect?.let { roundRect ->
            roundRect.rectF?.let {
                if (backgroundImage == null) canvas.drawRoundRect(
                    it,
                    roundRect.borderRadius,
                    roundRect.borderRadius,
                    optionPaints.fillPaint
                )
                resultRect?.let { resultRect ->
                    canvas.drawRect(resultRect, optionPaints.resultPaint)
                }
            }
        }
    }

    override fun draw(canvas: Canvas?) {
        roundRect?.let { roundRect ->
            roundRect.rectF.let {
                canvas?.clipPath(Path().apply {
                    addRoundRect(it, roundRect.borderRadius, roundRect.borderRadius, Path.Direction.CW)
                })
            }
        }
        super.draw(canvas)
    }

    fun goToResultsState(votingShare: Int, isSelectedOption: Boolean, optionStyle: TextPollOption, shouldAnimate: Boolean, optionWidth: Float?) {

        bindVotePercentageText(votingShare, optionStyle)
        votePercentageText.visibility = View.VISIBLE

        bindVoteIndicatorText(optionStyle)

        if (isSelectedOption) voteIndicatorView.visibility = View.VISIBLE

        val viewWidth = optionWidth?.dpAsPx(resources.displayMetrics) ?: this@TextOptionView.width
        val viewHeight = optionStyle.height.dpAsPx(resources.displayMetrics)

        optionTextView.run {
            gravity = Gravity.CENTER_VERTICAL
            val marginInPixels = 16.dpAsPx(resources.displayMetrics)

            text = optionStyle.text.rawValue

            setupRelativeLayoutParams(
                width = LayoutParams.WRAP_CONTENT,
                height = LayoutParams.MATCH_PARENT,
                leftMargin = marginInPixels
            ) {
                addRule(ALIGN_PARENT_LEFT)
            }

            val maxVotingTextView = textView {
                text = "100%"
                textSize = votePercentageText.textSize
                typeface = votePercentageText.typeface
            }

            val widthOfOtherViews =
                calculateWidthWithoutOptionText(voteIndicatorView, maxVotingTextView, isSelectedOption)

            maxWidth = viewWidth - widthOfOtherViews
        }
        requestLayout()

        if (shouldAnimate) {
            performResultsAnimation(votingShare, (optionStyle.resultFillColor.alpha * 255).toInt(), viewWidth, viewHeight)
        } else {
            immediatelyGoToResultsState(votingShare, (optionStyle.resultFillColor.alpha * 255).toInt(), viewWidth, viewHeight)
        }
    }

    private fun immediatelyGoToResultsState(votingShare: Int, resultFillAlpha: Int, viewWidth: Int, viewHeight: Int) {
        currentVote = votingShare
        votePercentageText.alpha = 1f
        optionPaints.resultPaint.alpha = resultFillAlpha
        voteIndicatorView.alpha = 1f
        votePercentageText.text = "$votingShare%"
        resultRect = RectF(0f, 0f, viewWidth.toFloat() / 100 * votingShare, viewHeight.toFloat())
    }

    private val easeInEaseOutInterpolator = TimeInterpolator { input ->
        val inputSquared = input * input
        inputSquared / (2.0f * (inputSquared - input) + 1.0f)
    }

    private fun performResultsAnimation(votingShare: Int, resultFillAlpha: Int, viewWidth: Int, viewHeight: Int) {
        currentVote = votingShare

        val alphaAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ALPHA_DURATION
            addUpdateListener {
                voteIndicatorView.alpha = it.animatedFraction
                votePercentageText.alpha = it.animatedFraction
            }
        }

        val resultFillAlphaAnimator = ValueAnimator.ofInt(0, resultFillAlpha).apply {
            duration = RESULT_FILL_ALPHA_DURATION
            addUpdateListener {
                optionPaints.resultPaint.alpha = (it.animatedValue as Int)
            }
        }

        val resultsAnimation = ValueAnimator.ofFloat(0f, votingShare.toFloat()).apply {
            duration = RESULT_FILL_PROGRESS_DURATION
            addUpdateListener {
                val animatedValue = it.animatedValue as Float
                votePercentageText.text = "${animatedValue.toInt()}%"
                resultRect = RectF(0f, 0f, viewWidth.toFloat() / 100 * animatedValue, viewHeight.toFloat())
            }
        }

        AnimatorSet().apply { playTogether(alphaAnimator, resultFillAlphaAnimator, resultsAnimation)
        interpolator = easeInEaseOutInterpolator }.start()
    }

    fun updateResults(votingShare: Int) {
        val resultProgressFillAnimator = ValueAnimator.ofFloat(currentVote.toFloat(), votingShare.toFloat()).apply {
            duration = RESULT_FILL_PROGRESS_DURATION
            addUpdateListener {
                val animatedValue = it.animatedValue as Float
                votePercentageText.text = "${animatedValue.toInt()}%"
                resultRect = RectF(0f, 0f, width.toFloat() / 100 * animatedValue, height.toFloat())
            }
            interpolator = easeInEaseOutInterpolator
        }
        resultProgressFillAnimator.start()
        currentVote = votingShare
    }

    private fun calculateWidthWithoutOptionText(
        voteIndicatorView: AppCompatTextView,
        votePercentageText: AppCompatTextView,
        isSelectedOption: Boolean
    ): Int {
        voteIndicatorView.measure(0, 0)
        votePercentageText.measure(0, 0)
        val voteIndicatorWidth =
            if (isSelectedOption) voteIndicatorView.measuredWidth + (voteIndicatorView.layoutParams as MarginLayoutParams).marginStart else 0
        val votePercentageMargins =
            (votePercentageText.layoutParams as MarginLayoutParams).marginEnd + (votePercentageText.layoutParams as MarginLayoutParams).marginStart
        val votePercentageWidth = votePercentageText.measuredWidth + votePercentageMargins
        val optionEndMargin = (optionTextView.layoutParams as MarginLayoutParams).marginStart

        return voteIndicatorWidth + optionEndMargin + votePercentageWidth
    }

    private fun bindVotePercentageText(
        votingShare: Int,
        optionStyle: TextPollOption
    ) {
        votePercentageText.run {
            textSize = (optionStyle.text.font.size * RESULTS_TEXT_SCALE_FACTOR)
            votePercentageText.text = "$votingShare%"
            setTextColor(optionStyle.text.color.asAndroidColor())
            val font = optionStyle.text.font.weight.getIncreasedFontWeight().mapToFont()
            typeface = Typeface.create(font.fontFamily, font.fontStyle)
        }
    }

    private fun FontWeight.getIncreasedFontWeight(): FontWeight {
        return FontWeight.values().getOrElse(this.ordinal + 2) { FontWeight.Black }
    }

    private fun bindVoteIndicatorText(optionStyle: TextPollOption) {
        voteIndicatorView.run {
            text = "\u2022"
            textSize = optionStyle.text.font.size.toFloat()
            setTextColor(optionStyle.text.color.asAndroidColor())
            val font = optionStyle.text.font.weight.mapToFont()
            typeface = Typeface.create(font.fontFamily, font.fontStyle)
        }
    }
}

private data class OptionPaints(
    val fillPaint: Paint = Paint(),
    val resultPaint: Paint = Paint()
)
