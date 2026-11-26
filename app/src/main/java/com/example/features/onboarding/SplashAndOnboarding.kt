package com.example.features.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.core.datastore.PrimaryClockZone
import com.example.core.datastore.UserPreferences
import com.example.core.network.NetworkTimeSyncStatus
import com.example.core.notifications.ScheduleCalculator
import com.example.core.permissions.PermissionHelper
import com.example.ui.components.RRBackgroundContainer
import com.example.ui.components.RRBrandMark
import com.example.ui.components.RRCard
import com.example.ui.components.RRGradientButton
import com.example.ui.components.RRStatusPill
import com.example.ui.theme.AccentPreset
import com.example.ui.theme.LocalRRExtraColors
import com.example.ui.theme.TabularClockMedium
import com.example.ui.theme.ThemeMode

@Composable
private fun AuroraWelcomeWaveBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF040917),
                    Color(0xFF081430),
                    Color(0xFF0A1D47),
                    Color(0xFF050B1C),
                ),
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x4438BDF8), Color.Transparent),
                center = Offset(w * 0.5f, h * 0.30f),
                radius = w * 0.72f,
            ),
        )
        val magentaWave = Path().apply {
            moveTo(0f, h * 0.74f)
            cubicTo(w * 0.25f, h * 0.62f, w * 0.60f, h * 0.84f, w, h * 0.68f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            path = magentaWave,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color(0x552563EB),
                    Color(0x667C3AED),
                    Color(0x55EC4899),
                ),
            ),
        )
        val cyanWave = Path().apply {
            moveTo(0f, h * 0.78f)
            cubicTo(w * 0.35f, h * 0.88f, w * 0.68f, h * 0.64f, w, h * 0.76f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            path = cyanWave,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color(0x6606B6D4),
                    Color(0x553B82F6),
                    Color(0x440F172A),
                ),
            ),
        )
    }
}

@Composable
fun SplashScreen() {
    val scale = remember { Animatable(0.84f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(1f, animationSpec = tween(550))
    }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, animationSpec = tween(650))
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        AuroraWelcomeWaveBackdrop(modifier = Modifier.fillMaxSize())
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .safeDrawingPadding()
                .scale(scale.value)
                .alpha(alpha.value)
                .padding(32.dp),
        ) {
            RRBrandMark(size = 108.dp)
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "RR Reminder",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic,
                ),
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Smart Time • Smart Life",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF93C5FD),
            )
            Spacer(modifier = Modifier.height(14.dp))
            RRStatusPill(
                text = "Version 2.0 • Ridam Raj",
                color = Color(0xFF38BDF8),
                icon = Icons.Rounded.Verified,
            )
        }
    }
}

@Composable
fun WelcomePreviewScreen(
    nowMillis: Long,
    preferences: UserPreferences,
    isFirstLaunch: Boolean,
    timeSyncStatus: NetworkTimeSyncStatus = NetworkTimeSyncStatus(),
    onContinue: () -> Unit,
) {
    val primaryZone = remember(preferences.primaryClockZone) {
        preferences.primaryClockZone.toZoneId()
    }
    val primaryTime = ScheduleCalculator.formatTime(
        millis = nowMillis,
        use24Hour = preferences.use24Hour,
        zoneId = primaryZone,
        includeSeconds = true,
    )
    val primaryDate = ScheduleCalculator.formatDate(
        millis = nowMillis,
        pattern = "EEE, ${preferences.dateFormatStyle.pattern}",
        zoneId = primaryZone,
    )
    val syncLabel = when {
        timeSyncStatus.ownerOverrideActive && !timeSyncStatus.ownerOverrideLabel.isNullOrBlank() ->
            timeSyncStatus.ownerOverrideLabel
        timeSyncStatus.isSynced -> timeSyncStatus.sourceLabel
        timeSyncStatus.isSyncing -> "Syncing Google Time"
        else -> "Live Device Clock"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AuroraWelcomeWaveBackdrop(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .widthIn(max = 560.dp)
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(18.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                RRBrandMark(size = 116.dp)

                Spacer(modifier = Modifier.height(26.dp))

                Text(
                    text = "RR Reminder",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontStyle = FontStyle.Italic,
                    ),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Smart Time • Smart Life",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                    color = Color(0xFFBBD6FF),
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(26.dp))

                Text(
                    text = "Plan  •  Organize  •  Achieve",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                    color = Color(0xFF7DD3FC),
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(22.dp))

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0x880B1A3B),
                    border = BorderStroke(1.dp, Color(0x5538BDF8)),
                    modifier = Modifier.testTag("welcome_india_time_pill"),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = primaryTime,
                            style = TabularClockMedium.copy(
                                fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = Color.White,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$primaryDate • $syncLabel",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF93C5FD),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp),
            ) {
                RRGradientButton(
                    text = if (isFirstLaunch) "Get Started" else "Get Started",
                    onClick = onContinue,
                    trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward,
                    colors = listOf(Color(0xFF1D4ED8), Color(0xFF38BDF8)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("welcome_preview_continue_btn"),
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 18.dp, height = 6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF38BDF8)),
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.35f)),
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.35f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeFeatureHighlightRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun OnboardingScreen(
    themeMode: ThemeMode,
    accentPreset: AccentPreset,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSelectAccent: (AccentPreset) -> Unit,
    onFinish: () -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var notifGranted by remember { mutableStateOf(PermissionHelper.hasNotificationPermission(context)) }
    var locationGranted by remember { mutableStateOf(PermissionHelper.hasAnyLocationPermission(context)) }
    var exactAlarmGranted by remember { mutableStateOf(PermissionHelper.canScheduleExactAlarms(context)) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notifGranted = PermissionHelper.hasNotificationPermission(context)
                locationGranted = PermissionHelper.hasAnyLocationPermission(context)
                exactAlarmGranted = PermissionHelper.canScheduleExactAlarms(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notifGranted = granted }

    val locLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        locationGranted = grants.values.any { it } || PermissionHelper.hasAnyLocationPermission(context)
    }

    RRBackgroundContainer {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .widthIn(max = 600.dp)
                .align(Alignment.Center)
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RRBrandMark(size = 36.dp)
                    Text(
                        text = "RR Reminder",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                TextButton(
                    onClick = onFinish,
                    modifier = Modifier.testTag("onboarding_skip_btn"),
                ) {
                    Text("Skip")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(3) { idx ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(
                                if (idx <= step) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                            ),
                    )
                }
            }

            AnimatedContent(
                targetState = step,
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                label = "onboardingStep",
            ) { currentStep ->
                when (currentStep) {
                    0 -> OnboardingWelcomePage()
                    1 -> OnboardingPermissionsPage(
                        notifGranted = notifGranted,
                        exactAlarmGranted = exactAlarmGranted,
                        locationGranted = locationGranted,
                        onRequestNotif = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onRequestExactAlarm = {
                            PermissionHelper.openExactAlarmSettings(context)
                            exactAlarmGranted = PermissionHelper.canScheduleExactAlarms(context)
                        },
                        onRequestLocation = {
                            locLauncher.launch(PermissionHelper.LOCATION_PERMISSIONS)
                        },
                    )
                    else -> OnboardingThemePage(
                        themeMode = themeMode,
                        accentPreset = accentPreset,
                        onSelectThemeMode = onSelectThemeMode,
                        onSelectAccent = onSelectAccent,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (step > 0) {
                    OutlinedButton(
                        onClick = { step-- },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("onboarding_back_btn"),
                    ) {
                        Text("Back")
                    }
                }
                Button(
                    onClick = {
                        if (step < 2) step++ else onFinish()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("onboarding_next_btn"),
                ) {
                    Text(if (step < 2) "Continue" else "Get Started")
                }
            }
        }
    }
}

@Composable
private fun OnboardingWelcomePage() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_rr_onboarding_art),
            contentDescription = "RR Reminder productivity artwork",
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(24.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(modifier = Modifier.height(22.dp))
        Text(
            text = "Your Time.\nYour Control.",
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Founded by Ridam Raj",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "A local-first productivity command center combining reliable reminders, exact alarms, live weather, world clock, precision timer & stopwatch, tasks, notes, habits, and focus routines.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(18.dp))
        RRCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text("Private & Offline-First", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Your reminders, tasks, notes, and habits stay safely on your device with optional local JSON backup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPermissionsPage(
    notifGranted: Boolean,
    exactAlarmGranted: Boolean,
    locationGranted: Boolean,
    onRequestNotif: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onRequestLocation: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Permissions & Reliability",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "RR Reminder only asks for what each feature genuinely needs. You can enable or change these anytime in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PermissionCard(
            icon = Icons.Rounded.NotificationsActive,
            title = "Notifications",
            explanation = "Required to display reminders, timer completions, and alarm alerts.",
            isGranted = notifGranted,
            actionLabel = "Allow Notifications",
            onAction = onRequestNotif,
            testTag = "perm_notif_btn",
        )

        PermissionCard(
            icon = Icons.Rounded.AlarmOn,
            title = "Exact Alarms",
            explanation = "Allows Android to wake on time for alarms and precision countdowns. Note: battery savers on some devices may still delay background work.",
            isGranted = exactAlarmGranted,
            actionLabel = "Check Exact Alarm",
            onAction = onRequestExactAlarm,
            testTag = "perm_alarm_btn",
        )

        PermissionCard(
            icon = Icons.Rounded.MyLocation,
            title = "Real-Time GPS Weather & Location (Optional)",
            explanation = "Used to fetch live Open-Meteo forecasts for your current GPS location or opt-in Location Reminders. You can always search cities manually instead.",
            isGranted = locationGranted,
            actionLabel = "Allow GPS Location",
            onAction = onRequestLocation,
            testTag = "perm_loc_btn",
        )
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    explanation: String,
    isGranted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    testTag: String,
) {
    val extra = LocalRRExtraColors.current
    RRCard {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGranted) extra.success else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (isGranted) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = "Granted",
                            tint = extra.success,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!isGranted) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onAction,
                        modifier = Modifier.testTag(testTag),
                    ) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingThemePage(
    themeMode: ThemeMode,
    accentPreset: AccentPreset,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSelectAccent: (AccentPreset) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Choose Your Look",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Pick your preferred theme and Aurora accent color. Transitions stay smooth across every screen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        RRCard {
            Text("Appearance Mode", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeMode.entries.forEach { mode ->
                    val selected = mode == themeMode
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectThemeMode(mode) },
                        label = { Text(mode.label.replace(" Mode", "").replace(" Default", "")) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        RRCard {
            Text("Accent Color", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AccentPreset.entries.forEach { preset ->
                    val selected = preset == accentPreset
                    Surface(
                        shape = CircleShape,
                        color = preset.primaryDark,
                        border = if (selected) {
                            BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface)
                        } else {
                            null
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { onSelectAccent(preset) },
                    ) {
                        if (selected) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = preset.label,
                                    tint = Color.White,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
