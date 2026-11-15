import { describe, expect, it } from "vitest";
import { formatCardNumber, toCardNumber } from "./PaymentForm";

describe("formatCardNumber", () => {
  it("groups the digits by 4 while typing", () => {
    expect(formatCardNumber("424242")).toBe("4242 42");
    expect(formatCardNumber("4242424242424242")).toBe("4242 4242 4242 4242");
  });

  it("drops what is not a digit and stops at 16 digits", () => {
    expect(formatCardNumber("4242-4242 abcd 4242 4242 99")).toBe("4242 4242 4242 4242");
  });
});

describe("toCardNumber", () => {
  it("sends the 16 digits only", () => {
    expect(toCardNumber("4000 0000 0000 0002")).toBe("4000000000000002");
  });
});
