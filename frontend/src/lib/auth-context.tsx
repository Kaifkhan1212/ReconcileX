import React, { createContext, useContext, useState, useMemo } from "react";
import type { AuthUser, AppUserRole } from "./types";
import { loginApi } from "./api";

interface AuthContextType {
  token: string | null;
  user: AuthUser | null;
  isAuthenticated: boolean;
  isAdmin: boolean;
  login: (username: string, password?: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

function decodeJwtPayload(token: string): { sub?: string; role?: string } | null {
  try {
    const parts = token.split(".");
    if (parts.length < 2 || !parts[1]) return null;
    const base64Url = parts[1];
    const base64 = base64Url.replace(/-/g, "+").replace(/_/g, "/");
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split("")
        .map((c) => "%" + ("00" + c.charCodeAt(0).toString(16)).slice(-2))
        .join("")
    );
    return JSON.parse(jsonPayload) as { sub?: string; role?: string };
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  // STRICT CONSTRAINT: Token is stored exclusively in-memory in React state.
  // NEVER write to localStorage or sessionStorage.
  const [token, setToken] = useState<string | null>(null);
  const [user, setUser] = useState<AuthUser | null>(null);

  const login = async (username: string, password?: string) => {
    const response = await loginApi(username, password);
    const jwt = response.token;
    setToken(jwt);

    const payload = decodeJwtPayload(jwt);
    const role: AppUserRole =
      payload?.role === "ADMIN" || payload?.role === "ROLE_ADMIN" ? "ADMIN" : "VIEWER";
    const sub = payload?.sub || username;

    setUser({ username: sub, role });
  };

  const logout = () => {
    setToken(null);
    setUser(null);
  };

  const value = useMemo(
    () => ({
      token,
      user,
      isAuthenticated: !!token,
      isAdmin: user?.role === "ADMIN",
      login,
      logout,
    }),
    [token, user]
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
