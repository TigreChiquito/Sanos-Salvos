/** @type {import('tailwindcss').Config} */
export default {
  content: ['./src/**/*.{astro,html,js,jsx,ts,tsx}'],
  theme: {
    extend: {
      colors: {
        sand:      '#F5EFE0',
        cream:     '#FDF8F0',
        terra:     '#C45C3A',
        'terra-d': '#9E4026',
        'terra-l': '#F0C5B4',
        sage:      '#6B8F71',
        'sage-l':  '#C8DEC9',
        brown:     '#2D1B12',
        'brown-m': '#5C3D2C',
        gold:      '#E8A838',
      },
      fontFamily: {
        display: ['Fraunces', 'serif'],
        sans:    ['Nunito', 'sans-serif'],
      },
    },
  },
  plugins: [],
};
