// The stock of product-service as a label. Low stock is shown: the cart refuses more than what is left
export function StockLabel({ stock }: { stock: number }) {
  if (stock === 0) {
    return <span className="status status-CANCELLED">Rupture de stock</span>;
  }
  if (stock <= 5) {
    return <span className="status status-PENDING">Plus que {stock} en stock</span>;
  }
  return <span className="status status-PAID">En stock</span>;
}
