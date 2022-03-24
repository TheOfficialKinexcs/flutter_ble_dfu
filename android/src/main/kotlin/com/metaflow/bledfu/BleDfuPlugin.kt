package com.metaflow.bledfu

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.util.Log
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.FlutterPlugin.FlutterPluginBinding
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.EventChannel.EventSink
import io.flutter.plugin.common.EventChannel.StreamHandler
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import no.nordicsemi.android.dfu.DfuProgressListenerAdapter
import no.nordicsemi.android.dfu.DfuServiceInitiator
import no.nordicsemi.android.dfu.DfuServiceListenerHelper
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.*


class BleDfuPlugin : FlutterPlugin, ActivityAware, MethodCallHandler, StreamHandler {

    private var eventSink: EventSink? = null
    private var binding: FlutterPluginBinding? = null
    private var channel: MethodChannel? = null
    private var eventChannel: EventChannel? = null
    private var activity: Activity? = null

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

        override fun onDfuCompleted(deviceAddress: String) {
            super.onDfuCompleted(deviceAddress)
            println("${Thread.currentThread().name}")
            
            println("invoking completion");
            channel!!.invokeMethod("onDfuCompleted", deviceAddress)
            //this@NordicDfuPlugin.runOnUiThread(java.lang.Runnable {
            //    channel!!.invokeMethod("onDfuCompleted", deviceAddress)
            //})
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

        channel = MethodChannel(binding.binaryMessenger, "ble_dfu")
        channel?.setMethodCallHandler(this)

        eventChannel = EventChannel(binding.binaryMessenger, "ble_dfu_event")
        eventChannel?.setStreamHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPluginBinding) {
        this.binding = null

        channel?.setMethodCallHandler(null)
        channel = null

        eventChannel?.setStreamHandler(null)
        eventChannel = null
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivity() {
        activity = null
    }

    /// MethodCallHandler

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "scanForDfuDevice" -> result.success("Android ${Build.VERSION.RELEASE}")

            "startDfu" -> {
                startDfuService(
                    result,
                    call.argument("deviceAddress")!!,
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

    private fun startDfuService(result: Result, deviceAddress: String, urlString: String) {
        Thread {
            Log.d("BleDfuPlugin", "startDfuService $deviceAddress $urlString")

            val uri = try {
                downloadFile(urlString, "version0299.zip")
            } catch (e: Exception) {
                Log.e("BleDfuPlugin", "got exception", e)
                activity?.runOnUiThread {
                    result.error("DF", "Download failed", e.message)
                }
                return@Thread
            }
            val binding = this.binding
            if (binding == null) {
                Log.e("BleDfuPlugin", "no app context binding after FW download. Can't continue")
                return@Thread
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                DfuServiceInitiator.createDfuNotificationChannel(binding.applicationContext)
            }

            val starter = DfuServiceInitiator(deviceAddress)
                .setKeepBond(false)
                //.setUnsafeExperimentalButtonlessServiceInSecureDfuEnabled(true)

            // In case of a ZIP file, the init packet (a DAT file) must be included inside the ZIP file.
            starter.setZip(uri, null)
            //starter.setUnsafeExperimentalButtonlessServiceInSecureDfuEnabled(true)
            //if (enableUnsafeExperimentalButtonlessServiceInSecureDfu != null) {
            //    starter.setUnsafeExperimentalButtonlessServiceInSecureDfuEnabled(enableUnsafeExperimentalButtonlessServiceInSecureDfu)
            //}

            DfuServiceListenerHelper.registerProgressListener(binding.applicationContext, dfuProgressListener)

            // You may use the controller to pause, resume or abort the DFU process.
            starter.start(binding.applicationContext, DfuService::class.java)

            activity?.runOnUiThread {
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
        print("hmm")
        val url = URL(urlString)
        val connection = url.openConnection()
        connection.connect()

        // input stream to read file - with 8k buffer
        val input = BufferedInputStream(url.openStream(), 8192)

        val binding = this.binding ?: return null

        // External directory path to save file
        val directory = File(binding.applicationContext.cacheDir, "kimia_dfu")

        // Create lumen dfu folder if it does not exist
        if (!directory.exists()) {
            Log.d("BleDfuPlugin", "directory does not exist")
            directory.mkdirs()
        }
    
        Log.d("BleDfuPlugin", "directory path")
        Log.d("BleDfuPlugin", "directory ${directory}")
   

        val outputFile = File(directory, fileName)
        
        // Output stream to write file
        val output = FileOutputStream(outputFile)
        val data = ByteArray(4096)

        var total = 0
        count = input.read(data)

        while (count != -1) {
            total += count

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
