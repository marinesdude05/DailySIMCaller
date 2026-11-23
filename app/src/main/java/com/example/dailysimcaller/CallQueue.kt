package com.example.dailysimcaller

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.telecom.Call
import android.telecom.TelecomManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object CallQueue {

    /** Operator-facing stages; keep them separate from diagnostic status messages. */
    enum class Stage { READY, CALLING, WAITING, CONNECTED, BREAK, FINISHED, STOPPED, NEEDS_HELP }

    private val handler = Handler(Looper.getMainLooper())

    var total by mutableIntStateOf(0)
        private set

    var stage by mutableStateOf(Stage.READY)
        private set

    var running by mutableStateOf(false)
        private set

    var status by mutableStateOf("Queue idle")
        private set

    var completed by mutableIntStateOf(0)
        private set

    private var application: Context? = null
    private var phoneNumbers = emptyList<String>()
    private var position = 0
    private var activeCall: Call? = null
    private var callback: Call.Callback? = null
    private var waitingForCall = false

    private var callSeconds = 45
    private var pauseSeconds = 10

    private var timer: Runnable? = null
    private var next: Runnable? = null
    private var connectTimeout: Runnable? = null

    private var startedTimer = false
    private var cancelledPendingNumber: String? = null
    private var cancelExpiresAtMs = 0L

    fun start(
        context: Context,
        numbers: List<String>,
        seconds: Int = 45,
        pause: Int = 10
    ): Boolean {

        if (
            running ||
            numbers.isEmpty() ||
            seconds !in 1..3600 ||
            pause !in 0..3600
        ) {
            return false
        }

        val telecom = context.getSystemService(
            TelecomManager::class.java
        )

        if (
            telecom.defaultDialerPackage != context.packageName ||
            context.checkSelfPermission(
                Manifest.permission.CALL_PHONE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            status = "Default phone role or permission missing"
            stage = Stage.NEEDS_HELP
            return false
        }

        application = context.applicationContext
        cancelledPendingNumber = null

        phoneNumbers = numbers.distinct()

        position = 0
        total = phoneNumbers.size
        completed = 0
        stage = Stage.CALLING
        callSeconds = seconds
        pauseSeconds = pause
        running = true
        ShutdownPowerHelper.queueStarted()

        dialNext()

        return running
    }

    private fun dialNext() {

        if (!running) return

        if (position >= phoneNumbers.size) {

            running = false

            status = "Queue complete: $completed calls attempted"
            stage = Stage.FINISHED

            closeForegroundSession()
            application?.let(ShutdownPowerHelper::queueFinished)
            return
        }

        val context = application

        if (context == null) {
            stop(false)
            stage = Stage.NEEDS_HELP
            return
        }

        val number = phoneNumbers[position]

        waitingForCall = true
        startedTimer = false
        stage = Stage.CALLING

        status = "Dialing ${position + 1} of " +
                "${phoneNumbers.size}: $number"

        try {

            val telecom = context.getSystemService(
                TelecomManager::class.java
            )

            telecom.placeCall(
                Uri.fromParts("tel", number, null),
                Bundle()
            )

        } catch (e: Exception) {

            stop(false)
            stage = Stage.NEEDS_HELP

            status = "Could not place call: " +
                    (e.message ?: "Unknown error")

            return
        }

        val timeout = Runnable {

            if (running && waitingForCall) {

                stop(false)
                stage = Stage.NEEDS_HELP

                status = "No call created by Android; queue stopped"
            }
        }

        connectTimeout = timeout

        handler.postDelayed(timeout, 30_000L)
    }

    fun onCallAdded(call: Call) {

        if (Build.VERSION.SDK_INT >= 29) {
            if (call.details.callDirection != Call.Details.DIRECTION_OUTGOING) return
        } else if (call.state == Call.STATE_RINGING) {
            return
        }

        val observed = PhoneCsv.cleanPhoneNumber(
            call.details.handle?.schemeSpecificPart.orEmpty()
        )

        if (
            cancelledPendingNumber != null &&
            SystemClock.elapsedRealtime() <= cancelExpiresAtMs &&
            observed == cancelledPendingNumber
        ) {

            cancelledPendingNumber = null
            call.disconnect()
            return
        }

        if (
            !running ||
            !waitingForCall ||
            activeCall != null
        ) {
            return
        }

        if (observed != phoneNumbers.getOrNull(position)) {
            return
        }

        waitingForCall = false

        connectTimeout?.let {
            handler.removeCallbacks(it)
        }

        connectTimeout = null
        activeCall = call

        val watcher = object : Call.Callback() {

            override fun onStateChanged(
                call: Call,
                state: Int
            ) {

                if (state == Call.STATE_ACTIVE) {
                    startConnectedTimer(call)
                }
            }
        }

        callback = watcher

        call.registerCallback(watcher, handler)

        if (call.state == Call.STATE_ACTIVE) {

            startConnectedTimer(call)

        } else {

            stage = Stage.WAITING
            status = "Waiting for answer: " +
                    "${position + 1} of ${phoneNumbers.size}"

            val timeout = Runnable {

                if (
                    running &&
                    activeCall === call &&
                    !startedTimer
                ) {

                    status = "No answer after 60 seconds; " +
                            "ending attempt"

                    call.disconnect()
                }
            }

            connectTimeout = timeout

            handler.postDelayed(timeout, 60_000L)
        }
    }

    private fun startConnectedTimer(call: Call) {

        if (
            !running ||
            activeCall !== call ||
            startedTimer
        ) {
            return
        }

        startedTimer = true
        stage = Stage.CONNECTED

        connectTimeout?.let {
            handler.removeCallbacks(it)
        }

        connectTimeout = null

        status = "Connected: ending after $callSeconds seconds " +
                "(${position + 1}/${phoneNumbers.size})"

        val end = Runnable {

            if (
                running &&
                activeCall === call &&
                call.state == Call.STATE_ACTIVE
            ) {

                status = "$callSeconds-second target reached; " +
                        "disconnect requested"

                call.disconnect()
            }
        }

        timer = end

        handler.postDelayed(
            end,
            callSeconds * 1000L
        )
    }

    fun onCallRemoved(call: Call) {

        if (activeCall !== call) {
            return
        }

        callback?.let {
            call.unregisterCallback(it)
        }

        callback = null

        timer?.let {
            handler.removeCallbacks(it)
        }

        timer = null

        connectTimeout?.let {
            handler.removeCallbacks(it)
        }

        connectTimeout = null
        activeCall = null

        if (!running) return

        completed++
        position++

        if (position >= phoneNumbers.size) {

            running = false

            status = "Queue complete: " +
                    "$completed calls attempted"
            stage = Stage.FINISHED

            closeForegroundSession()
            application?.let(ShutdownPowerHelper::queueFinished)

        } else {

            status = "Call ended; pause $pauseSeconds seconds"
            stage = Stage.BREAK

            val runnable = Runnable {
                dialNext()
            }

            next = runnable

            handler.postDelayed(
                runnable,
                pauseSeconds * 1000L
            )
        }
    }

    private fun closeForegroundSession() {

        val context = application ?: return

        context.stopService(
            Intent(
                context,
                CallSessionService::class.java
            )
        )
    }

    fun stop(disconnectCurrent: Boolean = true) {
        ShutdownPowerHelper.cancelPending()

        if (
            waitingForCall &&
            disconnectCurrent
        ) {

            cancelledPendingNumber =
                phoneNumbers.getOrNull(position)

            cancelExpiresAtMs =
                SystemClock.elapsedRealtime() + 30_000L
        }

        running = false
        waitingForCall = false

        timer?.let {
            handler.removeCallbacks(it)
        }

        next?.let {
            handler.removeCallbacks(it)
        }

        connectTimeout?.let {
            handler.removeCallbacks(it)
        }

        timer = null
        next = null
        connectTimeout = null

        val previous = activeCall

        callback?.let { listener ->
            previous?.unregisterCallback(listener)
        }

        callback = null
        activeCall = null

        if (disconnectCurrent) {
            previous?.disconnect()
        }

        status = "Queue stopped"
        stage = Stage.STOPPED

        closeForegroundSession()
    }
}
