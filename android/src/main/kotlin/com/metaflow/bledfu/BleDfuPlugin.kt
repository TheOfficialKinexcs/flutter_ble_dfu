package com.metaflow.bledfu

import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.FlutterPlugin.FlutterPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.EventChannel.EventSink
import io.flutter.plugin.common.EventChannel.StreamHandler
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import no.nordicsemi.android.dfu.DfuProgressListenerAdapter
import no.nordicsemi.android.dfu.DfuServiceInitiator
import no.nordicsemi.android.dfu.DfuServiceListenerHelper
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL


class BleDfuPlugin : FlutterPlugin, MethodCallHandler, StreamHandler {

    companion object {
        @JvmStatic
        fun registerWith(registrar: Registrar) {
            val plugin = BleDfuPlugin()

            val channel = MethodChannel(registrar.messenger(), "ble_dfu")
            channel.setMethodCallHandler(plugin)

            val eventChannel = EventChannel(registrar.messenger(), "ble_dfu_event")
            eventChannel.setStreamHandler(plugin)
        }
    }

    private var eventSink: EventSink? = null
    private val uiThreadHandler: Handler = Handler(Looper.getMainLooper())
    private var binding: FlutterPluginBinding? = null

    private val dfuProgressListener = object : DfuProgressListenerAdapter() {
        override fun onDfuAborted(deviceAddress: String) {
            super.onDfuAborted(deviceAddress)
            eventSink?.error("DA", "Dfu aborted", "Dfu aborted")
            unregisterProgressListener()
        }

        override fun onError(deviceAddress: String, error: Int, errorType: Int, message: String?) {
            super.onError(deviceAddress, error, errorType, message)
            eventSink?.error("DE", "Error $error", message)
            unregisterProgressListener()
        }

        override fun onDeviceDisconnected(deviceAddress: String) {
            super.onDeviceDisconnected(deviceAddress)
            unregisterProgressListener()
        }

        override fun onProgressChanged(
            deviceAddress: String,
            percent: Int,
            speed: Float,
            avgSpeed: Float,
            currentPart: Int,
            partsTotal: Int
        ) {
            super.onProgressChanged(deviceAddress, percent, speed, avgSpeed, currentPart, partsTotal)

            Log.d("BleDfuPlugin", "progress changed $percent")
            eventSink?.success("part: $currentPart, outOf: $partsTotal, to: $percent, speed: $avgSpeed")
        }
    }

    override fun onAttachedToEngine(binding: FlutterPluginBinding) {
        this.binding = binding
        val channel = MethodChannel(binding.binaryMessenger, "ble_dfu")
        channel.setMethodCallHandler(this)

        val eventChannel = EventChannel(binding.binaryMessenger, "ble_dfu_event")
        eventChannel.setStreamHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPluginBinding) {
        this.binding = null
    }

    /// MethodCallHandler

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "scanForDfuDevice" -> result.success("Android ${Build.VERSION.RELEASE}")

            "startDfu" -> {
                startDfuService(
                    result,
                    call.argument("deviceAddress")!!,
                    call.argument("deviceName")!!,
                    call.argument("url")!!
                )
            }

            else -> result.notImplemented()
        }
    }

    /// StreamHandler methods

    override fun onListen(arguments: Any?, events: EventSink) {
        Log.d("BleDfuPlugin", "onListen")
        eventSink = events
    }

    override fun onCancel(arguments: Any?) {
        Log.d("BleDfuPlugin", "onCancel")
        eventSink = null
    }

    private fun startDfuService(result: Result, deviceAddress: String, deviceName: String, urlString: String) {
        val binding = this.binding ?: return
        Thread {
            Log.d("BleDfuPlugin", "startDfuService $deviceAddress $deviceName $urlString")

            val uri = try {
                downloadFile(urlString, "dfu.zip")
            } catch (e: Exception) {
                Log.e("BleDfuPlugin", "got exception", e)
                uiThreadHandler.post {
                    result.error("DF", "Download failed", e.message)
                }
                return@Thread
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                DfuServiceInitiator.createDfuNotificationChannel(binding.applicationContext)
            }

            val starter = DfuServiceInitiator(deviceAddress)
                .setDeviceName(deviceName)
                .setKeepBond(false)

            // In case of a ZIP file, the init packet (a DAT file) must be included inside the ZIP file.
            starter.setZip(uri, null)

            DfuServiceListenerHelper.registerProgressListener(binding.applicationContext, dfuProgressListener)

            // You may use the controller to pause, resume or abort the DFU process.
            starter.start(binding.applicationContext, DfuService::class.java)

            uiThreadHandler.post {
                result.success("success")
            }

        }.start()
    }

    fun unregisterProgressListener() {
        binding?.let { binding ->
            DfuServiceListenerHelper.unregisterProgressListener(binding.applicationContext, dfuProgressListener)
        }
    }

    @Suppress("SameParameterValue")
    private fun downloadFile(urlString: String, fileName: String): Uri? {
        Log.d("BleDfuPlugin", "downloadFile $urlString $fileName")

        var count: Int

        val url = URL(urlString)
        val connection = url.openConnection()
        connection.connect()

        // input stream to read file - with 8k buffer
        val input = BufferedInputStream(url.openStream(), 8192)

        val binding = this.binding ?: return null

        // External directory path to save file
        val directory = File(binding.applicationContext.cacheDir, "lumen_dfu")

        // Create lumen dfu folder if it does not exist
        if (!directory.exists()) {
            directory.mkdirs()
        }

        val outputFile = File(directory, fileName)
        // Output stream to write file
        val output = FileOutputStream(outputFile)
        val data = ByteArray(4096)

        var total = 0
        count = input.read(data)

        while (count != -1) {
            total += count
//                Log.d("BleDfuPlugin", "Progress: $total out of $lengthOfFile")

            // writing data to file
            output.write(data, 0, count)

            count = input.read(data)
        }

        // flushing output
        output.flush()

        // closing streams
        output.close()
        input.close()

        Log.d("BleDfuPlugin", "Successful download ${outputFile.absolutePath}")

        return Uri.fromFile(outputFile)

    }
}
