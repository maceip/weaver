import type { Metadata } from 'next';
import { Geist, Geist_Mono } from 'next/font/google';
import { RootProvider } from 'fumadocs-ui/provider';
import './global.css';

const geist = Geist({ variable: '--font-geist-sans', subsets: ['latin'] });
const geistMono = Geist_Mono({ variable: '--font-geist-mono', subsets: ['latin'] });

export const metadata: Metadata = {
  metadataBase: new URL('https://maceip.github.io'),
  title: 'Stitch — Google Stitch on Android foldables',
  description:
    'Native Android client for Google Stitch with fold-aware tabletop studio, fold-to-compare, and fire-and-fold cover handoff.',
  openGraph: {
    url: 'https://maceip.github.io/stitch',
    siteName: 'Stitch',
    type: 'website',
  },
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className={`${geist.variable} ${geistMono.variable} antialiased`}>
        <RootProvider>{children}</RootProvider>
      </body>
    </html>
  );
}
