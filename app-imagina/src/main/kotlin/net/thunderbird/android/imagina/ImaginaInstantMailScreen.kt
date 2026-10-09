package net.thunderbird.android.imagina

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import net.thunderbird.android.R
import net.thunderbird.components.ui.bolt.atom.button.ButtonFilled
import net.thunderbird.components.ui.bolt.atom.button.ButtonText
import net.thunderbird.components.ui.bolt.atom.text.TextBodyLarge
import net.thunderbird.components.ui.bolt.atom.text.TextHeadlineMedium
import net.thunderbird.components.ui.bolt.template.Scaffold
import net.thunderbird.components.ui.bolt.theme.BoltTheme

/**
 * Whether the app may wake itself at an exact time. Thunderbird keeps the IMAP IDLE connection of each inbox alive
 * with exact alarms and turns push off without them; since Android 14 the person has to allow them («Alarmas y
 * recordatorios») for an app like this one.
 */
fun canReceiveMailAtOnce(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

/**
 * After the permissions step of the onboarding: asks the person to allow exact alarms, so new mail arrives at once
 * instead of every 15 minutes. Goes on by itself when the permission is there (also when coming back from Settings)
 * and lets the person skip it.
 */
@Composable
fun ImaginaInstantMailScreen(onNext: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Also on the first composition: an observer added to a resumed screen gets ON_RESUME at once
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (canReceiveMailAtOnce(context)) onNext()
    }

    Scaffold(modifier = modifier) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .safeContentPadding()
                .padding(horizontal = BoltTheme.spacings.quadruple),
            verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.triple, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_imagina_mark),
                contentDescription = null,
                modifier = Modifier.size(LOGO_SIZE_DP.dp),
            )
            TextHeadlineMedium(text = stringResource(R.string.imagina_instant_mail_title), textAlign = TextAlign.Center)
            TextBodyLarge(text = stringResource(R.string.imagina_instant_mail_description), textAlign = TextAlign.Center)
            ButtonFilled(
                text = stringResource(R.string.imagina_instant_mail_allow),
                onClick = { context.startActivity(exactAlarmSettings(context)) },
                modifier = Modifier.fillMaxWidth(),
            )
            ButtonText(text = stringResource(R.string.imagina_reconnect_dismiss), onClick = onNext)
        }
    }
}

/** The page of Settings where the person allows this app's alarms. */
private fun exactAlarmSettings(context: Context): Intent =
    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))

private const val LOGO_SIZE_DP = 88
