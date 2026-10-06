# Sentinel documentation site

Run `npm ci`, `npm run dev`, or `npm run build` from this directory. Use Node.js 22.19.0 as pinned
in `.nvmrc`.

## GitHub Pages setup

Before the first deployment, open the repository's **Settings → Pages** and choose **GitHub
Actions** as the build and deployment source. The workflow validates pull requests and deploys
pushes to `main`.

For the default project site, the URL is `https://<owner>.github.io/<repository>/`. To use a custom
domain, configure it in **Settings → Pages**, point its DNS records at GitHub Pages, and add a
`CNAME` file under `public/` containing the domain. Set `SITE_URL` to the custom site's origin and
`BASE_PATH` to `/` for a domain served from its root. These variables override values derived from
`GITHUB_REPOSITORY`; configure them as repository Actions variables so the workflow passes them to
the build.

## Local production-path preview

Pass the repository slug to build with the same base path GitHub Pages will use, then preview the
generated site:

```sh
GITHUB_REPOSITORY=owner/repo npm run build && npm run preview
```

For a custom domain, use `SITE_URL=https://docs.example.com BASE_PATH=/` in place of (or alongside)
`GITHUB_REPOSITORY`.
