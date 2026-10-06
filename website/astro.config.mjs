import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import starlightLinksValidator from 'starlight-links-validator';

const repository = process.env.GITHUB_REPOSITORY ?? 'sentinel-cli/sentinel-cli';
const [owner, repo] = repository.split('/');

export default defineConfig({
  site: `https://${owner}.github.io`,
  base: repo === `${owner}.github.io` ? '/' : `/${repo}/`,
  integrations: [
    starlight({
      title: 'Sentinel',
      description: 'Quality gates for AI coding agents.',
      plugins: [starlightLinksValidator()],
      sidebar: [
        { label: 'Start here', items: [{ label: 'Overview', link: '/' }, { label: 'Installation', link: '/getting-started/installation/' }, { label: 'Quick start', link: '/getting-started/quick-start/' }] },
        { label: 'Commands', items: [{ label: 'detect', link: '/commands/detect/' }, { label: 'init', link: '/commands/init/' }, { label: 'check', link: '/commands/check/' }, { label: 'integrate', link: '/commands/integrate/' }] },
        { label: 'Configuration', items: [{ label: 'sentinel.toml', link: '/configuration/sentinel-toml/' }, { label: 'Quality gates', link: '/configuration/gates/' }] },
        { label: 'Reference', items: [{ label: 'JSON output', link: '/reference/json-output/' }, { label: 'Exit codes', link: '/reference/exit-codes/' }] },
        { label: 'Integrations', items: [{ label: 'Claude Code', link: '/integrations/claude-code/' }, { label: 'OpenCode', link: '/integrations/opencode/' }] },
        { label: 'Project', items: [{ label: 'Security', link: '/security/' }, { label: 'Architecture', link: '/architecture/' }] },
      ],
    }),
  ],
});
