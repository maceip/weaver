import type { BaseLayoutProps } from 'fumadocs-ui/layouts/shared';

export const baseOptions: BaseLayoutProps = {
  nav: {
    title: <span className="font-semibold">Weaver</span>,
  },
  links: [
    {
      text: 'GitHub',
      url: 'https://github.com/maceip/weaver',
      external: true,
    },
    {
      text: 'Home',
      url: '/weaver',
      external: true,
    },
  ],
};
