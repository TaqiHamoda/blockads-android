# Device Owner Implementation Retrospective

## Overview
This document serves as a retrospective for the implementation of the **Device Owner (DO) Mode** and **VPN Lockdown Mode**. This feature provides an unbreakable impulse control mechanism by elevating the app to a Device Policy Controller (DPC).

## Feature Details
1. **Always-On VPN Enforcement**: Ensures the VPN cannot be bypassed or removed by the user in Android settings.
2. **System Restrictions**: Applies various `UserManager` restrictions:
   - `DISALLOW_CONFIG_VPN`: Prevents the user from modifying VPN settings.
   - `DISALLOW_APPS_CONTROL`: Prevents clearing app data or force-stopping background processes.
   - `DISALLOW_DEBUGGING_FEATURES`: Closes the ADB/USB Debugging bypass vector.
   - `DISALLOW_UNINSTALL_APPS`: Disables application uninstallation for added friction.
3. **Guardrailed UI Visibility**: Removes programmatic deprovisioning to prevent any UI-based bypass mechanisms during a lockdown cooldown. Advanced users must use ADB to remove the Device Owner profile.

## Challenges & Solutions

### 1. Order of Operations for VPN Lockdown
**Issue**: When the restrictions were enabled, the app kicked itself off the VPN list, effectively disabling the VPN on certain OEM ROMs.
**Root Cause**: The original code applied the `UserManager.DISALLOW_CONFIG_VPN` restriction *before* promoting the VPN to an Always-On state via `setAlwaysOnVpnPackage`. Android's framework responds to `DISALLOW_CONFIG_VPN` by abruptly clearing any running "user-configured" VPNs because the configuration layer is immediately locked down.
**Solution**: We swapped the execution order in `DeviceOwnerManager.kt`.
- `setAlwaysOnVpnPackage` is now called **first** to lock the VPN in as a mandatory admin-configured profile.
- Only then are the `UserManager` restrictions applied, preventing the OS from disconnecting the VPN.
- This order is gracefully mirrored in `clearRestrictions()`.

### 2. The VPN Lockdown Loophole (Internet Loss)
**Issue**: The VPN activated fine, but the device lost all internet access when restrictions were enabled (yielding DNS resolution errors like "couldn't find IP address").
**Root Cause**: Calling `setAlwaysOnVpnPackage(..., lockdownEnabled = true)` enforces a strict OS-level drop of all network packets that bypass the VPN's TUN interface. BlockAds inherently uses `addDisallowedApplication(packageName)` to bypass the VPN for its own upstream DNS queries (and for any user-whitelisted apps). Because of `lockdownEnabled = true`, the OS intercepted and dropped these bypassed packets, blinding the VPN's outbound resolution capabilities.
**Solution**: We changed the parameter to `lockdownEnabled = false`. The app still remains firmly set as an Always-On VPN (meaning the user cannot disable it and it auto-restarts), but the OS network filter relaxes enough to allow bypassed sockets—restoring the app's upstream DNS connectivity and fixing the whitelist functionality.

### 3. Programmatic Deprovisioning Vulnerability
**Issue**: Having a "Deprovision Device Owner" button in the UI created a vulnerability where an impulsive user could theoretically find a way to trigger it.
**Solution**: Removed the deprovisioning feature entirely from `DeviceOwnerManager.kt`, `SettingsViewModel.kt`, and the UI. Advanced users who intentionally provisioned the device via ADB can simply deprovision it using the standard `adb shell dpm remove-active-admin` command.

## Summary
The combination of `setAlwaysOnVpnPackage` and strict `UserManager` API restrictions provides an incredibly resilient barrier for impulse control. The delicate interplay between Android's VPN routing framework and the DPC restrictions requires meticulous ordering, as outlined above.
