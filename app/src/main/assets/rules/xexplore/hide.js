// Hides the Explore page's content column only on /explore (search, trends, "for you"),
// leaving the rest of X readable. Path-scoped and language-independent; a <style> is
// added/removed as the SPA changes the path (watched via MutationObserver).
(function () {
  var ID = "tst-x-explore";
  var SEL = 'div[data-testid="primaryColumn"]';
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
