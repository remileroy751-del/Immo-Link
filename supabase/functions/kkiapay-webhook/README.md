# KKiaPay webhook for ImmoLink

This Edge Function verifies the transaction server-side with the KKiaPay Admin SDK before activating a promotion, certification payment, or extra agency slots.

Required Supabase secrets/environment variables:
- KKIAPAY_PUBLIC_KEY
- KKIAPAY_PRIVATE_KEY
- KKIAPAY_SECRET_KEY

The Android app only contains the public key. Never put the private or secret key in the APK or GitHub.

Configure the KKiaPay webhook URL to:
`https://hraiykeenouojxrcwrlx.supabase.co/functions/v1/kkiapay-webhook`

Subscribe to successful and failed transaction events. The webhook's `partnerId` is the ImmoLink payment UUID.
