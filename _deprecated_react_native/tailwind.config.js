/** @type {import('tailwindcss').Config} */
const nativewind = require("nativewind/preset");

module.exports = {
  presets: [nativewind],
  content: ["./app/**/*.{js,jsx,ts,tsx}", "./components/**/*.{js,jsx,ts,tsx}"],
  theme: {
    extend: {
      colors: {
        // X brand colors
        x: {
          black: '#000000',
          dark: '#15202b',
          gray: '#cfd9de',
          light: '#eff3f4',
          blue: '#1d9bf0',
        }
      }
    },
  },
  plugins: [],
}
