package id.cognify.notch

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private var running by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        setContent { Home(running, ::toggle) }
    }

    private fun toggle() {
        if (running) {
            stopService(Intent(this, OverlayService::class.java))
            running = false
        } else if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } else {
            startForegroundService(Intent(this, OverlayService::class.java))
            running = true
        }
    }
}

@Composable
private fun Home(running: Boolean, onToggle: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var hint by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(Color(0xFF111111)).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        T("Cognify Notch", color = Color.White, fontSize = 28.sp)
        Box(
            Modifier.padding(top = 24.dp).background(Color.White, RoundedCornerShape(50)).clickable {
                hint = !android.provider.Settings.canDrawOverlays(ctx) && !running
                onToggle()
            }.padding(horizontal = 28.dp, vertical = 14.dp)
        ) { T(stringResource(if (running) R.string.stop else R.string.start), color = Color.Black, fontSize = 16.sp) }
        if (hint) T(stringResource(R.string.need_overlay), color = Color(0xFFAAAAAA), fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp))
    }
}
