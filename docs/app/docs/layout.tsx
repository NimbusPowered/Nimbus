import { DocsLayout } from 'fumadocs-ui/layouts/docs';
import { source } from '@/lib/source';
import type { ReactNode } from 'react';

export default function Layout({ children }: { children: ReactNode }) {
  return (
    <DocsLayout
      tree={source.getPageTree()}
      nav={{
        title: 'Nimbus',
      }}
      sidebar={{
        tabs: [
          {
            title: 'Guide',
            description: 'Getting started & setup',
            url: '/docs/guide/introduction',
          },
          {
            title: 'Configuration',
            description: 'TOML config reference',
            url: '/docs/config/nimbus-toml',
          },
          {
            title: 'API Reference',
            description: 'REST API & WebSocket',
            url: '/docs/reference/api',
          },
          {
            title: 'Developer',
            description: 'Architecture & plugins',
            url: '/docs/developer/architecture',
          },
        ],
      }}
      links={[
        {
          text: 'GitHub',
          url: 'https://github.com/jonax1337/Nimbus',
        },
      ]}
    >
      {children}
    </DocsLayout>
  );
}
