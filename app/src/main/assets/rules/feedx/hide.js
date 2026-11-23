// Hides the X home timeline, language-independent: targets data-testid (not localized
// aria-label text) and only on the /home path, so threads and profiles stay readable.
// A <style> is added/removed as the SPA changes the path (watched via MutationObserver).
(function () {
  var ID = "tst-hide-feed";
  function onHome() {
    var p = location.pathname;
    return p === "/home" || p === "/";
  }
  function apply() {
    var el = document.getElementById(ID);
    if (onHome()) {
      if (!el) {
        el = document.createElement("style");
        el.id = ID;
        el.textContent = 'div[data-testid="primaryColumn"] section { display: none !important; }';
        (document.head || document.documentElement).appendChild(el);
      }
    } else if (el) {
      el.remove();
    }
  }
  apply();
  new MutationObserver(apply).observe(document.documentElement, { childList: true, subtree: true });
})();
