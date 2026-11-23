package com.example.features.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.datastore.UserPreferences
import com.example.core.notifications.ScheduleCalculator
import com.example.core.permissions.PermissionHelper
import com.example.features.home.HomeUiState
import com.example.ui.components.RRBrandMark
import com.example.ui.components.RRCard
import com.example.ui.components.RRMetricBadge
import com.example.ui.components.RRStatusPill
import com.example.ui.theme.AccentPreset
import com.example.ui.theme.LocalRRExtraColors
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.TabularClockMedium
import com.example.ui.theme.ThemeMode

@Composable
fun SplashScreen() {
    val extra = LocalRRExtraColors.current
    val scale = remember { Animatable(0.82f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(1f, animationSpec = tween(550))
    }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, animationSpec = tween(650))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NavyDeep,
                        extra.accentPrimary.copy(alpha = 0.20f),
                        NavyDeep,
                    ),
                ),
            )
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .scale(scale.value)
                .alpha(alpha.value)
                .padding(32.dp),
        ) {
            RRBrandMark(size = 88.dp)
            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = "RR Reminder",
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your Time. Your Control.",
                style = MaterialTheme.typography.titleMedium,
                color = extra.accentPrimary,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Founded by Ridam Raj",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFB8C7EA),
            )
        }
    }
}

@Composable
fun WelcomePreviewScreen(
    homeState: HomeUiState,
    preferences: UserPreferences,
    isFirstLaunch: Boolean,
    onContinue: () -> Unit,
) {
    val extra = LocalRRExtraColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        extra.accentPrimary.copy(alpha = 0.12f),
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            )
            .safeDrawingPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RRBrandMark(size = 44.dp)
                    Column {
                        Text(
                            text = "RR Reminder",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "Your Time. Your Control.",
                            style = MaterialTheme.typography.labelMedium,
                            color = extra.accentPrimary,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                RRStatusPill(
                    text = "Founder: Ridam Raj",
                    color = extra.accentSecondary,
                    icon = Icons.Rounded.Verified,
                )
                Spacer(modifier = Modifier.height(18.dp))
                Image(
                    painter = painterResource(id = R.drawable.img_rr_onboarding_art),
                    contentDescription = "RR Reminder welcome preview",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = if (isFirstLaunch) "Welcome to RR Reminder" else "Welcome Back",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = ScheduleCalculator.formatTime(homeState.nowMillis, preferences.use24Hour) +
                        " • " +
                        ScheduleCalculator.formatDate(homeState.nowMillis, "EEEE, ${preferences.dateFormatStyle.pattern}"),
                    style = TabularClockMedium.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                RRCard(accentGlow = true) {
                    Text("Today's Preview", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RRMetricBadge(
                            label = "Reminders",
                            value = "${homeState.upcomingReminders.size}",
                            color = extra.accentPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        RRMetricBadge(
                            label = "Tasks",
                            value = "${homeState.todayTasks.size}",
                            color = extra.accentSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        RRMetricBadge(
                            label = "Habits",
                            value = "${homeState.activeHabitsCount}",
                            color = extra.success,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    val nextReminderTitle = homeState.upcomingReminders.firstOrNull()?.title
                    Text(
                        text = if (nextReminderTitle != null) {
                            "Next up: $nextReminderTitle"
                        } else {
                            "Your schedule is clear and ready for today."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("welcome_preview_continue_btn"),
            ) {
                Text(if (isFirstLaunch) "Set Up RR Reminder" else "Enter Dashboard")
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
            }
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

    var notifGranted by remember { mutableStateOf(PermissionHelper.hasNotificationPermission(context)) }
    var locationGranted by remember { mutableStateOf(PermissionHelper.hasCoarseLocationPermission(context)) }
    var exactAlarmGranted by remember { mutableStateOf(PermissionHelper.canScheduleExactAlarms(context)) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notifGranted = granted }

    val locLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> locationGranted = granted }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .align(Alignment.Center)
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top progress indicator
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

            // Step Indicators
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
                            locLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
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
                        "Your reminders, tasks, notes, and habits stay safely on your device with optional encrypted-ready JSON backup.",
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
            title = "Approximate Weather Location (Optional)",
            explanation = "Used only when you tap 'Use Location' for Weather or opt into Location Reminders. You can always search cities manually instead.",
            isGranted = locationGranted,
            actionLabel = "Allow Coarse Location",
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
            text = "Pick your preferred theme and accent color. Transitions stay smooth across every screen.",
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
