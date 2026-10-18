package com.omenight.bright.setup.data

import android.app.Activity
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.LocaleList
import android.provider.Settings
import android.util.Log
import java.util.Locale

/** The system-wide changes setup makes, all backed by permissions the system image whitelists. */
object SystemSetup {
    private const val TAG = "BrightSetup"
    private const val DEFAULT_TIME_ZONE = "Europe/Moscow"

    /** The languages offered, each shown in its own language. */
    val languages = listOf(
        Locale.forLanguageTag("ru-RU") to "Русский",
        Locale.forLanguageTag("en-US") to "English",
    )

    // The box has no cellular NITZ, so a network zone never arrives and the factory one (Shanghai) would stay
    fun applyTimeDefaults(context: Context) {
        runCatching {
            val resolver = context.contentResolver
            Settings.Global.putInt(resolver, Settings.Global.AUTO_TIME, 1)
            Settings.Global.putInt(resolver, Settings.Global.AUTO_TIME_ZONE, 0)
            context.getSystemService(AlarmManager::class.java).setTimeZone(DEFAULT_TIME_ZONE)
        }.onFailure { Log.w(TAG, "time defaults", it) }
    }

    fun currentLanguage(): Locale = LocaleList.getDefault()[0]

    // updatePersistentConfiguration would also need WRITE_SETTINGS, an appop a system app does not get on its own.
    // updateConfiguration needs only CHANGE_CONFIGURATION and still persists a locale marked as user set.
    fun setLanguage(locale: Locale) {
        runCatching {
            val service = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null)
            val config = service.javaClass.getMethod("getConfiguration").invoke(service) as Configuration
            config.setLocales(LocaleList(locale))
            Configuration::class.java.getField("userSetLocale").setBoolean(config, true)
            service.javaClass.getMethod("updateConfiguration", Configuration::class.java).invoke(service, config)
        }.onFailure { Log.w(TAG, "set language", it) }
    }

    /** Marks the device set up, retires the wizard from Home and opens the launcher. */
    fun complete(activity: Activity) {
        val resolver = activity.contentResolver
        runCatching {
            Settings.Global.putInt(resolver, Settings.Global.DEVICE_PROVISIONED, 1)
            Settings.Secure.putInt(resolver, USER_SETUP_COMPLETE, 1)
            Settings.Secure.putInt(resolver, TV_USER_SETUP_COMPLETE, 1)
        }.onFailure { Log.w(TAG, "provisioned flags", it) }
        retireOtherLaunchers(activity.packageManager, activity.packageName)
        activity.packageManager.setComponentEnabledSetting(
            ComponentName(activity, activity.javaClass),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
        activity.startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        activity.finish()
    }

    // Another system launcher would otherwise compete with Bright for Home once setup steps aside.
    // Disabled the way the Apps screen does it, so it can be turned back on there.
    private fun retireOtherLaunchers(pm: PackageManager, self: String) {
        val brightEnabled = runCatching { pm.getApplicationInfo(BRIGHT, 0).enabled }.getOrDefault(false)
        if (!brightEnabled) return
        val settings = pm.resolveActivity(Intent(Settings.ACTION_SETTINGS), 0)?.activityInfo?.packageName
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            // FallbackHome and its kin sit at a negative priority on purpose and must stay
            .filter { it.priority >= 0 }
            .map { it.activityInfo.applicationInfo }
            .filter { it.packageName != BRIGHT && it.packageName != self && it.packageName != settings }
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM != 0 }
            .distinctBy { it.packageName }
            .forEach { app ->
                runCatching {
                    pm.setApplicationEnabledSetting(app.packageName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER, 0)
                }.onFailure { Log.w(TAG, "disable ${app.packageName}", it) }
            }
    }

    private const val BRIGHT = "com.omenight.bright"

    // Both keys are hidden in the SDK
    private const val USER_SETUP_COMPLETE = "user_setup_complete"
    private const val TV_USER_SETUP_COMPLETE = "tv_user_setup_complete"
}
