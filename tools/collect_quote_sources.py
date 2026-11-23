"""Cache primary, public-domain source pages for an attributable offline quotation library.

Modern Sanmao excerpts are separately hand selected, short, and checked against publisher previews.
Only public text is accessed; cached source documents are research files, not app assets.
"""
from __future__ import annotations
import hashlib
import json
import logging
import re
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from urllib.parse import quote, unquote, urljoin

from bs4 import BeautifulSoup
from opencc import OpenCC
from scrapling.fetchers import Fetcher

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / "research" / "quote-sources"
CACHE.mkdir(parents=True, exist_ok=True)
CC = OpenCC("t2s")
logging.getLogger("scrapling").setLevel(logging.ERROR)


def fetch(url: str) -> str:
    path = CACHE / (hashlib.sha256(url.encode()).hexdigest()[:20] + ".html")
    if path.exists():
        return path.read_text(encoding="utf-8")
    response = Fetcher.get(url, timeout=25, retries=1)
    if response.status != 200:
        raise RuntimeError(f"HTTP {response.status}: {url}")
    html = str(response.html_content)
    path.write_text(html, encoding="utf-8")
    time.sleep(.5)
    return html


def wiki(title: str) -> str:
    return "https://zh.wikisource.org/zh-hans/" + quote(title.replace(" ", "_"), safe="/()")


def main_content(html: str):
    soup = BeautifulSoup(html, "html.parser")
    roots = soup.select(".mw-parser-output")
    if not roots:
        raise RuntimeError("Missing work text")
    root = max(roots, key=lambda node: len(node.get_text()))
    for node in root.select(".header-container, .wst-header, .ws-header, .licenseContainer, .licensetpl, .mw-editsection, .navbox, .noprint, .ws-noexport, .catlinks, .ws-data, .printfooter, .wst-footer"):
        node.decompose()
    return root


BOOKS = [
    ("许地山", "空山霝雨 (1925)"),
    ("朱自清", "背影 (散文集)"),
    ("徐志摩", "翡冷翠山居閒話"),
    ("徐志摩", "巴黎的麟爪"),
    ("徐志摩", "翡冷翠的一夜"),
    ("鲁迅", "野草"),
    ("鲁迅", "朝花夕拾"),
    ("洪应明", "菜根譚"),
    ("王永彬", "圍爐夜話"),
    ("陆绍珩（辑）", "醉古堂劍掃 (全覽)"),
]


def collect_book(pair):
    author, book = pair
    url = wiki(book)
    output = []
    try:
        html = fetch(url)
        root = main_content(html)
        # The table of contents belongs to this named work, not arbitrary category/author links.
        links = []
        for a in root.select("a[href]"):
            href = a.get("href", "")
            title = a.get("title", "")
            if not href.startswith("/wiki/") or ":" in unquote(href) or "redlink" in href:
                continue
            if title.startswith(book + "/") or any(p in title for p in ["野草/", "朝花夕拾/", "空山霝雨 (1925)/", "菜根譚/", "圍爐夜話/", "小窗幽記/"]):
                links.append((title, urljoin("https://zh.wikisource.org", href).replace("/wiki/", "/zh-hans/")))
        # Several editions use unprefixed chapter titles. Limit these to genuine text-bearing links
        # inside the contents block; navigation and general author/category links were removed above.
        if not links:
            toc = root.select_one(".ws-toc, .wst-toc, .mw-poem, ul, ol")
            if toc:
                for a in toc.select("a[href]"):
                    href = a.get("href", "")
                    title = a.get("title", "")
                    if href.startswith("/wiki/") and title and ":" not in unquote(href) and "redlink" not in href and title != book:
                        links.append((title, urljoin("https://zh.wikisource.org", href).replace("/wiki/", "/zh-hans/")))
        output.append({"author": author, "book": CC.convert(book), "chapter": CC.convert(book), "url": url, "text": root.get_text("\n", strip=True), "paragraphs": [p.get_text("", strip=True) for p in root.select("p")], "indexPage": bool(links)})
        for title, chapter_url in dict(links).items():
            try:
                chapter_root = main_content(fetch(chapter_url))
                output.append({"author": author, "book": CC.convert(book), "chapter": CC.convert(title.split("/")[-1]), "url": chapter_url, "text": chapter_root.get_text("\n", strip=True), "paragraphs": [p.get_text("", strip=True) for p in chapter_root.select("p")], "indexPage": False})
            except Exception as error:
                print(json.dumps({"chapter_error": title, "error": str(error)}, ensure_ascii=True), flush=True)
        print(json.dumps({"book": book, "pages": len(output), "chars": sum(len(x["text"]) for x in output)}, ensure_ascii=True), flush=True)
    except Exception as error:
        print(json.dumps({"book_error": book, "error": str(error)}, ensure_ascii=True), flush=True)
    return output


if __name__ == "__main__":
    records = []
    with ThreadPoolExecutor(max_workers=2) as pool:
        for pages in pool.map(collect_book, BOOKS):
            records.extend(pages)
            (CACHE / "source-records.json").write_text(json.dumps(records, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"total_pages": len(records), "total_chars": sum(len(x["text"]) for x in records)}, ensure_ascii=True))
