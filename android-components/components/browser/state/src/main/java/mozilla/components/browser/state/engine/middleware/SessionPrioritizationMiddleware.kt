/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.browser.state.engine.middleware

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.annotation.VisibleForTesting
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.selector.findTab
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.EngineSession.SessionPriority.DEFAULT
import mozilla.components.concept.engine.EngineSession.SessionPriority.HIGH
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.MiddlewareContext
import mozilla.components.support.base.coroutines.Dispatchers as MozillaDispatchers
import mozilla.components.support.base.log.logger.Logger

/**
 * [Middleware] implementation responsible for updating the priority of the selected [EngineSession]
 * to [HIGH] and the rest to [DEFAULT].
 */
class SessionPrioritizationMiddleware (
    private val clearAfterMillis: Long = 15000,
    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main),
    private val waitScope: CoroutineScope = CoroutineScope(MozillaDispatchers.Cached),
    ): Middleware<BrowserState, BrowserAction> {
    private val logger = Logger("SessionPrioritizationMiddleware")
    private var clearJob: Job? = null

    @VisibleForTesting
    internal var previousHighestPriorityTabId = ""

    override fun invoke(
        context: MiddlewareContext<BrowserState, BrowserAction>,
        next: (BrowserAction) -> Unit,
        action: BrowserAction,
    ) {
        when (action) {
            is EngineAction.UnlinkEngineSessionAction -> {
                val activeTab = context.state.findTab(action.tabId)
                activeTab?.engineState?.engineSession?.updateSessionPriority(DEFAULT)
                logger.info("Update the tab ${activeTab?.id} priority to ${DEFAULT.name}")
            }
            else -> {
                // no-op
            }
        }

        next(action)

        when (action) {
            is TabListAction,
            is EngineAction.LinkEngineSessionAction,
            -> {
                val state = context.state
                if (previousHighestPriorityTabId != state.selectedTabId) {
                    updatePriorityIfNeeded(state)
                }
            }
            is ContentAction.CheckForFormDataAction -> {
                val tab = context.state.findTab(action.tabId)
                if (action.containsFormData) {
                    tab?.engineState?.engineSession?.updateSessionPriority(HIGH)
                    logger.info("Update the tab ${tab?.id} priority to ${HIGH.name}")
                    clearHighPriority(context, tab!!.id)

                } else {
                    tab?.engineState?.engineSession?.updateSessionPriority(DEFAULT)
                    logger.info("Update the tab ${tab?.id} priority to ${DEFAULT.name}")
                }
            }
            is ContentAction.ClearHighPrioritySessionAction -> {
                logger.info("todocathy 3 ClearHighPrioritySessionAction")
                val tab = context.state.findTab(action.tabId)
                tab?.engineState?.engineSession?.updateSessionPriority(DEFAULT)
                logger.info("Update the previous high priority tab ${tab?.id} priority back to ${DEFAULT.name}")
            }
            else -> {
                // no-op
            }
        }
    }

    private fun updatePriorityIfNeeded(state: BrowserState) = mainScope.launch {
        val currentSelectedTab = state.selectedTabId?.let { state.findTab(it) }
        val previousSelectedTab = state.findTab(previousHighestPriorityTabId)
        val currentEngineSession: EngineSession? = currentSelectedTab?.engineState?.engineSession

        // We need to make sure we alter the previousHighestPriorityTabId, after the session is linked.
        // So we update the priority on the engine session, as we could get actions where the tab
        // is selected but not linked yet, causing out sync issues,
        // when previousHighestPriorityTabId didn't call updateSessionPriority()
        if (currentEngineSession != null) {
            // check for existing form data here and only set DEFAULT if there is
            previousSelectedTab?.engineState?.engineSession?.checkForFormData()

            currentEngineSession.updateSessionPriority(HIGH)
            logger.info("Update the currentSelectedTab ${currentSelectedTab.id} priority to ${HIGH.name}")
            previousHighestPriorityTabId = currentSelectedTab.id
        }
    }

    private fun clearHighPriority(context: MiddlewareContext<BrowserState, BrowserAction>, tabId: String) {
        logger.info("todocathy 1 clearHighPriority, currentJob: $clearJob?")
        clearJob?.cancel()

        val store = context.store
        clearJob = waitScope.launch {
            delay(clearAfterMillis)
            logger.info("todocathy 2 clearHighPriority dispatching after $clearAfterMillis")
            store.dispatch(ContentAction.ClearHighPrioritySessionAction(tabId))
        }
    }

}
