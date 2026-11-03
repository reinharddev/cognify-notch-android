package id.cognify.notch

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Layar kecil untuk mengetik tugas. Overlay tidak bisa menerima keyboard, jadi pengetikan dilakukan di sini. */
class AddTaskActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContent { Form(onDone = { finish() }) }
    }
}

@Composable
private fun Form(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val due = remember(text) { DueParser.parseDue(text) }
    fun submit() { if (text.isNotBlank()) TaskStore.add(ctx, text); onDone() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000)).clickable(MutableInteractionSource(), null) { onDone() },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.padding(top = 96.dp).width(420.dp).background(Color.Black, RoundedCornerShape(20.dp))
                .clickable(MutableInteractionSource(), null) {}.padding(20.dp),
        ) {
            T(stringResource(R.string.add_task), Color.White, 16.sp)
            BasicTextField(
                value = text, onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp).focusRequester(focus),
                textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                singleLine = true,
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) T(stringResource(R.string.add_hint), Color(0x66FFFFFF), 17.sp)
                        inner()
                    }
                },
            )
            T(
                due?.let { stringResource(R.string.will_remind, dueLabel(ctx, it)) } ?: stringResource(R.string.no_reminder),
                if (due != null) Color(0xFFFF9F0A) else Color(0x66FFFFFF), 12.sp, Modifier.padding(top = 10.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                T(stringResource(R.string.cancel), Color(0x99FFFFFF), 14.sp, Modifier.clickable { onDone() }.padding(horizontal = 14.dp, vertical = 8.dp))
                T(
                    stringResource(R.string.add), Color.Black, 14.sp,
                    Modifier.background(Color.White, RoundedCornerShape(50)).clickable { submit() }.padding(horizontal = 18.dp, vertical = 8.dp),
                )
            }
        }
    }
}
