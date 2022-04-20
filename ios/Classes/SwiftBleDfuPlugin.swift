import Flutter
import UIKit
import iOSDFULibrary
import CoreBluetooth
 
public class SwiftBleDfuPlugin: NSObject, FlutterPlugin, DFUServiceDelegate {
   public func dfuError(_ error: DFUError, didOccurWithMessage message: String) {
       print("\(deviceAddress!) onError, message : \(message)")
       channel.invokeMethod("onError", arguments: deviceAddress)
   }
 
  
   let registrar: FlutterPluginRegistrar
   let channel: FlutterMethodChannel
   var pendingResult: FlutterResult?
   var deviceAddress: String?
   private var dfuController    : DFUServiceController!
  
   init(_ registrar: FlutterPluginRegistrar, _ channel: FlutterMethodChannel) {
       self.registrar = registrar
       self.channel = channel
   }
  
   public static func register(with registrar: FlutterPluginRegistrar) {
       let channel = FlutterMethodChannel(name: "ble_dfu", binaryMessenger: registrar.messenger())
       let instance = SwiftBleDfuPlugin(registrar, channel)
       registrar.addMethodCallDelegate(instance, channel: channel)
   }
  
   public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
       if (call.method == "startDfu") {    
           let params = call.arguments as! Dictionary<String,String>
           deviceAddress = params["deviceAddress"]
           print("url: \(params["url"])")
           print("deviceAddress: \(deviceAddress)")
           start(params["url"]!, identifier: UUID(uuidString: params["deviceAddress"]!)!)
           result("started")
 
       } else if (call.method == "abortDfu") {
           _ = dfuController?.abort()
           dfuController = nil
       }
   }
 
   func start(_ url: String, identifier: UUID){
      
       do {
           //self.deviceAddress = identifier
           print("starting start")
           var newFilePath = "file://\(url)"
           print("new filePath: \(newFilePath)")
           let pathUrl = URL(string: newFilePath)!
           let zipfileData = try Data(contentsOf: pathUrl)
      
           let selectedFirmware = try DFUFirmware(zipFile: zipfileData)
          
           if selectedFirmware == nil {
               print("nil error")
               channel.invokeMethod("onError", arguments: deviceAddress)
           } else {

               let initiator = DFUServiceInitiator()
               //initiator.progressDelegate = self
               initiator.delegate = self
               initiator.enableUnsafeExperimentalButtonlessServiceInSecureDfu = true
               dfuController = initiator.with(firmware: selectedFirmware!).start(targetWithIdentifier: identifier)
           }
       } catch {
           print(error.localizedDescription)
       }
 
   }
  
   //MARK: DFUServiceDelegate
   public func dfuStateDidChange(to state: DFUState) {
       switch state {
       case .completed:
           //pendingResult?(deviceAddress)
           //pendingResult = nil
           print("\(deviceAddress!) onDfuCompleted")
           dfuController = nil
           channel.invokeMethod("onDfuCompleted", arguments: deviceAddress)
       default:
           print("dfuStateDidChange to: \(state.description())")
       }
   }
}


