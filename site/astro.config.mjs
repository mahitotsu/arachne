import { defineConfig } from 'astro/config';
import mdx from '@astrojs/mdx';
import sitemap from '@astrojs/sitemap';

export default defineConfig({
  site: process.env.SITE ?? 'https://example.com',
  base: process.env.BASE_PATH,
  integrations: [mdx(), sitemap()],
  redirects: {
    '/mapping': '/appendix',
  },
});