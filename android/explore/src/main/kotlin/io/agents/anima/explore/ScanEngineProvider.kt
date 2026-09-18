package io.agents.anima.explore

import io.agents.anima.core.PackRepository
import io.agents.anima.core.ScreenIdentifier
import io.agents.anima.core.ScreenUnderstander
import io.agents.anima.core.StableIdEngine

/**
 * A manual service locator, not DI magic. Android instantiates [ScanService]
 * itself, so constructor injection isn't available, and :explore cannot see
 * the concrete ScreenUnderstander (:understand) or PackRepository (:store)
 * implementations -- only their :core interfaces. Whoever composes the real
 * app (:app's Application class, or wherever the composition root ends up)
 * must set these before calling ScanService.start(). ScreenIdentifier defaults
 * to StableIdEngine since that's a real, available implementation in :core.
 *
 * If understander or repository are unset when a scan is requested, the
 * service fails fast with a clear log message and stops itself rather than
 * crashing or silently doing nothing -- see ScanService.onStartCommand.
 */
object ScanEngineProvider {
    var identifier: ScreenIdentifier = StableIdEngine()
    var understander: ScreenUnderstander? = null
    var repository: PackRepository? = null
}
