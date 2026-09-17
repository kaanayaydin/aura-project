"""Model calistirma cihazinin (device) secimi.

Varsayilan davranis "auto": Apple Silicon GPU'su (MPS) varsa onu, yoksa CUDA'yi,
hicbiri yoksa CPU'yu secer. Ortam degiskeniyle ("cpu", "mps", "cuda") elle
sabitlenebilir; istenen cihaz kullanilamiyorsa CPU'ya guvenli sekilde duser.

Cihaz cozumlemesi torch import'u gerektirdigi icin sonuc onbellege alinir;
her model yuklemesinde tekrar sorgulanmaz.
"""

import logging
from typing import Dict, Optional

logger = logging.getLogger(__name__)

AUTO = "auto"
CPU = "cpu"
MPS = "mps"
CUDA = "cuda"

# Ayni istek icin tekrar tekrar torch'a sormamak adina cozulen degerler saklanir.
_resolved_cache: Dict[str, str] = {}


def resolve_device(preference: str = AUTO, purpose: Optional[str] = None) -> str:
    """Istenen cihaz tercihini kullanilabilir bir cihaz adina cevirir."""
    key = (preference or AUTO).strip().lower()
    if key in _resolved_cache:
        return _resolved_cache[key]

    resolved = _resolve(key, purpose)
    _resolved_cache[key] = resolved
    return resolved


def _resolve(preference: str, purpose: Optional[str]) -> str:
    label = purpose or "model"

    try:
        import torch
    except ImportError:
        logger.warning("torch bulunamadi, %s icin CPU kullanilacak.", label)
        return CPU

    if preference == AUTO:
        if _mps_ready(torch):
            logger.info("%s icin Apple Silicon GPU (MPS) kullanilacak.", label)
            return MPS
        if torch.cuda.is_available():
            logger.info("%s icin CUDA kullanilacak.", label)
            return CUDA
        logger.info("%s icin hizlandirici bulunamadi, CPU kullanilacak.", label)
        return CPU

    if preference == MPS and not _mps_ready(torch):
        logger.warning("MPS istendi ama kullanilamiyor; %s CPU'da calisacak.", label)
        return CPU

    if preference == CUDA and not torch.cuda.is_available():
        logger.warning("CUDA istendi ama kullanilamiyor; %s CPU'da calisacak.", label)
        return CPU

    return preference


def _mps_ready(torch) -> bool:
    """MPS hem derlenmis hem calisma zamaninda erisilebilir olmali.

    `is_built()` True olup `is_available()` False olabilir (ornegin GPU erisimi
    kisitli ortamlarda); bu durumda MPS'e gecmek calisma zamani hatasi verir.
    """
    backend = getattr(torch.backends, "mps", None)
    if backend is None:
        return False
    return bool(backend.is_built() and backend.is_available())
