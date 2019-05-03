package io.rover.sdk.services

import android.content.Intent
import android.support.v4.content.LocalBroadcastManager
import io.rover.sdk.data.domain.Attributes
import io.rover.sdk.logging.log
import io.rover.sdk.streams.PublishSubject
import io.rover.sdk.streams.share
import org.json.JSONObject
import org.reactivestreams.Publisher

/**
 * Provides a single source for Rover events being emitted.
 *
 * You can subscribe to the [trackedEvents] Publisher to be informed of them.
 *
 * Alternatively, this service emits them as local Android broadcast intents.  This allows consumers
 * to receive them that cannot otherwise link against types in this library.
 */
open class EventEmitter(
    private val localBroadcastManager: LocalBroadcastManager
) {
    protected open val eventSubject = PublishSubject<Event>()

    data class Event(
        val eventAction: EventAction,
        val attributes: Attributes
    )

    open val trackedEvents: Publisher<Event> by lazy {  eventSubject.share() }

    open fun trackEvent(action: String, attributes: Map<String, Any>) {
        val intent = Intent(
            action
        )

        val analyticsName = EventAction.values().find { it.action == action }

        analyticsName?.let {
            eventSubject.onNext(Event(it, attributes))
        }

        intent.putExtra("attributes", JSONObject(attributes).toString())

        localBroadcastManager.sendBroadcast(intent)
        log.v("Event broadcast: $action, ${JSONObject(attributes).toString(4)}")
    }
}

enum class EventAction(val action: String) {
    EXPERIENCE_PRESENTED("io.rover.ExperiencePresented"),
    EXPERIENCE_DISMISSED("io.rover.ExperienceDismissed"),
    EXPERIENCE_VIEWED("io.rover.ExperienceViewed"),
    SCREEN_PRESENTED("io.rover.ScreenPresented"),
    SCREEN_DISMISSED("io.rover.ScreenDismissed"),
    SCREEN_VIEWED("io.rover.ScreenViewed"),
    BLOCK_TAPPED("io.rover.BlockTapped");
}
