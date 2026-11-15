import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// "npm run dev" serves the app on http://localhost:5173 and forwards /api/** to the API gateway, so the browser
// only ever talks to one origin: no CORS to configure in the gateway, same as behind nginx and Caddy.
// API_URL points to another gateway, e.g. the one of a server: API_URL=https://shop.example.com npm run dev
const apiUrl = process.env.API_URL ?? "http://localhost:8090";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": { target: apiUrl, changeOrigin: true },
    },
  },
});
