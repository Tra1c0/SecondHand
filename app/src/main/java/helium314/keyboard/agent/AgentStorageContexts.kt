// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.os.Build

/** Existing engine storage stays available during Direct Boot; secrets use the raw CE context. */
object AgentStorageContexts {
    @JvmStatic fun keyboard(context: Context): Context =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) context.createDeviceProtectedStorageContext() else context
}
