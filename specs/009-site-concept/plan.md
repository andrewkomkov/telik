# Plan: The site as a product page

- One HTML file for each language (`site/index.html`, `site/ru/index.html`) and one `site/style.css`.
- The scenes are HTML elements with CSS gradients. The page needs only two images: the mascot and the Open Graph picture.
- Motion: CSS scroll-driven animations (`animation-timeline: view()`) where the browser has them. Other browsers show
  the final state. The page works without JavaScript.
- Glass: `backdrop-filter: blur() saturate()`, an inner highlight with `box-shadow: inset`, and a gradient edge.
- The Open Graph picture `assets/og.jpg` is a screenshot of the hero, taken with Playwright from the shared media tools.
- Check: Playwright screenshots at 1440 px and 375 px in both languages, a check of the privacy pages, and the
  `pages.yml` deploy.
