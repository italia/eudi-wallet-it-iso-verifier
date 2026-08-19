# Sample Android App - NFC Implementation

## Overview

The Android sample app (`sampleandroid`) demonstrates the current policy-driven NFC flow.
The app passes a single `ProximityVerificationSettings` object to the SDK and lets the library
resolve engagement parsing, transport selection, and data retrieval.

## Implementation Highlights

### 1. NFC Configuration Uses `ProximityVerificationSettings`

```kotlin
val settings = ProximityVerificationSettings(
    requests = requests,
    preferredTransport = TransportPreference.AUTO,
    bleEnabled = true,
    nfcDataTransferEnabled = true,
    bleCentralClientMode = true,
    blePeripheralServerMode = false,
    bleUseL2CAP = false,
    bleUseL2CAPInEngagement = true,
    nfcScanMessage = "Hold near credential holder's device",
    nfcTransferMessage = "Transferring data, keep in reader's field",
    transportTimeout = 30.seconds,
    responseTimeout = 300.seconds
)
```

### 2. Callback-Driven UI Feedback

The sample surfaces the NFC lifecycle through `VerificationStep` updates:

```kotlin
override fun onVerificationUpdate(step: VerificationStep) {
    statusText = when (step) {
        is VerificationStep.NfcScanningStarted -> "NFC ready: ${step.message}"
        VerificationStep.NfcTagDetected -> "NFC tag detected"
        VerificationStep.NfcEngagementReading -> "Reading engagement data"
        VerificationStep.NfcEngagementCompleted -> "Engagement completed"
        is VerificationStep.TransportSelected -> "Transport: ${step.transportType}"
        else -> "Verification in progress"
    }
}
```

### 3. End-to-End Flow

1. Initialize the SDK.
2. Start `startVerificationWithNfc(settings, callback, activity)`.
3. Let the SDK scan the NFC engagement.
4. Let the SDK resolve the concrete transport.
5. Receive results through `VerificationOutcome`.

## Configuration Notes

### Transport Settings

| Option | Description | Default |
|---|---|---|
| `nfcDataTransferEnabled` | Allow NFC for data exchange | `true` |
| `bleEnabled` | Allow BLE when the session resolves to BLE | `true` |
| `preferredTransport` | Preferred method (`NFC`, `BLE`, `AUTO`) | `AUTO` |

Use `TransportPreference.AUTO` unless you have a strict product requirement to force one transport.

### Timeout Settings

| Option | Description | Default |
|---|---|---|
| `transportTimeout` | Max wait for connection and request send | `30s` |
| `responseTimeout` | Max wait for holder response | `300s` |
| `nfcScanMessage` | Prompt shown while waiting for tap | `"Hold near credential holder's device"` |
| `nfcTransferMessage` | Prompt shown during NFC transfer | `"Transferring data, keep in reader's field"` |

## Error Handling

The sample maps `VerificationOutcome.ProtocolFailed` into user-facing UI states:

```kotlin
when (outcome.reason) {
    ProtocolFailureReason.NFC_NOT_SUPPORTED -> "NFC not available on this device"
    ProtocolFailureReason.NFC_NOT_ENABLED -> "NFC is disabled. Please enable NFC in settings"
    ProtocolFailureReason.NFC_TAG_LOST -> "NFC connection lost. Please try again."
    ProtocolFailureReason.INVALID_ENGAGEMENT_DATA -> "Invalid NFC data. Ensure the other device is ready."
    ProtocolFailureReason.NFC_SCAN_CANCELLED -> "NFC scan cancelled by user."
    else -> outcome.message
}
```

## Transport Behavior

- `startVerificationWithNfc()` no longer requires a separate `NfcEngagementOptions` object.
- The sample does not block NFC verification behind BLE permissions.
- If the session later resolves to BLE and permissions are missing, the SDK reports a transport
  failure through the callback instead of preventing valid NFC-only flows upfront.

## Best Practices

### 1. Check NFC Availability

```kotlin
val hasNfc = NfcChecker.getNfcAvailability()
val nfcEnabled = NfcChecker.isNfcEnabled()
```

### 2. Let the SDK Resolve the Transport

Do not hardcode BLE as a prerequisite for NFC engagement. The SDK decides whether the active
session stays on NFC or moves to BLE.

### 3. Keep the User Informed

- Show when the app is ready for tap
- Confirm tag detection
- Indicate when reading engagement data
- Surface transport changes and completion

### 4. Test on Real Hardware

NFC cannot be reliably tested in emulators. Use physical devices for engagement and retrieval.

## Testing Checklist

- [ ] NFC availability on a device without NFC
- [ ] NFC disabled in system settings
- [ ] Successful end-to-end verification
- [ ] Tag lost during scan
- [ ] NFC-only and NFC-to-BLE transport resolution
- [ ] Multiple verifications in sequence
- [ ] Configuration changes during verification
- [ ] Background and foreground transitions

## Summary

The sample app demonstrates:

- Policy-driven NFC verification with `ProximityVerificationSettings`
- Callback-driven UI feedback for NFC lifecycle steps
- Error handling based on `VerificationOutcome.ProtocolFailed`
- A transport-neutral flow that does not incorrectly gate NFC behind BLE permissions
