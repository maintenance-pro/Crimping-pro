# LEONI.com — Design Pattern Extraction

Source: https://www.leoni.com/ (site CMS: TYPO3). Notes below combine what the fetched HTML/metadata expose directly (colors, structure, copy, nav) with visual conventions typical of this corporate-industrial site type. Where a value could not be confirmed from live CSS (cross-origin, not renderable in this tool), it's flagged **[inferred]** — verify against the live site's stylesheet before treating as exact.

## 1. Brand & Color

- **Primary brand blue**: `#003D89` (LEONI logo/brand blue, medium-dark cyan-blue).
- **Theme / header blue**: `#002857` (from `<meta name="theme-color">` — darker navy, likely used for header bar / mobile chrome).
- Palette role **[inferred]**: navy/blue as the dominant corporate color (trust, engineering, automotive-industrial), white background, dark charcoal/near-black body text, a neutral gray for secondary text and hairlines. Corporate industrial sites like this rarely use more than one accent hue — expect blue + white + gray/black only, no secondary brights.
- Logo: wordmark "LEONI" as flat SVG, used at consistent size in header and footer, always on white.

## 2. Typography **[inferred — confirm exact family from live CSS]**

- Sans-serif throughout (headline + body) — standard for German industrial/automotive corporate sites (custom brand grotesk or a licensed humanist sans).
- Clear size hierarchy: large bold hero headline → medium bold section headings ("Stories & Publications", "Markets & Solutions") → regular-weight body copy → small-caps or small uppercase for eyebrow labels/category tags (e.g. "Press release", "Story", "Corporate").
- Dates in news items formatted `DD.MM.YYYY` as a small label prefix before the headline.

## 3. Layout & Grid

- Wide, centered content column with generous max-width (desktop-first corporate site), full-bleed hero image/video.
- Section rhythm: each homepage module is a full-width band with its own heading, alternating plain-white and image-backed bands.
- News/press feed renders as a **responsive card grid** (multi-column on desktop, stacking on mobile), each card = thumbnail (optional) + date + category tag(s) + headline + short teaser, entire card wrapped in one link.
- "Load more stories" pattern instead of pagination — keeps the feed inline and progressively disclosed.

## 4. Navigation

- **Multi-level mega menu**: top-level items (About us, Markets & Solutions, Jobs & Careers, Press, Publications) each expand into a nested flyout — up to 3 levels deep (e.g. About us → Sustainability → ReWire Program → Focus Area Climate). Each nested level repeats a "Main menu" breadcrumb-style back link at the top.
- Persistent **utility bar above main nav**: "LEONI Worldwide" link + language switcher (English/Deutsch) — repeated in header and footer.
- Logo links home, sits left; primary nav right or below in a secondary row.
- "Skip to content" link present for accessibility.
- Footer nav mirrors top-level structure minimally: Markets & Solutions shortcuts, legal links (Imprint, Data protection, Legal notice, Privacy Settings), social icons (Facebook, LinkedIn, YouTube), copyright line with current year.

## 5. Key Homepage Components

**Hero band**
Full-bleed image, large H1 tied to a link (e.g. "Welcome to LEONI worldwide"), 2–3 sentence mission paragraph, single primary CTA button ("Discover our career opportunities").

**Video/media module**
Secondary heading + short description + embedded video thumbnail with play affordance + a privacy-consent gate ("Update privacy settings") shown before third-party video loads — standard EU cookie/consent pattern.

**Stories & Publications feed**
- Filter chips/tags above the grid: Press release, Corporate, Wiring Systems, Products & Solutions, Automotive Cables, Story, Trends — plus a "Show filter" toggle for more granular filtering.
- Each card: date, category tag(s), headline, one-line teaser, optional photo.
- Repeated at both top and bottom of the long feed (a "recap" heading), with "load more" between.

**Interactive/3D showcase module**
"Innovation inside: Click. Explore. Understand." — a promotional block introducing an interactive 3D product explorer, followed by a plain-text list of product-line links (Low Voltage Wiring Harness, High Voltage Wiring Harness, High Voltage Battery Solutions, etc.) as a simple in-page sitemap/quick-links list.

**Editorial/CTA image panel**
Recurring pattern: large photo (clickable) → small eyebrow label ("The Sustainability Report 2024") → bold headline link → 2–3 sentence description → text CTA link ("Discover our sustainability program"). Used for Sustainability and reused for other feature stories — a reusable "feature panel" component.

**Markets & Solutions closer**
Simple two-item link list before the footer — acts as a secondary/tertiary nav reinforcing key business lines.

**Footer**
Tagline ("Empowering connections.") → social icons → group name + strategic-partner note (external link) → sub-brand link (LEONI Cable Solutions) + Worldwide link → language switcher → legal link row → copyright.

**Locale/exit interstitial**
Modal-style confirmation when leaving the domain or switching site/region ("You are now on ###SITE###" / "Leave the website?" with Cancel/Leave) — used for both entry and outbound link warnings, typical of large multi-region corporate sites.

## 6. Interaction & Content Patterns

- Tag-driven filtering rather than a search-first UX for news content.
- Consent-gated embeds (video) shown by default with an explicit unlock action, not autoplay.
- Every visual "panel" (hero, feature, sustainability) follows the same anatomy: image → eyebrow → headline (linked) → short copy → single text or button CTA. Reuse this as the core content block across new pages.
- Copy tone: factual, third-person corporate voice; short declarative mission statements ("Empowering connections — it's in LEONI's DNA."); press items are terse and dated, not narrative.
- Multi-language support baked into IA (language switcher present in header and footer) — design should treat text as translatable (avoid tight-fixed-width text containers).

## 7. Recommendations if Building New Pages in This Style

- Anchor all UI to the navy `#003D89` / `#002857` pairing; keep everything else neutral (white/gray/near-black) — do not introduce new brand colors.
- Reuse the "feature panel" (image + eyebrow + headline + copy + CTA) as the primary flexible content block; it scales from homepage hero to any promo module.
- Use tag chips for taxonomy-driven content grids (press, stories) rather than dropdown filters.
- Keep nav deep-hierarchy friendly (mega-menu with breadcrumb-back), since the IA here is 3 levels deep in places.
- Always account for a language switcher and translatable copy lengths in any layout.
