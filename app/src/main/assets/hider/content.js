// Hides enabled XPath elements on the current site. CSS display:none only, never a
// reload. A MutationObserver re-applies on SPA re-renders (e.g. the X/Twitter feed).
let rules = [];

function matchesSite(host, site) {
  return host === site || host.endsWith("." + site);
}

function hide() {
  const host = location.hostname;
  for (const r of rules) {
    if (!matchesSite(host, r.site)) continue;
    let res;
    try {
      res = document.evaluate(r.xpath, document, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);
    } catch (e) {
      continue;
    }
    for (let i = 0; i < res.snapshotLength; i++) {
      const n = res.snapshotItem(i);
      if (n && n.style && n.getAttribute("data-tst-hidden") !== "1") {
        n.style.setProperty("display", "none", "important");
        n.setAttribute("data-tst-hidden", "1");
      }
    }
  }
}

browser.runtime.onMessage.addListener((msg) => {
  if (msg && msg.type === "rules") { rules = msg.rules || []; hide(); }
});

browser.runtime.sendMessage({ type: "get" }).then((msg) => {
  if (msg && msg.type === "rules") { rules = msg.rules || []; hide(); }
}).catch(() => {});

new MutationObserver(hide).observe(document.documentElement, { childList: true, subtree: true });
