package com.example.dailysimcaller

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.TelecomManager

/**
 * Real default-app management is reserved for fully managed company devices.
 * Normal installs cannot silently change Android HOME/DIALER roles.
 */
class DailySimDeviceAdminReceiver : DeviceAdminReceiver()

object ManagedDefaultApps {
    private const val PREFS = "managed_default_app_backup"
    private const val PREVIOUS_HOME = "previous_home"
    private const val PREVIOUS_PHONE = "previous_phone"

    fun isDeviceOwner(context: Context): Boolean =
        context.getSystemService(DevicePolicyManager::class.java)
            .isDeviceOwnerApp(context.packageName)

    private fun manager(context: Context) =
        context.getSystemService(DevicePolicyManager::class.java)

    private fun admin(context: Context) =
        ComponentName(context, DailySimDeviceAdminReceiver::class.java)

    private fun homeFilter(): IntentFilter =
        IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }

    private fun currentHome(context: Context): ComponentName? {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        @Suppress("DEPRECATION")
        val resolved = context.packageManager.resolveActivity(
            intent, PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo ?: return null
        if (resolved.packageName == "android") return null // system chooser
        return ComponentName(resolved.packageName, resolved.name)
    }

    fun setHome(context: Context, enabled: Boolean): String {
        if (!isDeviceOwner(context)) {
            return "Cannot change Home automatically: Daily SIM Caller is not the device owner. A normal app cannot override Android defaults."
        }
        return try {
            val dpm = manager(context)
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (enabled) {
                val previous = currentHome(context)
                if (previous != null && previous.packageName != context.packageName) {
                    prefs.edit().putString(PREVIOUS_HOME, previous.flattenToString()).apply()
                }
                dpm.addPersistentPreferredActivity(
                    admin(context), homeFilter(),
                    ComponentName(context, MainActivity::class.java)
                )
                "Home selection policy applied. Daily SIM Caller will become Home if Android accepts the policy."
            } else {
                // Release our Home handler, then restore the launcher recorded before enabling.
                dpm.clearPackagePersistentPreferredActivities(admin(context), context.packageName)
                val previous = ComponentName.unflattenFromString(
                    prefs.getString(PREVIOUS_HOME, "").orEmpty()
                )
                if (previous != null && previous.packageName != context.packageName) {
                    dpm.addPersistentPreferredActivity(admin(context), homeFilter(), previous)
                    "Previous Home app restored through device-owner policy."
                } else {
                    "Home override removed. No previous launcher was recorded; Android may still need a Home selection."
                }
            }
        } catch (e: SecurityException) {
            "Android denied the Home change: device-owner administration is required."
        } catch (e: Exception) {
            "Home change failed: ${e.message ?: "Unknown device policy error"}"
        }
    }

    fun setPhone(context: Context, enabled: Boolean): String {
        if (!isDeviceOwner(context)) {
            return "Cannot change Phone automatically: Daily SIM Caller is not the device owner."
        }
        if (Build.VERSION.SDK_INT < 34) {
            return "Direct Phone changes require Android 14 or newer on a fully managed device."
        }
        return try {
            val telecom = context.getSystemService(TelecomManager::class.java)
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val target = if (enabled) {
                val previous = telecom.defaultDialerPackage
                if (!previous.isNullOrBlank() && previous != context.packageName) {
                    prefs.edit().putString(PREVIOUS_PHONE, previous).apply()
                }
                context.packageName
            } else {
                val previous = prefs.getString(PREVIOUS_PHONE, "").orEmpty()
                if (previous.isNotBlank() && previous != context.packageName) {
                    previous
                } else {
                    telecom.systemDialerPackage?.takeIf { it != context.packageName }
                        ?: return "No previous/system Phone app available to restore automatically."
                }
            }
            manager(context).setDefaultDialerApplication(target)
            "Phone default change requested for ${if (enabled) "Daily SIM Caller" else target}. Android will confirm the active default."
        } catch (e: SecurityException) {
            "Android denied the Phone change: device-owner administration is required."
        } catch (e: IllegalArgumentException) {
            "Selected Phone app does not qualify as a default dialer on this device."
        } catch (e: Exception) {
            "Phone change failed: ${e.message ?: "Unknown device policy error"}"
        }
    }
}
