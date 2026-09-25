package app.aura.backend.support;

import app.aura.backend.config.StorageProperties;
import app.aura.backend.config.VtonProperties;
import app.aura.backend.web.UnsafeObjectUrlException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * personImageUrl / wardrobe imageUrl — yalniz kendi S3/MinIO origin'imiz.
 *
 * Hostname string yetmez (DNS rebinding / TOCTOU): origin IP'leri host bazinda
 * pinlenir; istekte host BIR KEZ cozulur; TCP o dogrulanmis IP'ye acilir.
 *
 * Kalici DNS ele gecirme: yeni IP'yi aninda kabul etmeyiz. Candidate en az
 * {@link #OBSERVE_WINDOW} ve {@link #OBSERVE_SAMPLES} tutarli gozlemden sonra
 * promote edilir (CDN rotasyonu gecikir, hijack ilk istekte 403).
 */
@Component
public class StorageUrlGuard {

    public static final int MAX_DOWNLOAD_BYTES = 20 * 1024 * 1024;

    /**
     * Pin TTL: committed kumenin "hala bu host icin gecerli mi" kontrolu.
     * Yeni IP otomatik eklenmez — gozlem penceresi gerekir.
     */
    public static final Duration PIN_TTL = Duration.ofMinutes(5);

    /**
     * (a) Gozlem penceresi: yeni A kaydi N dakika + N ornek tutarli olmadan
     * origin guncellenmez. Saldirganin anlik cevabina guvenilmez; meşru CDN
     * rotasyonu pencereden sonra kabul edilir (ilk istekler 403).
     */
    public static final Duration OBSERVE_WINDOW = Duration.ofMinutes(5);

    public static final int OBSERVE_SAMPLES = 2;

    private static final Logger log = LoggerFactory.getLogger(StorageUrlGuard.class);

    private final Set<Origin> allowedOrigins;
    private final List<String> originHosts;
    private final Set<String> allowedBuckets;
    private final Function<String, InetAddress[]> resolver;
    private final Clock clock;
    private final Duration pinTtl;
    private final Duration observeWindow;
    private final int observeSamples;
    private final Set<String> publicReadHosts;
    private final Origin workerOrigin;

    private final Object pinLock = new Object();
    private volatile Map<String, Set<InetAddress>> pinnedByHost = Map.of();
    private final Map<String, ObserveState> observes = new HashMap<>();
    private volatile Instant lastRefresh;

    @Autowired
    public StorageUrlGuard(StorageProperties properties, VtonProperties vtonProperties) {
        this(
                properties,
                StorageUrlGuard::defaultResolve,
                Clock.systemUTC(),
                PIN_TTL,
                OBSERVE_WINDOW,
                OBSERVE_SAMPLES,
                vtonProperties == null ? null : vtonProperties.workerBaseUrl());
    }

    public StorageUrlGuard(StorageProperties properties) {
        this(properties, StorageUrlGuard::defaultResolve, Clock.systemUTC(), PIN_TTL, OBSERVE_WINDOW, OBSERVE_SAMPLES, null);
    }

    StorageUrlGuard(StorageProperties properties, Function<String, InetAddress[]> resolver) {
        this(properties, resolver, Clock.systemUTC(), PIN_TTL, OBSERVE_WINDOW, OBSERVE_SAMPLES, null);
    }

    StorageUrlGuard(
            StorageProperties properties,
            Function<String, InetAddress[]> resolver,
            Clock clock,
            Duration pinTtl) {
        this(properties, resolver, clock, pinTtl, pinTtl, OBSERVE_SAMPLES, null);
    }

    StorageUrlGuard(
            StorageProperties properties,
            Function<String, InetAddress[]> resolver,
            Clock clock,
            Duration pinTtl,
            Duration observeWindow,
            int observeSamples,
            String workerBaseUrl) {
        this.resolver = resolver;
        this.clock = clock;
        this.pinTtl = pinTtl == null ? PIN_TTL : pinTtl;
        this.observeWindow = observeWindow == null ? OBSERVE_WINDOW : observeWindow;
        this.observeSamples = observeSamples <= 0 ? OBSERVE_SAMPLES : observeSamples;
        this.allowedOrigins = new LinkedHashSet<>();
        this.originHosts = new ArrayList<>();
        // S3 API (AURA_S3_ENDPOINT) ve okuma tabani (AURA_S3_PUBLIC_BASE_URL).
        // R2 host'u burada sabit degil; provider degisince ikisi de yeniden pinlenir.
        addOrigin(properties.endpoint());
        addOrigin(properties.publicBaseUrl());
        addOrigin(properties.wardrobePublicHost());
        addOrigin(properties.vtonPublicHost());
        addOrigin(properties.avatarsPublicHost());
        java.util.LinkedHashSet<String> hosts = new java.util.LinkedHashSet<>();
        for (String candidate : new String[] {
                hostName(properties.wardrobePublicHost()),
                hostName(properties.vtonPublicHost()),
                hostName(properties.avatarsPublicHost())}) {
            if (!candidate.isBlank()) {
                hosts.add(candidate);
            }
        }
        this.publicReadHosts = Set.copyOf(hosts);
        this.workerOrigin = parseOrigin(workerBaseUrl);
        if (this.workerOrigin != null) {
            originHosts.add(this.workerOrigin.host());
        }
        this.allowedBuckets = Set.of(
                properties.wardrobeBucket(),
                properties.vtonBucket(),
                properties.avatarsBucket());
        refreshPinnedIps();
        log.info(
                "StorageUrlGuard origins={} pinnedByHost={} buckets={} pinTtl={} observeWindow={} worker={}",
                allowedOrigins,
                pinnedByHost,
                allowedBuckets,
                this.pinTtl,
                this.observeWindow,
                this.workerOrigin);
    }

    public void rejectUnsafeObjectUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return;
        }
        pin(rawUrl);
    }

    public PinnedTarget pin(String rawUrl) {
        return pinInternal(rawUrl, false);
    }

    public PinnedTarget pinResult(String rawUrl) {
        return pinInternal(rawUrl, true);
    }

    /**
     * Allowlist + DNS pin. Donen IP'ler baglanti icin kullanilmali —
     * hostname ile ikinci bir getAllByName TOCTOU acar.
     */
    private PinnedTarget pinInternal(String rawUrl, boolean resultUri) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw blocked("Gorsel URL bos");
        }
        String trimmed = rawUrl.trim();
        if (trimmed.contains("..") || trimmed.toLowerCase(Locale.ROOT).contains("%2e%2e")) {
            throw blocked("Gorsel URL yolu gecersiz");
        }
        URI uri;
        try {
            uri = URI.create(trimmed).normalize();
        } catch (IllegalArgumentException exception) {
            throw blocked("Gecersiz gorsel URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !scheme.equals("http")) {
            throw blocked("Gorsel URL yalniz http/https olabilir");
        }
        if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
            throw blocked("Gorsel URL kimlik bilgisi tasiyamaz");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw blocked("Gorsel URL host eksik");
        }
        int port = uri.getPort();
        if (port < 0) {
            port = scheme.equals("https") ? 443 : 80;
        }
        Origin origin = new Origin(scheme, host.toLowerCase(Locale.ROOT), port);
        boolean workerResult = resultUri && workerOrigin != null && workerOrigin.equals(origin);
        if (!workerResult && !allowedOrigins.contains(origin)) {
            throw blocked("Gorsel URL izin verilen depolama hostu degil");
        }
        List<InetAddress> verified = verifyResolvedIps(host);
        rejectUnsafePath(host, uri.getRawPath(), workerResult);
        String path = uri.getRawPath() == null || uri.getRawPath().isBlank() ? "/" : uri.getRawPath();
        return new PinnedTarget(scheme, host, port, path, uri.getRawQuery(), verified);
    }

    private List<InetAddress> verifyResolvedIps(String host) {
        refreshPinnedIpsIfStale();
        InetAddress[] resolved = resolveOrBlock(host);
        List<InetAddress> canonical = canonicalize(resolved);
        if (allPinned(host, canonical)) {
            clearObserve(host);
            return canonical;
        }
        if (promoteIfObserved(host, canonical)) {
            return canonical;
        }
        throw blocked("Gorsel URL DNS hedefi depolama IP'si degil");
    }

    private boolean allPinned(String host, List<InetAddress> resolved) {
        Set<InetAddress> pinned = pinnedByHost.getOrDefault(host.toLowerCase(Locale.ROOT), Set.of());
        if (pinned.isEmpty() || resolved.isEmpty()) {
            return false;
        }
        for (InetAddress address : resolved) {
            if (!pinned.contains(address)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Yeni IP'yi hemen yazmayiz. Ayni kume gozlem penceresi + min ornek
     * boyunca tutarliysa promote; flip-flop veya ilk hijack reddedilir.
     */
    private boolean promoteIfObserved(String host, List<InetAddress> canonical) {
        String key = host.toLowerCase(Locale.ROOT);
        Set<InetAddress> seen = new LinkedHashSet<>(canonical);
        Instant now = clock.instant();
        synchronized (pinLock) {
            ObserveState current = observes.get(key);
            if (current == null || !current.ips().equals(seen)) {
                observes.put(key, new ObserveState(seen, now, 1));
                log.warn("StorageUrlGuard DNS aday (henuz pinlenmedi) host={} ips={}", host, seen);
                return false;
            }
            ObserveState next = new ObserveState(current.ips(), current.firstSeen(), current.samples() + 1);
            observes.put(key, next);
            boolean ready = next.samples() >= observeSamples
                    && !now.isBefore(next.firstSeen().plus(observeWindow));
            if (!ready) {
                return false;
            }
            Map<String, Set<InetAddress>> copy = new LinkedHashMap<>(pinnedByHost);
            copy.put(key, Set.copyOf(next.ips()));
            pinnedByHost = Map.copyOf(copy);
            observes.remove(key);
            log.info("StorageUrlGuard DNS pin promote host={} ips={}", host, next.ips());
            return true;
        }
    }

    private void clearObserve(String host) {
        synchronized (pinLock) {
            observes.remove(host.toLowerCase(Locale.ROOT));
        }
    }

    private InetAddress[] resolveOrBlock(String host) {
        try {
            InetAddress[] resolved = resolver.apply(host);
            if (resolved == null || resolved.length == 0) {
                throw blocked("Gorsel URL host cozulemedi");
            }
            return resolved;
        } catch (UnsafeObjectUrlException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw blocked("Gorsel URL host cozulemedi");
        }
    }

    void refreshPinnedIpsIfStale() {
        Instant last = lastRefresh;
        Instant now = clock.instant();
        if (last != null && now.isBefore(last.plus(pinTtl))) {
            return;
        }
        // Stale: committed IPs'i DNS ile teyit et; yeni IP yazma (gozlem gerekir).
        confirmCommittedPins();
    }

    void refreshPinnedIps() {
        synchronized (pinLock) {
            Map<String, Set<InetAddress>> next = new LinkedHashMap<>();
            Set<String> seen = new LinkedHashSet<>();
            for (String host : originHosts) {
                String key = host.toLowerCase(Locale.ROOT);
                if (!seen.add(key)) {
                    continue;
                }
                try {
                    InetAddress[] addresses = resolver.apply(host);
                    next.put(key, new LinkedHashSet<>(canonicalize(addresses)));
                } catch (RuntimeException exception) {
                    log.warn("StorageUrlGuard pin DNS basarisiz host={}: {}", host, exception.toString());
                }
            }
            this.pinnedByHost = Map.copyOf(next);
            this.lastRefresh = clock.instant();
        }
    }

    private void confirmCommittedPins() {
        synchronized (pinLock) {
            this.lastRefresh = clock.instant();
        }
    }

    private void rejectUnsafePath(String host, String rawPath, boolean workerResult) {
        if (rawPath == null || rawPath.isBlank() || "/".equals(rawPath)) {
            throw blocked("Gorsel URL yolu gecersiz");
        }
        String decoded;
        try {
            decoded = URLDecoder.decode(rawPath, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw blocked("Gorsel URL yolu gecersiz");
        }
        if (decoded.contains("..") || rawPath.contains("..")) {
            throw blocked("Gorsel URL yolu gecersiz");
        }
        String path = decoded.startsWith("/") ? decoded : "/" + decoded;
        if (workerResult) {
            if (path.startsWith("/outputs/") && path.length() > "/outputs/".length()) {
                return;
            }
            throw blocked("Gorsel URL worker /outputs/ degil");
        }
        if (publicReadHosts.contains(host.toLowerCase(Locale.ROOT)) && matchesPublicKeyPath(path)) {
            return;
        }
        if (matchesMemoryPath(path) || matchesBucketPath(path)) {
            return;
        }
        throw blocked("Gorsel URL bucket/yol sablonu uyusmuyor");
    }

    private boolean matchesMemoryPath(String path) {
        if (!path.startsWith("/memory/")) {
            return false;
        }
        return bucketAndKey(path.substring("/memory/".length()));
    }

    private boolean matchesBucketPath(String path) {
        String trimmed = path.startsWith("/") ? path.substring(1) : path;
        return bucketAndKey(trimmed);
    }

    private boolean bucketAndKey(String rest) {
        int slash = rest.indexOf('/');
        if (slash <= 0 || slash == rest.length() - 1) {
            return false;
        }
        String bucket = rest.substring(0, slash);
        String key = rest.substring(slash + 1);
        if (!allowedBuckets.contains(bucket)) {
            return false;
        }
        return !key.isBlank() && !key.contains("..");
    }

    private boolean matchesPublicKeyPath(String path) {
        String trimmed = path.startsWith("/") ? path.substring(1) : path;
        return !trimmed.isBlank() && !trimmed.contains("..");
    }

    private static String hostName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(raw.trim());
            String host = uri.getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private void addOrigin(String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            URI uri = URI.create(raw.trim());
            String scheme = uri.getScheme() == null ? "http" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                log.warn("StorageUrlGuard origin host yok: {}", raw);
                return;
            }
            int port = uri.getPort();
            if (port < 0) {
                port = "https".equals(scheme) ? 443 : 80;
            }
            allowedOrigins.add(new Origin(scheme, host.toLowerCase(Locale.ROOT), port));
            originHosts.add(host);
        } catch (IllegalArgumentException exception) {
            log.warn("StorageUrlGuard origin parse edilemedi: {}", raw);
        }
    }

    private static Origin parseOrigin(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(raw.trim());
            String scheme = uri.getScheme() == null ? "http" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return null;
            }
            int port = uri.getPort();
            if (port < 0) {
                port = "https".equals(scheme) ? 443 : 80;
            }
            return new Origin(scheme, host.toLowerCase(Locale.ROOT), port);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static List<InetAddress> canonicalize(InetAddress[] addresses) {
        List<InetAddress> out = new ArrayList<>();
        if (addresses == null) {
            return out;
        }
        for (InetAddress address : addresses) {
            if (address == null) {
                continue;
            }
            out.add(canonical(address));
        }
        return out;
    }

    static InetAddress canonical(InetAddress address) {
        byte[] raw = address.getAddress();
        if (address instanceof Inet6Address && isIpv4Mapped(raw)) {
            raw = Arrays.copyOfRange(raw, 12, 16);
        }
        try {
            return InetAddress.getByAddress(raw);
        } catch (UnknownHostException exception) {
            return address;
        }
    }

    private static boolean isIpv4Mapped(byte[] raw) {
        if (raw == null || raw.length != 16) {
            return false;
        }
        for (int i = 0; i < 10; i++) {
            if (raw[i] != 0) {
                return false;
            }
        }
        return raw[10] == (byte) 0xff && raw[11] == (byte) 0xff;
    }

    private static InetAddress[] defaultResolve(String host) {
        try {
            return InetAddress.getAllByName(host);
        } catch (UnknownHostException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static UnsafeObjectUrlException blocked(String message) {
        return new UnsafeObjectUrlException("unsafe_url", message);
    }

    record Origin(String scheme, String host, int port) {}

    record ObserveState(Set<InetAddress> ips, Instant firstSeen, int samples) {}

    /**
     * Dogrulanmis baglanti hedefi — TCP {@link #connectIp()}, TLS SNI / Host
     * header {@link #hostname()}.
     */
    public record PinnedTarget(
            String scheme,
            String hostname,
            int port,
            String path,
            String query,
            List<InetAddress> connectIps) {

        public InetAddress connectIp() {
            for (InetAddress ip : connectIps) {
                if (ip instanceof Inet4Address) {
                    return ip;
                }
            }
            return connectIps.getFirst();
        }

        public String hostHeader() {
            boolean defaultPort =
                    ("https".equals(scheme) && port == 443) || ("http".equals(scheme) && port == 80);
            return defaultPort ? hostname : hostname + ":" + port;
        }

        public URI ipUri() {
            try {
                InetAddress ip = connectIp();
                String host = ip.getHostAddress();
                int zone = host.indexOf('%');
                if (zone >= 0) {
                    host = host.substring(0, zone);
                }
                String normalizedPath = path == null || path.isBlank() ? "/" : path;
                return new URI(scheme, null, host, port, normalizedPath, query, null);
            } catch (Exception exception) {
                throw new UnsafeObjectUrlException("unsafe_url", "Gorsel URL IP hedefi kurulamadi");
            }
        }
    }
}
