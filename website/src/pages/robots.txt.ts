const sitemapUrl = new URL(
  `${import.meta.env.BASE_URL}sitemap-index.xml`,
  import.meta.env.SITE,
).href;

export function GET() {
  return new Response(`User-agent: *\nAllow: /\nSitemap: ${sitemapUrl}\n`, {
    headers: { 'Content-Type': 'text/plain; charset=utf-8' },
  });
}
