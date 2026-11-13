from pathlib import Path
root=Path(__file__).resolve().parents[1]
java=root/'app/src/main/java/com/unipoint'
shell=(java/'presentation/ui/DoupadShell.kt').read_text(encoding='utf-8')
workspace=(java/'presentation/ui/screens/pc/PcWorkspaceScreen.kt').read_text(encoding='utf-8')
pair=(java/'core/util/TrustedPcPairing.kt').read_text(encoding='utf-8')
rec=(java/'core/util/MirrorCrashRecorder.kt').read_text(encoding='utf-8')
gradle=(root/'app/build.gradle.kts').read_text(encoding='utf-8')
workflow=(root/'.github/workflows/build-apk.yml').read_text(encoding='utf-8')
assert 'setOrientationLocked(true)' in shell
assert 'TrustedPcPairing.save(context,endpoint,pin)' in shell
assert 'TrustedPcPairing.read(context,target.endpoint)' in shell
assert 'TrustedPcPairing.forgetAll(context)' in shell
assert 'if(state.mode==AppMode.PC){' in shell
assert 'AndroidKeyStore' in pair and 'AES/GCM/NoPadding' in pair
assert 'SCREEN_ORIENTATION_SENSOR_LANDSCAPE' in workspace
assert 'WindowInsetsCompat.Type.statusBars()' in workspace
assert 'rememberSaveable(initialTab)' in workspace
assert 'BackHandler(tab == "Screen")' in workspace
assert 'ContentScale.Fit' in workspace
assert 'Installed RAM' in workspace and 'Free memory' in workspace  # R21 professional memory cards
assert '%.1f GB' in workspace
assert 'PC_JAVA_CRASH' in rec
assert 'versionCode = 34' in gradle and '0.18.7-r22-single-workspace' in gradle
assert 'DuoPad-Pro-v0.18.7-R22-debug.apk' in workflow
print('DOUPAD_R20_PC_EXPERIENCE_SOURCE_PASS')
