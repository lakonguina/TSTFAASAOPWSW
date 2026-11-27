// Hides TikTok's "For You" video feed on the web home ("/") and /foryou, so the endless
// recommendation stream doesn't auto-play on arrival; search and specific videos still work.
(function () {
  var ID = "tst-tt-foryou";
  var SEL = '[data-e2e="recommend-list-item-container"], div[data-e2e="feed-video"]';
  function onTarget() {
    var p = location.pathname;
    return p === "/" || p.indexOf("/foryou") === 0;
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
