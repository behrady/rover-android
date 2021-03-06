package io.rover.sdk.assets

import io.rover.helpers.shouldEqual
import io.rover.sdk.data.domain.Background
import io.rover.sdk.data.domain.BackgroundContentMode
import io.rover.sdk.data.domain.BackgroundScale
import io.rover.sdk.data.domain.Color
import io.rover.sdk.data.domain.Image
import io.rover.sdk.ui.PixelSize
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.URI
import java.net.URLDecoder

object ImageOptimizationServiceSpec : Spek({
    fun decodeUriParams(uri: URI): Map<String, String> {
        return uri.query.split("&").map { it.split("=").map { URLDecoder.decode(it, "UTF-8") } }
            .associate { Pair(it[0], it[1]) }
    }

    val imageOptimizationService = ImageOptimizationService()

    describe("a stretched background") {
        val image = Image(
            120,
            100,
            "interesting.jpg",
            6000,
            URI("https://rover.io/image.jpg")
        )

        val background = Background(
            Color(0x7f, 0x7f, 0x7f, 0.0),
            BackgroundContentMode.Stretch,
            image,
            BackgroundScale.X3 // 480 dpi
        )

        context("optimized to display in exactly the same pixel-size block") {

            val (uri, _) = imageOptimizationService.optimizeImageBackground(
                background,
                PixelSize(120, 100),
                3.0f
            )!!

            val decodedParams = decodeUriParams(uri)

            it("it should ask imgix for a no-op scale") {
                decodedParams["w"] shouldEqual "120"
                decodedParams["h"] shouldEqual "100"
            }
        }

        context("optimized to display in a block with smaller pixel-size but the same aspect ratio") {

            val (uri, _) = imageOptimizationService.optimizeImageBackground(
                background,
                PixelSize(60, 50),
                3.0f
            )!!

            val decodedParams = decodeUriParams(uri)

            it("it should ask imgix to scale down by half") {
                decodedParams["w"] shouldEqual "60"
                decodedParams["h"] shouldEqual "50"
            }
        }

        context("optimized to display in smaller but same aspect ratio block") {
            val (uri, _) = imageOptimizationService.optimizeImageBackground(
                background,
                PixelSize(34, 29),
                3.0f
            )!!

            val decodedParams = decodeUriParams(uri)

            it("it should ask imgix to scale down") {
                decodedParams["w"] shouldEqual "34"
                decodedParams["h"] shouldEqual "29"
            }
        }

        context("optimized to display in a block with a wider dimension and a narrower dimension") {
            // TODO: should have same assertions as the same-pixel size case!

            val (uri, _) = imageOptimizationService.optimizeImageBackground(
                background,
                PixelSize(140, 90),
                3.0f
            )!!

            val decodedParams = decodeUriParams(uri)

            it("it should scale down the smaller dimension but not scale up the greater one") {
                decodedParams["w"] shouldEqual "120"
                decodedParams["h"] shouldEqual "90"
            }
        }
    }

    describe("an original size image block background at 3X (480 dpi) that must be cropped") {
        val image = Image(
            120,
            100,
            "interesting.jpg",
            6000,
            URI("https://rover.io/image.jpg")
        )

        val background = Background(
            Color(0x7f, 0x7f, 0x7f, 0.0),

            BackgroundContentMode.Original,

            image,

            BackgroundScale.X3
        )

        context("optimized to display in exactly the same size block on same density display") {
            val (uri, optimizedConfiguration) = imageOptimizationService.optimizeImageBackground(
                background,
                PixelSize(120, 100),
                3.0f
            )!!

            val decodedParams = decodeUriParams(uri)

            it("asks imgix for a no-op crop") {
                decodedParams["rect"] shouldEqual "0,0,120,100"
            }

            it("asks imgix for a no-op scale") {
                decodedParams["w"] shouldEqual "120"
                decodedParams["h"] shouldEqual "100"
            }

            it("sets exactly fitting insets") {
                optimizedConfiguration.insets.left shouldEqual 0
                optimizedConfiguration.insets.top shouldEqual 0
                optimizedConfiguration.insets.right shouldEqual 0
                optimizedConfiguration.insets.bottom shouldEqual 0
            }
        }

        context("optimized to display in exactly the same size block on a 560 dpi display") {
            val (uri, optimizedConfiguration) = imageOptimizationService.optimizeImageBackground(
                background,
                PixelSize(140, 117), // * 1.16666~ (480 dp -> 560 dp factor)
                3.5f
            )!!

            val decodedParams = decodeUriParams(uri)

            it("asks imgix for a no-op crop") {
                // we wouldn't want imgix to scale up for us, that would be a waste.
                decodedParams["rect"] shouldEqual "0,0,120,100"
            }

            it("asks imgix for a no-op scale") {
                decodedParams["w"].shouldEqual("120")
                decodedParams["h"].shouldEqual("100")
            }

            it("sets exactly fitting insets") {
                optimizedConfiguration.insets.left shouldEqual 0
                optimizedConfiguration.insets.top shouldEqual 0
                optimizedConfiguration.insets.right shouldEqual 0
                optimizedConfiguration.insets.bottom shouldEqual 0
            }
        }

        context("optimized to display in a slightly wider block on same density display") {
            val (uri, optimizedConfiguration) = imageOptimizationService.optimizeImageBackground(
                background,
                PixelSize(140, 100), // wider by 20 image pixels (which is the same as display pixels here)
                3.0f
            )!!

            val decodedParams = decodeUriParams(uri)

            it("asks imgix for a no-op crop") {
                decodedParams["rect"] shouldEqual "0,0,120,100"
            }

            it("asks imgix for a no-op scale") {
                decodedParams["w"] shouldEqual "120"
                decodedParams["h"] shouldEqual "100"
            }

            it("sets horizontal insets") {
                optimizedConfiguration.insets.left shouldEqual 10
                optimizedConfiguration.insets.right shouldEqual 10
                optimizedConfiguration.insets.top shouldEqual 0
                optimizedConfiguration.insets.bottom shouldEqual 0
            }
        }

        context("optimized to display in a slightly wider block on a 560 dpi display") {
            val (uri, optimizedConfiguration) = imageOptimizationService.optimizeImageBackground(
                background,
                PixelSize(163, 117), // wider by 20 image pixels and then * 1.16666~ (480 dp -> 560 dp factor)
                3.5f
            )!!

            val decodedParams = decodeUriParams(uri)

            it("asks imgix for a no-op crop") {
                // we wouldn't want imgix to scale up for us, that would be a waste.
                decodedParams["rect"] shouldEqual "0,0,120,100"
            }

            it("asks imgix for a no-op scale") {
                decodedParams["w"] shouldEqual "120"
                decodedParams["h"] shouldEqual "100"
            }

            it("sets horizontal insets") {
                optimizedConfiguration.insets.left shouldEqual 11 // TODO: maybe should be 12? rounding accumulation
                optimizedConfiguration.insets.right shouldEqual 11
                optimizedConfiguration.insets.top shouldEqual 0
                optimizedConfiguration.insets.bottom shouldEqual 0
            }
        }

        context("optimized to display in a narrower block on same density display") {
            val (uri, optimizedConfiguration) = imageOptimizationService.optimizeImageBackground(
                background,
                PixelSize(100, 100), // 20 image pixels narrower.
                3.0f
            )!!

            val decodedParams = decodeUriParams(uri)

            it("asks imgix to crop the sides") {
                decodedParams["rect"] shouldEqual "10,0,100,100"
            }

            it("asks imgix for a no-op scale") {
                decodedParams["w"] shouldEqual "100"
                decodedParams["h"] shouldEqual "100"
            }

            it("sets zero insets for the width dimension because the crop was done for us by imgix") {
                optimizedConfiguration.insets.left shouldEqual 0
                optimizedConfiguration.insets.right shouldEqual 0
                optimizedConfiguration.insets.top shouldEqual 0
                optimizedConfiguration.insets.bottom shouldEqual 0
            }
        }

        context("optimized to display in a narrower block on a 560 dpi display") {
            val (uri, optimizedConfiguration) = imageOptimizationService.optimizeImageBackground(
                background,
                PixelSize(117, 117), // 20 image pixels narrower and then * 1.16666~ (480 dp -> 560 dp factor)
                3.5f
            )!!

            val decodedParams = decodeUriParams(uri)

            it("asks imgix for a crop") {
                // we wouldn't want imgix to scale up for us, that would be a waste.
                decodedParams["rect"] shouldEqual "10,0,100,100"
            }

            it("asks imgix for a no-op scale") {
                decodedParams["w"] shouldEqual "100"
                decodedParams["h"] shouldEqual "100"
            }

            it("sets zero insets for the width dimension because the crop was done for us by imgix") {
                optimizedConfiguration.insets.left shouldEqual 0
                optimizedConfiguration.insets.right shouldEqual 0
                optimizedConfiguration.insets.top shouldEqual 0
                optimizedConfiguration.insets.bottom shouldEqual 0
            }
        }

        context("optimized to display in exactly the same size block on a 160 dpi display") {
            val (uri, optimizedConfiguration) = imageOptimizationService.optimizeImageBackground(
                background,
                PixelSize(40, 33), // * 0.3 (480 dp -> 160 dp factor)
                1f
            )!!

            val decodedParams = decodeUriParams(uri)

            it("asks imgix for a no-op crop") {
                decodedParams["rect"] shouldEqual "0,0,120,100"
            }

            it("asks imgix to scale down by a factor of three ") {
                decodedParams["w"] shouldEqual "40"
                decodedParams["h"] shouldEqual "33"
            }

            it("sets exactly fitting insets") {
                optimizedConfiguration.insets.left shouldEqual 0
                optimizedConfiguration.insets.top shouldEqual 0
                optimizedConfiguration.insets.right shouldEqual 0
                optimizedConfiguration.insets.bottom shouldEqual 0
            }
        }

        context("optimized to display in a narrower block on a 160 dpi display") {
            val (uri, optimizedConfiguration) = imageOptimizationService.optimizeImageBackground(
                background,
                PixelSize(33, 33), // 20 image pixels narrower and then * 0.3 (480 dp -> 160 dp factor)
                1f
            )!!

            val decodedParams = decodeUriParams(uri)

            it("asks imgix for a crop") {
                decodedParams["rect"] shouldEqual "10,0,100,100"
            }

            it("asks imgix to scale down") {
                decodedParams["w"] shouldEqual "33"
                decodedParams["h"] shouldEqual "33"
            }

            it("sets exactly fitting insets") {
                optimizedConfiguration.insets.left shouldEqual 0
                optimizedConfiguration.insets.top shouldEqual 0
                optimizedConfiguration.insets.right shouldEqual 0
                optimizedConfiguration.insets.bottom shouldEqual 0
            }
        }

        context("optimized to display in a slightly wider block on a 160 dpi display") {
            val (uri, optimizedConfiguration) = imageOptimizationService.optimizeImageBackground(
                background,
                PixelSize(47, 33), // wider by 20 image pixels and then * 0.3 (480 dp -> 160 dp factor)
                1f
            )!!

            val decodedParams = decodeUriParams(uri)

            it("asks imgix for a no-op crop") {
                decodedParams["rect"] shouldEqual "0,0,120,100"
            }

            it("asks imgix to scale down") {
                decodedParams["w"] shouldEqual "40"
                decodedParams["h"] shouldEqual "33"
            }

            it("sets horizontal insets") {
                optimizedConfiguration.insets.left shouldEqual 3
                optimizedConfiguration.insets.right shouldEqual 3
                optimizedConfiguration.insets.top shouldEqual 0
                optimizedConfiguration.insets.bottom shouldEqual 0
            }
        }
    }
})