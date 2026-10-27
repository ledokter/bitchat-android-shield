package com.bitchat.android.hotspot

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.R
import com.bitchat.android.ui.theme.BitchatFontFamily
import kotlinx.coroutines.delay

/** Chat-screen strip shown while the hotspot runs: how long, how many downloads, and a stop button. */
@Composable
fun HotspotBanner() {
    val live by HotspotStatus.live.collectAsState()
    val l = live ?: return
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    val runningMin = ((now - l.since) / 60_000).toInt().coerceAtLeast(0)
    val leftMin = (((l.stopsAt - now).coerceAtLeast(0) + 59_999) / 60_000).toInt()
    Surface(
        color = Color(0xFFFF9500), contentColor = Color.Black, shape = RoundedCornerShape(12.dp),
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth().clickable {
            context.startActivity(
                Intent(context, HotspotActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
        }
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "📡 " + stringResource(R.string.hs_banner_title, formatDuration(runningMin)),
                    fontFamily = BitchatFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp
                )
                Text(
                    stringResource(R.string.hs_auto_stop_in, leftMin, l.downloads),
                    fontFamily = BitchatFontFamily, fontSize = 11.sp
                )
            }
            Button(
                onClick = { HotspotStatus.requestStop() },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White)
            ) { Text(stringResource(R.string.hs_stop_short), fontFamily = BitchatFontFamily) }
        }
    }
}

private fun formatDuration(minutes: Int): String =
    if (minutes < 60) "$minutes min" else "${minutes / 60} h ${"%02d".format(minutes % 60)}"
