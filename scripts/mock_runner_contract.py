"""Explicit, reviewed instrumentation targets for the isolated Mock test APK.

Do not derive the allowlist from the APK being validated: unknown declarations
must fail closed. Keep the source-manifest regression in sync with additions.
"""
APP = 'io.openhoyi.mobile.mock'
TEST = APP + '.test'
RUNNERS = {
    'io.openhoyi.mobile.BrewAudioInstrumentation': APP,
    'io.openhoyi.mobile.MockHierarchyInstrumentation': TEST,
    'io.openhoyi.mobile.CurveFilterPopupInstrumentation': APP,
    'io.openhoyi.mobile.PendingRecipeLabelChecks': APP,
    'io.openhoyi.mobile.CloudflareDownloadLiveInstrumentation': APP,
}
