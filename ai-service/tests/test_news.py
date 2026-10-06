import asyncio
import socket
from datetime import datetime, timezone
import httpx
import pytest
from pydantic import ValidationError
from app.core.errors import ServiceError
from app.core.settings import Settings
from app.models.news import NewsAnalysis
from app.services.news import NewsFetcher, plain_text
from app.services.news_html import ArticleHTML, decode_html


async def test_feed_preserves_original_source_and_missing_publish_date():
    config = Settings(_env_file=None, news_feeds="https://trusted.example/feed", news_allowed_hosts="trusted.example")
    xml = b'<rss version="2.0"><channel><title>Test source</title><item><title>Original title</title><link>https://trusted.example/article</link><description>Actual supplied feed text</description></item></channel></rss>'
    transport = httpx.MockTransport(lambda request: httpx.Response(200, content=xml))
    def resolver(*args, **kwargs):
        return [(socket.AF_INET, socket.SOCK_STREAM, 6, "", ("8.8.8.8", 443))]
    batch = await NewsFetcher(config, transport, resolver).fetch()
    article = batch.articles[0]
    assert article.title == "Original title" and article.source == "Test source"
    assert article.sourceUrl == "https://trusted.example/article"
    assert article.publishTime is None and article.sourceUnverified
    assert article.fetchTime <= datetime.now(timezone.utc)


async def test_feed_rejects_private_address_and_no_configuration():
    config = Settings(_env_file=None, news_allowed_hosts="trusted.example")
    with pytest.raises(ServiceError, match="NEWS_FEEDS_EMPTY"):
        await NewsFetcher(config).fetch()
    def resolver(*args, **kwargs):
        return [(socket.AF_INET, socket.SOCK_STREAM, 6, "", ("127.0.0.1", 443))]
    with pytest.raises(ServiceError, match="NEWS_URL_REJECTED"):
        await NewsFetcher(config, resolver=resolver).validate_url("https://trusted.example/feed")
    assert plain_text("<p>真实内容</p><script>forged</script>") == "真实内容"
    with pytest.raises(ValidationError):
        NewsAnalysis.model_validate({"summary": "valid-looking", "sourceUrl": "https://fabricated.example"})


async def test_configured_proxy_receives_https_connect_request():
    requests = []

    async def proxy(reader, writer):
        requests.append(await reader.readuntil(b"\r\n\r\n"))
        writer.write(b"HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\n\r\n")
        await writer.drain()
        writer.close()
        await writer.wait_closed()

    async with await asyncio.start_server(proxy, "127.0.0.1", 0) as server:
        port = server.sockets[0].getsockname()[1]
        config = Settings(_env_file=None, news_feeds="https://trusted.example/feed",
                          news_allowed_hosts="trusted.example", news_proxy_url=f"http://127.0.0.1:{port}")
        def resolver(*args, **kwargs):
            return [(socket.AF_INET, socket.SOCK_STREAM, 6, "", ("8.8.8.8", 443))]
        with pytest.raises(ServiceError, match="NEWS_FETCH_FAILED"):
            await NewsFetcher(config, resolver=resolver).fetch()
    assert len(requests) == 1
    assert requests[0].startswith(b"CONNECT trusted.example:443 HTTP/1.1\r\n")


async def test_multiple_sources_interleave_articles_and_report_partial_failure():
    today = datetime.now(timezone.utc)
    day = today.strftime("%Y%m%d")
    link = f"https://www.news.cn/politics/{day}/abcdef/c.html"
    config = Settings(_env_file=None, news_feeds="https://trusted.example/feed,https://broken.example/feed",
                      news_sites="https://www.news.cn/politics/", news_allowed_hosts="trusted.example,broken.example,www.news.cn", news_max_articles=2)
    feed = '<rss version="2.0"><channel><title>RSS source</title>' + ''.join(
        f'<item><title>RSS article {i}</title><link>https://trusted.example/{i}</link><description>{"真实 RSS 内容" * 20}</description></item>'
        for i in range(5)) + '</channel></rss>'
    listing = f'<a href="{link}">最新科技政策报道原文</a><a href="https://unlisted.example/private">不允许的站点</a>'
    article = f'<meta name="publishdate" content="{today.date()}"><nav>导航不是正文</nav><div id="detail"><p>{"科技新闻原文段落" * 20}</p><script>伪造内容</script></div><footer>底部广告</footer>'
    def response(request):
        if request.url.host == "broken.example":
            return httpx.Response(503)
        value = article if str(request.url) == link else listing if request.url.host == "www.news.cn" else feed
        return httpx.Response(200, text=value)
    def resolver(*args, **kwargs):
        return [(socket.AF_INET, socket.SOCK_STREAM, 6, "", ("8.8.8.8", 443))]
    batch = await NewsFetcher(config, httpx.MockTransport(response), resolver).fetch()
    assert [item.source for item in batch.articles] == ["RSS source", "新华网"]
    assert batch.articles[1].sourceUrl == link
    assert "导航" not in batch.articles[1].content and "广告" not in batch.articles[1].content and "伪造" not in batch.articles[1].content
    assert batch.articles[1].publishTime.date() == today.date()
    assert batch.failedFeeds == 1
    assert [(source.status, source.articles) for source in batch.sources] == [("ok", 1), ("failed", 0), ("ok", 1)]


async def test_rss_summary_cannot_bypass_private_article_rejection():
    config = Settings(_env_file=None, news_feeds="https://trusted.example/feed", news_allowed_hosts="trusted.example,private.example")
    text = "原文提供的真实文字" * 20
    xml = f'<rss version="2.0"><channel><item><title>私有地址</title><link>https://private.example/article</link><description>{text}</description></item><item><title>正常新闻</title><link>https://trusted.example/article</link><description>{text}</description></item></channel></rss>'
    def resolver(host, *args, **kwargs):
        return [(socket.AF_INET, socket.SOCK_STREAM, 6, "", ("127.0.0.1" if host == "private.example" else "8.8.8.8", 443))]
    batch = await NewsFetcher(config, httpx.MockTransport(lambda request: httpx.Response(200, text=xml)), resolver).fetch()
    assert [item.title for item in batch.articles] == ["正常新闻"]


async def test_slow_source_does_not_discard_successful_source_or_exceed_request_budget():
    config = Settings(_env_file=None, news_feeds="https://trusted.example/feed,https://slow.example/feed",
                      news_allowed_hosts="trusted.example,slow.example", ai_timeout_seconds=11)
    text = "原文提供的真实文字" * 20
    xml = f'<rss version="2.0"><channel><item><title>正常新闻</title><link>https://trusted.example/article</link><description>{text}</description></item></channel></rss>'
    async def response(request):
        if request.url.host == "slow.example":
            await asyncio.sleep(5)
        return httpx.Response(200, text=xml)
    def resolver(*args, **kwargs):
        return [(socket.AF_INET, socket.SOCK_STREAM, 6, "", ("8.8.8.8", 443))]
    batch = await asyncio.wait_for(NewsFetcher(config, httpx.MockTransport(response), resolver).fetch(), 1.5)
    assert len(batch.articles) == 1 and batch.failedFeeds == 1
    assert batch.sources[1].errorCode == "NEWS_SOURCE_TIMEOUT"


def test_chinese_article_encoding_body_and_date_are_preserved():
    raw = '<meta charset="gb2312"><meta name="publishdate" content="2026-10-03"><div class="article-content"><p>真实中文原文</p></div><footer>导航</footer>'.encode("gb18030")
    parser = ArticleHTML()
    parser.feed(decode_html(raw))
    assert parser.content() == "真实中文原文"
    assert parser.published().isoformat() == "2026-10-03T00:00:00+08:00"


async def test_government_official_json_list_fetches_article_and_not_navigation():
    today = datetime.now(timezone.utc).date()
    link = f"https://www.gov.cn/yaowen/liebiao/{today:%Y%m}/content_1234567.htm"
    requested = []
    def response(request):
        requested.append(str(request.url))
        if request.url.path.endswith(".json"):
            return httpx.Response(200, json=[{"TITLE": "国务院政策新闻原文", "URL": link, "DOCRELPUBTIME": today.isoformat()}])
        return httpx.Response(200, text='<nav>导航菜单</nav><div class="pages_content"><p>政府网站实际提供的政策内容。</p></div>')
    def resolver(*args, **kwargs):
        return [(socket.AF_INET, socket.SOCK_STREAM, 6, "", ("8.8.8.8", 443))]
    config = Settings(_env_file=None, news_sites="https://www.gov.cn/yaowen/liebiao/", news_allowed_hosts="www.gov.cn")
    batch = await NewsFetcher(config, httpx.MockTransport(response), resolver).fetch()
    assert batch.articles[0].source == "中国政府网"
    assert batch.articles[0].sourceUrl == link
    assert batch.articles[0].publishTime.date() == today
    assert batch.articles[0].content == "政府网站实际提供的政策内容。"
    assert requested == ["https://www.gov.cn/yaowen/liebiao/YAOWENLIEBIAO.json", link]


async def test_redirects_validate_every_destination_before_fetching():
    requested = []
    config = Settings(_env_file=None, news_allowed_hosts="trusted.example,private.example")
    def resolver(host, *args, **kwargs):
        return [(socket.AF_INET, socket.SOCK_STREAM, 6, "", ("127.0.0.1" if host == "private.example" else "8.8.8.8", 443))]
    def response(request):
        requested.append(str(request.url))
        if request.url.path == "/safe":
            return httpx.Response(301, headers={"Location": "/final"})
        if request.url.path == "/private":
            return httpx.Response(302, headers={"Location": "https://private.example/article"})
        if request.url.path == "/downgrade":
            return httpx.Response(302, headers={"Location": "http://trusted.example/article"})
        return httpx.Response(200, text="真实原文")
    fetcher = NewsFetcher(config, resolver=resolver)
    async with httpx.AsyncClient(transport=httpx.MockTransport(response), follow_redirects=False) as client:
        raw, _ = await fetcher.download(client, "https://trusted.example/safe")
        assert raw.decode() == "真实原文"
        for path in ("private", "downgrade"):
            with pytest.raises(ServiceError, match="NEWS_URL_REJECTED"):
                await fetcher.download(client, "https://trusted.example/" + path)
    assert requested == ["https://trusted.example/safe", "https://trusted.example/final", "https://trusted.example/private", "https://trusted.example/downgrade"]
