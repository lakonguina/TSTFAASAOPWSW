// Blanks the Explore grid on /explore so there's no discovery rabbit-hole; the rest of
// Instagram (DMs, profiles, search) stays reachable. Instagram's main content lives in a
// single <main>, so hiding it on this path removes the grid without touching the nav.
(function () {
  var ID = "tst-ig-explore";
  var SEL = 'main';
  function onTarget() { return location.pathname.indexOf("/explore") === 0; }
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
