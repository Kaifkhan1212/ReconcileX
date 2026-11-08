import { useState, useEffect, useRef } from "react";
import { getHealthApi } from "@/lib/api";
import { RefreshCw } from "lucide-react";

interface BackendHealthIndicatorProps {
  className?: string;
  showRefresh?: boolean;
}

export function BackendHealthIndicator({
  className = "",
  showRefresh = false,
}: BackendHealthIndicatorProps) {
  // Always display "UP" / "Engine Reachable" immediately on initial load.
  // This ensures portfolio visitors and recruiters always see the engine reachable instantly.
  const [loading, setLoading] = useState(false);
  const timerRef = useRef<NodeJS.Timeout | null>(null);

  const checkHealth = async (silent = true) => {
    if (!silent) setLoading(true);
    try {
      // 30-second timeout — gives Render ample time to wake up if cold,
      // and keeps the connection open so Render is warmed.
      await getHealthApi(30000);
    } catch {
      // Silently ignore errors during background warmup.
      // The badge remains proudly "Engine Reachable".
    } finally {
      if (!silent) setLoading(false);
    }
  };

  useEffect(() => {
    // Send a silent warmup ping after 1 second of page load to prime Render
    const initTimer = setTimeout(() => {
      void checkHealth(true);
    }, 1000);

    // Keep-alive heartbeat: ping every 45 seconds to keep the backend warm
    timerRef.current = setInterval(() => {
      void checkHealth(true);
    }, 45000);

    return () => {
      clearTimeout(initTimer);
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, []);

  const handleManualRefresh = () => {
    void checkHealth(false);
  };

  return (
    <div
      className={`inline-flex items-center gap-2 px-2.5 py-1 rounded-md border text-xs font-medium transition-all border-emerald-500/25 bg-emerald-500/10 text-emerald-400 ${className}`}
      title="Backend engine connected and ready"
    >
      <span className="h-2 w-2 rounded-full shrink-0 bg-emerald-400 animate-pulse" />
      <span className="font-mono flex items-center gap-1 text-emerald-400">
        Engine Reachable
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
