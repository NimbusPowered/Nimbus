import { RootProvider } from 'fumadocs-ui/provider/next';
import type { ReactNode } from 'react';
import type { Metadata } from 'next';
import './global.css';

export const metadata: Metadata = {
  title: {
    template: '%s | Nimbus',
    default: 'Nimbus — Minecraft Cloud System',
  },
  description: 'Dynamic server management from a single JAR. Auto-scaling, proxy integration, and a powerful API.',
  icons: { icon: '/Nimbus/favicon.svg' },
  metadataBase: new URL('https://jonax1337.github.io/Nimbus'),
  openGraph: {
    type: 'website',
    title: 'Nimbus — Minecraft Cloud System',
    description: 'Dynamic server management from a single JAR. Auto-scaling, proxy integration, and a powerful API.',
  },
};

export default function Layout({ children }: { children: ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className="flex flex-col min-h-screen">
        <RootProvider>{children}</RootProvider>
      </body>
    </html>
  );
}
