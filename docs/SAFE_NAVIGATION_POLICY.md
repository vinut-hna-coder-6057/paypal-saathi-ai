# Safe navigation policy

## Status

**IMPLEMENTED AND UNIT-TESTED** as a narrow, user-triggered accessibility action. **DEVICE VALIDATION PENDING**; this behavior requires Android accessibility-policy review before release.

## Preconditions

The user must tap the matching “Open … in PayPal” button in the existing Saathi overlay. Before the service acts, it requires:

1. The foreground and live accessibility root belong to `com.paypal.android.p2pmobile`.
2. The task is waiting for the user and the task/step IDs match.
3. The observation is available and no older than 15 seconds.
4. The live bounded hierarchy rebuild has the same screen fingerprint as the planned observation.
5. The category and confidence match the task step and meet the minimum threshold.
6. The exact action descriptor is allow-listed, still present, visible to the user, enabled, clickable, supported by `ACTION_CLICK`, and represented by a recognized button/tab role.
7. The action has not already been attempted for that screen fingerprint in this service lifetime.

The service re-finds the node from the live tree; it does not retain stale node references from the original observation. The hierarchy is scanned with the existing depth/node bounds on the observation worker. Immediately before execution, the service rechecks the foreground package and task state, then invokes one node `ACTION_CLICK`. It never uses coordinates. A rejected or failed action is not automatically retried.

## Allowed labels

- From HOME: Send, Activity, Wallet, Settings, Help, Menu, More.
- From other non-sensitive categories: Activity, Wallet, Help, Home, Back.
- Security, sign-in, recipient-selection, amount-entry, review, confirmation, error, loading, unknown, stale, and ambiguous observations are not actionable.

These are exact UI labels, not generated strings. Workflow planning may still show manual guidance for steps outside this action allow-list.

## Always manual

The service never selects a recipient, enters an amount/account value, handles credentials/OTP/PIN, reviews payment details, submits a transaction, or authorizes payment. Confirmation is not inferred from a recommendation. All financial decisions remain the user's responsibility.

## Validation

`SafeActionPolicyTest` verifies wrong-app, stale, duplicate, missing-control, and financial-step rejection with synthetic observations. Actual Android node behavior and AccessibilityService policy compliance require real-device testing.
