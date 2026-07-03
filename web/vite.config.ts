/// <reference types="vitest/config" />
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

// GitHub Pages serves this project under /CloudbasePredictor/.
// Keep the base path in sync with the repository name.
export default defineConfig({
  base: "/CloudbasePredictor/",
  plugins: [react()],
  test: {
    environment: "node",
    include: ["src/**/*.{test,spec}.{ts,tsx}"],
  },
});
