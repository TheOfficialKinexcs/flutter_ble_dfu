import 'dart:io';
import 'dart:async';

import 'package:ble_dfu/ble_dfu.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';
import 'package:path/path.dart' as p;

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
      _deviceName = "DFU-Test";
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
          TextButton(child: Text("START DFU"), onPressed: () => deviceName != null ? onStartDfuPressed(deviceName) : null),
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
  
  Future<String> copyFromAssetsToCache() async {
    String path = 'assets/version0289.zip';
    final Directory docDir = await getTemporaryDirectory();
    final String localPath = docDir.path;
    File file = File('$localPath/${path.split('/').last}');
    final imageBytes = await rootBundle.load(path);
    final buffer = imageBytes.buffer;
    await file.writeAsBytes(
      buffer.asUint8List(imageBytes.offsetInBytes, imageBytes.lengthInBytes));  

    String localPath2 = p.join(localPath,"version0289.zip");

    return localPath2;
  }
  
  //https://drive.google.com/file/d/1Pmbcr1xuGIULXG_G7i2c1mHW-sG9x_AK/view?usp=sharing
  //https://drive.google.com/uc?export=download&id=1Pmbcr1xuGIULXG_G7i2c1mHW-sG9x_AK
  //https://drive.google.com/file/d/1HDHuIRF1-AEtoOfzKoVfh6GGFGXnZyAY/view?usp=sharing
  //https://drive.google.com/file/d/1O_LnoKtJ4czoLl-tZeTTl377b-QiHub0/view?usp=sharing
  onStartDfuPressed(String deviceName) async {
    //get list of files in directory
    // List<FileSystemEntity> _folders;
    // String pdfDirectory = '$localPath/';
    // final myDir = new Directory(pdfDirectory);
    // _folders = myDir.listSync(recursive: true, followLinks: false);
    // print("_folders: $_folders");
    String path = await copyFromAssetsToCache();
    print("isDfuComplete: ${BleDfu.isDfuComplete}");
    BleDfu.startDfu(path, _deviceAddress)
        .listen((onData) {
      setState(() {
        _lastDfuState = onData.toString();
      });
    });

    Timer.periodic(Duration(seconds: 2), (res) async {
      print("jeezus");
      if(BleDfu.isDfuComplete){
        res.cancel();
        BleDfu.isDfuComplete = false;
        final file = File(path);
        try {
          await file.delete();
          print("file deleted");
        } catch (e) {
          print("gg cant delete");
        }
      }
    });
  }
}
