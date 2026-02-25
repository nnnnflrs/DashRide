import Foundation
import Capacitor
import AVFoundation

@objc(TextToSpeechPlugin)
public class TextToSpeechPlugin: CAPPlugin, CAPBridgedPlugin, AVSpeechSynthesizerDelegate {
    public let identifier = "TextToSpeechPlugin"
    public let jsName = "TextToSpeech"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "speak", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "stop", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "isSpeaking", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setLanguage", returnType: CAPPluginReturnPromise)
    ]

    private var synthesizer: AVSpeechSynthesizer?
    private var currentLanguage: String = "en-US"
    private var pendingCall: CAPPluginCall?

    public override func load() {
        super.load()
        synthesizer = AVSpeechSynthesizer()
        synthesizer?.delegate = self

        // Configure audio session for playback
        do {
            try AVAudioSession.sharedInstance().setCategory(.playback, mode: .voicePrompt, options: [.duckOthers])
            try AVAudioSession.sharedInstance().setActive(true)
        } catch {
            print("TextToSpeech: Failed to configure audio session: \(error)")
        }

        print("TextToSpeechPlugin loaded")
    }

    @objc func speak(_ call: CAPPluginCall) {
        guard let text = call.getString("text"), !text.isEmpty else {
            call.reject("Text is required")
            return
        }

        let rate = call.getFloat("rate") ?? 0.5

        DispatchQueue.main.async { [weak self] in
            guard let self = self, let synthesizer = self.synthesizer else {
                call.reject("Synthesizer not initialized")
                return
            }

            // Stop any ongoing speech
            if synthesizer.isSpeaking {
                synthesizer.stopSpeaking(at: .immediate)
            }

            // Configure audio session
            do {
                try AVAudioSession.sharedInstance().setActive(true)
            } catch {
                print("TextToSpeech: Failed to activate audio session: \(error)")
            }

            let utterance = AVSpeechUtterance(string: text)
            utterance.rate = self.normalizeRate(rate)
            utterance.pitchMultiplier = 1.0
            utterance.volume = 1.0

            // Set voice based on language
            if let voice = AVSpeechSynthesisVoice(language: self.currentLanguage) {
                utterance.voice = voice
            } else {
                // Fallback to default English voice
                utterance.voice = AVSpeechSynthesisVoice(language: "en-US")
            }

            // Store call for completion callback
            self.pendingCall = call

            synthesizer.speak(utterance)
        }
    }

    @objc func stop(_ call: CAPPluginCall) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self, let synthesizer = self.synthesizer else {
                call.resolve()
                return
            }

            if synthesizer.isSpeaking {
                synthesizer.stopSpeaking(at: .immediate)
            }

            // Clear pending call
            self.pendingCall = nil

            call.resolve()
        }
    }

    @objc func isSpeaking(_ call: CAPPluginCall) {
        DispatchQueue.main.async { [weak self] in
            let speaking = self?.synthesizer?.isSpeaking ?? false
            call.resolve(["speaking": speaking])
        }
    }

    @objc func setLanguage(_ call: CAPPluginCall) {
        guard let language = call.getString("language"), !language.isEmpty else {
            call.reject("Language is required")
            return
        }

        currentLanguage = language
        call.resolve()
    }

    // MARK: - AVSpeechSynthesizerDelegate

    public func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        if let call = pendingCall {
            call.resolve()
            pendingCall = nil
        }
    }

    public func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        if let call = pendingCall {
            call.resolve()
            pendingCall = nil
        }
    }

    // MARK: - Helper Methods

    private func normalizeRate(_ rate: Float) -> Float {
        // Input rate is expected to be around 0.5-1.0 from JavaScript
        // AVSpeechUtterance rate ranges from 0 (slowest) to 1 (fastest)
        // Default rate is around 0.5
        // Clamp to valid range
        return max(AVSpeechUtteranceMinimumSpeechRate, min(rate, AVSpeechUtteranceMaximumSpeechRate))
    }
}
