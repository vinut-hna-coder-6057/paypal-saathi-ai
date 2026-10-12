import test from "node:test";
import assert from "node:assert/strict";
import { getGuidance } from "../src/services/guidanceEngine.js";

test("returns guidance for the send money category", () => {
  const result = getGuidance("send_money");

  assert.equal(result.category, "send_money");
  assert.ok(result.steps.length >= 3);
  assert.match(result.title, /Sending money/i);
});

test("supports a recipient selection alias", () => {
  const result = getGuidance("select recipient");

  assert.equal(result.category, "recipient_selection");
});

test("review guidance reminds users to verify details", () => {
  const result = getGuidance("review_payment");

  assert.equal(result.category, "review_payment");
  assert.ok(result.steps.some(step => /verify/i.test(step)));
});

test("unknown screens receive safe fallback guidance", () => {
  const result = getGuidance("unrecognized_screen");

  assert.equal(result.category, "unknown");
  assert.ok(result.steps.length > 0);
});

test("non-string input receives fallback guidance", () => {
  assert.equal(getGuidance(null).category, "unknown");
});
