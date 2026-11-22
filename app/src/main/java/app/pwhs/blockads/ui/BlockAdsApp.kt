package app.pwhs.blockads.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import app.pwhs.blockads.ui.data.HomeAppKey
import app.pwhs.blockads.ui.data.OnboardingKey
import app.pwhs.blockads.ui.data.SplashKey
import app.pwhs.blockads.ui.dialog.VPNConflictDialog
import app.pwhs.blockads.ui.firewall.prompt.FirewallPromptHost
import app.pwhs.blockads.ui.onboarding.OnboardingScreen
import app.pwhs.blockads.ui.splash.SplashScreen
import kotlinx.coroutines.launch


@Composable
fun BlockAdsApp(
    modifier: Modifier = Modifier,
    onRequestVpnPermission: () -> Unit,
    showVpnConflictDialog: Boolean = false,
    onDismissVpnConflictDialog: () -> Unit = {},
    onShowVpnConflictDialog: () -> Unit = {},
) {

    if (showVpnConflictDialog) {
        VPNConflictDialog(
            onDismissVpnConflictDialog = onDismissVpnConflictDialog,
        )
    }

    val backStack = rememberNavBackStack(SplashKey)
    val appPrefs: app.pwhs.blockads.data.datastore.AppPreferences = org.koin.compose.koinInject()
    val isLocked by appPrefs.lockdownEnabled.collectAsState(initial = false)
    val cooldownStart by appPrefs.cooldownStartTimestamp.collectAsState(initial = 0L)
    val duration by appPrefs.lockdownDuration.collectAsState(initial = 300000L)
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
        NavDisplay(
            backStack = backStack,
            onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
            modifier = modifier,
            entryProvider = entryProvider {
                entry<SplashKey> {
                    SplashScreen(
                        onNavigateToHome = {
                            backStack.add(HomeAppKey)
                            backStack.remove(SplashKey)
                        },
                        onNavigateToOnboarding = {
                            backStack.add(OnboardingKey)
                            backStack.remove(SplashKey)
                        }
                    )
                }
                entry<OnboardingKey> {
                    OnboardingScreen(
                        onNavigateToHome = {
                            backStack.add(HomeAppKey)
                            backStack.remove(OnboardingKey)
                        }
                    )
                }
                entry<HomeAppKey> {
                    FirewallPromptHost()
                    HomeApp(
                        onRequestVpnPermission = onRequestVpnPermission,
                        onShowVpnConflictDialog = onShowVpnConflictDialog
                    )
                }

            }
        )

        if (isLocked) {
            LockdownScreen(
                cooldownStart = cooldownStart,
                duration = duration,
                onStartCooldown = { timestamp ->
                    coroutineScope.launch {
                        appPrefs.setLastActiveBaselines(timestamp, android.os.SystemClock.elapsedRealtime())
                        appPrefs.setCooldownStartTimestamp(timestamp)
                    }
                },
                onCancelCooldown = {
                    coroutineScope.launch { appPrefs.setCooldownStartTimestamp(0L) }
                },
                onUnlockComplete = {
                    coroutineScope.launch {
                        appPrefs.setLockdownEnabled(false)
                        appPrefs.setCooldownStartTimestamp(0L)
                    }
                },
                onTimeTamperingDetected = {
                    coroutineScope.launch {
                        appPrefs.setCooldownStartTimestamp(0L)
                    }
                }
            )
        }
    }
}
