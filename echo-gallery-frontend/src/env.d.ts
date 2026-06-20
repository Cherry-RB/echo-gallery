/// <reference types="vite/client" />

declare module '*.vue' {
  import { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

// 讓 import.meta.env 擁有精準的型別提示
interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string
  // 如果未來有其他變數，可以在這裡繼續往下加
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}