// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.content.pm.ProviderInfo
import androidx.core.content.FileProvider

/** File roots must match the engine components that create the shared files. */
open class AgentFileProvider : FileProvider() {
    override fun attachInfo(context: Context, info: ProviderInfo) {
        super.attachInfo(AgentStorageContexts.keyboard(context), info)
    }
}
