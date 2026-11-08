from pathlib import Path
root = Path(__file__).resolve().parents[1]
mirror = (root / "app/src/main/java/com/unipoint/presentation/ui/DoupadMirrorActivity.kt").read_text(encoding="utf-8")
assert "pendingBlackRetry=reason" in mirror
assert "pendingBlackRetry = null" in mirror
assert "attemptDone.await(3, java.util.concurrent.TimeUnit.SECONDS)" in mirror
assert "if (blackRetry == null && error == null && !abandoned) return" in mirror
assert "PROFILE_NEXT:" in mirror
assert "outputFrames<=3 || outputFrames%60==0" in mirror
print("DOUPAD_R9_MIRROR_PROFILE_FALLBACK_CHECKS_PASS")
