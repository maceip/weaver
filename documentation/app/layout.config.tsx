import type { BaseLayoutProps } from 'fumadocs-ui/layouts/shared';

export const baseOptions: BaseLayoutProps = {
  nav: {
    title: <span className="font-semibold">Stitch</span>,
  },
  links: [
    {
      text: 'GitHub',
      url: 'https://github.com/maceip/stitch',
      external: true,
    },
    {
      text: 'Home',
      url: '/stitch',
      external: true,
    },
  ],
};
