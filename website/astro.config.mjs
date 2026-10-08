import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import starlightLinksValidator from 'starlight-links-validator';

const repository = process.env.GITHUB_REPOSITORY ?? 'sentinel-ai/sentinel-ai';
const [owner, repo] = repository.split('/');
const site = (process.env.SITE_URL || `https://${owner}.github.io`).replace(/\/+$/, '');
const rawBasePath = process.env.BASE_PATH || (repo === `${owner}.github.io` ? '/' : `/${repo}/`);
const normalizedBasePath = rawBasePath.replace(/^\/+|\/+$/g, '');
const base = normalizedBasePath ? `/${normalizedBasePath}/` : '/';
const basePrefix = base === '/' ? '' : base.slice(0, -1);

function prefixMarkdownLinks() {
  return (tree) => {
    const visit = (node) => {
      const isLinkNode = ['link', 'image', 'definition'].includes(node.type);
      if (isLinkNode && typeof node.url === 'string') {
        const isRootRelative = node.url.startsWith('/') && !node.url.startsWith('//');
        if (basePrefix && isRootRelative && !node.url.startsWith(`${basePrefix}/`)) {
          node.url = `${basePrefix}${node.url}`;
        }
      }
      for (const child of node.children ?? []) visit(child);
    };
    visit(tree);
  };
}

export default defineConfig({
  site,
  base,
  markdown: { remarkPlugins: [prefixMarkdownLinks] },
  integrations: [
    starlight({
      title: 'Sentinel',
      description: 'Quality gates for AI coding agents.',
      favicon: '/favicon.svg',
      social: [{ icon: 'github', label: 'GitHub', href: `https://github.com/${repository}` }],
      editLink: { baseUrl: `https://github.com/${repository}/edit/main/website/src/content/docs/` },
      plugins: [starlightLinksValidator()],
      sidebar: [
        { label: 'Start here', items: [{ label: 'Overview', link: '/' }, { label: 'Installation', link: '/getting-started/installation/' }, { label: 'Quick start', link: '/getting-started/quick-start/' }] },
        { label: 'Commands', items: [{ label: 'detect', link: '/commands/detect/' }, { label: 'init', link: '/commands/init/' }, { label: 'check', link: '/commands/check/' }, { label: 'integrate', link: '/commands/integrate/' }] },
        { label: 'Configuration', items: [{ label: 'sentinel.toml', link: '/configuration/sentinel-toml/' }, { label: 'Quality gates', link: '/configuration/gates/' }, { label: 'Learning', link: '/configuration/learning/' }] },
        { label: 'Reference', items: [{ label: 'JSON output', link: '/reference/json-output/' }, { label: 'Exit codes', link: '/reference/exit-codes/' }] },
        { label: 'Integrations', items: [{ label: 'Claude Code', link: '/integrations/claude-code/' }, { label: 'OpenCode', link: '/integrations/opencode/' }] },
        { label: 'Project', items: [{ label: 'Security', link: '/security/' }, { label: 'Architecture', link: '/architecture/' }] },
      ],
    }),
  ],
});
