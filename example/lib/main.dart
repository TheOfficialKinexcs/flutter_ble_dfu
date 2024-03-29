import 'dart:io';

import 'package:ble_dfu/ble_dfu.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() => runApp(MyApp());

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String? _deviceName;
  String _deviceAddress = "EB:9A:CE:41:6A:44";
  String? _lastDfuState;

  @override
  initState() {
    super.initState();

    initPlatformState();
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  initPlatformState() async {
    if (!Platform.isIOS) {
      _deviceName = "DFU-TEST";
      return;
    }

    // Platform messages may fail, so we use a try/catch PlatformException.
    try {
      _deviceName = await BleDfu.scanForDfuDevice;
    } on PlatformException {
      _deviceName = null;
    }

    setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final children = <Widget>[
      Text('Found device: ${_deviceName ?? "Not found"}'),
    ];

    if (Platform.isAndroid) {
      children.add(Padding(
        padding: const EdgeInsets.only(top: 8.0),
        child: Text('Device address: $_deviceAddress'),
      ));
    }

    children.add(Padding(
      padding: const EdgeInsets.only(top: 8.0),
      child: Text("Last state: ${_lastDfuState ?? ""}"),
    ));

    final deviceName = _deviceName;
    children.add(Padding(
      padding: const EdgeInsets.only(top: 16.0),
      child:
          TextButton(child: Text("START DFU"), onPressed: deviceName != null ? _onStartDfuPressed(deviceName) : null),
    ));

    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: Text('DFU Plugin example'),
        ),
        body: Center(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: children,
          ),
        ),
      ),
    );
  }

  _onStartDfuPressed(String deviceName) {
    BleDfu.startDfu("https://drive.google.com/uc?export=download&id=1Pmbcr1xuGIULXG_G7i2c1mHW-sG9x_AK", _deviceAddress, deviceName)
        .listen((onData) {
      setState(() {
        _lastDfuState = onData.toString();
      });
    });
  }
}
