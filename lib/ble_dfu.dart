import 'dart:async';

import 'package:flutter/services.dart';

class BleDfu {
  static const MethodChannel _channel = const MethodChannel('ble_dfu');

  static const EventChannel _eventChannel = const EventChannel('ble_dfu_event');

  static bool isDfuComplete = false;

  static bool isDownloadFailed = false;

  static Future<String> get scanForDfuDevice async {
    return await _channel.invokeMethod('scanForDfuDevice');
  }

  static Stream<dynamic> startDfu(String url, String deviceAddress) {
    final stream = _eventChannel.receiveBroadcastStream();
    _channel.invokeMethod('startDfu', {
      "deviceAddress": deviceAddress,
      "url": url
    });

    _channel.setMethodCallHandler((MethodCall call) {
      switch (call.method) {
        case 'onDfuCompleted':
          print("dfuCompleteddd");
          //sss = "done";
          isDfuComplete = true;
          break;
        case 'onDownloadFail':
          print("download failed");
          isDownloadFailed = true;
          break;
        case 'onError':
          print("onError");
          isDownloadFailed = true;
          break;
        default:
          throw UnimplementedError();
      }
      throw UnimplementedError();
    });

    return stream;
  }
  //prob not needed
  static Stream<int> get getRandomNumberStream {
    return _eventChannel.receiveBroadcastStream().cast();
  }
}
