import links from '../../../shared/content/links.v0.json' with { type: 'json' };
import recovery from '../../../shared/content/recovery.v0.json' with { type: 'json' };

// The same files the server serves from /api/v1/content/recovery and /links, bundled so help works offline.

const LINKS = new Map(links.links.map((link) => [link.id, link]));

export const ALL_LINKS = links.links;

export function linkUrl(id) {
  const link = LINKS.get(id);
  if (!link) throw new Error(`unknown link ${id}`);
  return link.url;
}

/** Both recovery paths in order, with catalogue text from `t(lang, key)`. */
export function recoveryPaths(lang, t) {
  return Object.entries(recovery.paths).map(([id, steps]) => ({
    id,
    title: t(lang, `recovery.${id}.title`),
    steps: steps.map((step) => ({
      text: t(lang, step.text_key),
      link: step.link ? { id: step.link, url: linkUrl(step.link), label: t(lang, LINKS.get(step.link).label_key) } : null,
    })),
  }));
}
