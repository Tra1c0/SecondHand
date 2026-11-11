// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.net.Uri
import helium314.keyboard.agent.security.readBounded
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Only user-picked SAF documents, with byte limits before parsing or truncating output. */
object AgentDocumentIO {
    fun readJson(context: Context, uri: Uri, limit: Int = 16_384): String {
        require(uri.scheme == "content" && limit in 1..1_048_576)
        val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { it.readBounded(limit) }
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
    }

    fun writeJson(context: Context, uri: Uri, encoded: String, limit: Int = 16_384) {
        require(uri.scheme == "content" && limit in 1..1_048_576)
        val bytes = encoded.toByteArray(Charsets.UTF_8)
        require(bytes.size <= limit)
        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use {
            it.write(bytes)
            it.flush()
        }
    }
}
