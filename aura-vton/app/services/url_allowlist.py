"""Object URL allowlist — Java StorageUrlGuard ile senkron (SSRF).

Pin TTL 300s: CDN/R2 A kaydi doner; her sokette hostname DNS TOCTOU acar.
Istek: host bir kez cozulur, TCP dogrulanmis IP'ye gider. Pin disi IP'de
origin bir kez yenilenir (meşru rota), hâlâ uymazsa reddedilir.
"""

from __future__ import annotations

import ipaddress
import socket
import time
from dataclasses import dataclass
from urllib.parse import unquote, urlparse

from app.config import settings
from app.services.errors import VtonInferenceError

PIN_TTL_SEC = 300.0

_PINNED_IPS: set[ipaddress.IPv4Address | ipaddress.IPv6Address] | None = None
_ALLOWED_ORIGINS: set[tuple[str, str, int]] | None = None
_PINNED_AT: float | None = None


def _origin(scheme: str, host: str, port: int) -> tuple[str, str, int]:
    return (scheme.lower(), host.lower().rstrip("."), int(port))


def _parse_origin(raw: str) -> tuple[str, str, int] | None:
    if not raw or not str(raw).strip():
        return None
    parsed = urlparse(str(raw).strip())
    if not parsed.hostname:
        return None
    scheme = (parsed.scheme or "http").lower()
    port = parsed.port
    if port is None:
        port = 443 if scheme == "https" else 80
    return _origin(scheme, parsed.hostname, port)


def _canon(ip: ipaddress.IPv4Address | ipaddress.IPv6Address) -> ipaddress.IPv4Address | ipaddress.IPv6Address:
    mapped = getattr(ip, "ipv4_mapped", None)
    if mapped is not None:
        return mapped
    return ip


def _resolve(host: str) -> list[ipaddress.IPv4Address | ipaddress.IPv6Address]:
    infos = socket.getaddrinfo(host, None, type=socket.SOCK_STREAM)
    out: list[ipaddress.IPv4Address | ipaddress.IPv6Address] = []
    for info in infos:
        addr = info[4][0]
        out.append(_canon(ipaddress.ip_address(addr)))
    return out


def _allowed_buckets() -> set[str]:
    return {
        settings.s3_vton_bucket,
        settings.s3_wardrobe_bucket,
        settings.s3_avatars_bucket,
    }


def allowed_origins() -> set[tuple[str, str, int]]:
    global _ALLOWED_ORIGINS
    if _ALLOWED_ORIGINS is None:
        origins: set[tuple[str, str, int]] = set()
        for raw in (settings.s3_endpoint, settings.s3_public_base_url):
            origin = _parse_origin(raw)
            if origin:
                origins.add(origin)
        _ALLOWED_ORIGINS = origins
    return _ALLOWED_ORIGINS


def _pin_ttl() -> float:
    return float(getattr(settings, "pin_ttl_seconds", PIN_TTL_SEC))


def pinned_ips(*, force: bool = False) -> set[ipaddress.IPv4Address | ipaddress.IPv6Address]:
    global _PINNED_IPS, _PINNED_AT
    now = time.monotonic()
    ttl = _pin_ttl()
    if (
        not force
        and _PINNED_IPS is not None
        and _PINNED_AT is not None
        and (now - _PINNED_AT) < ttl
    ):
        return _PINNED_IPS
    pinned: set[ipaddress.IPv4Address | ipaddress.IPv6Address] = set()
    seen_hosts: set[str] = set()
    for _scheme, host, _port in allowed_origins():
        if host in seen_hosts:
            continue
        seen_hosts.add(host)
        try:
            pinned.update(_resolve(host))
        except OSError:
            continue
    _PINNED_IPS = pinned
    _PINNED_AT = now
    return _PINNED_IPS


def reset_allowlist_cache() -> None:
    """Test: settings monkeypatch sonrasi pin yenile."""
    global _PINNED_IPS, _ALLOWED_ORIGINS, _PINNED_AT
    _PINNED_IPS = None
    _ALLOWED_ORIGINS = None
    _PINNED_AT = None


@dataclass(frozen=True)
class PinnedTarget:
    scheme: str
    hostname: str
    port: int
    ip: ipaddress.IPv4Address | ipaddress.IPv6Address
    path: str
    query: str

    @property
    def host_header(self) -> str:
        default = 443 if self.scheme == "https" else 80
        if self.port == default:
            return self.hostname
        return f"{self.hostname}:{self.port}"

    def ip_url(self) -> str:
        host = f"[{self.ip}]" if self.ip.version == 6 else str(self.ip)
        path = self.path or "/"
        query = f"?{self.query}" if self.query else ""
        return f"{self.scheme}://{host}:{self.port}{path}{query}"


def _pick_ip(resolved: list[ipaddress.IPv4Address | ipaddress.IPv6Address]):
    for addr in resolved:
        if addr.version == 4:
            return addr
    return resolved[0]


def pin_object_url(url: str) -> PinnedTarget:
    if not url or not str(url).strip():
        raise VtonInferenceError("Gorsel URL bos", code="SSRF")
    raw = str(url).strip()
    if ".." in raw or "%2e%2e" in raw.lower():
        raise VtonInferenceError("Gorsel URL yolu gecersiz", code="SSRF")
    parsed = urlparse(raw)
    scheme = (parsed.scheme or "").lower()
    if scheme not in {"http", "https"}:
        raise VtonInferenceError("Gorsel URL yalniz http/https olabilir", code="SSRF")
    if parsed.username or parsed.password:
        raise VtonInferenceError("Gorsel URL kimlik bilgisi tasiyamaz", code="SSRF")
    host = parsed.hostname
    if not host:
        raise VtonInferenceError("Gorsel URL host eksik", code="SSRF")
    port = parsed.port
    if port is None:
        port = 443 if scheme == "https" else 80
    origin = _origin(scheme, host, port)
    if origin not in allowed_origins():
        raise VtonInferenceError("Gorsel URL izin verilen depolama hostu degil", code="SSRF")
    try:
        resolved = [_canon(a) for a in _resolve(host)]
    except OSError as exc:
        raise VtonInferenceError("Gorsel URL host cozulemedi", code="SSRF") from exc
    pinned = pinned_ips()
    if not resolved or any(addr not in pinned for addr in resolved):
        pinned = pinned_ips(force=True)
        if not resolved or any(addr not in pinned for addr in resolved):
            raise VtonInferenceError("Gorsel URL DNS hedefi depolama IP'si degil", code="SSRF")
    path = unquote(parsed.path or "")
    if ".." in path or ".." in (parsed.path or ""):
        raise VtonInferenceError("Gorsel URL yolu gecersiz", code="SSRF")
    if not _path_matches_bucket(path):
        raise VtonInferenceError("Gorsel URL bucket/yol sablonu uyusmuyor", code="SSRF")
    return PinnedTarget(
        scheme=scheme,
        hostname=host,
        port=port,
        ip=_pick_ip(resolved),
        path=parsed.path or "/",
        query=parsed.query or "",
    )


def assert_object_url_allowed(url: str) -> None:
    pin_object_url(url)


def _path_matches_bucket(path: str) -> bool:
    if not path or path == "/":
        return False
    rest = path[1:] if path.startswith("/") else path
    if rest.startswith("memory/"):
        rest = rest[len("memory/") :]
    slash = rest.find("/")
    if slash <= 0 or slash == len(rest) - 1:
        return False
    bucket = rest[:slash]
    key = rest[slash + 1 :]
    if bucket not in _allowed_buckets():
        return False
    return bool(key) and ".." not in key
