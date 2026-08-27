package com.hack.kidhack.feature.child.onboarding

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidButtonVariant
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.core.permission.KidhackPermissions
import com.hack.kidhack.feature.child.components.ChildScreenScaffold
import com.hack.kidhack.permissionRequester
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Single-screen permission list. Runtime permissions use [PermissionRequester]
 * (Activity-registered) so the system dialog is shown. Deny → tap 「허용하기」 again.
 */
@Composable
fun ChildPermissionWizard(
    onFinished: () -> Unit,
    @StringRes titleRes: Int = R.string.child_onboarding_title,
    @StringRes subtitleRes: Int = R.string.child_onboarding_subtitle,
    @StringRes continueRes: Int = R.string.child_onboarding_done,
    persistDone: Boolean = true,
    viewModel: ChildOnboardingViewModel = koinViewModel { parametersOf(persistDone) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = LocalKidhackColors.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissions = permissionRequester()

    var grantTick by remember { mutableIntStateOf(0) }
    /** Simple one-button alert (e.g. need foreground location first). */
    var alertMessage by remember { mutableStateOf<String?>(null) }
    /** Confirm then open background-location settings / permission page. */
    var pendingBackgroundSettings by remember { mutableStateOf(false) }

    fun refreshGrants() {
        grantTick++
    }

    @Suppress("UNUSED_EXPRESSION")
    grantTick

    fun isGranted(item: OnboardingStep): Boolean = when (item.kind) {
        OnboardingPermissionKind.RuntimeMulti -> KidhackPermissions.hasLocation(context)
        OnboardingPermissionKind.RuntimeSingle -> {
            val permission = item.runtimePermission ?: return true
            KidhackPermissions.isGranted(context, permission)
        }
        OnboardingPermissionKind.SettingsUsage -> KidhackPermissions.hasUsageStats(context)
        OnboardingPermissionKind.SettingsOverlay -> KidhackPermissions.hasOverlay(context)
        OnboardingPermissionKind.SettingsAccessibility -> KidhackPermissions.hasAccessibility(context)
        OnboardingPermissionKind.MediaProjection -> KidhackPermissions.hasScreenCaptureConsent(context)
        OnboardingPermissionKind.SettingsAppDetails -> false
    }

    fun afterRuntimeAttempt(item: OnboardingStep) {
        refreshGrants()
        if (isGranted(item)) {
            when (item.kind) {
                OnboardingPermissionKind.RuntimeMulti -> {
                    KidhackPermissions.clearDenyHistory(
                        context,
                        KidhackPermissions.locationCoarse,
                        KidhackPermissions.locationFine,
                    )
                }
                OnboardingPermissionKind.RuntimeSingle -> {
                    item.runtimePermission?.let {
                        KidhackPermissions.clearDenyHistory(context, it)
                    }
                }
                else -> Unit
            }
            viewModel.clearError()
        } else {
            when (item.kind) {
                OnboardingPermissionKind.RuntimeMulti -> {
                    KidhackPermissions.recordDeny(
                        context,
                        KidhackPermissions.locationCoarse,
                        KidhackPermissions.locationFine,
                    )
                }
                OnboardingPermissionKind.RuntimeSingle -> {
                    item.runtimePermission?.let {
                        KidhackPermissions.recordDeny(context, it)
                    }
                }
                else -> Unit
            }
            if (item.required) viewModel.denyOrMissingRequired()
        }
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        refreshGrants()
        viewModel.clearError()
    }

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        KidhackPermissions.markScreenCaptureConsent(
            context,
            result.resultCode == Activity.RESULT_OK,
        )
        refreshGrants()
        state.items.firstOrNull { it.kind == OnboardingPermissionKind.MediaProjection }
            ?.let { afterRuntimeAttempt(it) }
    }

    LaunchedEffect(state.finished) {
        if (state.finished) onFinished()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshGrants()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val allRequiredGranted = KidhackPermissions.hasAllRequiredForChildControl(context)
    val activity = KidhackPermissions.findActivity(context)

    fun openPermissionSettings() {
        viewModel.notifyPermissionBlocked()
        runCatching {
            settingsLauncher.launch(KidhackPermissions.appDetailsSettingsIntent(context))
        }
    }

    fun requestForegroundLocation(item: OnboardingStep) {
        if (KidhackPermissions.hasLocation(context)) {
            refreshGrants()
            return
        }
        viewModel.clearError()
        val act = activity
        val needed = KidhackPermissions.locationPermissionsToRequest(context)
        if (act != null &&
            needed.isNotEmpty() &&
            KidhackPermissions.shouldOpenSettingsForAny(act, *needed)
        ) {
            openPermissionSettings()
            return
        }
        permissions.request(
            KidhackPermissions.locationCoarse,
            KidhackPermissions.locationFine,
        ) { result ->
            val fineOk = result[KidhackPermissions.locationFine] == true ||
                KidhackPermissions.hasLocation(context)
            if (fineOk) {
                afterRuntimeAttempt(item)
            } else {
                val coarseOk = result[KidhackPermissions.locationCoarse] == true ||
                    KidhackPermissions.isGranted(context, KidhackPermissions.locationCoarse)
                if (coarseOk && !KidhackPermissions.hasLocation(context)) {
                    if (act != null &&
                        KidhackPermissions.shouldOpenSettingsForPermission(
                            act,
                            KidhackPermissions.locationFine,
                        )
                    ) {
                        openPermissionSettings()
                    } else {
                        permissions.requestOne(KidhackPermissions.locationFine) {
                            afterRuntimeAttempt(item)
                        }
                    }
                } else {
                    afterRuntimeAttempt(item)
                }
            }
        }
    }

    fun requestItem(item: OnboardingStep) {
        viewModel.clearError()
        when (item.kind) {
            OnboardingPermissionKind.RuntimeMulti -> requestForegroundLocation(item)
            OnboardingPermissionKind.RuntimeSingle -> {
                val permission = item.runtimePermission
                if (permission.isNullOrBlank()) return
                if (KidhackPermissions.isGranted(context, permission)) {
                    refreshGrants()
                    return
                }
                if (permission == KidhackPermissions.backgroundLocation &&
                    !KidhackPermissions.hasLocation(context)
                ) {
                    alertMessage =
                        context.getString(R.string.child_onboarding_bg_need_location_body)
                    return
                }
                // API 30+: requestPermissions(BACKGROUND) opens this app's Location page.
                // Show a dialog first so the tap always gives visible feedback.
                if (permission == KidhackPermissions.backgroundLocation) {
                    pendingBackgroundSettings = true
                    return
                }
                val act = activity
                if (act != null &&
                    KidhackPermissions.shouldOpenSettingsForPermission(act, permission)
                ) {
                    openPermissionSettings()
                    return
                }
                permissions.requestOne(permission) {
                    afterRuntimeAttempt(item)
                }
            }
            OnboardingPermissionKind.SettingsUsage -> {
                runCatching {
                    settingsLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }.onFailure { viewModel.denyOrMissingRequired() }
            }
            OnboardingPermissionKind.SettingsOverlay -> {
                runCatching {
                    settingsLauncher.launch(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                }.onFailure { viewModel.denyOrMissingRequired() }
            }
            OnboardingPermissionKind.SettingsAccessibility -> {
                runCatching {
                    settingsLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }.onFailure { viewModel.denyOrMissingRequired() }
            }
            OnboardingPermissionKind.SettingsAppDetails -> {
                viewModel.clearError()
                runCatching {
                    settingsLauncher.launch(KidhackPermissions.appDetailsSettingsIntent(context))
                }.onFailure { viewModel.denyOrMissingRequired() }
            }
            OnboardingPermissionKind.MediaProjection -> {
                if (KidhackPermissions.hasScreenCaptureConsent(context)) {
                    refreshGrants()
                    return
                }
                val projectionManager =
                    context.getSystemService(MediaProjectionManager::class.java)
                if (projectionManager == null) {
                    viewModel.denyOrMissingRequired()
                    return
                }
                runCatching {
                    mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
                }.onFailure { viewModel.denyOrMissingRequired() }
            }
        }
    }

    val foregroundLocationGranted = KidhackPermissions.hasLocation(context)
    val backgroundItem = state.items.firstOrNull {
        it.runtimePermission == KidhackPermissions.backgroundLocation
    }

    fun openBackgroundLocationPage() {
        val permission = KidhackPermissions.backgroundLocation ?: return
        val item = backgroundItem ?: return
        KidhackPermissions.markAsked(context, permission)
        runCatching {
            permissions.requestOne(permission) { granted ->
                afterRuntimeAttempt(item)
                if (!granted && !KidhackPermissions.hasBackgroundLocation(context)) {
                    alertMessage =
                        context.getString(R.string.child_onboarding_bg_open_settings_body)
                }
            }
        }.onFailure {
            runCatching {
                settingsLauncher.launch(KidhackPermissions.appDetailsSettingsIntent(context))
            }
        }
    }

    if (alertMessage != null) {
        AlertDialog(
            onDismissRequest = { alertMessage = null },
            confirmButton = {
                TextButton(onClick = { alertMessage = null }) {
                    Text(stringResource(R.string.child_onboarding_alert_confirm))
                }
            },
            title = { Text(stringResource(R.string.child_onboarding_bg_need_location_title)) },
            text = { Text(alertMessage.orEmpty()) },
        )
    }

    if (pendingBackgroundSettings) {
        AlertDialog(
            onDismissRequest = { pendingBackgroundSettings = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingBackgroundSettings = false
                        openBackgroundLocationPage()
                    },
                ) {
                    Text(stringResource(R.string.child_onboarding_bg_open_settings_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingBackgroundSettings = false }) {
                    Text(stringResource(R.string.child_onboarding_alert_cancel))
                }
            },
            title = { Text(stringResource(R.string.child_onboarding_bg_open_settings_title)) },
            text = { Text(stringResource(R.string.child_onboarding_bg_open_settings_body)) },
        )
    }

    ChildScreenScaffold(
        header = {
            Text(
                text = stringResource(titleRes),
                color = Color.White,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBlack,
                fontSize = 24.sp,
            )
            Text(
                text = stringResource(subtitleRes),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
            )
        },
    ) {
        state.items.forEach { item ->
            val isBackgroundLocation =
                item.runtimePermission == KidhackPermissions.backgroundLocation
            val needsForegroundFirst =
                isBackgroundLocation && !foregroundLocationGranted && !isGranted(item)
            PermissionRow(
                item = item,
                granted = isGranted(item),
                // Keep clickable so the warning dialog can show (disabled buttons swallow taps).
                allowEnabled = true,
                lockedHint = if (needsForegroundFirst) {
                    stringResource(R.string.child_onboarding_bg_need_location_hint)
                } else {
                    null
                },
                onAllow = { requestItem(item) },
            )
        }

        if (state.error != null) {
            Text(state.error.orEmpty(), color = colors.danger, fontSize = 13.sp)
        }

        KidButton(
            text = stringResource(continueRes),
            onClick = {
                if (KidhackPermissions.hasAllRequiredForChildControl(context)) {
                    viewModel.finish()
                } else {
                    viewModel.denyOrMissingRequired()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = allRequiredGranted,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PermissionRow(
    item: OnboardingStep,
    granted: Boolean,
    onAllow: () -> Unit,
    allowEnabled: Boolean = true,
    lockedHint: String? = null,
) {
    val colors = LocalKidhackColors.current
    KidCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(item.emojiRes), fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(item.titleRes),
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 15.sp,
                        color = colors.text,
                    )
                    Text(
                        text = stringResource(
                            if (item.required) {
                                R.string.child_onboarding_required_badge
                            } else {
                                R.string.child_onboarding_optional_badge
                            },
                        ),
                        color = if (item.required) colors.danger else colors.muted,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 11.sp,
                    )
                }
                Text(
                    text = stringResource(item.bodyRes),
                    color = colors.muted,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontSemibold,
                    fontSize = 12.sp,
                )
                if (lockedHint != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = lockedHint,
                        color = colors.danger,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontSemibold,
                        fontSize = 12.sp,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            if (granted) {
                Text(
                    text = stringResource(R.string.child_onboarding_granted),
                    color = colors.brand,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.brandLight)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            } else {
                KidButton(
                    text = when (item.kind) {
                        OnboardingPermissionKind.SettingsUsage,
                        OnboardingPermissionKind.SettingsOverlay,
                        OnboardingPermissionKind.SettingsAccessibility,
                        OnboardingPermissionKind.SettingsAppDetails,
                        -> stringResource(R.string.child_onboarding_settings)
                        else -> stringResource(R.string.child_onboarding_allow)
                    },
                    onClick = onAllow,
                    variant = KidButtonVariant.Secondary,
                    enabled = allowEnabled,
                )
            }
        }
    }
}
