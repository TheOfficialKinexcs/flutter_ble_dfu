import 'dart:async';

import 'package:flutter/services.dart';

class BleDfu {
  static const MethodChannel _channel = const MethodChannel('ble_dfu');

  static const EventChannel _eventChannel = const EventChannel('ble_dfu_event');

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
          break;
        default:
          throw UnimplementedError();
      }
      throw UnimplementedError();
    });

    return stream;
  }

  static Stream<int> get getRandomNumberStream {
    return _eventChannel.receiveBroadcastStream().cast();
  }
}
