from pathlib import Path
root=Path(__file__).resolve().parents[1]
shell=(root/"app/src/main/java/com/unipoint/presentation/ui/DoupadShell.kt").read_text()
theme=(root/"app/src/main/java/com/unipoint/presentation/ui/theme/Theme.kt").read_text()
mouse=(root/"app/src/main/java/com/unipoint/presentation/ui/screens/android/AndroidMousePadScreen.kt").read_text()
icon=(root/"app/src/main/res/drawable/ic_launcher.xml").read_text()
main=(root/"app/src/main/java/com/unipoint/presentation/ui/MainActivity.kt").read_text()
build=(root/"app/build.gradle.kts").read_text()
assert 'Text("DOUPAD"' in shell
assert 'Control your PC & TV from your phone' in shell
assert 'Ready to connect' in shell
assert 'Text("Saved Devices"' in shell and 'Text("See All"' in shell
assert 'saved.take(2).forEach' in shell
assert 'Text(if(live)"Open" else "Connect"' in shell
assert 'text={Text("Rename")}' in shell
assert 'text={Text("Remove from saved")}' in shell
for label in ('Mouse Pad','Air Mouse','Remote','Mirror'):
    assert f'ConceptAction("{label}"' in shell
assert 'Triple("Home",Icons.Default.Home,0)' in shell
assert 'Triple("Devices",Icons.Default.Computer,4)' in shell
assert 'Triple("Tools",Icons.Default.Handyman,3)' in shell
assert 'Triple("Settings",Icons.Default.Settings,5)' in shell
assert '5->{' in shell
assert 'listOf("Saved","All","TV","PC")' in shell
assert '0xFFF7F4F1' in theme and '0xFFF0ECE9' in theme
assert '0xFFFCFAF8' in mouse
assert '#171717' in icon and '#FFFFFF' in icon
assert 'isAppearanceLightStatusBars = true' in main
assert 'versionCode = 12' in build
assert '0.12.0-reference-perfect-beta' in build
print("DOUPAD_PRO_CONCEPT_04_IMPLEMENTATION_PASS")
