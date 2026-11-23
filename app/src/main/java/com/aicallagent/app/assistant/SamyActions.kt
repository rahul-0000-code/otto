package com.aicallagent.app.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.util.Log
import com.aicallagent.app.CallAccessibilityService
import com.aicallagent.app.agent.AgentSettings
import com.aicallagent.app.agent.CloudLlm

/** Turns a voice command into a phone action: understand it, find the contact, then do it. */
object SamyActions {
    private const val TAG = "Samy"
    private val WHATSAPP_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")

    /** Blocking (network): call from a worker thread. Gemini when there's a cloud key, else the built-in phrasings. */
    fun interpret(context: Context, said: String): VoiceCommand {
        val config = AgentSettings.load(context)
        if (AgentSettings.hasCloudBrain(context, config)) {
            runCatching {
                val raw = CloudLlm.complete(
                    config.provider, AgentSettings.apiKey(context, config.provider), config.model(),
                    CommandParser.systemPrompt(config.voice), listOf(CloudLlm.Turn("user", said)), maxTokens = 200, json = true,
                )
                return CommandParser.fromJson(raw)
            }.onFailure { Log.w(TAG, "interpret failed: ${it.javaClass.simpleName}") }
        }
        return CommandParser.rules(said, config.voice)
    }

    fun contacts(context: Context): List<ContactEntry> {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val out = ArrayList<ContactEntry>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null,
            // Primary and mobile numbers first, so de-duplication keeps the number people actually use.
            "${ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY} DESC, ${ContactsContract.CommonDataKinds.Phone.TYPE} = ${ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0)?.trim().orEmpty()
                val number = c.getString(1)?.trim().orEmpty()
                if (name.isNotEmpty() && number.isNotEmpty()) out += ContactEntry(name, number)
            }
        }
        return out
    }

    fun hasWhatsApp(context: Context): Boolean = WHATSAPP_PACKAGES.any { installed(context, it) }

    /** Runs the command. Returns a short line for the screen, e.g. "Calling Pooja Didi". */
    fun execute(context: Context, command: VoiceCommand, contact: ContactEntry?): String {
        val name = contact?.name.orEmpty()
        return when (command.action) {
            VoiceCommand.Action.CALL -> {
                val uri = Uri.fromParts("tel", contact!!.number, null)
                val direct = granted(context, Manifest.permission.CALL_PHONE)
                start(context, Intent(if (direct) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri))
                if (direct) "Calling $name" else "Tap call to ring $name"
            }
            VoiceCommand.Action.SMS -> {
                if (granted(context, Manifest.permission.SEND_SMS)) {
                    val sms = context.getSystemService(SmsManager::class.java)
                    sms.sendMultipartTextMessage(contact!!.number, null, sms.divideMessage(command.message), null, null)
                    "Text sent to $name"
                } else {
                    start(context, Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", contact!!.number, null)).putExtra("sms_body", command.message))
                    "Tap send to text $name"
                }
            }
            VoiceCommand.Action.WHATSAPP -> {
                val pkg = WHATSAPP_PACKAGES.firstOrNull { installed(context, it) }
                val phone = PhoneNumbers.forWhatsApp(contact!!.number)
                val uri = Uri.parse("https://api.whatsapp.com/send?phone=$phone&text=${Uri.encode(command.message)}")
                start(context, Intent(Intent.ACTION_VIEW, uri).apply { if (pkg != null) setPackage(pkg) })
                // WhatsApp gives other apps no way to send: Otto's accessibility service presses Send,
                // and only once the chat's box holds exactly this message.
                if (CallAccessibilityService.sendWhatsAppWhenReady(context, command.message)) "Sending to $name on WhatsApp"
                else "Tap send in WhatsApp"
            }
            VoiceCommand.Action.NAVIGATE -> {
                val maps = "com.google.android.apps.maps"
                val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${Uri.encode(command.destination)}&mode=${command.mode.mapsCode}"))
                if (installed(context, maps)) start(context, nav.setPackage(maps))
                else start(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(command.destination)}")))
                "Navigating to ${command.destination} ${command.mode.label}"
            }
            VoiceCommand.Action.UNKNOWN -> ""
        }
    }

    private fun start(context: Context, intent: Intent) {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun granted(context: Context, permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun installed(context: Context, pkg: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)
}
