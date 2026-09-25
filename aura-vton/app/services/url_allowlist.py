"""Object URL allowlist — Java StorageUrlGuard ile senkron (SSRF).

Pin host-bazli. Yeni DNS IP aninda kabul edilmez: gozlem penceresi
(pin_observe_seconds + samples) tutarli olmadan promote yok. TCP pin'li IP.
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
OBSERVE_WINDOW_SEC = 300.0
OBSERVE_SAMPLES = 2

_PINNED_BY_HOST: dict[str, set[ipaddress.IPv4Address | ipaddress.IPv6Address]] | None = None
_ALLOWED_ORIGINS: set[tuple[str, str, int]] | None = None
_PINNED_AT: float | None = None
_CANDIDATES: dict[str, tuple[frozenset, float, int]] = {}


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
        for raw in (
            settings.s3_endpoint,
            settings.s3_public_base_url,
            settings.wardrobe_public_host,
            settings.vton_public_host,
            settings.avatars_public_host,
        ):
            origin = _parse_origin(raw)
            if origin:
                origins.add(origin)
        _ALLOWED_ORIGINS = origins
    return _ALLOWED_ORIGINS


def _now() -> float:
    return time.monotonic()


def _observe_window() -> float:
    return float(getattr(settings, "pin_observe_seconds", OBSERVE_WINDOW_SEC))


def _observe_samples() -> int:
    return int(getattr(settings, "pin_observe_samples", OBSERVE_SAMPLES))


def _pin_ttl() -> float:
    return float(getattr(settings, "pin_ttl_seconds", PIN_TTL_SEC))


def pinned_ips_for(host: str) -> set[ipaddress.IPv4Address | ipaddress.IPv6Address]:
    pinned_by_host()
    return _PINNED_BY_HOST.get(host.lower().rstrip("."), set()) if _PINNED_BY_HOST else set()


def pinned_ips(*, force: bool = False) -> set[ipaddress.IPv4Address | ipaddress.IPv6Address]:
    """Geriye donuk: tum host pinlerinin birlesimi (test)."""
    by_host = pinned_by_host(force=force)
    out: set[ipaddress.IPv4Address | ipaddress.IPv6Address] = set()
    for ips in by_host.values():
        out.update(ips)
    return out


def pinned_by_host(*, force: bool = False) -> dict[str, set[ipaddress.IPv4Address | ipaddress.IPv6Address]]:
    """force=True committed kümeyi saldirgan cevabiyla DEĞİŞTİRMEZ.

    Sadece bootstrap (None) veya TTL sonrasi teyit damgasi. Yeni IP yalniz
    gozlem penceresi ile promote edilir.
    """
    global _PINNED_BY_HOST, _PINNED_AT
    now = _now()
    ttl = _pin_ttl()
    if (
        not force
        and _PINNED_BY_HOST is not None
        and _PINNED_AT is not None
        and (now - _PINNED_AT) < ttl
    ):
        return _PINNED_BY_HOST
    if _PINNED_BY_HOST is None:
        next_map: dict[str, set[ipaddress.IPv4Address | ipaddress.IPv6Address]] = {}
        seen_hosts: set[str] = set()
        for _scheme, host, _port in allowed_origins():
            if host in seen_hosts:
                continue
            seen_hosts.add(host)
            try:
                next_map[host.lower()] = set(_resolve(host))
            except OSError:
                continue
        _PINNED_BY_HOST = next_map
    _PINNED_AT = now
    return _PINNED_BY_HOST


def reset_allowlist_cache() -> None:
    """Test: settings monkeypatch sonrasi pin yenile."""
    global _PINNED_BY_HOST, _ALLOWED_ORIGINS, _PINNED_AT, _CANDIDATES
    _PINNED_BY_HOST = None
    _ALLOWED_ORIGINS = None
    _PINNED_AT = None
    _CANDIDATES = {}


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
    pinned = pinned_ips_for(host)
    # Java verifyResolvedIps: allPinned → clearObserve; aksi halde gozlem.
    if resolved and all(addr in pinned for addr in resolved):
        _clear_observe(host)
    elif not _observe_and_maybe_promote(host, resolved):
        raise VtonInferenceError("Gorsel URL DNS hedefi depolama IP'si degil", code="SSRF")
    path = unquote(parsed.path or "")
    if ".." in path or ".." in (parsed.path or ""):
        raise VtonInferenceError("Gorsel URL yolu gecersiz", code="SSRF")
    if host.lower().rstrip(".") in _public_read_hosts():
        key = path[1:] if path.startswith("/") else path
        if not key:
            raise VtonInferenceError("Gorsel URL yolu gecersiz", code="SSRF")
    elif not _path_matches_bucket(path):
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


def _clear_observe(host: str) -> None:
    """Pin ile eslesen cozumleme bu hostun aday sayacini siler (Java clearObserve)."""
    _CANDIDATES.pop(host.lower().rstrip("."), None)


def _observe_and_maybe_promote(
    host: str,
    resolved: list[ipaddress.IPv4Address | ipaddress.IPv6Address],
) -> bool:
    """Kalici hijack ilk istekte False. Tutarli aday pencere+ornek sonra True."""
    global _PINNED_BY_HOST, _CANDIDATES
    key = host.lower().rstrip(".")
    seen = frozenset(resolved)
    now = _now()
    current = _CANDIDATES.get(key)
    if current is None or current[0] != seen:
        _CANDIDATES[key] = (seen, now, 1)
        return False
    ips, first, samples = current
    samples += 1
    _CANDIDATES[key] = (ips, first, samples)
    if samples < _observe_samples() or (now - first) < _observe_window():
        return False
    if _PINNED_BY_HOST is None:
        _PINNED_BY_HOST = {}
    _PINNED_BY_HOST[key] = set(ips)
    _CANDIDATES.pop(key, None)
    return True


def _public_read_hosts() -> set[str]:
    hosts: set[str] = set()
    for raw in (
        settings.wardrobe_public_host,
        settings.vton_public_host,
        settings.avatars_public_host,
    ):
        origin = _parse_origin(raw)
        if origin:
            hosts.add(origin[1])
    return hosts


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
