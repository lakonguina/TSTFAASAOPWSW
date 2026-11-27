// Blanks the Reels player on /reels so the short-video feed is unreachable, while the rest of
// Instagram stays usable. Reels render inside the single <main>, hidden only on this path.
(function () {
  var ID = "tst-ig-reels";
  var SEL = 'main';
  function onTarget() { return location.pathname.indexOf("/reels") === 0; }
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
