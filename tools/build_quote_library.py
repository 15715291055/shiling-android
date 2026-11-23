"""Build a frozen, non-repeating library from attributable published passages.

No invented quotations or author-style generations. Traditional/simplified conversion
and whitespace/punctuation normalization are the only transformations.
"""
from __future__ import annotations
import collections
import hashlib
import json
import random
import re
import unicodedata
from functools import lru_cache
from pathlib import Path
from bs4 import BeautifulSoup
from opencc import OpenCC
from collect_quote_sources import CACHE, ROOT, fetch, main_content

CC = OpenCC("t2s")
ASSETS = ROOT / "app" / "src" / "main" / "assets"


@lru_cache(maxsize=4096)
def normalized(value):
    return "".join(c for c in CC.convert(unicodedata.normalize("NFKC", value)) if c.isalnum())


def clean(value):
    return CC.convert(re.sub(r"\s+", "", value)).replace("．", "。").replace("﹖", "？")


SANMAO = [
    ("天下万物的来和去，都有它的时间。", "把快乐当传染病·没有找呀", "https://www.crown.com.tw/view.aspx?bc=003107&p=4"),
    ("知音，能有一个已经很好了，不必太多。", "把快乐当传染病·最重要的是被爱吗", "https://www.crown.com.tw/view.aspx?bc=003107&p=5"),
    ("做一个有弹性的人，当是我们一生追寻的目标。", "把快乐当传染病·隔离与沟通", "https://www.crown.com.tw/view.aspx?bc=003107&p=3"),
    ("耐心、韧性、谅解、宽容、包涵，都是爱的代名词。", "把快乐当传染病·隔离与沟通", "https://www.crown.com.tw/view.aspx?bc=003107&p=2"),
]

# Individually reviewed modern prose, not automatically attributed dialogue or poetry
# quoted by a prose author. The following must match the named work's original exactly.
CURATED_PROSE = [
    ("许地山", "生", "我底生活好像一棵龙舌兰，一叶一叶，慢慢地长起来。"),
    ("许地山", "春底林野", "在这万山环抱底桃林中，除那班爱闹的孩子以外，万物把春光领略得心眼都迷蒙了。"),
    ("朱自清", "荷塘月色", "月光如流水一般，静静地泻在这一片叶子和花上。"),
    ("朱自清", "荷塘月色", "我爱热闹，也爱冷静；爱群居，也爱独处。"),
    ("朱自清", "荷塘月色", "像今晚上，一个人在这苍茫的月下，什么都可以想，什么都可以不想，便觉是个自由的人。"),
    ("朱自清", "荷塘月色", "这一片天地好像是我的；我也像超出了平常的自己，到了另一世界里。"),
    ("朱自清", "荷塘月色", "曲曲折折的荷塘上面，弥望的是田田的叶子。"),
    ("朱自清", "荷塘月色", "薄薄的青雾浮起在荷塘里。"),
    ("朱自清", "荷塘月色", "这是独处的妙处，我且受用这无边的荷香月色好了。"),
    ("徐志摩", "翡冷翠山居闲话", "我们浑朴的天真是像含羞草似的，一经同伴的抵触，他就卷了起来，但在澄静的日光下，和风中，他的姿态是自然的，他的生活是无阻碍的。"),
    ("徐志摩", "巴黎的麟爪", "生活的趣味大半就在不预期的发见，要是所有的明天全是今天刻板的化身，那我们活什么来了？"),
    ("鲁迅", "好的故事", "我真爱这一篇好的故事，趁碎影还在，我要追回他，完成他，留下他。"),
    ("鲁迅", "好的故事", "我在朦胧中，看见一个好的故事。"),
]


def main():
    records = json.loads((CACHE / "source-records.json").read_text(encoding="utf-8"))
    quotes = []
    proof = []
    seen = set()

    def append(text, author, work, url, original, role="author"):
        key = normalized(text)
        if key in seen:
            return False
        assert key in normalized(original), (author, work, text)
        seen.add(key)
        identifier = hashlib.sha256((author + "|" + key).encode()).hexdigest()[:20]
        work = work.replace(" (散文集)", "").replace(" (全览)", "")
        quotes.append({"id": identifier, "text": text, "author": author, "work": work, "sourceUrl": url, "creditRole": role})
        proof.append({"id": identifier, "sourceUrl": url, "sourceSha256": hashlib.sha256(original.encode()).hexdigest(), "exactNormalizedMatch": True})
        return True

    for text, work, url in SANMAO:
        assert len(normalized(text)) <= 25  # Minimal publisher-preview excerpt per source page.
        original = BeautifulSoup(fetch(url), "html.parser").get_text("", strip=True)
        append(text, "三毛", work, url, original)

    for author, chapter, text in CURATED_PROSE:
        source = next(row for row in records if row["author"] == author and row["chapter"] == chapter)
        work = source["book"].replace("空山霝雨 (1925)", "空山灵雨")
        if work != chapter:
            work += "·" + chapter
        append(text, author, work, source["url"], source["text"])

    pools = collections.defaultdict(list)
    bad = re.compile(r"[�□■◆※]|[A-Za-z0-9]|作者|编辑|版本|作品|校勘|本书|本集|出版|附录|版权|版权所有|此页面|维基|本作品|底本|录入|序言|译文|卷[一二三四五六七八九十]|杀|尸|流血|奴才|妓|淫|奸|蛮|贼|枪|死|仇|仇恨|毒|羡官|功名|朝廷|太监")
    dependent = re.compile(r"^(他|她|它|其|于是|因而|所以|但是|然而|便|乃|至于|这时|那时|然后|此时|余曰|子曰|我说|你说|咱|那人)")
    keywords = ["生活", "日子", "生命", "岁月", "人间", "读书", "自然", "快乐", "自由", "希望", "心", "光", "花", "风", "清", "静", "山", "水", "月", "明", "真", "善", "闲", "雨", "春", "秋", "梦", "书"]
    for record in records:
        author = record["author"]
        if author == "陈继儒" or not record["text"]:
            continue  # Historical attribution was explicitly rejected during source review.
        if author not in {"洪应明", "王永彬", "陆绍珩（辑）"}:
            continue  # Modern authors are individually reviewed above.
        original = record["text"]
        if record.get("indexPage"):
            continue
        paragraphs = record.get("paragraphs", [])
        if author == "洪应明":
            # The aphorisms are numbered li elements; the p elements are other writers' prefaces.
            paragraphs = [node.get_text("", strip=True) for node in main_content(fetch(record["url"])).select("ol > li")]
        elif author == "王永彬":
            paragraphs = paragraphs[2:]  # Omit book introduction and dated signature.
        elif author == "陆绍珩（辑）":
            root = main_content(fetch(record["url"]))
            paragraphs = []
            for node in root.select("p"):
                heading = node.find_previous(["h1", "h2", "h3"])
                heading_text = re.sub(r"\s+", "", heading.get_text()) if heading else ""
                if re.match(r"卷[一二三四五六七八九十]+", heading_text):
                    paragraphs.append(node.get_text("", strip=True))
        # Preserve paragraph boundaries: never manufacture a sentence by joining unrelated
        # aphorisms, captions, or two separate entries missing a full stop in the transcription.
        paragraphs = [clean(p) for p in paragraphs if p.strip()]
        if author in {"许地山", "朱自清", "徐志摩", "鲁迅"}:
            paragraphs = [p for p in paragraphs if not any(c in p for c in "‘’“”「」『』")]
        flattened = "\n".join(p if p.endswith(tuple("。！？!?")) else p + "。" for p in paragraphs)
        for text in re.findall(r"[^。！？!?\n]+[。！？!?]", flattened):
            text = text.strip("，；：、—· ")
            if re.search(r"译者|编者|本文|原稿|版本|校勘|一九[〇一二三四五六七八九]|写完于|写于|上帝|凶险|暴行|丧事|惨淡|鬼|诅咒|奴|剥削|太太|小姐|议员|王子|管家|父亲赋闲|无隅|种族|有句|云：|诗曰|诗云|古人云|书云|俗语|妇人|女子|男尊|女卑", text):
                continue
            if not 16 <= len(text) <= 64 or bad.search(text) or dependent.search(text):
                continue
            if any(c in text for c in "‘’“”「」『』[]【】〈〉《》〖〗"):
                continue  # Do not turn a character's dialogue/embedded citation into an author motto.
            if text.count("（") != text.count("）") or text.count("(") != text.count(")"):
                continue
            if sum("\u4e00" <= c <= "\u9fff" for c in text) < len(text) * .65:
                continue
            score = sum(3 if len(word) > 1 else 1 for word in keywords if word in text)
            score += 3 if 22 <= len(text) <= 46 else 0
            score += 1 if "，" in text else 0
            # Favor each work's own text. Headers/navigation are removed, and exact matching
            # to the cached original is required again before inclusion.
            work = record["chapter"] if record["chapter"] != record["book"] else record["book"]
            if work.startswith("空山霝雨"):
                work = "空山灵雨" + ("·" + work.split("/")[-1] if "/" in work else "")
            if record["chapter"] != record["book"]:
                work = record["book"].replace("空山霝雨 (1925)", "空山灵雨") + "·" + record["chapter"]
            pools[author].append((score, text, work, record["url"], original))

    order = ["许地山", "朱自清", "徐志摩", "鲁迅", "洪应明", "王永彬", "陆绍珩（辑）"]
    # Maximize source diversity rather than claiming every harvested sentence is a famous saying.
    counts = {}
    target = 1200
    for author in order:
        candidates = sorted(pools[author], key=lambda row: (-row[0], row[2], row[1]))
        limit = {"许地山": 140, "朱自清": 180, "徐志摩": 80, "鲁迅": 90, "洪应明": 400, "王永彬": 160, "陆绍珩（辑）": target}[author]
        selected = 0
        for _, text, work, url, original in candidates:
            if selected >= limit or len(quotes) >= target:
                break
            if append(text, author, work, url, original, "compiler" if "（辑）" in author else "author"):
                selected += 1
        counts[author] = {"eligible": len(candidates), "selected": selected}
    assert len(quotes) >= 1096, (len(quotes), counts)
    assert len({normalized(row["text"]) for row in quotes}) == len(quotes)
    assert all(row["author"] != "三毛" for row in quotes[len(SANMAO):])
    ASSETS.mkdir(parents=True, exist_ok=True)
    (ASSETS / "daily_quotes.json").write_text(json.dumps(quotes, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (CACHE / "quote-match-proof.json").write_text(json.dumps(proof, ensure_ascii=False, indent=2), encoding="utf-8")
    summary = {"count": len(quotes), "sanmaoShortExcerpts": len(SANMAO), "uniqueNormalizedText": len(seen), "authorCounts": dict(collections.Counter(row["author"] for row in quotes)), "candidateSelection": counts, "selection": "文献摘句，不宣称每条均为广为流传的名言", "sanmaoVerification": "皇冠出版方试阅页逐句核对，非网络语录合集", "otherSources": "Wikisource原文转录；每条逐字规范化匹配；古籍辑录作品保留（辑）署名，不假称编者原创"}
    (ROOT / "verification" / "quote-library-validation.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=True, indent=2))


if __name__ == "__main__":
    main()
