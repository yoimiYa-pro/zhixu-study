import asyncio
import calendar
import ipaddress
import json
import socket
from datetime import datetime, timedelta, timezone
from zoneinfo import ZoneInfo
from html.parser import HTMLParser
from urllib.parse import urljoin, urlsplit, urlunsplit

import feedparser
import httpx
from app.core.errors import ServiceError, log
from app.core.settings import Settings
from app.models.news import Article, FeedBatch, NewsSource, SourceResult
from app.services.news_html import article_html, list_links, source_name


class TextParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.parts = []
        self.hidden = 0

    def handle_starttag(self, tag, attrs):
        if tag in ("script", "style", "noscript"):
            self.hidden += 1
        elif tag in ("p", "div", "br", "li", "h1", "h2") and not self.hidden:
            self.parts.append("\n")

    def handle_endtag(self, tag):
        if tag in ("script", "style", "noscript") and self.hidden:
            self.hidden -= 1

    def handle_data(self, data):
        if not self.hidden:
            self.parts.append(data)


def plain_text(html: str) -> str:
    parser = TextParser()
    parser.feed(html[:500000])
    return "\n".join(line.strip() for line in "".join(parser.parts).splitlines() if line.strip())


class NewsFetcher:
    def __init__(self, settings: Settings, transport=None, resolver=None):
        self.settings = settings
        self.transport = transport
        self.resolver = resolver or socket.getaddrinfo
        self.hosts = {host.strip().lower() for host in settings.news_allowed_hosts.split(",") if host.strip()}

    async def validate_url(self, url: str):
        try:
            parts = urlsplit(url)
            allowed = parts.scheme == "https" and parts.hostname and not parts.username and not parts.password and parts.port in (None, 443) and parts.hostname.lower() in self.hosts
        except ValueError:
            allowed = False
        if not allowed:
            raise ServiceError("NEWS_URL_REJECTED", "来源地址不在已配置的 HTTPS 域名列表中", 400)
        try:
            addresses = await asyncio.to_thread(self.resolver, parts.hostname, 443, type=socket.SOCK_STREAM)
            if not addresses or any(not ipaddress.ip_address(item[4][0]).is_global for item in addresses):
                raise ServiceError("NEWS_URL_REJECTED", "来源不能指向内网地址", 400)
        except (socket.gaierror, ValueError):
            raise ServiceError("NEWS_FETCH_FAILED", "时政来源地址暂时无法解析") from None

    async def download(self, client, url: str):
        # Automatic redirects stay disabled; validate each explicit hop before sending it.
        for hop in range(4):
            await self.validate_url(url)
            async with client.stream("GET", url) as response:
                if 300 <= response.status_code < 400:
                    location = response.headers.get("Location")
                    if not location or hop == 3:
                        raise ServiceError("NEWS_URL_REJECTED", "来源跳转次数过多或缺少目标地址", 400)
                    url = urljoin(url, location)
                    continue
                response.raise_for_status()
                body = bytearray()
                async for chunk in response.aiter_bytes():
                    body.extend(chunk)
                    if len(body) > 2_000_000:
                        raise ServiceError("NEWS_FETCH_FAILED", "时政来源响应过大")
                return bytes(body), response.encoding or "utf-8"

    def sources(self):
        sources = []
        for kind, configured in (("rss", self.settings.news_feeds), ("site", self.settings.news_sites)):
            for url in dict.fromkeys(value.strip() for value in configured.split(",") if value.strip()):
                sources.append(NewsSource(name=source_name(url), url=url, kind=kind))
        if len(sources) > 12:
            raise ServiceError("NEWS_SOURCE_LIMIT", "最多配置 12 个时政来源", 400)
        return sources

    def canonical_link(self, link: str):
        parts = urlsplit(link)
        if parts.scheme == "http" and parts.hostname in self.hosts and not parts.username and not parts.password and parts.port in (None, 80):
            parts = parts._replace(scheme="https", netloc=parts.hostname)
        return urlunsplit(parts._replace(fragment=""))

    async def fetch(self):
        sources = self.sources()
        if not sources:
            raise ServiceError("NEWS_FEEDS_EMPTY", "尚未配置时政 RSS / Atom 或网站来源")
        reports = [SourceResult(**source.model_dump(), status="ok", articles=0) for source in sources]
        article_failures = [0 for source in sources]
        semaphore = asyncio.Semaphore(4)
        started = asyncio.get_running_loop().time()
        budget = max(1, self.settings.ai_timeout_seconds - 10)
        deadline = started + budget
        cutoff = datetime.now(timezone.utc) - timedelta(days=self.settings.news_lookback_days)
        async with httpx.AsyncClient(timeout=httpx.Timeout(15, connect=5), follow_redirects=False, trust_env=False,
                                     transport=self.transport, proxy=self.settings.news_proxy_url.get_secret_value() or None,
                                     headers={"User-Agent": "CivilStudy/1.0 (private learning feed reader)"}) as client:
            async def bounded(coroutines, stop_at=deadline):
                tasks = [asyncio.create_task(coroutine) for coroutine in coroutines]
                if not tasks:
                    return []
                done, pending = await asyncio.wait(tasks, timeout=max(0, stop_at - asyncio.get_running_loop().time()))
                for task in pending:
                    task.cancel()
                await asyncio.gather(*tasks, return_exceptions=True)
                return [task.result() if task in done and not task.cancelled() and task.exception() is None else None for task in tasks]

            async def candidates(index, source):
                try:
                    government = source.kind == "site" and urlsplit(source.url).hostname in {"www.gov.cn", "gov.cn"} and urlsplit(source.url).path.startswith("/yaowen/")
                    download_url = urlunsplit(urlsplit(source.url)._replace(path="/yaowen/liebiao/YAOWENLIEBIAO.json", query="", fragment="")) if government else source.url
                    async with semaphore:
                        raw, encoding = await self.download(client, download_url)
                    if source.kind == "site":
                        if government:
                            records = json.loads(raw)
                            if not isinstance(records, list) or not records:
                                raise ServiceError("NEWS_FETCH_FAILED", "政府网要闻列表格式已改变")
                            items = []
                            for record in records[:60]:
                                if not isinstance(record, dict) or not record.get("TITLE") or not record.get("URL"):
                                    continue
                                published = None
                                try:
                                    published = datetime.fromisoformat(record.get("DOCRELPUBTIME", ""))
                                    if not published.tzinfo:
                                        published = published.replace(tzinfo=ZoneInfo("Asia/Shanghai"))
                                except (ValueError, TypeError):
                                    pass
                                items.append((plain_text(record["TITLE"])[:500], self.canonical_link(record["URL"]), "", published))
                            return items
                        links = list_links(raw, encoding, source.url)
                        if not links:
                            raise ServiceError("NEWS_FETCH_FAILED", "网站列表中没有可读取的文章")
                        return [(title, link, "", None) for link, title in links[:30]]
                    parsed = feedparser.parse(raw)
                    if not parsed.entries:
                        raise ServiceError("NEWS_FETCH_FAILED", "来源中没有可读取的文章")
                    publisher = plain_text(parsed.feed.get("title", ""))[:200]
                    if publisher:
                        reports[index].name = publisher
                    items = []
                    for entry in parsed.entries[:60]:
                        title, link = plain_text(entry.get("title", ""))[:500], entry.get("link")
                        if not title or not link:
                            continue
                        date_parts = entry.get("published_parsed")
                        published = datetime.fromtimestamp(calendar.timegm(date_parts), timezone.utc) if date_parts else None
                        content = plain_text(entry.get("content", [{}])[0].get("value", "") or entry.get("summary", ""))[:30000]
                        items.append((title, self.canonical_link(link), content, published))
                    return items
                except (httpx.HTTPError, ServiceError, ValueError, OverflowError):
                    reports[index].status = "failed"
                    reports[index].errorCode = "NEWS_FETCH_FAILED"
                    log.warning("news_source_failed index=%s", index)
                    return []

            pools = await bounded((candidates(i, source) for i, source in enumerate(sources)), started + budget * 0.4)
            for index, pool in enumerate(pools):
                if pool is None:
                    reports[index].status = "failed"
                    reports[index].errorCode = "NEWS_SOURCE_TIMEOUT"
                    pools[index] = []
            # Interleave sources before downloading articles; one publisher cannot occupy all slots.
            queue, seen = [], set()
            for offset in range(max((len(pool) for pool in pools), default=0)):
                for index, pool in enumerate(pools):
                    if offset >= len(pool):
                        continue
                    title, link, content, published = pool[offset]
                    if link in seen or (published and published < cutoff):
                        continue
                    seen.add(link)
                    queue.append((index, title, link, content, published))

            async def article(item):
                index, title, link, content, published = item
                try:
                    await self.validate_url(link)
                except (ServiceError, ValueError):
                    article_failures[index] += 1
                    return None
                try:
                    if sources[index].kind == "site" or len(content) < 80:
                        async with semaphore:
                            raw, encoding = await self.download(client, link)
                        extracted, original_date = article_html(raw, encoding)
                        content = extracted or content
                        published = published or original_date
                except (httpx.HTTPError, ServiceError, ValueError, OverflowError):
                    # A failed article is never replaced with the list page or generated text.
                    if sources[index].kind != "rss":
                        article_failures[index] += 1
                        return None
                now = datetime.now(timezone.utc)
                if not content or (published and published < cutoff):
                    return None
                if published and published > now:
                    published = None
                return index, Article(title=title, content=content, source=reports[index].name, sourceUrl=link,
                                      publishTime=published, fetchTime=now, sourceUnverified=published is None)

            articles = []
            # At most two bounded waves, even if a source contains many broken article links.
            for offset in range(0, min(len(queue), self.settings.news_max_articles * 2), self.settings.news_max_articles):
                results = await bounded(article(item) for item in queue[offset:offset + self.settings.news_max_articles])
                for result in results:
                    if result and len(articles) < self.settings.news_max_articles:
                        index, value = result
                        articles.append(value)
                        reports[index].articles += 1
                if len(articles) >= self.settings.news_max_articles:
                    break
        if not articles:
            raise ServiceError("NEWS_FETCH_FAILED", "没有成功取得近期时政内容，请检查来源配置或网络")
        for index, report in enumerate(reports):
            if not report.articles and article_failures[index]:
                report.status = "failed"
                report.errorCode = "NEWS_ARTICLE_FAILED"
        return FeedBatch(articles=articles, failedFeeds=sum(report.status == "failed" for report in reports), sources=reports)
