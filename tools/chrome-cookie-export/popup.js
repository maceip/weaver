const DOMAINS = [
  "stitch.withgoogle.com",
  "accounts.google.com",
  "google.com",
  "app-companion-430619.appspot.com",
];

const SESSION_NAMES = /OSID|SID|LSID|ACCOUNT|SAPISID|SSID|__Secure|__Host/;

async function collectCookies() {
  const seen = new Set();
  const cookies = [];
  for (const domain of DOMAINS) {
    const list = await chrome.cookies.getAll({ domain });
    for (const c of list) {
      const key = `${c.domain}|${c.name}|${c.path}`;
      if (seen.has(key)) continue;
      seen.add(key);
      cookies.push({
        host: c.domain,
        name: c.name,
        value: c.value,
        path: c.path,
        secure: c.secure,
        expires: c.expirationDate ?? null,
        httpOnly: c.httpOnly,
        session: c.session,
      });
    }
  }
  return { exportedAt: new Date().toISOString(), domains: DOMAINS, cookies };
}

function downloadJson(payload) {
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  chrome.downloads.download({
    url,
    filename: "weaver-cookies-export.json",
    saveAs: true,
  });
}

function setStatus(text) {
  document.getElementById("status").textContent = text;
}

document.getElementById("export").addEventListener("click", async () => {
  setStatus("Collecting…");
  const payload = await collectCookies();
  const session = payload.cookies.filter((c) => SESSION_NAMES.test(c.name));
  const stitch = session.filter((c) => c.host.includes("stitch"));
  setStatus(
    `cookies=${payload.cookies.length} session=${session.length}\nstitch OSID: ${
      stitch.some((c) => c.name === "OSID") ? "yes" : "MISSING"
    }`,
  );
  if (!chrome.downloads) {
    setStatus("Copy JSON manually (downloads permission not declared).");
    window.__payload = payload;
    return;
  }
  downloadJson(payload);
});

document.getElementById("copy").addEventListener("click", async () => {
  const payload = window.__payload ?? (await collectCookies());
  await navigator.clipboard.writeText(JSON.stringify(payload, null, 2));
  setStatus("Copied JSON to clipboard.");
});
