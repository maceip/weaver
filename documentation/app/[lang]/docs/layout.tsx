import { DocsLayout } from 'fumadocs-ui/layouts/docs';
import { baseOptions } from '@/app/layout.config';
import { source } from '@/lib/source';
import { LanguageSwitcher } from '@/components/language-switcher';

export default function Layout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ lang: string }>;
}) {
  return (
    <DocsLayout
      tree={source.pageTree['ko']}
      {...baseOptions}
      sidebar={{ footer: <LanguageSwitcher /> }}
    >
      {children}
    </DocsLayout>
  );
}
