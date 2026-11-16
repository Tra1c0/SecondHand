// SPDX-License-Identifier: GPL-3.0-only
// Renders the legacy launcher PNGs (Android 5-7, no adaptive icons) from the logo sources in
// docs/referenzen/logo/. Each icon shows the middle 72 x 72 of the 108 x 108 canvas, background plus
// foreground, masked as a rounded square (radius 18 % of the size) for ic_launcher.png and as a
// circle for ic_launcher_round.png, transparent outside.
//
// Usage: node tools/render_logo.js   (needs the globally installed playwright with Chromium)
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const root = path.resolve(__dirname, '..');
const logoDir = path.join(root, 'docs/referenzen/logo');
const resDir = path.join(root, 'app/src/main/res');
const sizes = { mdpi: 48, hdpi: 72, xhdpi: 96, xxhdpi: 144, xxxhdpi: 192 };

// The content between <svg ...> and </svg> of a source file.
const inner = (name) => fs.readFileSync(path.join(logoDir, name), 'utf8')
  .replace(/^[\s\S]*?<svg[^>]*>/, '').replace(/<\/svg>\s*$/, '');

const background = inner('secondhand-hintergrund.svg');
const foreground = inner('secondhand-vordergrund.svg');

function svg(size, shape) {
  const clip = shape === 'round'
    ? '<circle cx="54" cy="54" r="36"/>'
    : '<rect x="18" y="18" width="72" height="72" rx="12.96" ry="12.96"/>'; // 18 % of 72
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="18 18 72 72">
  <defs><clipPath id="mask">${clip}</clipPath></defs>
  <g clip-path="url(#mask)">${background}${foreground}</g>
</svg>`;
}

(async () => {
  const browser = await chromium.launch();
  try {
    for (const [density, size] of Object.entries(sizes)) {
      const page = await browser.newPage({ viewport: { width: size, height: size }, deviceScaleFactor: 1 });
      for (const [file, shape] of [['ic_launcher.png', 'square'], ['ic_launcher_round.png', 'round']]) {
        await page.setContent(`<!doctype html><html><body style="margin:0;background:transparent">${svg(size, shape)}</body></html>`);
        const out = path.join(resDir, `mipmap-${density}`, file);
        await page.screenshot({ path: out, omitBackground: true, clip: { x: 0, y: 0, width: size, height: size } });
        console.log('wrote', path.relative(root, out), `${size}x${size}`);
      }
      await page.close();
    }
  } finally {
    await browser.close();
  }
})();
