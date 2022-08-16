import Flutter
import UIKit
import ReplayKit
import Photos

public class SwiftFlutterScreenRecordingPlugin: NSObject, FlutterPlugin {
    
    let recorder = RPScreenRecorder.shared()
    
    var videoOutputURL : URL?
    var videoWriter : AVAssetWriter?
    
    var audioMicInput:AVAssetWriterInput!
    var audioAppInput:AVAssetWriterInput!
    var videoWriterInput : AVAssetWriterInput?
    var nameVideo: String = ""
    var recordAudioMic: Bool = false;
    var recordAudioApp: Bool = false;
    var isReady: Bool = false;
    var delayDuration: Double = 0; //milliseconds
    var myResult: FlutterResult?
    var timer: Timer?
    
    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "flutter_screen_recording", binaryMessenger: registrar.messenger())
        let instance = SwiftFlutterScreenRecordingPlugin()
        registrar.addMethodCallDelegate(instance, channel: channel)
    }
    
    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        
        if(call.method == "startRecordScreen"){
            myResult = result
            let args = call.arguments as? Dictionary<String, Any>
            
            self.recordAudioMic = (args?["audioMic"] as? Bool)!
            self.recordAudioApp = (args?["audioApp"] as? Bool)!
            self.delayDuration = (args?["delayDuration"] as? Double)!
            self.nameVideo = (args?["name"] as? String)!+".mp4"
            startRecording()
            
        }else if(call.method == "stopRecordScreen"){
            if(videoWriter != nil){
                stopRecording()
                let documentsPath = NSSearchPathForDirectoriesInDomains(.documentDirectory, .userDomainMask, true)[0] as NSString
                result(String(documentsPath.appendingPathComponent(nameVideo)))
            }
            result("")
        }
    }
    
    
    
    @objc func startRecording() {
        
        //Use ReplayKit to record the screen
        //Create the file path to write to
        let documentsPath = NSSearchPathForDirectoriesInDomains(.documentDirectory, .userDomainMask, true)[0] as NSString
        self.videoOutputURL = URL(fileURLWithPath: documentsPath.appendingPathComponent(nameVideo))
        
        //Check the file does not already exist by deleting it if it does
        do {
            try FileManager.default.removeItem(at: videoOutputURL!)
        } catch {
            
        }
        
        do {
            try videoWriter = AVAssetWriter(outputURL: videoOutputURL!, fileType: AVFileType.mp4)
        } catch let writerError as NSError {
            print("Error opening video file", writerError);
            videoWriter = nil;
            return;
        }
        
        //Create the video and audio settings
        if #available(iOS 11.0, *) {
            recorder.isMicrophoneEnabled = recordAudioMic
            
            let videoSettings: [String : Any] = [
                AVVideoCodecKey  : AVVideoCodecH264,
                AVVideoWidthKey  : UIScreen.main.bounds.width,
                AVVideoHeightKey : UIScreen.main.bounds.height,
                AVVideoCompressionPropertiesKey: [
                    //AVVideoQualityKey: 1,
                    AVVideoProfileLevelKey: AVVideoProfileLevelH264HighAutoLevel,
                    AVVideoAverageBitRateKey: 6000000
                ],
            ]
            //Create the asset writer input object which is actually used to write out the video
            self.videoWriterInput = AVAssetWriterInput(mediaType: AVMediaType.video, outputSettings: videoSettings);
            self.videoWriterInput?.expectsMediaDataInRealTime = true;
            self.videoWriter?.add(videoWriterInput!);
            
            if(recordAudioMic) {
                let audioOutputSettings: [String : Any] = [
                    AVNumberOfChannelsKey : 2,
                    AVFormatIDKey : kAudioFormatMPEG4AAC,
                    AVSampleRateKey: 44100,
                    AVEncoderAudioQualityKey: AVAudioQuality.high.rawValue
                ]
                //Create the asset writer input object which is actually used to write out the audio
                self.audioMicInput = AVAssetWriterInput(mediaType: AVMediaType.audio, outputSettings: audioOutputSettings)
                self.audioMicInput?.expectsMediaDataInRealTime = true;
                self.videoWriter?.add(audioMicInput!);
            }
            
            if(recordAudioApp) {
                let audioOutputSettings: [String : Any] = [
                    AVNumberOfChannelsKey : 2,
                    AVFormatIDKey : kAudioFormatMPEG4AAC,
                    AVSampleRateKey: 44100,
                    AVEncoderAudioQualityKey: AVAudioQuality.high.rawValue
                ]
                //Create the asset writer input object which is actually used to write out the audio
                self.audioAppInput = AVAssetWriterInput(mediaType: AVMediaType.audio, outputSettings: audioOutputSettings)
                self.audioAppInput?.expectsMediaDataInRealTime = true;
                self.videoWriter?.add(audioAppInput!);
            }
        }
        
        //Tell the screen recorder to start capturing and to call the handler
        if #available(iOS 11.0, *) {
            
            recorder.startCapture(handler: { (cmSampleBuffer, rpSampleType, error) in
                guard error == nil else {
                    //Handle error
                    print("Error starting capture");
                    self.myResult!(false)
                    return;
                }

                self.myResult!(true)
                
                guard let timer = self.timer else {
                    DispatchQueue.main.async { 
                        self.timer = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { [weak self] _ in
                            guard let delayDuration = self?.delayDuration else {
                                return;
                            }
                            if (delayDuration <= 0) {
                                self?.isReady = true
                                return
                            }
                            
                            self?.delayDuration = delayDuration - 50
                        }
                    }
                    return
                }
                
                if (self.isReady == false) {
                    return
                }
                
                switch rpSampleType {
                case RPSampleBufferType.video:
                    print("Writing video...");
                    if self.videoWriter?.status == AVAssetWriter.Status.unknown {
                        self.videoWriter?.startWriting()
                        self.videoWriter?.startSession(atSourceTime:  CMSampleBufferGetPresentationTimeStamp(cmSampleBuffer))
                    } else if self.videoWriter?.status == AVAssetWriter.Status.writing {
                        if (self.videoWriterInput?.isReadyForMoreMediaData == true) {
                            print("Append sample...");
                            if  self.videoWriterInput?.append(cmSampleBuffer) == false {
                                print("Problems writing video")
                            }
                        }
                    }
                case RPSampleBufferType.audioMic:
                    if(self.recordAudioMic){
                        print("Writing audio....");
                        if self.audioMicInput?.isReadyForMoreMediaData == true {
                            print("starting audio....");
                            if self.audioMicInput?.append(cmSampleBuffer) == false {
                                print("Problems writing audio")
                            }
                        }
                    }
                case RPSampleBufferType.audioApp:
                    if(self.recordAudioApp){
                        print("Writing audio....");
                        if self.audioAppInput?.isReadyForMoreMediaData == true {
                            print("starting audio....");
                            if self.audioAppInput?.append(cmSampleBuffer) == false {
                                print("Problems writing audio")
                            }
                        }
                    }
                    
                default:
                    print("not a video sample, so ignore");
                }
                
            } ){(error) in
                guard error == nil else {
                    //Handle error
                    print("Screen record not allowed");
                    self.myResult!(false)
                    return;
                }
            }
        } else {
            //Fallback on earlier versions
        }
    }
    
    @objc func stopRecording() {
        //Stop Recording the screen
        if #available(iOS 11.0, *) {
            recorder.stopCapture( handler: { (error) in
                print("Stopping recording...");
            })
        } else {
            //  Fallback on earlier versions
        }
        
        self.videoWriterInput?.markAsFinished();
        if(self.recordAudioMic) {
            self.audioMicInput?.markAsFinished();
        }
        if(self.recordAudioApp) {
            self.audioAppInput?.markAsFinished();
        }
        
        self.videoWriter?.finishWriting {
            print("Finished writing video");
            //Now save the video
            PHPhotoLibrary.shared().performChanges({
                PHAssetChangeRequest.creationRequestForAssetFromVideo(atFileURL: self.videoOutputURL!)
            })
        }
        
        self.isReady = false
        self.delayDuration = 0
        
        if self.timer != nil {
            DispatchQueue.main.async { 
                self.timer!.invalidate()
                self.timer = nil
            }
        }
    } 
}
