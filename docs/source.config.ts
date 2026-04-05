import { defineDocs, defineConfig } from 'fumadocs-mdx/config';
import { transformerNimbus } from './lib/nimbus-transformer';

export const docs = defineDocs({
  dir: 'content/docs',
});

export default defineConfig({
  mdxOptions: {
    rehypeCodeOptions: {
      transformers: [transformerNimbus()],
    },
  },
});
