"""HTTP / object URL uzerinden gorsel indirme — stream + IP pin."""

from __future__ import annotations

import http.client
import io
import logging
import socket
import ssl
from urllib.parse import urljoin

import httpx
from PIL import Image

from app.config import settings
from app.services.errors import VtonInferenceError
from app.services.image_codec import decode_base64_image
from app.services.url_allowlist import PinnedTarget, pin_object_url

logger = logging.getLogger("aura.vton.fetch")

MAX_REDIRECTS = 2
_CHUNK = 64 * 1024
_REDIRECTS = {301, 302, 303, 307, 308}


def load_image_from_url(
    url: str,
    *,
    label: str = "image",
    timeout: float = 60.0,
    transport: httpx.BaseTransport | None = None,
) -> Image.Image:
    """Presigned / public URL → RGB PIL Image.

    follow_redirects kapali; Location en fazla 2 kez, her hedef pin.
    TCP dogrulanmis IP'ye; Host / TLS SNI orijinal hostname.
    Content-Length ve stream toplami ``settings.max_download_bytes``.
    """
    if not url or not str(url).strip():
        raise VtonInferenceError(f"{label} URL bos", code="BAD_IMAGE")
    current = str(url).strip()
    limit = int(settings.max_download_bytes)
    try:
        if transport is not None:
            data = _get_bounded_httpx(current, label=label, limit=limit, timeout=timeout, transport=transport)
        else:
            data = _get_bounded_httplib(current, label=label, limit=limit, timeout=timeout)
        if not data:
            raise ValueError("bos govde")
        with Image.open(io.BytesIO(data)) as img:
            img.load()
            return img.convert("RGB")
    except VtonInferenceError:
        raise
    except Exception as exc:  # noqa: BLE001
        raise VtonInferenceError(
            f"{label} URL indirilemedi: {exc}",
            code="BAD_IMAGE",
        ) from exc


def _get_bounded_httpx(
    url: str,
    *,
    label: str,
    limit: int,
    timeout: float,
    transport: httpx.BaseTransport,
) -> bytes:
    current = url
    with httpx.Client(timeout=timeout, follow_redirects=False, transport=transport) as client:
        for _ in range(MAX_REDIRECTS + 1):
            target = pin_object_url(current)
            with client.stream(
                "GET",
                target.ip_url(),
                headers={"Host": target.host_header},
            ) as response:
                if response.status_code in _REDIRECTS:
                    location = response.headers.get("location")
                    if not location:
                        raise VtonInferenceError(f"{label} yonlendirme Location yok", code="SSRF")
                    current = urljoin(current, location)
                    continue
                response.raise_for_status()
                return _read_capped_iter(response.iter_bytes(chunk_size=_CHUNK), response.headers, label, limit)
    raise VtonInferenceError(f"{label} cok fazla yonlendirme", code="SSRF")


def _get_bounded_httplib(url: str, *, label: str, limit: int, timeout: float) -> bytes:
    current = url
    for _ in range(MAX_REDIRECTS + 1):
        target = pin_object_url(current)
        status, headers, body = _httplib_get(target, timeout=timeout, limit=limit, label=label)
        if status in _REDIRECTS:
            location = headers.get("location") or headers.get("Location")
            if not location:
                raise VtonInferenceError(f"{label} yonlendirme Location yok", code="SSRF")
            current = urljoin(current, location)
            continue
        if status < 200 or status >= 300:
            raise VtonInferenceError(f"{label} URL HTTP {status}", code="BAD_IMAGE")
        return body
    raise VtonInferenceError(f"{label} cok fazla yonlendirme", code="SSRF")


class _SniHttpsConnection(http.client.HTTPSConnection):
    """TCP self.host (IP); wrap_socket SNI = orijinal hostname."""

    def __init__(self, ip: str, port: int, timeout: float, server_hostname: str, context: ssl.SSLContext):
        super().__init__(ip, port=port, timeout=timeout, context=context)
        self._sni = server_hostname

    def connect(self) -> None:
        sock = socket.create_connection((self.host, self.port), self.timeout)
        self.sock = self._context.wrap_socket(sock, server_hostname=self._sni)


def _httplib_get(
    target: PinnedTarget,
    *,
    timeout: float,
    limit: int,
    label: str,
) -> tuple[int, dict[str, str], bytes]:
    ip = str(target.ip) if target.ip.version == 4 else str(target.ip)
    path = target.path or "/"
    if target.query:
        path = f"{path}?{target.query}"
    if target.scheme == "https":
        ctx = ssl.create_default_context()
        conn: http.client.HTTPConnection = _SniHttpsConnection(
            ip, target.port, timeout, target.hostname, ctx
        )
    else:
        conn = http.client.HTTPConnection(ip, port=target.port, timeout=timeout)
    try:
        conn.putrequest("GET", path, skip_host=True, skip_accept_encoding=True)
        conn.putheader("Host", target.host_header)
        conn.putheader("Connection", "close")
        conn.endheaders()
        resp = conn.getresponse()
        headers = {k.lower(): v for k, v in resp.getheaders()}
        if resp.status in _REDIRECTS:
            resp.read()
            return resp.status, headers, b""
        body = _read_capped_file(resp, headers, label, limit)
        return resp.status, headers, body
    finally:
        conn.close()


def _declared_too_large(headers, label: str, limit: int) -> None:
    raw = None
    if hasattr(headers, "get"):
        raw = headers.get("content-length") or headers.get("Content-Length")
    if raw is None:
        return
    try:
        declared = int(raw)
    except (TypeError, ValueError) as exc:
        raise VtonInferenceError(f"{label} Content-Length gecersiz", code="BAD_IMAGE") from exc
    if declared > limit:
        raise VtonInferenceError(
            f"{label} Content-Length {declared} bayt siniri {limit} asiyor",
            code="TOO_LARGE",
        )


def _read_capped_iter(chunks, headers, label: str, limit: int) -> bytes:
    _declared_too_large(headers, label, limit)
    total = 0
    out = bytearray()
    for chunk in chunks:
        if not chunk:
            continue
        total += len(chunk)
        if total > limit:
            raise VtonInferenceError(
                f"{label} indirme {limit} bayt sinirini asti",
                code="TOO_LARGE",
            )
        out.extend(chunk)
    return bytes(out)


def _read_capped_file(resp: http.client.HTTPResponse, headers, label: str, limit: int) -> bytes:
    _declared_too_large(headers, label, limit)
    total = 0
    out = bytearray()
    while True:
        chunk = resp.read(_CHUNK)
        if not chunk:
            break
        total += len(chunk)
        if total > limit:
            resp.close()
            raise VtonInferenceError(
                f"{label} indirme {limit} bayt sinirini asti",
                code="TOO_LARGE",
            )
        out.extend(chunk)
    return bytes(out)


def resolve_image(
    *,
    image_url: str | None,
    image_base64: str | None,
    label: str,
) -> Image.Image | None:
    """URL oncelikli; yoksa base64. Ikisi de yoksa None."""
    if image_url and str(image_url).strip():
        return load_image_from_url(image_url, label=label)
    if image_base64 and str(image_base64).strip():
        return decode_base64_image(image_base64, label=label)
    return None
