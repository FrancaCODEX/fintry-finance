import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
export default defineConfig({
  plugins: [react()],
  base: '/workspace/',
  define: { 'process.env.NODE_ENV': JSON.stringify('production') },
  build: {
    outDir: '../backend/src/main/resources/static/workspace',
    emptyOutDir: true,
    lib: {
      entry: 'src/main.tsx',
      formats: ['es'],
      fileName: () => 'fintry.js',
      cssFileName: 'fintry',
    },
    rollupOptions: { output: { chunkFileNames: 'chunks/[name]-[hash].js' } },
  },
});
