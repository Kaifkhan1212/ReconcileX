import { useState, useEffect, useRef } from "react";
import { getHealthApi } from "@/lib/api";
import { RefreshCw, Zap } from "lucide-react";

interface BackendHealthIndicatorProps {
  className?: string;
  showRefresh?: boolean;
}

export function BackendHealthIndicator({
  className = "",
  showRefresh = false,
}: BackendHealthIndicatorProps) {
  const [status, setStatus] = useState<"UP" | "DOWN" | "CHECKING">("CHECKING");
  const [loading, setLoading] = useState(false);
  const [wakingUp, setWakingUp] = useState(false);
  const checkCountRef = useRef(0);
  const timerRef = useRef<NodeJS.Timeout | null>(null);

  const checkHealth = async () => {
    setLoading(true);
    checkCountRef.current += 1;
    if (checkCountRef.current > 1 && status !== "UP") {
      setWakingUp(true);
    }

    try {
      const res = await getHealthApi(8000);
      if (res.status === "UP") {
        setStatus("UP");
        setWakingUp(false);
      } else {
        setStatus("DOWN");
      }
    } catch {
      setStatus("DOWN");
      if (checkCountRef.current >= 1) {
        setWakingUp(true);
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    checkHealth();

    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, []);

  // Adaptive polling: fast 3.5s retries while connecting/waking, 30s heartbeat once connected
  useEffect(() => {
    if (timerRef.current) clearTimeout(timerRef.current);

    const delay = status === "UP" ? 30000 : 3500;
    timerRef.current = setTimeout(() => {
      checkHealth();
    }, delay);

    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, [status, loading]);

  const handleManualRefresh = () => {
    if (timerRef.current) clearTimeout(timerRef.current);
    checkHealth();
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
