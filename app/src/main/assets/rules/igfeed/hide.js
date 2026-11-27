// Hides the Instagram home feed (the scrolling posts on "/") while leaving stories, search
// and direct profile/post visits intact. Feed posts are real <article> elements, a stable
// hook under Instagram's hashed classes.
(function () {
  var ID = "tst-ig-feed";
  var SEL = 'main article';
  function onTarget() { return location.pathname === "/"; }
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
