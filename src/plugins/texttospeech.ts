import { registerPlugin } from '@capacitor/core'

export interface TextToSpeechPlugin {
  speak(options: { text: string; rate?: number }): Promise<void>
  stop(): Promise<void>
  isSpeaking(): Promise<{ speaking: boolean }>
  setLanguage(options: { language: string }): Promise<void>
}

const TextToSpeech = registerPlugin<TextToSpeechPlugin>('TextToSpeech')

export default TextToSpeech
