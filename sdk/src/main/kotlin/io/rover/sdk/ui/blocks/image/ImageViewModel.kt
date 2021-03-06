package io.rover.sdk.ui.blocks.image

import io.rover.sdk.assets.AssetService
import io.rover.sdk.assets.ImageOptimizationService
import io.rover.sdk.logging.log
import io.rover.sdk.streams.PublishSubject
import io.rover.sdk.streams.Publishers
import io.rover.sdk.streams.Scheduler
import io.rover.sdk.streams.filterNulls
import io.rover.sdk.streams.flatMap
import io.rover.sdk.streams.map
import io.rover.sdk.streams.observeOn
import io.rover.sdk.streams.onErrorReturn
import io.rover.sdk.streams.share
import io.rover.sdk.streams.shareHotAndReplay
import io.rover.sdk.streams.subscribe
import io.rover.sdk.streams.timeout
import io.rover.sdk.ui.PixelSize
import io.rover.sdk.ui.concerns.MeasuredSize
import io.rover.sdk.data.domain.Block
import io.rover.sdk.data.domain.Image
import io.rover.sdk.ui.RectF
import io.rover.sdk.ui.dpAsPx
import org.reactivestreams.Publisher
import java.util.concurrent.TimeUnit

internal class ImageViewModel(
    private val image: Image?,
    private val block: Block,
    private val assetService: AssetService,
    private val imageOptimizationService: ImageOptimizationService,
    mainScheduler: Scheduler
) : ImageViewModelInterface {

    override fun informDimensions(measuredSize: MeasuredSize) {
        measurementsSubject.onNext(measuredSize)
    }

    override fun measuredSizeReadyForPrefetch(measuredSize: MeasuredSize) {
        prefetchMeasurementsSubject.onNext(measuredSize)
    }

    private val prefetchMeasurementsSubject = PublishSubject<MeasuredSize>()
    private val measurementsSubject = PublishSubject<MeasuredSize>()

    private var fadeInNeeded = false

    override val imageUpdates: Publisher<ImageViewModelInterface.ImageUpdate> = Publishers.merge(
        prefetchMeasurementsSubject.imageFetchTransform(),
        measurementsSubject
            .flatMap {
                Publishers.just(it)
                    .imageFetchTransform()
                    .share()
                    .apply {
                        if (image != null) {
                            timeout(50, TimeUnit.MILLISECONDS)
                                .subscribe(
                                    { },
                                    { error ->
                                        log.v("Fade in needed, because $error")
                                        fadeInNeeded = true
                                    }
                                )
                        }
                    }
            }
    ).shareHotAndReplay(0).observeOn(mainScheduler) // shareHot because this chain is responsible for side-effect of pre-warming cache, even before subscribed.

    private fun Publisher<MeasuredSize>.imageFetchTransform(): Publisher<ImageViewModelInterface.ImageUpdate> {
        return flatMap { measuredSize ->
            if (image == null) {
                Publishers.empty()
            } else {
                val uriWithParameters = imageOptimizationService.optimizeImageBlock(
                    image,
                    block.border.width,
                    PixelSize(
                        measuredSize.width.dpAsPx(measuredSize.density),
                        measuredSize.height.dpAsPx(measuredSize.density)
                    ),
                    measuredSize.density
                )

                // so if item does not appear within a threshold of time then turn on a fade-in bit?
                assetService.imageByUrl(uriWithParameters.toURL())
                    .map { bitmap ->
                        ImageViewModelInterface.ImageUpdate(
                            bitmap,
                            fadeInNeeded
                        )
                    }.onErrorReturn { error ->
                        log.w("Problem fetching image: $error, ignoring.")
                        null
                    }.filterNulls()
            }
        }
    }

    override fun intrinsicHeight(bounds: RectF): Float {
        // get aspect ratio of image and use it to calculate the height needed to accommodate
        // the image at its correct aspect ratio given the width
        return if (image == null) {
            0f
        } else {
            val heightToWidthRatio = image.height.toFloat() / image.width.toFloat()
            return bounds.width() * heightToWidthRatio
        }
    }
}
