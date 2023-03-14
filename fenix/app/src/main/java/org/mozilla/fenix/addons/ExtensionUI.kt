/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.addons

import android.content.Context
import android.widget.Toast
import mozilla.components.concept.engine.webextension.WebExtension
import mozilla.components.support.base.log.logger.Logger

class ExtensionUI(
    private val applicationContext: Context,
) {
    private val logger = Logger("ExtensionUI")

    fun onInstallPermissionRequest(extension: WebExtension): Boolean {
        logger.info("onInstallPermissionRequest: ${extension.id}")

        Toast.makeText(
            applicationContext,
            "In theory, you should see a list of permissions and either continue or cancel the install process of ${extension.getMetadata()?.name} but @willdurand does not know how to show a popup so here is a nice Toast instead...",
            Toast.LENGTH_LONG,
        ).show()

        return true
    }
}
