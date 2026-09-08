"""Check the static policy site's links, semantics, and privacy constraints."""

from html.parser import HTMLParser
from pathlib import Path
import re
from urllib.parse import unquote, urlsplit


ROOT = Path(__file__).resolve().parents[2] / "site"
POLICIES = {"privacy", "terms", "refunds", "cookies"}


class Page(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.nodes: list[tuple[str, dict[str, str | None]]] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        self.nodes.append((tag, dict(attrs)))


def luminance(color: str) -> float:
    rgb = [int(color[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    linear = [v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4 for v in rgb]
    return sum(value * weight for value, weight in zip(linear, (0.2126, 0.7152, 0.0722)))


def check() -> None:
    css = (ROOT / "styles.css").read_text(encoding="utf-8")
    assert not re.search(r"@import|url\(\s*['\"]?(?:https?:)?//", css, re.IGNORECASE), "Remote CSS resource"
    meta = re.search(r"\.meta\s*\{[^}]*color:\s*#([0-9a-fA-F]{6})", css)
    background = re.search(r"body\s*\{[^}]*background:\s*#([0-9a-fA-F]{6})", css)
    assert meta and background
    dark, light = sorted((luminance(background[1]), luminance(meta[1])))
    ratio = (light + 0.05) / (dark + 0.05)
    assert ratio >= 4.5, f"Small header text contrast: {ratio:.2f}:1"
    for policy in POLICIES:
        assert (ROOT / policy / "index.html").is_file(), f"Missing {policy} page"
    pages = list(ROOT.rglob("*.html"))
    for path in pages:
        page = Page()
        page.feed(path.read_text(encoding="utf-8"))
        tags = [tag for tag, _ in page.nodes]
        assert tags.count("h1") == 1 and tags.count("main") == 1, path
        assert any(tag == "html" and attrs.get("lang") == "en" for tag, attrs in page.nodes), path
        assert not {"script", "iframe", "embed", "object", "form"}.intersection(tags), path
        links: set[str] = set()
        for tag, attrs in page.nodes:
            if tag == "img":
                assert "alt" in attrs, path
            reference = attrs.get("href") if tag in {"a", "link"} else attrs.get("src")
            if not reference:
                continue
            url = urlsplit(reference)
            if url.scheme or url.netloc:
                assert tag == "a", f"Third-party resource: {path}: {reference}"
                continue
            target = (path.parent / unquote(url.path)).resolve() if url.path else path
            if target.is_dir():
                target /= "index.html"
            assert target.is_relative_to(ROOT) and target.is_file(), f"Broken link: {path}: {reference}"
            if tag == "a" and target.name == "index.html":
                links.add(target.parent.name)
            if url.fragment:
                target_page = Page()
                target_page.feed(target.read_text(encoding="utf-8"))
                assert any(attrs.get("id") == url.fragment for _, attrs in target_page.nodes), reference
        assert POLICIES <= links, f"Policy navigation incomplete: {path}"
    print(f"PASS: {len(pages)} pages; policy navigation, local assets, semantics; contrast {ratio:.2f}:1")


if __name__ == "__main__":
    check()
