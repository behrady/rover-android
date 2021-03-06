@file:JvmName("Android")

package io.rover.sdk.streams

import androidx.lifecycle.GenericLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import android.view.View
import org.reactivestreams.Publisher
import org.reactivestreams.Subscription

internal sealed class ViewEvent {
    class Attach : ViewEvent()
    class Detach : ViewEvent()
}

/**
 * Observe attach and detach events from the given Android [View].
 */
internal fun View.attachEvents(): Publisher<ViewEvent> {
    return Publisher { subscriber ->
        var requested = false
        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewDetachedFromWindow(v: View) {
                subscriber.onNext(ViewEvent.Detach())
            }

            override fun onViewAttachedToWindow(v: View) {
                subscriber.onNext(ViewEvent.Attach())
            }
        }

        subscriber.onSubscribe(object : Subscription {
            override fun cancel() {
                removeOnAttachStateChangeListener(listener)
            }

            override fun request(n: Long) {
                if (n != Long.MAX_VALUE) throw RuntimeException("Backpressure signalling not supported.  Request Long.MAX_VALUE.")
                if (requested) return
                requested = true
                addOnAttachStateChangeListener(listener)
            }
        })
    }
}

internal fun LifecycleOwner.asPublisher(): Publisher<Lifecycle.Event> {
    return Publisher { subscriber ->
        var requested = false
        val observer = GenericLifecycleObserver { _, event -> subscriber.onNext(event) }

        subscriber.onSubscribe(object : Subscription {
            override fun cancel() {
                this@asPublisher.lifecycle.removeObserver(observer)
            }

            override fun request(n: Long) {
                if (n != Long.MAX_VALUE) throw RuntimeException("Backpressure signalling not supported.  Request Long.MAX_VALUE.")
                if (requested) return
                requested = true
                this@asPublisher.lifecycle.addObserver(observer)
            }
        })
    }
}

/**
 * Returns a [Publisher] that is unsubscribed from [this] when the given [View] is detached.
 */
internal fun <T> Publisher<T>.androidLifecycleDispose(view: View): Publisher<T> {
    return this.takeUntil(
        view.attachEvents().filter { it is ViewEvent.Detach }
    )
}

/**
 * Returns a [Publisher] that is unsubscribed from [this] when the given [LifecycleOwner] (Fragment
 * or Activity) goes out-of-lifecycle.
 */
internal fun <T> Publisher<T>.androidLifecycleDispose(lifecycleOwner: LifecycleOwner): Publisher<T> {
    return this.takeUntil(
        lifecycleOwner.asPublisher().filter { it == Lifecycle.Event.ON_DESTROY }
    )
}