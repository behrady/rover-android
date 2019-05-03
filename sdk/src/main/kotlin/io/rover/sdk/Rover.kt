package io.rover.sdk

import android.app.Application
import android.arch.lifecycle.Lifecycle
import android.content.Context
import android.graphics.Color
import android.os.Parcelable
import android.support.annotation.ColorInt
import android.support.v4.content.LocalBroadcastManager
import android.util.DisplayMetrics
import io.rover.sdk.assets.AndroidAssetService
import io.rover.sdk.assets.ImageDownloader
import io.rover.sdk.assets.ImageOptimizationService
import io.rover.sdk.data.events.AnalyticsService
import io.rover.sdk.data.domain.Background
import io.rover.sdk.data.domain.Barcode
import io.rover.sdk.data.domain.BarcodeBlock
import io.rover.sdk.data.domain.Block
import io.rover.sdk.data.domain.Border
import io.rover.sdk.data.domain.ButtonBlock
import io.rover.sdk.data.domain.Experience
import io.rover.sdk.data.domain.Image
import io.rover.sdk.data.domain.ImageBlock
import io.rover.sdk.data.domain.RectangleBlock
import io.rover.sdk.data.domain.Row
import io.rover.sdk.data.domain.Screen
import io.rover.sdk.data.domain.Text
import io.rover.sdk.data.domain.TextBlock
import io.rover.sdk.data.domain.WebView
import io.rover.sdk.data.domain.WebViewBlock
import io.rover.sdk.data.graphql.GraphQlApiService
import io.rover.sdk.data.http.HttpClient
import io.rover.sdk.services.EventEmitter
import io.rover.sdk.logging.log
import io.rover.sdk.platform.IoMultiplexingExecutor
import io.rover.sdk.platform.LocalStorage
import io.rover.sdk.services.BarcodeRenderingService
import io.rover.sdk.services.EmbeddedWebBrowserDisplay
import io.rover.sdk.services.MeasurementService
import io.rover.sdk.streams.Scheduler
import io.rover.sdk.streams.forAndroidMainThread
import io.rover.sdk.streams.forExecutor
import io.rover.sdk.services.SessionStore
import io.rover.sdk.services.SessionTracker
import io.rover.sdk.ui.RoverViewModel
import io.rover.sdk.ui.blocks.barcode.BarcodeBlockView
import io.rover.sdk.ui.blocks.barcode.BarcodeBlockViewModel
import io.rover.sdk.ui.blocks.barcode.BarcodeViewModel
import io.rover.sdk.ui.blocks.button.ButtonBlockView
import io.rover.sdk.ui.blocks.button.ButtonBlockViewModel
import io.rover.sdk.ui.blocks.concerns.background.BackgroundViewModel
import io.rover.sdk.ui.blocks.concerns.border.BorderViewModel
import io.rover.sdk.ui.blocks.concerns.layout.BlockViewModel
import io.rover.sdk.ui.blocks.concerns.layout.CompositeBlockViewModelInterface
import io.rover.sdk.ui.blocks.concerns.layout.LayoutPaddingDeflection
import io.rover.sdk.ui.blocks.concerns.layout.LayoutableView
import io.rover.sdk.ui.blocks.concerns.layout.LayoutableViewModel
import io.rover.sdk.ui.blocks.concerns.layout.Measurable
import io.rover.sdk.ui.blocks.concerns.text.AndroidRichTextToSpannedTransformer
import io.rover.sdk.ui.blocks.concerns.text.TextViewModel
import io.rover.sdk.ui.blocks.image.ImageBlockView
import io.rover.sdk.ui.blocks.image.ImageBlockViewModel
import io.rover.sdk.ui.blocks.image.ImageViewModel
import io.rover.sdk.ui.blocks.rectangle.RectangleBlockView
import io.rover.sdk.ui.blocks.rectangle.RectangleBlockViewModel
import io.rover.sdk.ui.blocks.text.TextBlockView
import io.rover.sdk.ui.blocks.text.TextBlockViewModel
import io.rover.sdk.ui.blocks.web.WebBlockView
import io.rover.sdk.ui.blocks.web.WebViewBlockViewModel
import io.rover.sdk.ui.blocks.web.WebViewModel
import io.rover.sdk.ui.layout.BlockAndRowLayoutManager
import io.rover.sdk.ui.layout.BlockAndRowRecyclerAdapter
import io.rover.sdk.ui.layout.Layout
import io.rover.sdk.ui.layout.ViewType
import io.rover.sdk.ui.layout.row.RowView
import io.rover.sdk.ui.layout.row.RowViewModel
import io.rover.sdk.ui.layout.screen.ScreenViewModel
import io.rover.sdk.ui.navigation.NavigationViewModel
import io.rover.sdk.ui.toolbar.ExperienceToolbarViewModel
import io.rover.sdk.ui.toolbar.ToolbarConfiguration
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executor

/**
 * Entry point for the Rover SDK.
 *
 * The Rover SDK consists of several discrete modules, which each offer a major vertical
 * (eg. Experiences and Location) of the Rover Platform.  It's up to you to select which
 * are appropriate to activate in your app.
 *
 * Serves as a dependency injection container (a sort of backplane) for the various components of
 * the Rover SDK.
 */
open class Rover(
    /**
     * When initializing Rover you must give it a reference
     */
    open val application: Application,

    /**
     * Set your Rover Account Token (API Key) here.
     */
    open var accountToken: String? = null,

    /**
     * Set the background colour for the Custom Chrome tabs that are used for presenting web content
     * in a web browser.
     */
    open val chromeTabBackgroundColor: Int,

    open val endpoint: String = "https://api.rover.io/graphql",

    open val mainScheduler: Scheduler = Scheduler.forAndroidMainThread(),

    open val ioExecutor: Executor = IoMultiplexingExecutor.build("io"),

    open val ioScheduler: Scheduler = Scheduler.forExecutor(
        ioExecutor
    ),

    open val imageDownloader: ImageDownloader = ImageDownloader(ioExecutor),

    open val assetService: AndroidAssetService = AndroidAssetService(imageDownloader, ioScheduler, mainScheduler),

    open val imageOptimizationService: ImageOptimizationService = ImageOptimizationService(),

    open val httpClient: HttpClient = HttpClient(ioScheduler),

    open val webBrowserDisplay: EmbeddedWebBrowserDisplay = EmbeddedWebBrowserDisplay(chromeTabBackgroundColor),

    open val localStorage: LocalStorage = LocalStorage(application),

    open val sessionStore: SessionStore = SessionStore(localStorage),

    open val eventEmitter: EventEmitter = EventEmitter(LocalBroadcastManager.getInstance(application)),

    val analyticsService: AnalyticsService = AnalyticsService(
        application,
        accountToken,
        eventEmitter
    ),

    /**
     * Not for use by typical applications: present so OAuth/SSO with apps that log into the Rover web apps can use the SDK.  You can safely ignore this.
     */
    open var bearerToken: String? = null,

    open val apiService: GraphQlApiService = GraphQlApiService(URL(endpoint), accountToken, bearerToken, httpClient),

    open val sessionTracker: SessionTracker = SessionTracker(eventEmitter, sessionStore, 60),

    open val textFormatter: AndroidRichTextToSpannedTransformer = AndroidRichTextToSpannedTransformer(),

    open val barcodeRenderingService: BarcodeRenderingService = BarcodeRenderingService(),

    open val measurementService: MeasurementService = MeasurementService(
        displayMetrics = application.resources.displayMetrics,
        richTextToSpannedTransformer = textFormatter,
        barcodeRenderingService = barcodeRenderingService
    ),

    open val views: Views = Views()
) {
    open val viewModels: ViewModels by lazy {
        ViewModels(
            apiService,
            mainScheduler,
            eventEmitter,
            sessionTracker,
            imageOptimizationService,
            assetService,
            measurementService
        )
    }

    companion object {
        /**
         * Be sure to always call this before [Rover.initialize] in your Application's onCreate()!
         *
         * Rover internally uses the standard HTTP client included with Android, but to work
         * effectively it needs HTTP caching enabled.  Unfortunately, this can only be done at the
         * global level, so we ask that you call this method -- [installSaneGlobalHttpCache] -- at
         * application start time (unless you have already added your own cache to Android's
         * [HttpURLConnection].
         */
        @JvmStatic
        fun installSaneGlobalHttpCache(applicationContext: Context) {
            HttpClient.installSaneGlobalHttpCache(applicationContext)
        }

        fun initialize(application: Application, accountToken: String, @ColorInt chromeTabColor: Int = Color.BLACK) {
            shared = Rover(application = application, accountToken = accountToken, chromeTabBackgroundColor = chromeTabColor)
        }

        /**
         * The global Rover context. A Rover instance must be set here in order to use any of Rover.
         * You can use one of the [initialize] methods to do so.
         */
        @JvmStatic
        var shared: Rover? = null
    }

    init {
        log.i("Started Rover Android SDK v${BuildConfig.VERSION_NAME}.")
    }
}

// TODO: consider moving entire class into appropriate sub-package
open class ViewModels(
//    protected open val rover: Rover
    protected val apiService: GraphQlApiService,
    protected val mainScheduler: Scheduler,
    protected val eventEmitter: EventEmitter,
    protected val sessionTracker: SessionTracker,
    protected val imageOptimizationService: ImageOptimizationService,
    protected val assetService: AndroidAssetService,
    protected val measurementService: MeasurementService
) {
    open fun experienceViewModel(
        experienceRequest: RoverViewModel.ExperienceRequest,
        campaignId: String?,
        activityLifecycle: Lifecycle
    ): RoverViewModel {
        return RoverViewModel(
            experienceRequest = experienceRequest,
            graphQlApiService = apiService,
            mainThreadScheduler = mainScheduler,
            sessionTracker = sessionTracker,
            resolveNavigationViewModel = { experience, icicle ->
                experienceNavigationViewModel(experience, campaignId, activityLifecycle, icicle)
            }
        )
    }

    open fun experienceToolbarViewModel(toolbarConfiguration: ToolbarConfiguration): ExperienceToolbarViewModel {
        return ExperienceToolbarViewModel(toolbarConfiguration)
    }

    open fun experienceNavigationViewModel(
        experience: Experience,
        campaignId: String?,
        activityLifecycle: Lifecycle,
        icicle: Parcelable? = null
    ): NavigationViewModel {
        return NavigationViewModel(
            experience,
            eventEmitter = eventEmitter,
            campaignId = campaignId,
            sessionTracker = sessionTracker,
            resolveScreenViewModel = { screen -> screenViewModel(screen) },
            resolveToolbarViewModel = { configuration -> experienceToolbarViewModel(configuration) },
            activityLifecycle = activityLifecycle,
            icicle = icicle
        )
    }

    open fun screenViewModel(
        screen: Screen
    ): ScreenViewModel {
        return ScreenViewModel(
            screen,
            backgroundViewModel(screen.background),
            resolveRowViewModel = { row -> rowViewModel(row) }
        )
    }

    open fun backgroundViewModel(
        background: Background
    ): BackgroundViewModel {
        return BackgroundViewModel(
            background = background,
            assetService = assetService,
            imageOptimizationService = imageOptimizationService,
            mainScheduler = mainScheduler
        )
    }

    open fun rowViewModel(
        row: Row
    ): RowViewModel {
        return RowViewModel(
            row = row,
            blockViewModelResolver = { block ->
                blockContentsViewModel(block)
            },
            backgroundViewModel = this.backgroundViewModel(row.background)
        )
    }

    open fun blockContentsViewModel(
        block: Block
    ): CompositeBlockViewModelInterface {
        when (block) {
            is RectangleBlock -> {
                return RectangleBlockViewModel(
                    blockViewModel = blockViewModel(block, emptySet(), null),
                    backgroundViewModel = backgroundViewModel(block.background),
                    borderViewModel = borderViewModel(block.border)
                )
            }
            is TextBlock -> {
                val textViewModel = textViewModel(block.text, singleLine = false)
                return TextBlockViewModel(
                    blockViewModel = blockViewModel(block, emptySet(), textViewModel),
                    textViewModel = textViewModel,
                    backgroundViewModel = backgroundViewModel(block.background),
                    borderViewModel = borderViewModel(block.border)
                )
            }
            is ImageBlock -> {
                val imageViewModel = imageViewModel(block.image, block)
                return ImageBlockViewModel(
                    blockViewModel = blockViewModel(block, emptySet(), imageViewModel),
                    backgroundViewModel = backgroundViewModel(block.background),
                    imageViewModel = imageViewModel,
                    borderViewModel = borderViewModel(block.border)
                )
            }
            is ButtonBlock -> {
                return ButtonBlockViewModel(
                    blockViewModel = blockViewModel(block, emptySet(), null),
                    borderViewModel = borderViewModel(block.border),
                    backgroundViewModel = backgroundViewModel(block.background),
                    textViewModel = textViewModel(block.text, singleLine = true)
                )
            }
            is WebViewBlock -> {
                return WebViewBlockViewModel(
                    blockViewModel = blockViewModel(block, setOf(), null),
                    backgroundViewModel = backgroundViewModel(block.background),
                    borderViewModel = borderViewModel(block.border),
                    webViewModel = webViewModel(block.webView)
                )
            }
            is BarcodeBlock -> {
                val barcodeViewModel = barcodeViewModel(block.barcode)
                return BarcodeBlockViewModel(
                    blockViewModel = blockViewModel(block, emptySet(), barcodeViewModel),
                    barcodeViewModel = barcodeViewModel
                )
            }
            else -> throw Exception(
                "This Rover UI block type is not supported by this version of the SDK: ${block.javaClass.simpleName}."
            )
        }
    }

    open fun blockViewModel(
        block: Block,
        paddingDeflections: Set<LayoutPaddingDeflection>,
        measurable: Measurable?
    ): BlockViewModel {
        return BlockViewModel(
            block = block,
            paddingDeflections = paddingDeflections,
            measurable = measurable
        )
    }

    open fun borderViewModel(
        border: Border
    ): BorderViewModel {
        return BorderViewModel(border)
    }

    open fun textViewModel(
        text: Text,
        singleLine: Boolean
    ): TextViewModel {
        return TextViewModel(
            styledText = text,
            measurementService = measurementService,
            singleLine = singleLine
        )
    }

    open fun imageViewModel(
        image: Image?,
        containingBlock: Block
    ): ImageViewModel {
        return ImageViewModel(
            image = image,
            block = containingBlock,
            imageOptimizationService = imageOptimizationService,
            assetService = assetService,
            mainScheduler = mainScheduler
        )
    }

    open fun webViewModel(
        webView: WebView
    ): WebViewModel {
        return WebViewModel(webView)
    }

    open fun barcodeViewModel(
        barcode: Barcode
    ): BarcodeViewModel {
        return BarcodeViewModel(
            barcode,
            measurementService
        )
    }
}

open class Views {
    // TODO: consider moving into RoverView
    fun blockAndRowLayoutManager(
        layout: Layout,
        displayMetrics: DisplayMetrics
    ): BlockAndRowLayoutManager {
        return BlockAndRowLayoutManager(
            layout,
            displayMetrics
        )
    }

    open fun blockView(
        viewType: ViewType,
        context: Context
    ): LayoutableView<out LayoutableViewModel> {
        return when(viewType) {
            ViewType.Row -> RowView(context)
            ViewType.Rectangle -> RectangleBlockView(context)
            ViewType.Text -> TextBlockView(context)
            ViewType.Button -> ButtonBlockView(context)
            ViewType.Image -> ImageBlockView(context)
            ViewType.WebView -> WebBlockView(context)
            ViewType.Barcode -> BarcodeBlockView(context)
        }
    }

    // TODO: consider moving into RoverView
    fun blockAndRowRecyclerAdapter(
        layout: Layout,
        displayMetrics: DisplayMetrics
    ): BlockAndRowRecyclerAdapter {
        return BlockAndRowRecyclerAdapter(
            layout = layout,
            displayMetrics = displayMetrics,
            blockViewFactory = { viewType, context ->
                blockView(viewType, context)
            }
        )
    }
}