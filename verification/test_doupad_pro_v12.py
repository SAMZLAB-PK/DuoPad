from pathlib import Path
root=Path(__file__).resolve().parents[1]
shell=(root/"app/src/main/java/com/unipoint/presentation/ui/DoupadShell.kt").read_text()
theme=(root/"app/src/main/java/com/unipoint/presentation/ui/theme/Theme.kt").read_text()
typography=(root/"app/src/main/java/com/unipoint/presentation/ui/theme/Type.kt").read_text()
logo=(root/"app/src/main/res/drawable/ic_launcher.xml").read_text()
build=(root/"app/build.gradle.kts").read_text()
assert 'painterResource(id=R.drawable.ic_launcher)' in shell
assert 'contentDescription="DouPad app logo"' in shell
assert 'Text("DOUPAD"' in shell
assert 'Control your PC & TV from your phone' in shell
assert 'Color(0xFFF7F4F1)' in shell and 'Color(0xFFF7F4F1)' in theme
assert 'Color(0xFFF0ECE9)' in shell and 'Color(0xFFF0ECE9)' in theme
assert 'Color(0xFF121212)' in shell
assert 'Color(0xFF0CCB37)' in shell
assert 'Text("Saved Devices"' in shell
assert 'saved.take(2).forEach' in shell
assert 'text={Text("Rename")}' in shell
assert 'text={Text("Remove from saved")}' in shell
assert 'Text(if(live)"Open" else "Connect"' in shell
for name in ('Mouse Pad','Air Mouse','Remote','Mirror'):
    assert f'ConceptAction("{name}"' in shell
assert 'modifier=modifier.height(83.dp)' in shell
assert 'navigationBarsPadding()' in shell
assert 'Modifier.width(56.dp).height(29.dp)' in shell
assert 'Triple("Tools",Icons.Default.Handyman,"tools")' in shell
assert 'Triple("Apps",Icons.Default.GridView,"apps")' in shell
assert 'Triple("Shell",Icons.Default.Terminal,"shellFull")' in shell
assert 'Triple("Info",Icons.Default.Info,"infoFull")' in shell
assert 'FontFamily.SansSerif' in typography
assert 'FontWeight.Black' in typography
assert '#171717' in logo and '#FFFFFF' in logo
assert 'versionCode = 14' in build
assert '0.14.0-mirror-recovery-beta' in build
print("DOUPAD_PRO_V12_REFERENCE_UI_SOURCE_PASS")
