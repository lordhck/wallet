"""Put a new release on docs/index.html. Run by the release workflow."""
import html
import os
import re

PAGE = "docs/index.html"
EMPTY = "Nothing released yet."

tag = os.environ["GITHUB_REF_NAME"]
repo = os.environ["GITHUB_REPOSITORY"]
sha256 = os.environ["SHA256"]
commit = os.environ["COMMIT"]
message = html.escape(os.environ["MESSAGE"])
version = tag.removeprefix("v")
base = f"https://github.com/{repo}"


def between(page, name):
    pattern = rf"(<!-- {name}:start -->\n)(.*?)(<!-- {name}:end -->)"
    return re.compile(pattern, re.S), re.search(pattern, page, re.S).group(2)


page = open(PAGE, encoding="utf8").read()

pattern, _ = between(page, "release")
release = f"""<i>Version {version}</i> (<a href="{base}/commit/{commit}">{commit}</a>)

<p>
Download: <a href="{base}/releases/download/{tag}/btc-wallet-{version}.apk">[ android ]</a><br>
<code>sha256 {sha256}</code>
</p>
"""
page = pattern.sub(lambda m: m[1] + release + m[3], page)

pattern, old = between(page, "changelog")
log = f"{version}  {message}\n" + old.replace(EMPTY + "\n", "")
page = pattern.sub(lambda m: m[1] + log + m[3], page)

open(PAGE, "w", encoding="utf8").write(page)
