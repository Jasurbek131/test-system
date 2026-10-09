import type { CapacitorConfig } from '@capacitor/cli'

const config: CapacitorConfig = {
  appId: 'uz.testtizimi.app',
  appName: 'Test tizimi',
  webDir: 'dist',
  android: {
    // Android 15 (targetSdk 35) da kontent status bar ostiga kirib ketmasligi uchun
    adjustMarginsForEdgeToEdge: 'auto',
  },
}

export default config
