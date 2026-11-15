import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { cartApi } from "../api/endpoints";
import type { Cart } from "../api/types";
import { useAuth } from "../auth/AuthContext";

interface CartState {
  // Units in the cart, shown in the header. 0 when nobody is logged in
  count: number;
  // To call with the cart returned by each cart request: the header follows without another request
  update: (cart: Cart | null) => void;
  refresh: () => void;
}

const CartContext = createContext<CartState | null>(null);

export function CartProvider({ children }: { children: ReactNode }) {
  const { session } = useAuth();
  const [count, setCount] = useState(0);

  const update = useCallback((cart: Cart | null) => setCount(cart?.totalQuantity ?? 0), []);

  const refresh = useCallback(() => {
    if (!session) {
      setCount(0);
      return;
    }
    // Only a counter: if order-service does not answer, the header shows 0 and the cart page says why
    cartApi.get().then(update, () => setCount(0));
  }, [session, update]);

  // A new session (login, logout, another account) has another cart
  useEffect(refresh, [refresh]);

  const value = useMemo(() => ({ count, update, refresh }), [count, update, refresh]);
  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart(): CartState {
  const cart = useContext(CartContext);
  if (!cart) {
    throw new Error("useCart outside of <CartProvider>");
  }
  return cart;
}
