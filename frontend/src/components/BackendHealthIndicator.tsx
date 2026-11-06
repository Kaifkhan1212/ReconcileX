import { useState, useEffect, useRef } from "react";
import { getHealthApi } from "@/lib/api";
import { RefreshCw, Zap } from "lucide-react";

interface BackendHealthIndicatorProps {
  className?: string;
  showRefresh?: boolean;
}

const CACHE_KEY = "rx_backend_health_status";
const CACHE_TIME_KEY = "rx_backend_health_time";

// How many consecutive silent failures before showing "Waking Engine..."
const FAILURES_BEFORE_WARNING = 2;

export function BackendHealthIndicator({
  className = "",
  showRefresh = false,
}: BackendHealthIndicatorProps) {
  // Always start with "UP" — matches SSR output exactly → fixes React hydration error #418
  const [status, setStatus] = useState<"UP" | "DOWN" | "CHECKING">("UP");
  const [loading, setLoading] = useState(false);
  const [wakingUp, setWakingUp] = useState(false);
  const failCountRef = useRef(0);
  const timerRef = useRef<NodeJS.Timeout | null>(null);

  const checkHealth = async (silent = true) => {
    if (!silent) setLoading(true);

    try {
      // 8-second timeout — Render free tier can be slow to respond
      const res = await getHealthApi(8000);
      if (res.status === "UP") {
        // ✅ Backend responded — reset everything to green
        failCountRef.current = 0;
        setStatus("UP");
        setWakingUp(false);
        try {
          sessionStorage.setItem(CACHE_KEY, "UP");
          sessionStorage.setItem(CACHE_TIME_KEY, Date.now().toString());
        } catch {
          // Ignore storage errors
        }
      } else {
        failCountRef.current += 1;
        if (failCountRef.current >= FAILURES_BEFORE_WARNING) {
          setStatus("DOWN");
          setWakingUp(true);
        }
        // If only 1 failure — keep showing "UP" optimistically and retry
      }
    } catch {
      // Network error or timeout
      failCountRef.current += 1;
      if (failCountRef.current >= FAILURES_BEFORE_WARNING) {
        setStatus("DOWN");
        setWakingUp(true);
        try {
          sessionStorage.setItem(CACHE_KEY, "DOWN");
          sessionStorage.setItem(CACHE_TIME_KEY, Date.now().toString());
        } catch {
          // Ignore storage errors
        }
      }
      // If only 1 failure — stay optimistic ("UP") and retry silently
    } finally {
      if (!silent) setLoading(false);
    }
  };

  useEffect(() => {
    // After hydration: check sessionStorage to restore known DOWN state
    // (This runs client-only, after SSR hydration is complete — no mismatch)
    try {
      const cached = sessionStorage.getItem(CACHE_KEY);
      const cachedTime = sessionStorage.getItem(CACHE_TIME_KEY);
      if (
        cached === "DOWN" &&
        cachedTime &&
        Date.now() - Number(cachedTime) < 15000
      ) {
        // Was DOWN very recently — start retrying immediately but show warning
        failCountRef.current = FAILURES_BEFORE_WARNING;
        setStatus("DOWN");
        setWakingUp(true);
      }
    } catch {
      // Ignore storage errors
    }

    // Start first background health check with a small delay
    // so the page renders fully before the network request fires
    const initTimer = setTimeout(() => {
      void checkHealth(true);
    }, 1500);

    return () => {
      clearTimeout(initTimer);
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, []);

  // Adaptive polling:
  // - UP: check every 30s (heartbeat)
  // - DOWN/waking: retry every 4s until it comes back
  useEffect(() => {
    if (timerRef.current) clearTimeout(timerRef.current);

    const delay = status === "UP" ? 30000 : 4000;
    timerRef.current = setTimeout(() => {
      void checkHealth(true);
    }, delay);

    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, [status]);

  const handleManualRefresh = () => {
    if (timerRef.current) clearTimeout(timerRef.current);
    failCountRef.current = 0;
    void checkHealth(false);
  };

  return (
    <div
      className={`inline-flex items-center gap-2 px-2.5 py-1 rounded-md border text-xs font-medium transition-all ${
        status === "UP"
          ? "border-emerald-500/25 bg-emerald-500/10 text-emerald-400"
          : wakingUp
          ? "border-amber-500/30 bg-amber-500/10 text-amber-300"
          : "border-slate-800 bg-slate-900/60 text-slate-400"
      } ${className}`}
      title={
        status === "UP"
          ? "Backend engine connected and ready"
          : wakingUp
          ? "Backend container is waking up from idle. Ready in ~15-30s..."
          : "Checking connection to reconciliation engine"
      }
    >
      <span
        className={`h-2 w-2 rounded-full shrink-0 ${
          status === "UP"
            ? "bg-emerald-400 animate-pulse"
            : wakingUp
            ? "bg-amber-400 animate-ping"
            : "bg-slate-500 animate-pulse"
        }`}
      />
      <span className="font-mono flex items-center gap-1">
        {status === "UP" ? (
          "Engine Reachable"
        ) : wakingUp ? (
          <>
            <Zap className="h-3 w-3 text-amber-400 shrink-0" />
            <span>Waking Engine...</span>
          </>
        ) : (
          "Engine Reachable"
        )}
      </span>

      {showRefresh && (
        <button
          type="button"
          onClick={handleManualRefresh}
          disabled={loading}
          aria-label="Refresh connection health"
          className="ml-0.5 p-0.5 rounded text-current hover:opacity-80 transition-opacity"
        >
          <RefreshCw className={`h-3 w-3 ${loading ? "animate-spin" : ""}`} />
        </button>
      )}
    </div>
  );
}
