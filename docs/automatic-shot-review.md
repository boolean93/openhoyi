# Automatic extraction review

The API key settings page has a separate, default-off switch. It discloses that confirmed cups' pressure, machine flow, water, temperature and available scale measurements go to DeepSeek and can incur charges. It authorizes only the current saved credential identity. Replacing or deleting the credential or disabling the switch invalidates pending requests. Notes, names, IDs, historical cups, chat history and attachments are not sent.

## Trigger and isolation

- The application owns the review, not a screen/session. The existing terminal cup handler captures the authorization only for a confirmed, uninterrupted completion, after the history record commits. The existing asynchronous sample writer reports when that cup's sample data commits. Failed writes, absent/unusable evidence, no authorization and no saved key do not send a request.
- A bounded representation of observed brewing samples goes to the fixed DeepSeek Flash HTTPS chat/completions endpoint through the existing protected transport. It includes explicit units and provenance, the optional machine duration and a distinct observed sample span. It does not infer how the coffee tastes or pretend to know the intended recipe. No agent tools, search, further history access or recipe/machine writes are available.
- A local per-cup attempt marker commits **before** sending. The same cup is never replayed, including on restart, even if a timeout or process death leaves the outcome unknown. Endpoint, transport operation, credential epoch and consent revision are fenced. Disabling or changing the credential closes stale in-flight transport and suppresses stale results. If a request already reached the provider, cancellation does not guarantee it was not processed or charged.
- The live finish page shows a compact progress/result line for its current cup. The full historical detail shows the persistent frozen result, if received. Later cups, navigation, and process restarts cannot reassign results. Mock never sends to a real provider.

## Verification

Offline unit tests exercise bounded sample payloads, same protected transport with a fake HTTPS connection, durable-attempt contract, isolation, duplicate prevention, timeout, local persistence, consent and key-rotation fences. Real hardware or paid provider acceptance is separate.
