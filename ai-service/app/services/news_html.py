"""Small, bounded parsers for public news lists and their original article bodies."""
import re
from datetime import datetime
from html.parser import HTMLParser
from urllib.parse import urljoin, urlsplit, urlunsplit
from zoneinfo import ZoneInfo

VOID = {"area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr"}
BODY_MARKERS = {"detail", "articlebody", "articlecontent", "article-content", "article_content", "trs_editor", "left_zw", "pages_content", "zoom"}
BRANDS = {"news.cn": "新华网", "cnr.cn": "央广网", "gov.cn": "中国政府网", "chinanews.com.cn": "中国新闻网"}


def source_name(url: str) -> str:
    host = urlsplit(url).hostname or "来源"
    brand = next((name for suffix, name in BRANDS.items() if host == suffix or host.endswith("." + suffix)), host)
    rss = {"china.xml": "时政", "world.xml": "国际", "finance.xml": "财经", "edu.xml": "教育", "fz.xml": "法治"}
    section = rss.get(urlsplit(url).path.rsplit("/", 1)[-1])
    return brand + (" · " + section if section else "")


def decode_html(raw: bytes, encoding: str = "utf-8") -> str:
    declared = re.search(br"charset\s*=\s*[\"']?([\w-]+)", raw[:6000], re.I)
    codec = declared.group(1).decode("ascii") if declared else encoding
    if codec.lower() in {"gb2312", "gbk"}:
        codec = "gb18030"
    try:
        return raw.decode(codec, errors="replace")
    except LookupError:
        return raw.decode("utf-8", errors="replace")


class ArticleHTML(HTMLParser):
    def __init__(self):
        super().__init__()
        self.stack = []
        self.blocks = []
        self.dates = []
        self.visible = []

    def handle_starttag(self, tag, attrs):
        attributes = dict(attrs)
        if tag == "meta":
            key = (attributes.get("name") or attributes.get("property") or "").lower()
            if key in {"pubdate", "publishdate", "publish_date", "date", "article:published_time", "dc.date.issued"}:
                self.dates.append(attributes.get("content", ""))
        if tag not in VOID:
            self.stack.append(tag)
        markers = {attributes.get("id", "").lower(), *attributes.get("class", "").lower().split()}
        if tag in {"div", "article", "section"} and (markers & BODY_MARKERS or tag == "article"):
            self.blocks.append({"depth": len(self.stack), "closed": False, "parts": []})
        if tag in {"p", "div", "br", "li", "h1", "h2"}:
            self.handle_data("\n")

    def handle_endtag(self, tag):
        if tag not in self.stack:
            return
        depth = len(self.stack) - self.stack[::-1].index(tag)
        for block in self.blocks:
            if block["depth"] >= depth:
                block["closed"] = True
        del self.stack[depth - 1:]

    def handle_data(self, data):
        if any(tag in self.stack for tag in ("script", "style", "noscript")):
            return
        self.visible.append(data)
        for block in self.blocks:
            if not block["closed"]:
                block["parts"].append(data)

    def content(self) -> str:
        values = ["\n".join(line.strip() for line in "".join(block["parts"]).splitlines() if line.strip()) for block in self.blocks]
        return max(values, key=len, default="")[:30000]

    def published(self):
        values = self.dates + re.findall(r"\b20\d{2}[-/]\d{1,2}[-/]\d{1,2}[ T]\d{2}:\d{2}(?::\d{2})?", "".join(self.visible))[:1]
        for value in values:
            try:
                date = datetime.fromisoformat(value.strip().replace("/", "-").replace("Z", "+00:00"))
                return date if date.tzinfo else date.replace(tzinfo=ZoneInfo("Asia/Shanghai"))
            except ValueError:
                continue
        return None


class NewsLinks(HTMLParser):
    def __init__(self, url: str):
        super().__init__()
        self.url = url
        self.links = []
        self.current = None

    def handle_starttag(self, tag, attrs):
        if tag == "a":
            attributes = dict(attrs)
            self.current = [attributes.get("href", ""), [], attributes.get("title", ""), [], 0]
        elif self.current and tag in {"strong", "h1", "h2", "h3", "h4"}:
            self.current[4] += 1

    def handle_data(self, data):
        if self.current:
            self.current[1].append(data)
            if self.current[4]:
                self.current[3].append(data)

    def handle_endtag(self, tag):
        if tag == "a" and self.current:
            href, chunks, fallback = self.current[:3]
            parts = urlsplit(urljoin(self.url, href))
            # Some publishers still emit HTTP links; only upgrade the same known publisher.
            host = parts.hostname or ""
            known = any(host == suffix or host.endswith("." + suffix) for suffix in ("news.cn", "cnr.cn", "gov.cn"))
            if parts.scheme == "http" and known and not parts.username and not parts.password and parts.port in (None, 80):
                parts = parts._replace(scheme="https", netloc=host)
            path = parts.path
            article_path = (re.search(r"/20\d{6}/[^/]+/c\.html$", path)
                            or re.search(r"/20\d{6}/t20\d{6}_\d+\.shtml$", path)
                            or re.search(r"/20\d{2}-\d{2}/\d{2}/content_\d+\.htm$", path)
                            or re.search(r"/20\d{4}/content_\d+\.htm$", path))
            title = " ".join("".join(self.current[3] or chunks).split()) or fallback
            if host in {"www.gov.cn", "gov.cn"} and not path.startswith(("/yaowen/", "/zhengce/")):
                article_path = False
            if known and article_path and len(title) >= 6:
                self.links.append((title[:500], urlunsplit(parts._replace(fragment=""))))
            self.current = None
        elif self.current and tag in {"strong", "h1", "h2", "h3", "h4"} and self.current[4]:
            self.current[4] -= 1


def article_html(raw: bytes, encoding: str):
    parser = ArticleHTML()
    parser.feed(decode_html(raw, encoding))
    return parser.content(), parser.published()


def list_links(raw: bytes, encoding: str, url: str):
    parser = NewsLinks(url)
    parser.feed(decode_html(raw, encoding))
    # Most recent URLs first, so pinned historical stories do not displace daily news.
    def date_key(item):
        match = re.search(r"/(20\d{6})/", item[1])
        return match.group(1) if match else ""
    return sorted(dict((link, title) for title, link in parser.links).items(), key=lambda item: date_key((item[1], item[0])), reverse=True)
