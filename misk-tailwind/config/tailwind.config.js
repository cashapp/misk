/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [ "../../misk-*/src/main/**/*.{html,js,kt}" ],
  theme: {
    extend: {},
  },
  plugins: [
    require('@tailwindcss/typography'),
    require('@tailwindcss/forms'),
    require('@tailwindcss/line-clamp'),
    require('@tailwindcss/aspect-ratio'),
  ],
}
