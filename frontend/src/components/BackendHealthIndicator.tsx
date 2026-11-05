import { useState, useEffect, useRef } from "react";
import { getHealthApi } from "@/lib/api";
import { RefreshCw, Zap } from "lucide-react";

interface BackendHealthIndicatorProps {
  className?: string;
  showRefresh?: boolean;
}

const CACHE_KEY = "rx_backend_health_status";
const CACHE_TIME_KEY = "rx_backend_health_time";
const CACHE_MAX_AGE_MS = 60 * 1000; // 1 minute fresh cache

function getInitialHealthStatus(): "UP" | "DOWN" | "CHECKING" {
  // Always default optimistically to UP so visitors immediately see "Engine Reachable"
  if (typeof window === "undefined") return "UP";
  try {
    const cached = sessionStorage.getItem(CACHE_KEY);
    const cachedTime = sessionStorage.getItem(CACHE_TIME_KEY);
    if (cached === "DOWN" && cachedTime && Date.now() - Number(cachedTime) < 15000) {
      return "DOWN";
    }
  } catch {
    // Ignore storage errors
  }
  return "UP";
}

export function BackendHealthIndicator({
  className = "",
  showRefresh = false,
}: BackendHealthIndicatorProps) {
  const [status, setStatus] = useState<"UP" | "DOWN" | "CHECKING">(getInitialHealthStatus);
  const [loading, setLoading] = useState(false);
  const [wakingUp, setWakingUp] = useState(false);
  const checkCountRef = useRef(0);
  const timerRef = useRef<NodeJS.Timeout | null>(null);

  const checkHealth = async (silent = true) => {
    if (!silent) setLoading(true);
    checkCountRef.current += 1;

    try {
      // 5-second timeout for responsive check
      const res = await getHealthApi(5000);
      if (res.status === "UP") {
        setStatus("UP");
        setWakingUp(false);
        try {
          sessionStorage.setItem(CACHE_KEY, "UP");
          sessionStorage.setItem(CACHE_TIME_KEY, Date.now().toString());
        } catch {
          // Ignore storage errors
        }
      } else {
        setStatus("DOWN");
      }
    } catch {
      setStatus("DOWN");
      setWakingUp(true);
      try {
        sessionStorage.setItem(CACHE_KEY, "DOWN");
        sessionStorage.setItem(CACHE_TIME_KEY, Date.now().toString());
      } catch {
        // Ignore storage errors
      }
    } finally {
      if (!silent) setLoading(false);
    }
  };

  useEffect(() => {
    // Initial silent check on mount
    void checkHealth(true);

    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, []);

  // Adaptive polling: fast 3.5s retries while waking, 30s heartbeat once connected
  useEffect(() => {
    if (timerRef.current) clearTimeout(timerRef.current);

    const delay = status === "UP" ? 30000 : 3500;
    timerRef.current = setTimeout(() => {
      void checkHealth(true);
    }, delay);

    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, [status]);

  const handleManualRefresh = () => {
    if (timerRef.current) clearTimeout(timerRef.current);
    void checkHealth(false);
  };

  return (
    <div
      className={`inline-flex items-center gap-2 px-2.5 py-1 rounded-md border text-xs font-medium transition-all ${
        status === "UP"
          ? "border-emerald-500/25 bg-emerald-500/10 text-emerald-400"
          : wakingUp
          ? "border-amber-500/30 bg-amber-500/10 text-amber-300"
          : status === "CHECKING"
          ? "border-slate-800 bg-slate-900/60 text-slate-400"
          : "border-rose-500/25 bg-rose-500/10 text-rose-400"
      } ${className}`}
      title={
        status === "UP"
          ? "Backend engine connected and ready"
          : wakingUp
          ? "Backend container is waking up from idle (Render Free Tier). Connecting in ~15-30s..."
          : "Checking connection to reconciliation engine"
      }
    >
      <span
        className={`h-2 w-2 rounded-full shrink-0 ${
          status === "UP"
            ? "bg-emerald-400 animate-pulse"
            : wakingUp
            ? "bg-amber-400 animate-ping"
            : status === "CHECKING"
            ? "bg-slate-500 animate-pulse"
            : "bg-rose-500"
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
        ) : status === "CHECKING" ? (
          "Connecting..."
        ) : (
          "Connecting..."
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
