import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  build: {
    // 讓 esbuild 在壓縮/轉換 CSS 時，以「相容到 Safari 14」為準，
    // 避免自動套用像是 CSS Media Query Range Syntax（例如 width<=1200px）
    // 這類新版 Safari（16.4+）才支援的語法糖，導致舊版 iOS 上的 RWD 失效。
    //
    // 這裡選 safari14 是保守值：涵蓋到 iOS 14 以上的裝置。
    // 如果你確定不需要支援這麼舊的機型，可以視情況調高（例如 safari15）。
    cssTarget: ['safari14'],

    // 同步設定 JS 的 target，確保打包出來的 JS 語法也一致相容到同個範圍
    // （避免出現 CSS 相容但 JS 語法舊瀏覽器又看不懂的情況）
    target: ['es2020', 'safari14'],
  },
})
