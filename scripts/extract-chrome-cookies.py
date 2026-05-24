#!/usr/bin/env python3
"""Decrypt Google/Stitch cookies from local Chromium profiles (macOS).

Writes JSON to .local-secrets/ (gitignored). Requires Keychain approval for
"Chrome Safe Storage" on first run.

  python3 scripts/extract-chrome-cookies.py
  python3 scripts/extract-chrome-cookies.py --browser canary --out .local-secrets/my.json
"""

from __future__ import annotations

import argparse
import json
import os
import sys

try:
    from browser_cookie3 import Chrome
except ImportError:
    print("Install deps: python3 -m venv .venv && .venv/bin/pip install browser-cookie3 pycryptodome", file=sys.stderr)
    sys.exit(1)

PROFILES = {
    "chrome": "~/Library/Application Support/Google/Chrome/Default/Cookies",
    "canary": "~/Library/Application Support/Google/Chrome Canary/Default/Cookies",
    "beta": "~/Library/Application Support/Google/Chrome Beta/Default/Cookies",
    "dev": "~/Library/Application Support/Google/Chrome Dev/Default/Cookies",
}

DOMAINS = (
    "stitch.withgoogle.com",
    "accounts.google.com",
    "google.com",
    "app-companion-430619.appspot.com",
)


def export_cookies(cookie_file: str) -> list[dict]:
    seen: set[tuple[str, str, str]] = set()
    rows: list[dict] = []
    for domain in DOMAINS:
        jar = Chrome(cookie_file=cookie_file, domain_name=domain).load()
        for c in jar:
            key = (c.domain, c.name, c.path)
            if key in seen:
                continue
            seen.add(key)
            rows.append(
                {
                    "host": c.domain,
                    "name": c.name,
                    "value": c.value,
                    "path": c.path,
                    "secure": c.secure,
                    "expires": c.expires,
                    "httpOnly": c._rest.get("HTTPOnly") == "",
                }
            )
    return rows


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--browser", choices=PROFILES.keys(), default="canary")
    parser.add_argument(
        "--out",
        default=".local-secrets/chrome-cookies.json",
        help="Output path (default: .local-secrets/chrome-cookies.json)",
    )
    args = parser.parse_args()

    cookie_file = os.path.expanduser(PROFILES[args.browser])
    if not os.path.isfile(cookie_file):
        print(f"Cookie DB not found: {cookie_file}", file=sys.stderr)
        sys.exit(1)

    cookies = export_cookies(cookie_file)
    session = [
        c
        for c in cookies
        if any(
            token in c["name"]
            for token in ("OSID", "SID", "LSID", "ACCOUNT", "SAPISID", "SSID", "__Secure", "__Host")
        )
    ]

    out_path = os.path.expanduser(args.out)
    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
    payload = {"browser": args.browser, "cookieDb": cookie_file, "domains": list(DOMAINS), "cookies": cookies}
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(payload, f, indent=2)

    print(f"Wrote {len(cookies)} cookies ({len(session)} session-ish) -> {out_path}")
    for c in sorted(session, key=lambda x: (x["host"], x["name"])):
        if "stitch" in c["host"] or c["host"] == "accounts.google.com" or c["host"] == ".google.com":
            print(f"  {c['host']}\t{c['name']}\tlen={len(c['value'])}")


if __name__ == "__main__":
    main()
