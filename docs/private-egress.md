# Docker 私有出网转发

宿主机能直连模型和新闻来源、Docker 网桥连接超时时，可以启用 `egress-relay`。它只接受所配置私有网段的 HTTPS CONNECT。默认目标限于 `AI_BASE_URL`、`EMBEDDING_BASE_URL` 的域名和 `NEWS_ALLOWED_HOSTS`，默认端口为 443。用户自定义模型目标由 AI 服务以 `AI_SERVICE_TOKEN` 生成绑定主机、端口和时间的 HMAC 签名，代理仅接受 60 秒内的有效签名；因此添加提供商时无需重启代理或修改固定白名单。DNS 结果必须全部为公网地址，连接使用校验后的 IP。TLS 在客户端和目标服务之间建立；转发服务不读取模型 API Key、请求正文或模型输出。

## 本服务器的配置

当前项目网桥为 `br-4d35a9710c01`，网段为 `172.19.0.0/16`，网关为 `172.19.0.1`。私有 `.env` 设置：

```dotenv
COMPOSE_PROFILES='app,egress'
EGRESS_BIND_HOST='172.19.0.1'
EGRESS_ALLOWED_CIDR='172.19.0.0/16'
AI_PROXY_URL='http://172.19.0.1:17890'
NEWS_PROXY_URL='http://172.19.0.1:17890'
```

新增的 UFW 规则：

```bash
sudo ufw allow in on br-4d35a9710c01 proto tcp \
  from 172.19.0.0/16 to 172.19.0.1 port 17890 \
  comment 'civil-service-ai private egress relay'
```

该服务绑定网关地址，来源网段与目标域名或目标签名同时受检查。容器以普通用户运行，文件系统只读，全部 Linux capabilities 被移除；只获得转发配置与验证签名所需的内部服务密钥。重启策略为 `unless-stopped`。

## 更新和检查

```bash
docker compose config --quiet
docker compose --profile app --profile egress up -d --build --wait ai-service egress-relay
docker compose --profile app --profile egress ps
docker compose logs --tail 20 egress-relay
```

变更服务器默认模型域名或新闻白名单后，同时重新创建 `ai-service` 与 `egress-relay`。用户自定义接入在前端管理，使用 HTTPS；经签名的自定义目标可以使用自己的 HTTPS 端口。网桥被删除重建或迁移服务器后，先用 `docker network inspect civil-service-ai_study` 核对实际网关、网段和网桥标识，再对应更新配置和精确的 UFW 规则。

## 关闭

将 `AI_PROXY_URL`、`NEWS_PROXY_URL` 清空，移除 `COMPOSE_PROFILES` 中的 `egress`，重新创建 AI 服务，然后停止转发服务并删除同一条 UFW 规则：

```bash
docker compose --profile app up -d --force-recreate ai-service
docker compose --profile egress stop egress-relay
sudo ufw delete allow in on br-4d35a9710c01 proto tcp \
  from 172.19.0.0/16 to 172.19.0.1 port 17890
```

## 针对转发的测试

```bash
cd deploy/egress
python3 -m unittest -v test_relay
```

测试涵盖来源网段、目标域名和端口、凭据拒绝、内网 DNS 地址拒绝、DNS 地址固定、连接上限和实际字节转发，另验证自定义目标签名绑定主机与端口、过期拒绝，以及签名隧道字节传输。新闻测试另验证 `NEWS_PROXY_URL` 产生 HTTPS CONNECT 请求。
