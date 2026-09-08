

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <pthread.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <netinet/ip.h>
#include <arpa/inet.h>
#include <sys/socket.h>
#include <signal.h>
#include <time.h>
#include <errno.h>
#include <ctype.h>
#include <stdarg.h>
#ifdef __ANDROID__
#include <android/log.h>
#endif

typedef enum { LOG_ERROR = 0, LOG_WARN = 1, LOG_INFO = 2, LOG_DEBUG = 3 } LogLevel;

static LogLevel g_log_level = LOG_INFO;
static FILE *g_log_file = NULL;
static pthread_mutex_t g_log_lock = PTHREAD_MUTEX_INITIALIZER;

static const char *log_level_name(LogLevel lvl) {
    switch (lvl) {
        case LOG_ERROR: return "ERROR";
        case LOG_WARN:  return "WARN ";
        case LOG_INFO:  return "INFO ";
        default:        return "DEBUG";
    }
}

static void log_msg(LogLevel level, const char *tag, const char *fmt, ...) {
    if (level > g_log_level) return;
    time_t now = time(NULL);
    struct tm tmv;
    localtime_r(&now, &tmv);
    char timestr[16];
    strftime(timestr, sizeof(timestr), "%H:%M:%S", &tmv);
    FILE *out = g_log_file ? g_log_file : stderr;
    pthread_mutex_lock(&g_log_lock);
    fprintf(out, "%s %s [%-9s] ", timestr, log_level_name(level), tag);
    va_list ap;
    va_start(ap, fmt);
    va_list ap2;
    va_copy(ap2, ap);
    vfprintf(out, fmt, ap);
    va_end(ap);
    fprintf(out, "\n");
    if (g_log_file) fflush(g_log_file);
    pthread_mutex_unlock(&g_log_lock);
#ifdef __ANDROID__
    char msgbuf[1024];
    vsnprintf(msgbuf, sizeof(msgbuf), fmt, ap2);
    int prio = ANDROID_LOG_INFO;
    if (level == LOG_ERROR) prio = ANDROID_LOG_ERROR;
    else if (level == LOG_WARN) prio = ANDROID_LOG_WARN;
    else if (level == LOG_DEBUG) prio = ANDROID_LOG_DEBUG;
    __android_log_print(prio, "CloakCore", "[%s] %s", tag, msgbuf);
#endif
    va_end(ap2);
}

#define LOGE(tag, ...) log_msg(LOG_ERROR, tag, __VA_ARGS__)
#define LOGW(tag, ...) log_msg(LOG_WARN,  tag, __VA_ARGS__)
#define LOGI(tag, ...) log_msg(LOG_INFO,  tag, __VA_ARGS__)
#define LOGD(tag, ...) log_msg(LOG_DEBUG, tag, __VA_ARGS__)

static __thread unsigned int t_rand_seed;
static __thread int t_rand_seeded = 0;

static ssize_t send_all(int fd, const void *buf, size_t len) {
    const uint8_t *p = (const uint8_t *)buf;
    size_t sent_total = 0;
    while (sent_total < len) {
        ssize_t n = send(fd, p + sent_total, len - sent_total, 0);
        if (n < 0) {
            if (errno == EINTR) continue;
            return -1;
        }
        if (n == 0) break;
        sent_total += (size_t)n;
    }
    return (ssize_t)sent_total;
}

static int safe_rand(void) {
    if (!t_rand_seeded) {
        unsigned int seed = 0;
        FILE *f = fopen("/dev/urandom", "rb");
        if (f) {
            size_t r = fread(&seed, 1, sizeof(seed), f);
            (void)r;
            fclose(f);
        }
        if (seed == 0) {
            struct timespec ts;
            clock_gettime(CLOCK_MONOTONIC, &ts);
            seed = (unsigned int)(ts.tv_nsec ^ ts.tv_sec ^ (uintptr_t)pthread_self() ^ (uintptr_t)getpid());
            if (seed == 0) seed = 0x9e3779b9u;
        }
        t_rand_seed = seed;
        t_rand_seeded = 1;
    }
    return rand_r(&t_rand_seed);
}

#define MAX_SNI_LIST     16
#define MAX_TTL_LIST     8
#define MAX_SERVER_LIST  32
#define MAX_FRAG_LENGTHS 8
#define MAX_CIPHERS      24
#define MAX_FALLBACK_PORTS 8

typedef struct {
    char host[64];
    int  port;
    int  family;
} ServerAddr;

typedef struct {
    int  listen_port;

    ServerAddr servers[MAX_SERVER_LIST];
    int  server_count;

    int  fallback_ports[MAX_FALLBACK_PORTS];
    int  fallback_port_count;

    char sni_list[MAX_SNI_LIST][256];
    int  sni_count;

    int  ttl_list[MAX_TTL_LIST];
    int  ttl_count;

    int  jitter_min_ms;
    int  jitter_max_ms;

    int  calibrate;
    int  fragment_real;
    int  fragment_lengths[MAX_FRAG_LENGTHS];
    int  fragment_length_count;

    uint16_t decoy_ciphers[MAX_CIPHERS];
    int decoy_cipher_count;

    int  adaptive;
    int  randomize_sni_case;
    int  summary_interval_sec;

    char stats_path[256];
    char log_file_path[256];
} Config;

static Config g_cfg = {
    .listen_port   = 40443,
    .server_count  = 0,
    .fallback_port_count = 0,
    .sni_count     = 0,
    .ttl_count     = 0,
    .jitter_min_ms = 20,
    .jitter_max_ms = 80,
    .calibrate     = 0,
    .fragment_real = 0,
    .fragment_length_count = 0,
    .decoy_cipher_count = 0,
    .adaptive      = 1,
    .randomize_sni_case = 0,
    .summary_interval_sec = 60,
    .stats_path    = "cloak.stats",
    .log_file_path = "",
};

static char *trim(char *s) {
    while (isspace((unsigned char)*s)) s++;
    if (*s == '\0') return s;
    char *end = s + strlen(s) - 1;
    while (end > s && isspace((unsigned char)*end)) *end-- = '\0';
    return s;
}

static int is_raw_ip(const char *s, int *family) {
    struct in_addr a4;
    struct in6_addr a6;
    if (inet_pton(AF_INET, s, &a4) == 1) { *family = AF_INET; return 1; }
    if (inet_pton(AF_INET6, s, &a6) == 1) { *family = AF_INET6; return 1; }
    return 0;
}

static void parse_sni_list(const char *value) {
    char buf[2048];
    strncpy(buf, value, sizeof(buf) - 1);
    buf[sizeof(buf) - 1] = '\0';

    g_cfg.sni_count = 0;
    char *saveptr = NULL;
    char *tok = strtok_r(buf, ",", &saveptr);
    while (tok && g_cfg.sni_count < MAX_SNI_LIST) {
        strncpy(g_cfg.sni_list[g_cfg.sni_count], trim(tok), 255);
        g_cfg.sni_count++;
        tok = strtok_r(NULL, ",", &saveptr);
    }
}

static void parse_ttl_list(const char *value) {
    char buf[256];
    strncpy(buf, value, sizeof(buf) - 1);
    buf[sizeof(buf) - 1] = '\0';

    g_cfg.ttl_count = 0;
    char *saveptr = NULL;
    char *tok = strtok_r(buf, ",", &saveptr);
    while (tok && g_cfg.ttl_count < MAX_TTL_LIST) {
        char *t = trim(tok);
        char *end = NULL;
        long v = strtol(t, &end, 10);
        if (end != t) g_cfg.ttl_list[g_cfg.ttl_count] = (int)v;
        g_cfg.ttl_count++;
        tok = strtok_r(NULL, ",", &saveptr);
    }
}

static void parse_fragment_lengths(const char *value) {
    char buf[128];
    strncpy(buf, value, sizeof(buf) - 1);
    buf[sizeof(buf) - 1] = '\0';

    g_cfg.fragment_length_count = 0;
    char *saveptr = NULL;
    char *tok = strtok_r(buf, ",", &saveptr);
    while (tok && g_cfg.fragment_length_count < MAX_FRAG_LENGTHS) {
        char *t = trim(tok);
        char *end = NULL;
        long v = strtol(t, &end, 10);
        if (end != t && v > 0) {
            g_cfg.fragment_lengths[g_cfg.fragment_length_count] = (int)v;
            g_cfg.fragment_length_count++;
        }
        tok = strtok_r(NULL, ",", &saveptr);
    }
}

static void parse_decoy_ciphers(const char *value) {
    char buf[512];
    strncpy(buf, value, sizeof(buf) - 1);
    buf[sizeof(buf) - 1] = '\0';

    g_cfg.decoy_cipher_count = 0;
    char *saveptr = NULL;
    char *tok = strtok_r(buf, ",", &saveptr);
    while (tok && g_cfg.decoy_cipher_count < MAX_CIPHERS) {
        char *t = trim(tok);
        char *end = NULL;
        unsigned long v = strtoul(t, &end, 16);
        if (end != t) {
            g_cfg.decoy_ciphers[g_cfg.decoy_cipher_count] = (uint16_t)v;
            g_cfg.decoy_cipher_count++;
        }
        tok = strtok_r(NULL, ",", &saveptr);
    }
}

typedef struct {
    char host[64];
    int  family;
    int  port;
} RawServerEntry;

static RawServerEntry g_raw_servers[MAX_SERVER_LIST];
static int g_raw_server_count = 0;

static void parse_one_server(char *entry) {
    if (g_raw_server_count >= MAX_SERVER_LIST) return;
    char *e = trim(entry);
    char host[64];
    int port = 0;

    if (e[0] == '[') {

        char *close = strchr(e, ']');
        if (!close) {
            LOGW("config", "malformed IPv6 entry '%s' (missing ']'), skipping", e);
            return;
        }
        size_t hl = (size_t)(close - e - 1);
        if (hl >= sizeof(host)) hl = sizeof(host) - 1;
        strncpy(host, e + 1, hl);
        host[hl] = '\0';
        char *colon_after = strchr(close, ':');
        if (colon_after) {
            char *end = NULL;
            long v = strtol(colon_after + 1, &end, 10);
            if (end != colon_after + 1) port = (int)v;
        }
    } else {
        const char *colon = strrchr(e, ':');
        if (!colon) {
            strncpy(host, e, sizeof(host) - 1);
            host[sizeof(host) - 1] = '\0';
        } else {
            size_t hl = (size_t)(colon - e);
            if (hl >= sizeof(host)) hl = sizeof(host) - 1;
            strncpy(host, e, hl);
            host[hl] = '\0';
            {
                char *end = NULL;
                long v = strtol(colon + 1, &end, 10);
                if (end != colon + 1) port = (int)v;
            }
        }
    }

    int family;
    if (!is_raw_ip(host, &family)) {
        LOGW("config",
            "'%s' in connect_list is not a raw IPv4/IPv6 address -- skipping it. "
            "Domain names here would leak your real destination via plaintext DNS. "
            "Use an IP instead (IPv6 needs brackets: [::1]:443).", host);
        return;
    }

    RawServerEntry *r = &g_raw_servers[g_raw_server_count];
    strncpy(r->host, host, sizeof(r->host) - 1);
    r->host[sizeof(r->host) - 1] = '\0';
    r->family = family;
    r->port = port;
    g_raw_server_count++;
}

static void parse_connect_list(const char *value) {
    char buf[1024];
    strncpy(buf, value, sizeof(buf) - 1);
    buf[sizeof(buf) - 1] = '\0';

    g_raw_server_count = 0;
    char *saveptr = NULL;
    char *tok = strtok_r(buf, ",", &saveptr);
    while (tok) {
        parse_one_server(tok);
        tok = strtok_r(NULL, ",", &saveptr);
    }
}

static void parse_fallback_ports(const char *value) {
    char buf[256];
    strncpy(buf, value, sizeof(buf) - 1);
    buf[sizeof(buf) - 1] = '\0';

    g_cfg.fallback_port_count = 0;
    char *saveptr = NULL;
    char *tok = strtok_r(buf, ",", &saveptr);
    while (tok && g_cfg.fallback_port_count < MAX_FALLBACK_PORTS) {
        char *t = trim(tok);
        char *end = NULL;
        long v = strtol(t, &end, 10);
        if (end != t) g_cfg.fallback_ports[g_cfg.fallback_port_count++] = (int)v;
        tok = strtok_r(NULL, ",", &saveptr);
    }
}

static void expand_servers(void) {
    g_cfg.server_count = 0;
    for (int i = 0; i < g_raw_server_count; i++) {
        RawServerEntry *r = &g_raw_servers[i];

        if (r->port != 0) {
            if (g_cfg.server_count >= MAX_SERVER_LIST) {
                LOGW("config", "connect_list has more entries than fit (max %d), truncating", MAX_SERVER_LIST);
                break;
            }
            ServerAddr *s = &g_cfg.servers[g_cfg.server_count++];
            strncpy(s->host, r->host, sizeof(s->host) - 1);
            s->host[sizeof(s->host) - 1] = '\0';
            s->port = r->port;
            s->family = r->family;
        } else {
            for (int j = 0; j < g_cfg.fallback_port_count; j++) {
                if (g_cfg.server_count >= MAX_SERVER_LIST) {
                    LOGW("config", "connect_list expansion hit the max of %d entries, truncating", MAX_SERVER_LIST);
                    return;
                }
                ServerAddr *s = &g_cfg.servers[g_cfg.server_count++];
                strncpy(s->host, r->host, sizeof(s->host) - 1);
                s->host[sizeof(s->host) - 1] = '\0';
                s->port = g_cfg.fallback_ports[j];
                s->family = r->family;
            }
        }
    }
}

static int as_bool(const char *value) {
    return (!strcasecmp(value, "true") || !strcasecmp(value, "yes") ||
            !strcasecmp(value, "1")    || !strcasecmp(value, "on"));
}

static void apply_setting(const char *key, const char *value) {
    if (!strcasecmp(key, "listen_port")) {
        char *end = NULL;
        long v = strtol(value, &end, 10);
        if (end != value) g_cfg.listen_port = (int)v;
    } else if (!strcasecmp(key, "connect_list")) {
        parse_connect_list(value);
    } else if (!strcasecmp(key, "fallback_ports")) {
        parse_fallback_ports(value);
    } else if (!strcasecmp(key, "sni_list")) {
        parse_sni_list(value);
    } else if (!strcasecmp(key, "ttl_list")) {
        parse_ttl_list(value);
    } else if (!strcasecmp(key, "jitter_min_ms")) {
        char *end = NULL;
        long v = strtol(value, &end, 10);
        if (end != value) g_cfg.jitter_min_ms = (int)v;
    } else if (!strcasecmp(key, "jitter_max_ms")) {
        char *end = NULL;
        long v = strtol(value, &end, 10);
        if (end != value) g_cfg.jitter_max_ms = (int)v;
    } else if (!strcasecmp(key, "calibrate")) {
        g_cfg.calibrate = as_bool(value);
    } else if (!strcasecmp(key, "fragment")) {
        g_cfg.fragment_real = as_bool(value);
    } else if (!strcasecmp(key, "fragment_lengths")) {
        parse_fragment_lengths(value);
    } else if (!strcasecmp(key, "decoy_ciphers")) {
        parse_decoy_ciphers(value);
    } else if (!strcasecmp(key, "adaptive")) {
        g_cfg.adaptive = as_bool(value);
    } else if (!strcasecmp(key, "randomize_sni_case")) {
        g_cfg.randomize_sni_case = as_bool(value);
    } else if (!strcasecmp(key, "summary_interval_sec")) {
        char *end = NULL;
        long v = strtol(value, &end, 10);
        if (end != value) g_cfg.summary_interval_sec = (int)v;
    } else if (!strcasecmp(key, "log_level")) {
        if (!strcasecmp(value, "error"))      g_log_level = LOG_ERROR;
        else if (!strcasecmp(value, "warn"))  g_log_level = LOG_WARN;
        else if (!strcasecmp(value, "info"))  g_log_level = LOG_INFO;
        else if (!strcasecmp(value, "debug")) g_log_level = LOG_DEBUG;
        else LOGW("config", "unknown log_level '%s' (use error/warn/info/debug), keeping current", value);
    } else if (!strcasecmp(key, "log_file")) {
        strncpy(g_cfg.log_file_path, value, sizeof(g_cfg.log_file_path) - 1);
    } else if (!strcasecmp(key, "verbose")) {

        g_log_level = as_bool(value) ? LOG_DEBUG : LOG_WARN;
        LOGW("config", "'verbose' is a legacy setting -- use log_level=debug/info/warn/error instead");
    } else {
        LOGW("config", "unknown key '%s', ignoring", key);
    }
}

static int load_config(const char *path) {
    FILE *f = fopen(path, "r");
    if (!f) {
        LOGE("config", "could not open config file '%s'", path);
        return -1;
    }

    char line[2048];
    int lineno = 0;
    while (fgets(line, sizeof(line), f)) {
        lineno++;
        char *s = trim(line);
        if (*s == '\0' || *s == '#') continue;

        char *eq = strchr(s, '=');
        if (!eq) {
            LOGW("config", "line %d: no '=' found, skipping", lineno);
            continue;
        }
        *eq = '\0';
        apply_setting(trim(s), trim(eq + 1));
    }

    fclose(f);
    return 0;
}

static socklen_t fill_sockaddr(const ServerAddr *srv, struct sockaddr_storage *out) {
    memset(out, 0, sizeof(*out));
    if (srv->family == AF_INET) {
        struct sockaddr_in *a = (struct sockaddr_in *)out;
        a->sin_family = AF_INET;
        a->sin_port = htons((uint16_t)srv->port);
        inet_pton(AF_INET, srv->host, &a->sin_addr);
        return sizeof(*a);
    } else {
        struct sockaddr_in6 *a = (struct sockaddr_in6 *)out;
        a->sin6_family = AF_INET6;
        a->sin6_port = htons((uint16_t)srv->port);
        inet_pton(AF_INET6, srv->host, &a->sin6_addr);
        return sizeof(*a);
    }
}

static void set_hop_limit(int fd, int family, int ttl) {
    if (family == AF_INET) {
        (void)setsockopt(fd, IPPROTO_IP, IP_TTL, &ttl, sizeof(ttl));
    } else {
        (void)setsockopt(fd, IPPROTO_IPV6, IPV6_UNICAST_HOPS, &ttl, sizeof(ttl));
    }
}

static void write_example_config(const char *path) {
    FILE *f = fopen(path, "w");
    if (!f) return;
    fprintf(f,
"# ============================================================\n"
"#  cloak.conf -- settings for the cloak proxy\n"
"#  Lines starting with # are comments. Blank lines are ignored.\n"
"#  Format is:  key = value\n"
"# ============================================================\n"
"\n"
"# Local port that your VPN/proxy client (v2rayNG, Xray, etc.)\n"
"# should point its outbound at, e.g. 127.0.0.1:40443\n"
"listen_port = 40443\n"
"\n"
"# One or more real destination servers. cloak probes them all AT\n"
"# THE SAME TIME and uses whichever answers first. MUST be raw IPv4\n"
"# or IPv6 addresses -- a domain name here would leak your real\n"
"# destination through plaintext DNS before cloak gets a chance to\n"
"# hide anything.\n"
"# Format: ip:port, ip:port, ...  (IPv6 needs brackets: [2606:4700::1]:443)\n"
"# If you leave the :port off an entry (just the bare IP), cloak\n"
"# automatically expands it into one entry PER PORT in\n"
"# fallback_ports below, and races all of them together -- handy if\n"
"# your ISP blocks port 443 specifically rather than the IP itself.\n"
"# Give an explicit :port to pin a specific IP to just that port.\n"
"connect_list = 104.18.38.202, 104.18.39.100:443, [2606:4700::1]\n"
"\n"
"# Cloudflare's other published HTTPS-capable ports, used to expand\n"
"# any connect_list entry that didn't specify its own port. Only\n"
"# these specific ports work for HTTPS through Cloudflare -- plain\n"
"# port 80 is HTTP-only and carries no TLS/SNI to hide, so it's\n"
"# deliberately not in this list.\n"
"fallback_ports = 443, 2053, 2083, 2087, 2096, 8443\n"
"\n"
"# Allowed/innocuous domain names used as the decoy SNI. One is\n"
"# picked at random for each new connection so the pattern isn't\n"
"# always the same value (harder to blocklist).\n"
"sni_list = www.hcaptcha.com, www.speedtest.net, www.bing.com\n"
"\n"
"# TTLs to try for the decoy packets, low to high. cloak sends ONE\n"
"# decoy per value in this list, which raises the odds that at\n"
"# least one of them dies exactly where a DPI box is inspecting\n"
"# traffic, without you having to know the exact hop count yourself.\n"
"ttl_list = 4, 5, 6, 8\n"
"\n"
"# Random delay range (milliseconds) between sending the decoy(s)\n"
"# and sending the real ClientHello. Randomized on purpose so a DPI\n"
"# watching for a suspiciously fixed timing pattern doesn't get one.\n"
"jitter_min_ms = 20\n"
"jitter_max_ms = 80\n"
"\n"
"# true/false -- instead of using ttl_list as-is, run a one-time\n"
"# heuristic \"poor man's traceroute\" per connection to guess a\n"
"# TTL near the real server's hop distance. Noisy on mobile\n"
"# networks; leave false and rely on ttl_list + adaptive instead.\n"
"calibrate = false\n"
"\n"
"# true/false -- also split the REAL ClientHello into small TCP\n"
"# segments (in addition to the TTL decoys). Helps against DPI that\n"
"# just pattern-matches one whole packet instead of tracking flows.\n"
"fragment = false\n"
"\n"
"# Only used when fragment = true. Exact byte-length split points for\n"
"# the real ClientHello, e.g. \"5,94,1\" sends a 5-byte chunk, then a\n"
"# 94-byte chunk, then a 1-byte chunk, then whatever's left as one\n"
"# final chunk. Same idea as PattNG's finalMask \"lengths\" field --\n"
"# choose cuts that land inside the SNI field so a single packet\n"
"# never contains the whole domain name. Leave empty to use a simple\n"
"# built-in default (a tiny first chunk, then fixed-size chunks).\n"
"fragment_lengths = \n"
"\n"
"# Cipher suite list for the DECOY handshake's fingerprint, as\n"
"# 4-hex-digit codes, e.g. \"1301,1302,c02b,c02f\". This only changes\n"
"# how cloak's own decoy looks (its JA3-style shape) -- it can NOT\n"
"# change the real hello's ciphers, since that hello is built by your\n"
"# VPN client (v2rayNG/Xray), not by cloak. Leave empty to use the\n"
"# built-in Chrome-like default list.\n"
"decoy_ciphers = \n"
"\n"
"# true/false -- remember which TTLs and servers actually got a\n"
"# real TLS response back (logged in cloak.stats) and try the\n"
"# best-performing ones first on future connections.\n"
"adaptive = true\n"
"\n"
"# true/false -- randomly flips the case of letters in the decoy SNI\n"
"# per connection (e.g. \"www.bing.com\" -> \"wWw.BiNg.CoM\"). Real\n"
"# servers match hostnames case-insensitively, so this changes\n"
"# nothing about whether the decoy itself works -- it only helps\n"
"# against older/simpler DPI that does an exact case-sensitive string\n"
"# match on the SNI. Most modern DPI normalizes case first and won't\n"
"# be affected either way, so treat this as a minor extra, not a fix\n"
"# for a specific problem.\n"
"randomize_sni_case = false\n"
"\n"
"# How often (seconds) the INFO-level heartbeat line prints, e.g.\n"
"# \"alive -- 42 connection(s) (39 ok, 3 failed) in the last 60s\".\n"
"summary_interval_sec = 60\n"
"\n"
"# How much detail to log: error, warn, info, or debug.\n"
"#   error - only real failures\n"
"#   warn  - + rejected config entries, unusual conditions\n"
"#   info  - + a periodic one-line summary every summary_interval_sec\n"
"#            (recommended for daily use -- no per-connection spam)\n"
"#   debug - + every individual decoy packet, real connection, and\n"
"#            TTL/stats detail (recommended only while tuning, noisy)\n"
"log_level = info\n"
"\n"
"# Leave empty to log to the terminal (stderr). Set a path to also\n"
"# keep a persistent log file, useful since cloak is meant to run\n"
"# unattended for a long time.\n"
"log_file = \n"
    );
    fclose(f);
}

typedef struct { int ttl; long success; long fail; } TtlStat;
typedef struct { char host[64]; int port; long success; long fail; } ServerStat;

static TtlStat    g_ttl_stats[MAX_TTL_LIST];
static int        g_ttl_stat_count = 0;
static ServerStat g_server_stats[MAX_SERVER_LIST];
static int        g_server_stat_count = 0;
static pthread_mutex_t g_stats_lock = PTHREAD_MUTEX_INITIALIZER;

static TtlStat *find_ttl_stat(int ttl) {
    for (int i = 0; i < g_ttl_stat_count; i++)
        if (g_ttl_stats[i].ttl == ttl) return &g_ttl_stats[i];
    if (g_ttl_stat_count < MAX_TTL_LIST) {
        g_ttl_stats[g_ttl_stat_count].ttl = ttl;
        g_ttl_stats[g_ttl_stat_count].success = 0;
        g_ttl_stats[g_ttl_stat_count].fail = 0;
        return &g_ttl_stats[g_ttl_stat_count++];
    }
    return NULL;
}

static ServerStat *find_server_stat(const char *host, int port) {
    for (int i = 0; i < g_server_stat_count; i++)
        if (g_server_stats[i].port == port && !strcmp(g_server_stats[i].host, host))
            return &g_server_stats[i];
    if (g_server_stat_count < MAX_SERVER_LIST) {
        ServerStat *s = &g_server_stats[g_server_stat_count++];
        strncpy(s->host, host, sizeof(s->host) - 1);
        s->port = port;
        s->success = 0;
        s->fail = 0;
        return s;
    }
    return NULL;
}

static void load_stats(void) {
    FILE *f = fopen(g_cfg.stats_path, "r");
    if (!f) return;
    char kind[8], host[64];
    int ttl, port;
    long succ, fail;
    char line[256];
    while (fgets(line, sizeof(line), f)) {
        char *p = trim(line);
        if (strncmp(p, "TTL ", 4) == 0) {
            char *e1 = NULL, *e2 = NULL, *e3 = NULL;
            long t = strtol(p + 4, &e1, 10);
            long s1 = strtol(e1, &e2, 10);
            long f1 = strtol(e2, &e3, 10);
            if (e1 != p + 4 && e2 != e1 && e3 != e2) {
                TtlStat *s = find_ttl_stat((int)t);
                if (s) { s->success = s1; s->fail = f1; }
            }
        } else if (strncmp(p, "SRV ", 4) == 0) {
            char host2[64]; int port2; long succ2, fail2;
            char *cur = p + 4;
            char *end = NULL;
            char *sp = strchr(cur, ' ');
            if (!sp) continue;
            size_t hl = sp - cur;
            if (hl >= sizeof(host2)) hl = sizeof(host2)-1;
            strncpy(host2, cur, hl); host2[hl]='\0';
            cur = sp+1;
            long pt = strtol(cur, &end, 10);
            if (end == cur) continue;
            cur = end;
            long sc = strtol(cur, &end, 10);
            if (end == cur) continue;
            cur = end;
            long fc = strtol(cur, &end, 10);
            if (end == cur) continue;
            ServerStat *s = find_server_stat(host2, (int)pt);
            if (s) { s->success = sc; s->fail = fc; }
        }
    }
    fclose(f);
}

static void save_stats(void) {
    FILE *f = fopen(g_cfg.stats_path, "w");
    if (!f) return;
    fprintf(f, "# auto-generated by cloak -- do not edit while cloak is running\n");
    for (int i = 0; i < g_ttl_stat_count; i++)
        fprintf(f, "TTL %d %ld %ld\n", g_ttl_stats[i].ttl, g_ttl_stats[i].success, g_ttl_stats[i].fail);
    for (int i = 0; i < g_server_stat_count; i++)
        fprintf(f, "SRV %s %d %ld %ld\n", g_server_stats[i].host, g_server_stats[i].port,
                g_server_stats[i].success, g_server_stats[i].fail);
    fclose(f);
}

static void record_ttl_result(int ttl, int success) {
    pthread_mutex_lock(&g_stats_lock);
    TtlStat *s = find_ttl_stat(ttl);
    if (s) { if (success) s->success++; else s->fail++; }
    save_stats();
    pthread_mutex_unlock(&g_stats_lock);
}

static void record_server_result(const char *host, int port, int success) {
    pthread_mutex_lock(&g_stats_lock);
    ServerStat *s = find_server_stat(host, port);
    if (s) { if (success) s->success++; else s->fail++; }
    save_stats();
    pthread_mutex_unlock(&g_stats_lock);
}

static double success_rate(long success, long fail) {

    return (double)(success + 1) / (double)(success + fail + 2);
}

static void reorder_ttl_list_by_stats(void) {
    for (int i = 0; i < g_cfg.ttl_count - 1; i++) {
        for (int j = 0; j < g_cfg.ttl_count - 1 - i; j++) {
            TtlStat *a = find_ttl_stat(g_cfg.ttl_list[j]);
            TtlStat *b = find_ttl_stat(g_cfg.ttl_list[j + 1]);
            double ra = a ? success_rate(a->success, a->fail) : 0.5;
            double rb = b ? success_rate(b->success, b->fail) : 0.5;
            if (ra < rb) {
                int tmp = g_cfg.ttl_list[j];
                g_cfg.ttl_list[j] = g_cfg.ttl_list[j + 1];
                g_cfg.ttl_list[j + 1] = tmp;
            }
        }
    }
}

static void reorder_servers_by_stats(void) {
    for (int i = 0; i < g_cfg.server_count - 1; i++) {
        for (int j = 0; j < g_cfg.server_count - 1 - i; j++) {
            ServerStat *a = find_server_stat(g_cfg.servers[j].host, g_cfg.servers[j].port);
            ServerStat *b = find_server_stat(g_cfg.servers[j + 1].host, g_cfg.servers[j + 1].port);
            double ra = a ? success_rate(a->success, a->fail) : 0.5;
            double rb = b ? success_rate(b->success, b->fail) : 0.5;
            if (ra < rb) {
                ServerAddr tmp = g_cfg.servers[j];
                g_cfg.servers[j] = g_cfg.servers[j + 1];
                g_cfg.servers[j + 1] = tmp;
            }
        }
    }
}

static void put_u16(uint8_t *buf, size_t *p, uint16_t v) {
    buf[(*p)++] = (v >> 8) & 0xFF;
    buf[(*p)++] = v & 0xFF;
}

static size_t build_fake_client_hello(uint8_t *out, size_t cap, const char *sni) {
    size_t sni_len = strlen(sni);
    uint8_t body[1024];
    size_t p = 0;

    body[p++] = 0x03; body[p++] = 0x03;
    for (int i = 0; i < 32; i++) body[p++] = (uint8_t)safe_rand();
    body[p++] = 0x00;

    static const uint16_t default_ciphers[] = {
        0x1301, 0x1302, 0x1303,
        0xC02B, 0xC02F, 0xC02C, 0xC030,
        0xCCA9, 0xCCA8, 0xC013, 0xC014,
        0x009C, 0x009D, 0x002F, 0x0035
    };
    const uint16_t *ciphers = default_ciphers;
    size_t cipher_count = sizeof(default_ciphers) / sizeof(default_ciphers[0]);
    if (g_cfg.decoy_cipher_count > 0) {
        ciphers = g_cfg.decoy_ciphers;
        cipher_count = (size_t)g_cfg.decoy_cipher_count;
    }
    put_u16(body, &p, (uint16_t)(cipher_count * 2));
    for (size_t i = 0; i < cipher_count; i++)
        put_u16(body, &p, ciphers[i]);

    body[p++] = 0x01; body[p++] = 0x00;

    size_t ext_len_pos = p; p += 2;
    size_t ext_start = p;

    put_u16(body, &p, 0x0000);
    put_u16(body, &p, (uint16_t)(sni_len + 5));
    put_u16(body, &p, (uint16_t)(sni_len + 3));
    body[p++] = 0x00;
    put_u16(body, &p, (uint16_t)sni_len);
    memcpy(body + p, sni, sni_len); p += sni_len;

    put_u16(body, &p, 0x0017); put_u16(body, &p, 0x0000);

    put_u16(body, &p, 0xFF01); put_u16(body, &p, 0x0001); body[p++] = 0x00;

    put_u16(body, &p, 0x000A); put_u16(body, &p, 0x0008);
    put_u16(body, &p, 0x0006);
    put_u16(body, &p, 0x001D); put_u16(body, &p, 0x0017); put_u16(body, &p, 0x0018);

    put_u16(body, &p, 0x000B); put_u16(body, &p, 0x0002);
    body[p++] = 0x01; body[p++] = 0x00;

    put_u16(body, &p, 0x0023); put_u16(body, &p, 0x0000);

    {
        put_u16(body, &p, 0x0010);
        put_u16(body, &p, 0x000E);
        put_u16(body, &p, 0x000C);
        body[p++] = 0x02; body[p++] = 'h'; body[p++] = '2';
        body[p++] = 0x08;
        memcpy(body + p, "http/1.1", 8); p += 8;
    }

    put_u16(body, &p, 0x0005); put_u16(body, &p, 0x0005);
    body[p++] = 0x01; put_u16(body, &p, 0x0000); put_u16(body, &p, 0x0000);

    {
        static const uint16_t sigs[] = {
            0x0403, 0x0503, 0x0603, 0x0807, 0x0808,
            0x0809, 0x080A, 0x080B, 0x0804, 0x0805,
            0x0806, 0x0401, 0x0501, 0x0601, 0x0203, 0x0201
        };
        put_u16(body, &p, 0x000D);
        put_u16(body, &p, (uint16_t)(sizeof(sigs) + 2));
        put_u16(body, &p, (uint16_t)sizeof(sigs));
        for (size_t i = 0; i < sizeof(sigs) / sizeof(sigs[0]); i++)
            put_u16(body, &p, sigs[i]);
    }

    {
        put_u16(body, &p, 0x0033);
        put_u16(body, &p, 0x0026);
        put_u16(body, &p, 0x0024);
        put_u16(body, &p, 0x001D);
        put_u16(body, &p, 0x0020);
        for (int i = 0; i < 32; i++) body[p++] = (uint8_t)safe_rand();
    }

    put_u16(body, &p, 0x002D); put_u16(body, &p, 0x0002);
    body[p++] = 0x01; body[p++] = 0x01;

    put_u16(body, &p, 0x002B); put_u16(body, &p, 0x0005);
    body[p++] = 0x04;
    put_u16(body, &p, 0x0304); put_u16(body, &p, 0x0303);

    size_t ext_total = p - ext_start;
    body[ext_len_pos]     = (ext_total >> 8) & 0xFF;
    body[ext_len_pos + 1] = ext_total & 0xFF;

    size_t body_len = p;
    uint8_t hs[4 + 1024];
    hs[0] = 0x01;
    hs[1] = (uint8_t)((body_len >> 16) & 0xFF);
    hs[2] = (uint8_t)((body_len >> 8) & 0xFF);
    hs[3] = (uint8_t)(body_len & 0xFF);
    memcpy(hs + 4, body, body_len);
    size_t hs_len = 4 + body_len;

    if (cap < hs_len + 5) return 0;
    out[0] = 0x16; out[1] = 0x03; out[2] = 0x01;
    out[3] = (uint8_t)((hs_len >> 8) & 0xFF);
    out[4] = (uint8_t)(hs_len & 0xFF);
    memcpy(out + 5, hs, hs_len);
    return hs_len + 5;
}

static int probe_reachable_at_ttl(const ServerAddr *srv, int ttl) {
    int fd = socket(srv->family, SOCK_STREAM, 0);
    if (fd < 0) return -1;
    set_hop_limit(fd, srv->family, ttl);

    struct timeval tv = {.tv_sec = 0, .tv_usec = 500000};
    (void)setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));
    (void)setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));

    struct sockaddr_storage dst;
    socklen_t dst_len = fill_sockaddr(srv, &dst);

    int rc = connect(fd, (struct sockaddr *)&dst, dst_len);
    int reached = (rc == 0) || (errno == ECONNREFUSED);
    close(fd);
    return reached;
}

static int calibrate_ttl(const ServerAddr *srv) {
    int lo = 1, hi = 32, best = -1;
    while (lo <= hi) {
        int mid = (lo + hi) / 2;
        int reached = probe_reachable_at_ttl(srv, mid);
        LOGD("calibrate", "ttl=%d reachable=%d", mid, reached);
        if (reached) { best = mid; hi = mid - 1; } else { lo = mid + 1; }
    }
    if (best < 0) return g_cfg.ttl_count > 0 ? g_cfg.ttl_list[0] : 5;
    int t = best - 2;
    return t < 1 ? 1 : t;
}

static int reserve_ephemeral_port(int family) {
    int fd = socket(family, SOCK_STREAM, 0);
    if (fd < 0) return 0;
    int yes = 1;
    (void)setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &yes, sizeof(yes));
#ifdef SO_REUSEPORT
    (void)setsockopt(fd, SOL_SOCKET, SO_REUSEPORT, &yes, sizeof(yes));
#endif
    struct sockaddr_storage local;
    memset(&local, 0, sizeof(local));
    socklen_t local_len;
    if (family == AF_INET) {
        struct sockaddr_in *a = (struct sockaddr_in *)&local;
        a->sin_family = AF_INET;
        local_len = sizeof(*a);
    } else {
        struct sockaddr_in6 *a = (struct sockaddr_in6 *)&local;
        a->sin6_family = AF_INET6;
        local_len = sizeof(*a);
    }
    (void)bind(fd, (struct sockaddr *)&local, local_len);

    struct sockaddr_storage assigned;
    socklen_t len = sizeof(assigned);
    getsockname(fd, (struct sockaddr *)&assigned, &len);
    int port = (family == AF_INET)
        ? ntohs(((struct sockaddr_in *)&assigned)->sin_port)
        : ntohs(((struct sockaddr_in6 *)&assigned)->sin6_port);
    close(fd);
    return port;
}

static int bind_to_port(int fd, int port, int family) {
    int yes = 1;
    (void)setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &yes, sizeof(yes));
#ifdef SO_REUSEPORT
    (void)setsockopt(fd, SOL_SOCKET, SO_REUSEPORT, &yes, sizeof(yes));
#endif
    struct sockaddr_storage local;
    memset(&local, 0, sizeof(local));
    socklen_t local_len;
    if (family == AF_INET) {
        struct sockaddr_in *a = (struct sockaddr_in *)&local;
        a->sin_family = AF_INET;
        a->sin_port = htons((uint16_t)port);
        local_len = sizeof(*a);
    } else {
        struct sockaddr_in6 *a = (struct sockaddr_in6 *)&local;
        a->sin6_family = AF_INET6;
        a->sin6_port = htons((uint16_t)port);
        local_len = sizeof(*a);
    }
    return bind(fd, (struct sockaddr *)&local, local_len);
}

static const char *pick_random_sni(void) {
    if (g_cfg.sni_count == 0) return "www.hcaptcha.com";
    return g_cfg.sni_list[safe_rand() % g_cfg.sni_count];
}

static void randomize_sni_case(const char *sni, char *out, size_t out_cap) {
    size_t len = strlen(sni);
    if (len + 1 > out_cap) len = out_cap - 1;
    for (size_t i = 0; i < len; i++) {
        char c = sni[i];
        if (isalpha((unsigned char)c) && (safe_rand() % 2)) {
            c = isupper((unsigned char)c) ? (char)tolower((unsigned char)c) : (char)toupper((unsigned char)c);
        }
        out[i] = c;
    }
    out[len] = '\0';
}

static void send_one_decoy(const ServerAddr *srv, int shared_port, int ttl, int use_shared_port) {
    const char *sni = pick_random_sni();
    char cased_sni[256];
    if (g_cfg.randomize_sni_case) {
        randomize_sni_case(sni, cased_sni, sizeof(cased_sni));
        sni = cased_sni;
    }

    int fd = socket(srv->family, SOCK_STREAM, 0);
    if (fd < 0) { perror("socket(decoy)"); return; }

    if (use_shared_port) bind_to_port(fd, shared_port, srv->family);

    set_hop_limit(fd, srv->family, ttl);

    struct timeval tv = {.tv_sec = 0, .tv_usec = 300000};
    (void)setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));
    (void)setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));

    struct sockaddr_storage dst;
    socklen_t dst_len = fill_sockaddr(srv, &dst);

    if (connect(fd, (struct sockaddr *)&dst, dst_len) == 0) {
        uint8_t hello[1200];
        size_t n = build_fake_client_hello(hello, sizeof(hello), sni);
        if (n > 0) {
            send_all(fd, hello, n);
            LOGD("decoy", "sni=%s ttl=%d shared_port=%d sent", sni, ttl, use_shared_port);
        }
    } else {
        LOGD("decoy", "ttl=%d died in transit as expected", ttl);
    }

    close(fd);
}

static void send_all_decoys(const ServerAddr *srv, int shared_port) {
    if (g_cfg.ttl_count == 0) {
        send_one_decoy(srv, shared_port, 5, 1);
        return;
    }
    for (int i = 0; i < g_cfg.ttl_count; i++) {
        send_one_decoy(srv, shared_port, g_cfg.ttl_list[i], i == 0);
    }
}

typedef struct { int from_fd; int to_fd; } RelayArgs;

static void *relay_direction(void *arg) {
    RelayArgs *ra = (RelayArgs *)arg;
    uint8_t buf[16384];
    ssize_t n;
    while ((n = recv(ra->from_fd, buf, sizeof(buf), 0)) > 0) {
        if (send_all(ra->to_fd, buf, (size_t)n) < n) break;
    }
    shutdown(ra->to_fd, SHUT_WR);
    free(ra);
    return NULL;
}

static void send_fragmented(int fd, const uint8_t *data, size_t len) {
    int one = 1;
    (void)setsockopt(fd, IPPROTO_TCP, TCP_NODELAY, &one, sizeof(one));

    size_t off = 0;

    if (g_cfg.fragment_length_count > 0) {

        for (int i = 0; i < g_cfg.fragment_length_count && off < len; i++) {
            size_t chunk = (size_t)g_cfg.fragment_lengths[i];
            size_t n = (len - off < chunk) ? (len - off) : chunk;
            send_all(fd, data + off, n);
            off += n;
            usleep(2000);
        }

        if (off < len) send_all(fd, data + off, len - off);
        return;
    }

    size_t chunk = 4;
    while (off < len) {
        size_t n = (len - off < chunk) ? (len - off) : chunk;
        send_all(fd, data + off, n);
        off += n;
        chunk = 32;
        usleep(2000);
    }
}

typedef struct {
    int done;
    int winner_index;
    pthread_mutex_t lock;
    pthread_cond_t  cond;
    int refcount;
} RaceState;

typedef struct {
    int index;
    ServerAddr srv;
    RaceState *state;
} ProbeArg;

static void race_state_release(RaceState *state) {
    pthread_mutex_lock(&state->lock);
    state->refcount--;
    int should_free = (state->refcount == 0);
    pthread_mutex_unlock(&state->lock);
    if (should_free) {
        pthread_mutex_destroy(&state->lock);
        pthread_cond_destroy(&state->cond);
        free(state);
    }
}

#include <poll.h>
#include <fcntl.h>
#include <sys/random.h>

static int connect_with_timeout(int fd, struct sockaddr *dst, socklen_t dst_len, int timeout_ms) {
    int flags = fcntl(fd, F_GETFL, 0);
    if (flags >= 0) (void)fcntl(fd, F_SETFL, flags | O_NONBLOCK);

    int rc = connect(fd, dst, dst_len);
    if (rc == 0) return 0;
    if (errno != EINPROGRESS) return -1;

    struct pollfd pfd = { .fd = fd, .events = POLLOUT };
    rc = poll(&pfd, 1, timeout_ms);
    if (rc <= 0) return -1;

    int err = 0;
    socklen_t len = sizeof(err);
    getsockopt(fd, SOL_SOCKET, SO_ERROR, &err, &len);
    return (err == 0) ? 0 : -1;
}

static void *probe_thread(void *arg) {
    ProbeArg *pa = (ProbeArg *)arg;
    int fd = socket(pa->srv.family, SOCK_STREAM, 0);
    int rc = -1;

    if (fd >= 0) {
        struct sockaddr_storage dst;
        socklen_t dst_len = fill_sockaddr(&pa->srv, &dst);
        rc = connect_with_timeout(fd, (struct sockaddr *)&dst, dst_len, 3000);
        close(fd);
    }

    if (rc == 0) {
        pthread_mutex_lock(&pa->state->lock);
        if (!pa->state->done) {
            pa->state->done = 1;
            pa->state->winner_index = pa->index;
            pthread_cond_signal(&pa->state->cond);
        }
        pthread_mutex_unlock(&pa->state->lock);
    }

    race_state_release(pa->state);
    free(pa);
    return NULL;
}

static int race_servers(void) {
    RaceState *state = malloc(sizeof(RaceState));
    state->done = 0;
    state->winner_index = -1;
    state->refcount = g_cfg.server_count + 1;
    pthread_mutex_init(&state->lock, NULL);
    pthread_cond_init(&state->cond, NULL);

    for (int i = 0; i < g_cfg.server_count; i++) {
        ProbeArg *pa = malloc(sizeof(ProbeArg));
        pa->index = i;
        pa->srv = g_cfg.servers[i];
        pa->state = state;
        pthread_t t;
        (void)pthread_create(&t, NULL, probe_thread, pa);
        pthread_detach(t);
    }

    struct timespec ts;
    clock_gettime(CLOCK_REALTIME, &ts);
    ts.tv_sec += 3;

    pthread_mutex_lock(&state->lock);
    while (!state->done) {
        int rc = pthread_cond_timedwait(&state->cond, &state->lock, &ts);
        if (rc == ETIMEDOUT) break;
    }
    int winner = state->winner_index;
    pthread_mutex_unlock(&state->lock);

    race_state_release(state);
    return winner;
}

static pthread_mutex_t g_summary_lock = PTHREAD_MUTEX_INITIALIZER;
static int g_summary_total = 0;
static int g_summary_ok = 0;
static int g_summary_failed = 0;

static void record_summary(int ok) {
    pthread_mutex_lock(&g_summary_lock);
    g_summary_total++;
    if (ok) g_summary_ok++; else g_summary_failed++;
    pthread_mutex_unlock(&g_summary_lock);
}

static ssize_t recv_full_tls_record(int fd, uint8_t *buf, size_t cap) {
    size_t got = 0;

    while (got < 5) {
        ssize_t n = recv(fd, buf + got, 5 - got, 0);
        if (n <= 0) return n;
        got += (size_t)n;
    }

    if (buf[0] != 0x16) {

        return (ssize_t)got;
    }

    size_t record_len = ((size_t)buf[3] << 8) | buf[4];
    size_t total_needed = 5 + record_len;
    if (total_needed > cap) total_needed = cap;

    while (got < total_needed) {
        ssize_t n = recv(fd, buf + got, total_needed - got, 0);
        if (n <= 0) break;
        got += (size_t)n;
    }

    return (ssize_t)got;
}

static void *handle_client(void *arg) {
    int client_fd = *(int *)arg;
    free(arg);

    struct timeval hello_tv = {.tv_sec = 5, .tv_usec = 0};
    (void)setsockopt(client_fd, SOL_SOCKET, SO_RCVTIMEO, &hello_tv, sizeof(hello_tv));

    uint8_t real_hello[8192];
    ssize_t hello_len = recv_full_tls_record(client_fd, real_hello, sizeof(real_hello));
    if (hello_len <= 0) { close(client_fd); return NULL; }

    struct timeval no_tv2 = {.tv_sec = 0, .tv_usec = 0};
    (void)setsockopt(client_fd, SOL_SOCKET, SO_RCVTIMEO, &no_tv2, sizeof(no_tv2));

    if (g_cfg.adaptive) {
        reorder_servers_by_stats();
        reorder_ttl_list_by_stats();
    }

    int winner = race_servers();
    if (winner < 0) {
        LOGE("connect", "no server in connect_list answered in time");
        record_summary(0);
        close(client_fd);
        return NULL;
    }
    ServerAddr srv = g_cfg.servers[winner];

    if (g_cfg.calibrate) {
        int t = calibrate_ttl(&srv);
        g_cfg.ttl_list[0] = t;
        g_cfg.ttl_count = 1;
    }

    int shared_port = reserve_ephemeral_port(srv.family);
    send_all_decoys(&srv, shared_port);

    int jitter_range = g_cfg.jitter_max_ms - g_cfg.jitter_min_ms;
    int jitter = g_cfg.jitter_min_ms + (jitter_range > 0 ? safe_rand() % jitter_range : 0);
    usleep(jitter * 1000);

    int server_fd = socket(srv.family, SOCK_STREAM, 0);
    if (server_fd < 0) {
        LOGE("connect", "socket() failed: %s", strerror(errno));
        close(client_fd);
        return NULL;
    }
    bind_to_port(server_fd, shared_port, srv.family);

    struct sockaddr_storage dst;
    socklen_t dst_len = fill_sockaddr(&srv, &dst);

    if (connect(server_fd, (struct sockaddr *)&dst, dst_len) < 0) {
        LOGD("connect", "connect(real server) failed: %s", strerror(errno));
        if (g_cfg.adaptive) {
            record_server_result(srv.host, srv.port, 0);
            for (int i = 0; i < g_cfg.ttl_count; i++) record_ttl_result(g_cfg.ttl_list[i], 0);
        }
        record_summary(0);
        close(client_fd); close(server_fd);
        return NULL;
    }

    if (g_cfg.fragment_real) {
        send_fragmented(server_fd, real_hello, (size_t)hello_len);
    } else {
        send_all(server_fd, real_hello, (size_t)hello_len);
    }
    LOGD("connect", "real hello -> %s:%d (%zd bytes, jitter=%dms)",
         srv.host, srv.port, hello_len, jitter);

    int summary_ok = 1;
    if (g_cfg.adaptive) {
        struct timeval peek_tv = {.tv_sec = 3, .tv_usec = 0};
        setsockopt(server_fd, SOL_SOCKET, SO_RCVTIMEO, &peek_tv, sizeof(peek_tv));
        uint8_t peek[8];
        ssize_t got = recv(server_fd, peek, sizeof(peek), MSG_PEEK);
        int success = (got > 0 && peek[0] == 0x16);
        summary_ok = success;

        record_server_result(srv.host, srv.port, success);
        for (int i = 0; i < g_cfg.ttl_count; i++) record_ttl_result(g_cfg.ttl_list[i], success);
        LOGD("stats", "connection marked %s", success ? "success" : "failure");

        struct timeval no_tv = {.tv_sec = 0, .tv_usec = 0};
        setsockopt(server_fd, SOL_SOCKET, SO_RCVTIMEO, &no_tv, sizeof(no_tv));
    }
    record_summary(summary_ok);

    RelayArgs *a1 = malloc(sizeof(RelayArgs)); a1->from_fd = client_fd; a1->to_fd = server_fd;
    RelayArgs *a2 = malloc(sizeof(RelayArgs)); a2->from_fd = server_fd; a2->to_fd = client_fd;
    pthread_t t1, t2;
    pthread_create(&t1, NULL, relay_direction, a1);
    pthread_create(&t2, NULL, relay_direction, a2);
    pthread_join(t1, NULL);
    pthread_join(t2, NULL);

    close(client_fd); close(server_fd);
    return NULL;
}

static volatile sig_atomic_t g_shutdown_requested = 0;

static void handle_shutdown_signal(int sig) {
    (void)sig;
    g_shutdown_requested = 1;
}

static void *summary_thread(void *arg) {
    (void)arg;
    int interval = g_cfg.summary_interval_sec > 0 ? g_cfg.summary_interval_sec : 60;

    while (!g_shutdown_requested) {
        for (int i = 0; i < interval && !g_shutdown_requested; i++) sleep(1);
        if (g_shutdown_requested) break;

        pthread_mutex_lock(&g_summary_lock);
        int total = g_summary_total, ok = g_summary_ok, failed = g_summary_failed;
        g_summary_total = 0; g_summary_ok = 0; g_summary_failed = 0;
        pthread_mutex_unlock(&g_summary_lock);

        int pct = (total > 0) ? (int)((100.0 * ok) / total) : 0;
        LOGI("status", "last %ds: %d total, %d ok (%d%%), %d failed",
             interval, total, ok, pct, failed);
    }
    return NULL;
}

int main(int argc, char **argv) {
    srand((unsigned)time(NULL));
    signal(SIGPIPE, SIG_IGN);
    signal(SIGINT, handle_shutdown_signal);
    signal(SIGTERM, handle_shutdown_signal);

    const char *config_path = (argc > 1) ? argv[1] : "cloak.conf";

    FILE *check = fopen(config_path, "r");
    if (!check) {
        printf("no config file found at '%s' -- writing a starter one for you.\n", config_path);
        write_example_config(config_path);
        printf("edit it, then run this program again.\n");
        return 0;
    }
    fclose(check);

    if (load_config(config_path) < 0) return 1;

    if (g_cfg.log_file_path[0] != '\0') {
        g_log_file = fopen(g_cfg.log_file_path, "a");
        if (!g_log_file) {
            LOGW("config", "could not open log_file '%s', falling back to stderr", g_cfg.log_file_path);
        }
    }

    if (g_cfg.fallback_port_count == 0) parse_fallback_ports("443,2053,2083,2087,2096,8443");
    expand_servers();

    if (g_cfg.server_count == 0) {
        LOGE("config", "'connect_list' is empty (or every entry was rejected as a "
             "non-IP domain name) in %s", config_path);
        return 1;
    }
    if (g_cfg.sni_count == 0) parse_sni_list("www.hcaptcha.com,www.speedtest.net,www.bing.com");
    if (g_cfg.ttl_count == 0) parse_ttl_list("4,5,6,8");

    load_stats();

    int listen_fd = socket(AF_INET, SOCK_STREAM, 0);
    if (listen_fd < 0) {
        LOGE("listen", "socket() failed: %s", strerror(errno));
        return 1;
    }
    int yes = 1;
    (void)setsockopt(listen_fd, SOL_SOCKET, SO_REUSEADDR, &yes, sizeof(yes));

    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = INADDR_ANY;
    addr.sin_port = htons((uint16_t)g_cfg.listen_port);

    if (bind(listen_fd, (struct sockaddr *)&addr, sizeof(addr)) < 0) {
        perror("bind");
        return 1;
    }
    listen(listen_fd, 64);

    int unique_ip_count = 0;
    for (int i = 0; i < g_cfg.server_count; i++) {
        int seen = 0;
        for (int j = 0; j < i; j++) {
            if (g_cfg.servers[j].family == g_cfg.servers[i].family &&
                !strcmp(g_cfg.servers[j].host, g_cfg.servers[i].host)) { seen = 1; break; }
        }
        if (!seen) unique_ip_count++;
    }

    char sni_join[2048] = "";
    for (int i = 0; i < g_cfg.sni_count; i++) {
        strcat(sni_join, g_cfg.sni_list[i]);
        if (i < g_cfg.sni_count - 1) strcat(sni_join, ", ");
    }
    char ttl_join[128] = "";
    for (int i = 0; i < g_cfg.ttl_count; i++) {
        char buf[16];
        snprintf(buf, sizeof(buf), i < g_cfg.ttl_count - 1 ? "%d, " : "%d", g_cfg.ttl_list[i]);
        strcat(ttl_join, buf);
    }
    char fallback_join[128] = "";
    for (int i = 0; i < g_cfg.fallback_port_count; i++) {
        char buf[16];
        snprintf(buf, sizeof(buf), i < g_cfg.fallback_port_count - 1 ? "%d, " : "%d", g_cfg.fallback_ports[i]);
        strcat(fallback_join, buf);
    }

    printf("cloak -- listening on 127.0.0.1:%d (Ctrl+C to stop)\n\n", g_cfg.listen_port);
    printf("  servers:        %d configured (%d unique IPs x up to %d ports)\n",
           g_cfg.server_count, unique_ip_count, g_cfg.fallback_port_count);
    printf("  fallback ports: %s\n", fallback_join);
    printf("  sni list:       %s\n", sni_join);
    printf("  ttl list:       %s\n", ttl_join);
    printf("  jitter:         %d-%dms\n", g_cfg.jitter_min_ms, g_cfg.jitter_max_ms);
    printf("  calibrate:      %s\n", g_cfg.calibrate ? "on" : "off");
    printf("  fragment:       %s\n", g_cfg.fragment_real ? "on" : "off");
    printf("  adaptive:       %s\n\n", g_cfg.adaptive ? "on" : "off");

    pthread_t summary_tid;
    (void)pthread_create(&summary_tid, NULL, summary_thread, NULL);
    pthread_detach(summary_tid);

    while (!g_shutdown_requested) {
        struct pollfd pfd = { .fd = listen_fd, .events = POLLIN };
        int rc = poll(&pfd, 1, 500);
        if (rc <= 0) continue;

        struct sockaddr_in ca;
        socklen_t cl = sizeof(ca);
        int *cfd = malloc(sizeof(int));
        *cfd = accept(listen_fd, (struct sockaddr *)&ca, &cl);
        if (*cfd < 0) { free(cfd); continue; }

        pthread_t tid;
        (void)pthread_create(&tid, NULL, handle_client, cfd);
        pthread_detach(tid);
    }
    close(listen_fd);

    if (g_shutdown_requested) {
        printf("\ncloak: shutting down (signal received)\n");
        printf("cloak: stats saved to %s\n", g_cfg.stats_path);
    }

    return 0;
}

#ifdef __ANDROID__
#include <jni.h>
static int g_jni_listen_fd = -1;
static pthread_t g_jni_thread;
static int g_jni_running = 0;
static char g_jni_config_path[1024];

static void* jni_cloak_thread(void* arg) {
    const char* path = (const char*)arg;
    if (load_config(path) < 0) {
        LOGE("jni", "failed to load config %s", path);
        g_jni_running = 0;
        return NULL;
    }
    if (g_cfg.log_file_path[0] != '\0') {
        g_log_file = fopen(g_cfg.log_file_path, "a");
    }
    if (g_cfg.fallback_port_count == 0) parse_fallback_ports("443,2053,2083,2087,2096,8443");
    expand_servers();
    if (g_cfg.server_count == 0) {
        LOGE("jni", "connect_list empty");
        g_jni_running = 0;
        return NULL;
    }
    if (g_cfg.sni_count == 0) parse_sni_list("www.hcaptcha.com,www.speedtest.net,www.bing.com");
    if (g_cfg.ttl_count == 0) parse_ttl_list("4,5,6,8");
    load_stats();
    int fd = socket(AF_INET, SOCK_STREAM, 0);
    if (fd < 0) {
        LOGE("jni", "socket failed %s", strerror(errno));
        g_jni_running = 0;
        return NULL;
    }
    int yes = 1;
    (void)setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &yes, sizeof(yes));
    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = INADDR_ANY;
    addr.sin_port = htons((uint16_t)g_cfg.listen_port);
    if (bind(fd, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
        LOGE("jni", "bind %d failed %s", g_cfg.listen_port, strerror(errno));
        close(fd);
        g_jni_running = 0;
        return NULL;
    }
    listen(fd, 64);
    g_jni_listen_fd = fd;
    LOGI("jni", "cloak listening on 127.0.0.1:%d", g_cfg.listen_port);
    pthread_t summary_tid;
    (void)pthread_create(&summary_tid, NULL, summary_thread, NULL);
    pthread_detach(summary_tid);
    while (!g_shutdown_requested) {
        struct pollfd pfd = { .fd = fd, .events = POLLIN };
        int rc = poll(&pfd, 1, 500);
        if (rc <= 0) continue;
        struct sockaddr_in ca;
        socklen_t cl = sizeof(ca);
        int *cfd = (int*)malloc(sizeof(int));
        *cfd = accept(fd, (struct sockaddr*)&ca, &cl);
        if (*cfd < 0) { free(cfd); continue; }
        pthread_t tid;
        (void)pthread_create(&tid, NULL, handle_client, cfd);
        pthread_detach(tid);
    }
    close(fd);
    g_jni_listen_fd = -1;
    g_jni_running = 0;
    return NULL;
}

JNIEXPORT jint JNICALL Java_io_github_immaghzbad_aetherst_core_CloakNative_start(JNIEnv* env, jclass clazz, jstring jPath) {
    if (g_jni_running) return -1;
    const char* path = (*env)->GetStringUTFChars(env, jPath, 0);
    strncpy(g_jni_config_path, path, sizeof(g_jni_config_path)-1);
    (*env)->ReleaseStringUTFChars(env, jPath, path);
    g_shutdown_requested = 0;
    g_jni_running = 1;
    if (pthread_create(&g_jni_thread, NULL, jni_cloak_thread, g_jni_config_path) != 0) {
        g_jni_running = 0;
        return -1;
    }
    pthread_detach(g_jni_thread);
    return 0;
}

JNIEXPORT jint JNICALL Java_io_github_immaghzbad_aetherst_core_CloakNative_stop(JNIEnv* env, jclass clazz) {
    g_shutdown_requested = 1;
    if (g_jni_listen_fd >= 0) {
        shutdown(g_jni_listen_fd, SHUT_RDWR);
        close(g_jni_listen_fd);
        g_jni_listen_fd = -1;
    }
    g_jni_running = 0;
    return 0;
}

JNIEXPORT jint JNICALL Java_io_github_immaghzbad_aetherst_core_CloakNative_isRunning(JNIEnv* env, jclass clazz) {
    return g_jni_running;
}

JNIEXPORT void JNICALL Java_io_github_immaghzbad_aetherst_core_CloakNative_setLogLevel(JNIEnv* env, jclass clazz, jint level) {
    if (level <= LOG_ERROR) g_log_level = LOG_ERROR;
    else if (level == 1) g_log_level = LOG_WARN;
    else if (level == 2) g_log_level = LOG_INFO;
    else g_log_level = LOG_DEBUG;
}
#endif
