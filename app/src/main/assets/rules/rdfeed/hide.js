// Hides Reddit's home feed on the front page and the default listing sorts (and r/popular,
// r/all), so there's no endless scroll on arrival — but a specific subreddit or post still
// opens. Targets new Reddit's <shreddit-feed> and the older posts-list test id.
(function () {
  var ID = "tst-reddit-feed";
  var SEL = 'shreddit-feed, [data-testid="posts-list"]';
  function onTarget() {
    var p = location.pathname;
    return p === "/" || /^\/(best|hot|new|top|rising)\b/.test(p) ||
      p.indexOf("/r/popular") === 0 || p.indexOf("/r/all") === 0;
  }
  function apply() {
    var el = document.getElementById(ID);
    if (onTarget()) {
      if (!el) {
        el = document.createElement("style");
        el.id = ID;
        el.textContent = SEL + ' { display: none !important; }';
        (document.head || document.documentElement).appendChild(el);
      }
    } else if (el) { el.remove(); }
  }
  apply();
  new MutationObserver(apply).observe(document.documentElement, { childList: true, subtree: true });
})();
