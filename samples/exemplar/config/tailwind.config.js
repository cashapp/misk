/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [ "../src/main/**/*.{html,js,kt}" ],
  theme: {
    extend: {},
  },
  plugins: [
    require('@tailwindcss/typography'),
    require('@tailwindcss/forms'),
    require('@tailwindcss/aspect-ratio'),
  ],
}
