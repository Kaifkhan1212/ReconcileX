import React, { createContext, useContext, useState, useEffect, useMemo, useCallback, useRef } from "react";
import type { AuthUser, AppUserRole } from "./types";
import { loginApi, registerUnauthorizedHandler } from "./api";

export interface AuthContextType {
  token: string | null;
  user: AuthUser | null;
  isAuthenticated: boolean;
  isAdmin: boolean;
  isViewer: boolean;
  sessionExpired: boolean;
  remainingSeconds: number | null;
  authModalOpen: boolean;
  setAuthModalOpen: (open: boolean) => void;
  openLoginModal: () => void;
  dismissSessionExpired: () => void;
  login: (username: string, password?: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function decodeJwtPayload(
  token: string,
): { sub?: string; role?: string; exp?: number } | null {
  try {
    const parts = token.split(".");
    if (parts.length < 2 || !parts[1]) return null;
    const base64Url = parts[1];
    const base64 = base64Url.replace(/-/g, "+").replace(/_/g, "/");
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split("")
        .map((c) => "%" + ("00" + c.charCodeAt(0).toString(16)).slice(-2))
        .join(""),
    );
    return JSON.parse(jsonPayload) as { sub?: string; role?: string; exp?: number };
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  // STRICT CONSTRAINT: Token is stored exclusively in-memory in React state.
  // NEVER write to localStorage or sessionStorage.
  const [token, setToken] = useState<string | null>(null);
  const [user, setUser] = useState<AuthUser | null>(null);
  const [sessionExpired, setSessionExpired] = useState(false);
  const [remainingSeconds, setRemainingSeconds] = useState<number | null>(null);
  const [authModalOpen, setAuthModalOpen] = useState(false);
  const wasLoggedInRef = useRef(false);

  // Handle 401s from any API call to gracefully expire session in-memory
  useEffect(() => {
    registerUnauthorizedHandler(() => {
      setToken(null);
      setUser(null);
      setRemainingSeconds(null);
      if (wasLoggedInRef.current) {
        setSessionExpired(true);
      }
      wasLoggedInRef.current = false;
    });
    return () => registerUnauthorizedHandler(null);
  }, []);

  // Monitor token expiration periodically
  useEffect(() => {
    if (!token) {
      setRemainingSeconds(null);
      return;
    }

    const payload = decodeJwtPayload(token);
    if (!payload?.exp) {
      setRemainingSeconds(null);
      return;
    }

    const checkExpiration = () => {
      const nowSeconds = Math.floor(Date.now() / 1000);
      const diff = payload.exp! - nowSeconds;
      if (diff <= 0) {
        setToken(null);
        setUser(null);
        setRemainingSeconds(0);
        if (wasLoggedInRef.current) {
          setSessionExpired(true);
        }
        wasLoggedInRef.current = false;
      } else {
        setRemainingSeconds(diff);
      }
    };

    checkExpiration();
    const interval = setInterval(checkExpiration, 1000);
    return () => clearInterval(interval);
  }, [token]);

  const isExpired = remainingSeconds !== null && remainingSeconds <= 0;

  const login = useCallback(async (username: string, password?: string) => {
    const response = await loginApi(username, password);
    const jwt = response.token;
    setToken(jwt);
    setSessionExpired(false);
    wasLoggedInRef.current = true;

    const payload = decodeJwtPayload(jwt);
    const rawRole = (payload?.role || "").trim().toUpperCase();
    const role: AppUserRole = rawRole === "ADMIN" || rawRole === "ROLE_ADMIN" ? "ADMIN" : "VIEWER";
    const sub = payload?.sub || username;

    setUser({ username: sub, role });
    if (payload?.exp) {
      const nowSeconds = Math.floor(Date.now() / 1000);
      setRemainingSeconds(Math.max(0, payload.exp - nowSeconds));
    }
  }, []);

  const logout = useCallback(() => {
    setToken(null);
    setUser(null);
    setRemainingSeconds(null);
    setSessionExpired(false);
    wasLoggedInRef.current = false;
  }, []);

  const dismissSessionExpired = useCallback(() => {
    setSessionExpired(false);
  }, []);

  const openLoginModal = useCallback(() => {
    setAuthModalOpen(true);
  }, []);

  const value = useMemo(
    () => ({
      token: isExpired ? null : token,
      user: isExpired ? null : user,
      isAuthenticated: !!token && !isExpired,
      isAdmin: !isExpired && user?.role === "ADMIN",
      isViewer: !isExpired && user?.role === "VIEWER",
      sessionExpired,
      remainingSeconds,
      authModalOpen,
      setAuthModalOpen,
      openLoginModal,
      dismissSessionExpired,
      login,
      logout,
    }),
    [
      token,
      user,
      isExpired,
      sessionExpired,
      remainingSeconds,
      authModalOpen,
      openLoginModal,
      dismissSessionExpired,
      login,
      logout,
    ],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}
