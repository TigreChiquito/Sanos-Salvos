import { defineConfig } from 'astro/config';
import react from '@astrojs/react';
import tailwind from '@astrojs/tailwind';

export default defineConfig({
  integrations: [
    react(),
    tailwind({
      // Desactivamos la inyección automática de @tailwind base/components/utilities
      // porque las controlamos manualmente en global.css
      applyBaseStyles: false,
    }),
  ],
});
