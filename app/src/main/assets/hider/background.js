// Relays the enabled element rules from the app to the content scripts.
// The app pushes rules over the native port; content scripts pull or receive them.
let rules = [];

let port;
try { port = browser.runtime.connectNative("browser"); } catch (e) { port = null; }
port && port.onMessage.addListener((msg) => {
  rules = (msg && msg.rules) || [];
  browser.tabs.query({}).then((tabs) => {
    for (const t of tabs) {
      browser.tabs.sendMessage(t.id, { type: "rules", rules }).catch(() => {});
    }
  });
});

browser.runtime.onMessage.addListener((msg, sender, send) => {
  if (msg && msg.type === "get") send({ type: "rules", rules });
  return true;
});
