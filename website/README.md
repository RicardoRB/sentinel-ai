# Ward documentation site

Run `npm ci`, `npm run dev`, or `npm run build` from this directory. Use Node.js 22.22.0 as pinned
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

## Writing docs

Readers scan these pages for one option or one behavior. Keep them scannable:

- Keep prose paragraphs at 80 words or fewer, with one topic each.
- Put lookup facts (options, exit codes, per-gate or per-tool behavior) in tables or lists.
- Describe the common path first. Edge cases, verification evidence, and migration notes go in later
  sections, `<details>` blocks, or an `Aside`.
- Keep a heading's text when other pages link to its anchor.

Command pages use this layout:

1. One-sentence purpose.
2. A `sh` usage block.
3. `## Options`: a table of every option, including `-C <dir>`.
4. Behavior sections, common path first.
5. Edge cases and troubleshooting.
6. `## Exit codes`: a `Code | Meaning` table.

To list paragraphs over the limit, run this from `src/content/docs`; no output means every page
passes:

```sh
find . -name '*.mdx' -exec awk 'BEGIN{RS=""} FNR==1 && /^---/{next} !/^(```|\||#|<|-|\*|[0-9]+\.|import)/{w=split($0,a,/[ \t\n]+/); if(w>80) print FILENAME": "w" words"}' {} +
```
